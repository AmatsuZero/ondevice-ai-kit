import AVFoundation
import XCTest

final class CameraSourceCloseTests: XCTestCase {
    func testClosedCameraRejectsCallbackRegistrationAndReleasesItsCapture() async {
        let camera = CameraSource(session: AVCaptureSession())
        await camera.close()
        var retained: FrameCallbackOwner? = FrameCallbackOwner()
        weak var released = retained

        camera.onFrame = { [token = retained!] _ in _ = token }
        retained = nil

        XCTAssertNil(camera.onFrame, "Terminal close must reject later callback registration")
        XCTAssertNil(released, "A closed camera must not retain newly registered callback owners")
    }

    func testCloseDetachesOutputsAndReleasesFrameCallback() async throws {
        let session = AVCaptureSession()
        let frameQueue = DispatchQueue(label: "camera.close.test.frames")
        let camera = CameraSource(session: session, frameQueue: frameQueue)
        let output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(camera, queue: frameQueue)
        session.addOutput(output)
        XCTAssertEqual(session.outputs.count, 1)
        var retained: FrameCallbackOwner? = FrameCallbackOwner()
        weak var released = retained
        camera.onFrame = { [token = retained!] _ in _ = token }
        retained = nil
        XCTAssertNotNil(released)

        await camera.close()

        XCTAssertFalse(session.isRunning)
        XCTAssertTrue(session.outputs.isEmpty)
        XCTAssertNil(output.sampleBufferDelegate)
        XCTAssertNil(output.sampleBufferCallbackQueue)
        XCTAssertNil(camera.onFrame)
        XCTAssertNil(released, "Closing must release objects retained by the frame callback")
    }

    func testReplacingCallbackReleasesCapturesOutsideTheRegistrationLock() async {
        let camera = CameraSource(session: AVCaptureSession())
        let released = expectation(description: "released capture can read callback state")
        var token: CallbackCapture? = CallbackCapture {
            XCTAssertNil(camera.onFrame)
            released.fulfill()
        }
        camera.onFrame = { [captured = token!] _ in _ = captured }
        token = nil

        DispatchQueue.global().async { camera.onFrame = nil }

        await fulfillment(of: [released], timeout: 2)
        await camera.close()
    }

    func testConcurrentCallbackRegistrationCannotReopenClosedCamera() async {
        let camera = CameraSource(session: AVCaptureSession())
        let writers = expectation(description: "callback writers finish")
        writers.expectedFulfillmentCount = 4
        for _ in 0..<4 {
            DispatchQueue.global().async {
                for _ in 0..<500 {
                    camera.onFrame = { _ in }
                    _ = camera.onFrame
                    camera.onFrame = nil
                }
                writers.fulfill()
            }
        }

        await camera.close()
        await fulfillment(of: [writers], timeout: 5)
        XCTAssertNil(camera.onFrame)
        await camera.close()
    }

    func testCloseWaitsForQueuedFrameWork() async {
        let frameQueue = DispatchQueue(label: "camera.close.test.blocked-frames")
        let camera = CameraSource(session: AVCaptureSession(), frameQueue: frameQueue)
        let entered = expectation(description: "queued frame work entered")
        let prematureReturn = expectation(description: "close returned before frame work finished")
        prematureReturn.isInverted = true
        let release = DispatchSemaphore(value: 0)
        frameQueue.async { entered.fulfill(); release.wait() }
        await fulfillment(of: [entered], timeout: 2)
        let closing = Task {
            await camera.close()
            prematureReturn.fulfill()
        }
        await fulfillment(of: [prematureReturn], timeout: 0.2)
        release.signal()
        await closing.value
    }

    func testConcurrentRepeatedCloseIsIdempotent() async {
        let session = AVCaptureSession()
        let output = AVCaptureVideoDataOutput()
        session.addOutput(output)
        let camera = CameraSource(session: session)
        async let first: Void = camera.close()
        async let second: Void = camera.close()
        _ = await (first, second)
        await camera.close()
        XCTAssertTrue(session.outputs.isEmpty)
        XCTAssertNil(camera.onFrame)
    }

    func testDroppingCameraFinishesWorkerAndReleasesCallbackCapture() async {
        var camera: CameraSource? = CameraSource()
        weak var releasedCamera = camera
        let released = expectation(description: "worker closes after facade is released")
        var token: CallbackCapture? = CallbackCapture { released.fulfill() }
        camera?.onFrame = { [captured = token!] _ in _ = captured }
        token = nil

        camera = nil

        XCTAssertNil(releasedCamera, "The worker must not retain its facade")
        await fulfillment(of: [released], timeout: 2)
    }
}

private final class FrameCallbackOwner: Sendable {}

private final class CallbackCapture: Sendable {
    private let onRelease: @Sendable () -> Void
    init(onRelease: @escaping @Sendable () -> Void) { self.onRelease = onRelease }
    deinit { onRelease() }
}
