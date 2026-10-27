import XCTest
import UIKit

@MainActor final class CameraOwnerReleaseTests: XCTestCase {
    func testControllerDestructionClosesRetainedFaceBridgeAndReleasesInputs() async throws {
        var owner: DemoController? = DemoController()
        weak var releasedOwner = owner
        let bridge = try XCTUnwrap(owner?.bridge)
        defer { bridge.close() }
        let image = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
        bridge.inputs["pending"] = IOSFaceBridge.InputEntry(image: try XCTUnwrap(image.cgImage), finished: {})
        owner = nil
        XCTAssertNil(releasedOwner)
        for _ in 0..<100 {
            if !bridge.availability().available { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertFalse(bridge.availability().available, "Destroying the Demo must close bridge admission")
        XCTAssertTrue(bridge.inputs.isEmpty, "Destroying the Demo must release pending images")
        for _ in 0..<100 {
            if await bridge.detector.availability().reason == .closed { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        let availability = await bridge.detector.availability()
        XCTAssertEqual(availability.reason, .closed)
    }
}
