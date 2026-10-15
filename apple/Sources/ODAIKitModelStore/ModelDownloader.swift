import Foundation
public protocol ModelDownloadTransport: Sendable {
    func download(file: ModelFile, to destination: URL, progress: @escaping @Sendable (Int64) -> Void) async throws
}
public struct HTTPSDownloadPolicy: Sendable {
    public let allowedHosts: Set<String>
    public init(allowedHosts: Set<String>) { self.allowedHosts = Set(allowedHosts.map { $0.lowercased() }) }
    public func validate(_ url: URL) throws {
        guard url.scheme == "https", let host = url.host?.lowercased(), allowedHosts.contains(host),
              url.user == nil, url.password == nil, url.port == nil || url.port == 443 else { throw ModelStoreError.invalidSource }
    }
}
public struct HTTPSModelDownloader: ModelDownloadTransport {
    public let policy: HTTPSDownloadPolicy
    public init(allowedHosts: Set<String>) { self.policy = HTTPSDownloadPolicy(allowedHosts: allowedHosts) }
    public func download(file: ModelFile, to destination: URL, progress: @escaping @Sendable (Int64) -> Void) async throws {
        try policy.validate(file.url)
        let operation = DownloadOperation(file: file, destination: destination, policy: policy, progress: progress)
        try await withTaskCancellationHandler(operation: { try await operation.run() }, onCancel: { operation.cancel() })
    }
}
private final class DownloadOperation: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let file: ModelFile
    private let destination: URL
    private let policy: HTTPSDownloadPolicy
    private let progress: @Sendable (Int64) -> Void
    private let lock = NSLock()
    private var cancelled = false
    private var task: URLSessionDownloadTask?
    private var continuation: CheckedContinuation<Void, Error>?
    // URLSession delivers delegate callbacks serially.
    private var resultError: Error?
    init(file: ModelFile, destination: URL, policy: HTTPSDownloadPolicy, progress: @escaping @Sendable (Int64) -> Void) {
        self.file = file; self.destination = destination; self.policy = policy; self.progress = progress
    }
    func run() async throws {
        try await withCheckedThrowingContinuation { continuation in
            lock.lock()
            self.continuation = continuation
            let configuration = URLSessionConfiguration.ephemeral
            configuration.timeoutIntervalForRequest = 60; configuration.timeoutIntervalForResource = 3600
            configuration.urlCache = nil; configuration.httpCookieStorage = nil
            let session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
            let task = session.downloadTask(with: file.url); self.task = task
            let cancelled = self.cancelled
            lock.unlock()
            task.resume(); if cancelled { task.cancel() }
        }
    }
    func cancel() { lock.lock(); cancelled = true; let task = self.task; lock.unlock(); task?.cancel() }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        do { guard let url = request.url else { throw ModelStoreError.invalidSource }; try policy.validate(url); completionHandler(request) }
        catch { resultError = error; completionHandler(nil); task.cancel() }
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesWritten > file.bytes || (totalBytesExpectedToWrite > file.bytes) { resultError = ModelStoreError.integrity; downloadTask.cancel() }
        else { progress(totalBytesWritten) }
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        do {
            guard let response = downloadTask.response as? HTTPURLResponse, response.statusCode == 200,
                  let finalURL = response.url else { throw ModelStoreError.invalidSource }
            try policy.validate(finalURL)
            guard try location.resourceValues(forKeys: [.fileSizeKey]).fileSize == Int(file.bytes) else { throw ModelStoreError.integrity }
            try FileManager.default.moveItem(at: location, to: destination)
        } catch { resultError = error }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock(); let continuation = self.continuation; self.continuation = nil; let cancelled = self.cancelled; lock.unlock()
        session.finishTasksAndInvalidate()
        if cancelled { continuation?.resume(throwing: CancellationError()) }
        else if let error = resultError ?? error { continuation?.resume(throwing: error) }
        else { continuation?.resume() }
    }
}
