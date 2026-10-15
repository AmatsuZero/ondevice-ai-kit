import XCTest
import CoreGraphics
import ODAIKitFace
import ODAIKitCore
@testable import ODAIKitAppleVision
actor SuspendedFaceRunner {
    var started: CheckedContinuation<Void,Never>?
    var waiting: CheckedContinuation<[FaceObservation],Never>?
    var hasStarted = false
    func run() async -> [FaceObservation] { hasStarted = true; started?.resume(); started = nil; return await withCheckedContinuation { waiting = $0 } }
    func waitForStart() async { if !hasStarted { await withCheckedContinuation { started = $0 } } }
    func complete() { waiting?.resume(returning: []); waiting = nil }
}
final class VisionFaceDetectorTests: XCTestCase {
    func image() -> ImageInput {
        let context = CGContext(data: nil, width: 64, height: 64, bitsPerComponent: 8, bytesPerRow: 256, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        context.setFillColor(CGColor(gray: 1, alpha: 1)); context.fill(CGRect(x: 0,y: 0,width: 64,height: 64))
        return ImageInput(image: context.makeImage()!)
    }
    func testBlankImageUsesRealVisionAndReturnsSuccessfulEmptyResult() async throws {
        let detector = VisionFaceDetector()
        let result = try await detector.detect(input: image(), options: FaceOptions())
        XCTAssertTrue(result.faces.isEmpty); XCTAssertEqual(result.backendId, "apple.vision.face")
        await detector.close()
        do { _ = try await detector.detect(input: image(), options: FaceOptions()); XCTFail("closed") }
        catch { XCTAssertEqual(error as? KitError, .closed) }
    }
    func testCancelledRequestRetainsBackendUntilCompletion() async throws {
        let runner = SuspendedFaceRunner()
        let detector = VisionFaceDetector(runner: { _,_ in await runner.run() })
        let input = image(); let task = Task { try await detector.detect(input: input, options: FaceOptions()) }
        await runner.waitForStart(); task.cancel()
        do { _ = try await detector.detect(input: input, options: FaceOptions()); XCTFail("busy until backend returns") }
        catch { XCTAssertEqual(error as? KitError, .busy) }
        await runner.complete()
        do { _ = try await task.value; XCTFail("cancelled") } catch is CancellationError { }
        await detector.close()
    }
    func testBackendFailureIsNotNoFaces() async throws {
        let detector = VisionFaceDetector(runner: { _,_ in throw KitError.backend("test") })
        do { _ = try await detector.detect(input: image(), options: FaceOptions()); XCTFail("must fail") }
        catch { XCTAssertEqual(error as? KitError, .backend("test")) }
    }
}
