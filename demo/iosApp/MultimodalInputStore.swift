import Darwin
import Foundation

/// 每种媒体独立持有注册表；操作租约的生命周期长于选择所有权。
@MainActor
final class MultimodalInputStore: IOSMultimodalInputProvider {
  enum CopyPhase: Sendable { case beforeSourceOpen, chunkWritten }
  private struct Entry {
    let url: URL
    var selected = true
    var leases = 0
  }
  private struct CopyOutcome: Sendable {
    let owned: Bool
    let error: (any Error)?
  }
  private let directory: URL
  private let maxBytes: Int
  private let checkpoint: @Sendable (CopyPhase) throws -> Void
  private let startAccess: (URL) -> Bool
  private let stopAccess: (URL) -> Void
  private var entries: [String: Entry] = [:]
  private var inflight: [String: Task<CopyOutcome, Never>] = [:]
  private var closed = false

  init(
    directory: URL = FileManager.default.temporaryDirectory
      .appendingPathComponent("odai-multimodal-input-" + UUID().uuidString, isDirectory: true),
    maxBytes: Int = 32 * 1024 * 1024,
    checkpoint: @escaping @Sendable (CopyPhase) throws -> Void = { _ in },
    startAccess: @escaping (URL) -> Bool = { $0.startAccessingSecurityScopedResource() },
    stopAccess: @escaping (URL) -> Void = { $0.stopAccessingSecurityScopedResource() }
  ) throws {
    guard maxBytes > 0 else { throw InputError.invalidSource }
    self.directory = directory
    self.maxBytes = maxBytes
    self.checkpoint = checkpoint
    self.startAccess = startAccess
    self.stopAccess = stopAccess
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
  }

  func startImport(from source: URL) throws -> Task<String, Error> {
    guard !closed, source.isFileURL else { throw InputError.invalidSource }
    let scoped = startAccess(source)
    let token = UUID().uuidString.lowercased()
    let destination = directory.appendingPathComponent(token)
    let maxBytes = maxBytes
    let checkpoint = checkpoint
    let worker = Task.detached(priority: .userInitiated) {
      var owned = false
      do {
        try Self.copy(from: source, to: destination, maxBytes: maxBytes, owned: &owned) { phase in
          try Task.checkCancellation()
          try checkpoint(phase)
          try Task.checkCancellation()
        }
        return CopyOutcome(owned: owned, error: nil)
      } catch {
        return CopyOutcome(owned: owned, error: error)
      }
    }
    inflight[token] = worker
    return Task {
      let outcome = await withTaskCancellationHandler {
        await worker.value
      } onCancel: {
        worker.cancel()
      }
      // 只有 worker 退出并关闭描述符后，才释放 scope 和目录的在途所有权。
      defer {
        if scoped { stopAccess(source) }
        inflight.removeValue(forKey: token)
        removeEmptyDirectory()
      }
      if outcome.owned { entries[token] = Entry(url: destination, selected: false) }
      do {
        guard !closed, !Task.isCancelled, !worker.isCancelled else { throw CancellationError() }
        if let error = outcome.error { throw error }
        entries[token]?.selected = true
        return token
      } catch {
        collect(token)
        throw error
      }
    }
  }

  func importImage(
    from source: URL, checkpoint: () throws -> Void = { try Task.checkCancellation() }
  ) throws -> String {
    guard !closed, source.isFileURL else { throw InputError.invalidSource }
    let scoped = startAccess(source)
    defer { if scoped { stopAccess(source) } }
    let token = UUID().uuidString.lowercased()
    let destination = directory.appendingPathComponent(token)
    var owned = false
    do {
      try Self.copy(from: source, to: destination, maxBytes: maxBytes, owned: &owned) { _ in
        try checkpoint()
      }
    } catch {
      if owned { entries[token] = Entry(url: destination, selected: false) }
      collect(token)
      throw error
    }
    entries[token] = Entry(url: destination)
    return token
  }

  nonisolated private static func copy(
    from source: URL, to destination: URL, maxBytes: Int, owned: inout Bool,
    checkpoint: (CopyPhase) throws -> Void
  ) throws {
    try checkpoint(.beforeSourceOpen)
    let descriptor = source.withUnsafeFileSystemRepresentation { path in
      path.map { Darwin.open($0, O_RDONLY | O_NOFOLLOW | O_NONBLOCK) } ?? -1
    }
    guard descriptor >= 0 else { throw InputError.invalidSource }
    defer { Darwin.close(descriptor) }
    var metadata = stat()
    guard fstat(descriptor, &metadata) == 0,
      metadata.st_mode & S_IFMT == S_IFREG
    else { throw InputError.invalidSource }
    let output = destination.withUnsafeFileSystemRepresentation { path in
      path.map { Darwin.open($0, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, S_IRUSR | S_IWUSR) }
        ?? -1
    }
    guard output >= 0 else { throw InputError.invalidSource }
    owned = true
    defer { Darwin.close(output) }
    var buffer = [UInt8](repeating: 0, count: 64 * 1024)
    var total = 0
    while true {
      let count = Darwin.read(descriptor, &buffer, buffer.count)
      if count < 0 {
        if errno == EINTR { continue }
        throw InputError.invalidSource
      }
      if count == 0 { break }
      guard count <= maxBytes - total else { throw InputError.invalidSource }
      try buffer.withUnsafeBytes { bytes in
        var written = 0
        while written < count {
          let result = Darwin.write(
            output, bytes.baseAddress!.advanced(by: written), count - written)
          if result < 0 && errno == EINTR { continue }
          guard result > 0 else { throw InputError.invalidSource }
          written += result
        }
      }
      total += count
      try checkpoint(.chunkWritten)
    }
    guard total > 0 else { throw InputError.invalidSource }
  }

  func acquire(reference: String) throws -> any IOSMultimodalInputLease {
    guard !closed, var entry = entries[reference], entry.selected else {
      throw InputError.invalidSource
    }
    entry.leases += 1
    entries[reference] = entry
    return Lease(url: entry.url) {
      self.entries[reference]?.leases -= 1
      self.collect(reference)
    }
  }

  func remove(reference: String) {
    entries[reference]?.selected = false
    collect(reference)
  }
  func close() {
    closed = true
    for worker in inflight.values { worker.cancel() }
    for reference in Array(entries.keys) { remove(reference: reference) }
    removeEmptyDirectory()
  }
  private func collect(_ reference: String) {
    if let entry = entries[reference], !entry.selected, entry.leases == 0 {
      do {
        try FileManager.default.removeItem(at: entry.url)
        entries.removeValue(forKey: reference)
      } catch { return }
    }
    removeEmptyDirectory()
  }
  private func removeEmptyDirectory() {
    if closed && entries.isEmpty && inflight.isEmpty {
      directory.withUnsafeFileSystemRepresentation { path in
        if let path { _ = rmdir(path) }
      }
    }
  }
  private enum InputError: Error { case invalidSource }
  private final class Lease: IOSMultimodalInputLease {
    let url: URL
    private var action: (() -> Void)?
    init(url: URL, action: @escaping () -> Void) {
      self.url = url
      self.action = action
    }
    func release() {
      let callback = action
      action = nil
      callback?()
    }
  }
}
