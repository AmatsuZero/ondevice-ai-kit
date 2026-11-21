import Foundation
import ODAIKitCore

public enum VideoAudioPolicy: String, Sendable, Equatable, Codable { case preserve, discard }

public struct VideoProcessingRequest: Sendable, Equatable {
    public let inputFile: URL
    public let scaleFactor: Int
    public let audioPolicy: VideoAudioPolicy

    public init(inputFile: URL, scaleFactor: Int, audioPolicy: VideoAudioPolicy = .preserve) throws {
        guard inputFile.isFileURL else { throw VideoClientError.invalidInput("video input must be a file URL") }
        guard scaleFactor >= 2 else { throw VideoClientError.invalidInput("scale factor must be at least 2") }
        self.inputFile = inputFile; self.scaleFactor = scaleFactor; self.audioPolicy = audioPolicy
    }
}

public struct VideoOutputInfo: Sendable, Equatable {
    public let width: Int
    public let height: Int
    public let frameCount: Int
    public let durationSeconds: Double
    public let audioTrackCount: Int

    public init(width: Int, height: Int, frameCount: Int, durationSeconds: Double, audioTrackCount: Int) throws {
        guard width > 0, height > 0, frameCount > 0, durationSeconds.isFinite, durationSeconds > 0,
              audioTrackCount >= 0 else { throw VideoClientError.invalidInput("invalid video output metadata") }
        self.width = width; self.height = height; self.frameCount = frameCount
        self.durationSeconds = durationSeconds; self.audioTrackCount = audioTrackCount
    }
}

public struct VideoProcessingProgress: Sendable, Equatable {
    public let completedFrames: Int
    public let totalFrames: Int?

    public init(completedFrames: Int, totalFrames: Int? = nil) throws {
        guard completedFrames >= 0 else { throw VideoClientError.invalidInput("completed frames must be non-negative") }
        if let totalFrames { guard totalFrames > 0, completedFrames <= totalFrames else { throw VideoClientError.invalidInput("invalid video progress") } }
        self.completedFrames = completedFrames; self.totalFrames = totalFrames
    }
}

public protocol VideoOutputLease: Sendable {
    var file: URL { get }
    var info: VideoOutputInfo { get }
    func release() async throws
}

public struct VideoProcessingResult: Sendable {
    public let backendId: String
    public let output: any VideoOutputLease
    public init(backendId: String, output: any VideoOutputLease) { self.backendId = backendId; self.output = output }
}

public enum VideoBackendSelection: Sendable, Equatable { case `default`, explicit(String) }

public protocol VideoSuperResolutionBackend: Sendable {
    var backendId: String { get }
    var supportedScaleFactors: [Int] { get }
    var supportedAudioPolicies: [VideoAudioPolicy] { get }
    func availability() async -> CapabilityAvailability
    func process(_ request: VideoProcessingRequest,
                 progress: (@Sendable (VideoProcessingProgress) -> Void)?) async throws -> any VideoOutputLease
    func drain() async
    func close() async
}

public struct VideoBackendDescriptor: Sendable, Equatable {
    public let backendId: String
    public let availability: CapabilityAvailability
    public let supportedScaleFactors: [Int]
    public let supportedAudioPolicies: [VideoAudioPolicy]
    public init(backendId: String, availability: CapabilityAvailability,
                supportedScaleFactors: [Int], supportedAudioPolicies: [VideoAudioPolicy]) {
        self.backendId = backendId; self.availability = availability
        self.supportedScaleFactors = supportedScaleFactors; self.supportedAudioPolicies = supportedAudioPolicies
    }
}

public enum VideoClientError: Error, Equatable, Sendable {
    case invalidInput(String)
    case invalidRegistration(String)
    case backendNotRegistered(String)
    case backendUnavailable(String, UnavailableReason)
    case unsupportedScaleFactor(Int)
    case unsupportedAudioPolicy(VideoAudioPolicy)
    case busy
    case closed
    case cancelled
}
