import Foundation
import CoreMedia
import CryptoKit

/// Diagnostics for the exact owned video copy and decoded frames, not a quality claim.
public struct MLXVLMVideoMetadata: Sendable, Equatable {
    public struct Time: Sendable, Equatable {
        public let value: Int64
        public let timescale: Int32
        init(_ time: CMTime) { value = time.value; timescale = time.timescale }
    }
    public let snapshotSHA256: String
    public let requestedTimes: [Time]
    public let actualTimes: [Time]
    public let clipDuration: Time
    public let sampledSpan: Time
    public let widths: [Int]
    public let heights: [Int]

    init(snapshot: MLXVLMVideoSnapshot) throws {
        snapshotSHA256 = SHA256.hash(data: try Data(contentsOf: snapshot.file))
            .map { String(format: "%02x", $0) }.joined()
        requestedTimes = snapshot.frames.map { Time($0.requestedTime) }
        actualTimes = snapshot.frames.map { Time($0.actualTime) }
        clipDuration = Time(snapshot.duration)
        sampledSpan = Time(snapshot.frames.last!.actualTime - snapshot.frames.first!.actualTime)
        widths = snapshot.frames.map { $0.image.width }
        heights = snapshot.frames.map { $0.image.height }
    }
}
