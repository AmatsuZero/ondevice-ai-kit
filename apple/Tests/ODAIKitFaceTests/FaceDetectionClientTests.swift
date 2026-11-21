import CoreGraphics
import XCTest
import ODAIKitCore
@testable import ODAIKitFace

private actor FixtureFaceBackend: FaceDetector {
    nonisolated let backendId: String
    var available = true
    var detectCount = 0
    var closeCount = 0
    init(_ id: String, available: Bool = true) { self.backendId = id; self.available = available }
    func availability() -> CapabilityAvailability {
        CapabilityAvailability(status: available ? .available : .unavailable,
                               reason: available ? nil : .unsupportedDevice, backendId: backendId)
    }
    func detect(input: ImageInput, options: FaceOptions) throws -> FaceResult {
        detectCount += 1
        return FaceResult(frameId: input.frameId, timestampNs: input.timestampNs,
                          orientedWidth: input.orientedWidth, orientedHeight: input.orientedHeight,
                          previewTransform: PreviewTransform(scaleX: 1, scaleY: 1, offsetX: 0, offsetY: 0),
                          backendId: backendId, faces: [])
    }
    func close() { closeCount += 1 }
}

final class FaceDetectionClientTests: XCTestCase {
    private func input() -> ImageInput {
        let context = CGContext(data: nil, width: 2, height: 2, bitsPerComponent: 8,
                                bytesPerRow: 8, space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        return ImageInput(image: context.makeImage()!)
    }

    func testSharedSelectionContractAndNoFallback() async throws {
        let file = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("../../../fixtures/contracts/backend-selection.json").standardizedFileURL
        let fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        let defaultId = try XCTUnwrap(fixture["defaultBackendId"] as? String)
        let alternateId = try XCTUnwrap(fixture["alternateBackendId"] as? String)
        let local = FixtureFaceBackend(defaultId)
        let cloud = FixtureFaceBackend(alternateId, available: false)
        let client = try FaceDetectionClient(defaultBackendId: defaultId, registrations: [
            .init(capabilityId: "face.detection", backendId: defaultId, detector: local),
            .init(capabilityId: "face.detection", backendId: alternateId, detector: cloud)
        ])
        let result = try await client.detect(input: input())
        XCTAssertEqual(result.backendId, defaultId)
        do { _ = try await client.detect(input: input(), backend: .explicit(alternateId)); XCTFail("unavailable") }
        catch { XCTAssertEqual(error as? FaceDetectionClientError,
                               .backendUnavailable(capabilityId: "face.detection", backendId: alternateId, reason: .unsupportedDevice)) }
        let cloudDetectCount = await cloud.detectCount
        XCTAssertEqual(cloudDetectCount, 0)
        do { _ = try await client.detect(input: input(), backend: .explicit("test.face.missing")); XCTFail("missing") }
        catch { XCTAssertEqual(error as? FaceDetectionClientError,
                               .backendNotRegistered(capabilityId: "face.detection", backendId: "test.face.missing")) }
        do { _ = try await client.detect(input: input(), optionIds: ["vendor.unknown"]); XCTFail("option") }
        catch { XCTAssertEqual(error as? FaceDetectionClientError,
                               .unsupportedOption(capabilityId: "face.detection", backendId: defaultId, optionId: "vendor.unknown")) }
        let localDetectCount = await local.detectCount
        XCTAssertEqual(localDetectCount, 1)
        await client.close()
        let localCloseCount = await local.closeCount
        let cloudCloseCount = await cloud.closeCount
        XCTAssertEqual(localCloseCount, 1)
        XCTAssertEqual(cloudCloseCount, 1)
    }

    func testRegistrationRejectsDuplicateAndWrongCapability() throws {
        let backend = FixtureFaceBackend("test.face.local")
        let registration = FaceDetectionRegistration(capabilityId: "face.detection", backendId: "test.face.local", detector: backend)
        XCTAssertThrowsError(try FaceDetectionClient(defaultBackendId: "test.face.local", registrations: [registration, registration]))
        XCTAssertThrowsError(try FaceDetectionClient(defaultBackendId: "test.face.local", registrations: [
            .init(capabilityId: "face.verification", backendId: "test.face.local", detector: backend)]))
    }

}
