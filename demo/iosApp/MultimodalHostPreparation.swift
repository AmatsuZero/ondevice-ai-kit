import Foundation

/// Owns preparation only. It never starts or restarts generation.
@MainActor
final class MultimodalHostPreparation {
  private var task: Task<Void, Never>?
  private var started = false

  func start(
    make: @escaping @MainActor () async throws -> MultimodalHost,
    ready: @escaping @MainActor (MultimodalHost?) -> Void
  ) {
    guard !started else { return }
    started = true
    // Never retain the owner across preparation; release still cancels late publication.
    task = Task { @MainActor [weak self] in
      do {
        try Task.checkCancellation()
        let host = try await make()
        guard !Task.isCancelled else {
          host.close()
          return
        }
        ready(host)
      } catch is CancellationError {
        self?.started = false
        // Owner cancellation must not publish a replacement or failure.
      } catch {
        self?.started = false
        if !Task.isCancelled { ready(nil) }
      }
    }
  }
  deinit { task?.cancel() }
}
