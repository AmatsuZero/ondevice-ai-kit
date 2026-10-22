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
    // The task captures no owner; releasing this object cancels late publication.
    task = Task { @MainActor in
      do {
        try Task.checkCancellation()
        let host = try await make()
        guard !Task.isCancelled else {
          host.close()
          return
        }
        ready(host)
      } catch is CancellationError {
        // Owner cancellation must not publish a replacement or failure.
      } catch {
        if !Task.isCancelled { ready(nil) }
      }
    }
  }
  deinit { task?.cancel() }
}
