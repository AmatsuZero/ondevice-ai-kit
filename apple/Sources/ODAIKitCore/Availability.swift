import Foundation
public enum CapabilityStatus: String, Codable, Sendable { case available, unavailable, requiresAssets }
public enum UnavailableReason: String, Codable, Sendable {
    case requiresOSVersion, backendNotLinked, permissionDenied, unsupportedDevice, missingAssets, closed
}
public struct CapabilityAvailability: Codable, Sendable, Equatable {
    public let status: CapabilityStatus
    public let reason: UnavailableReason?
    public let backendId: String
    public let minOS: String?
    public let offline: Bool
    public init(status: CapabilityStatus, reason: UnavailableReason? = nil, backendId: String, minOS: String? = nil, offline: Bool = true) {
        self.status = status; self.reason = reason; self.backendId = backendId; self.minOS = minOS; self.offline = offline
    }
}
public enum KitError: Error, Equatable, Sendable { case busy, closed, cancelled, invalidInput(String), backend(String) }
public enum TaskTerminal: String, Codable, Sendable { case completed, failed, cancelled }
