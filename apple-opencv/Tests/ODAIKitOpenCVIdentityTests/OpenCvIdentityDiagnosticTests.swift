import XCTest
import Foundation
import ODAIKitIdentity
@testable import ODAIKitOpenCVIdentity

final class OpenCvIdentityDiagnosticTests: XCTestCase {
    func testNativeCategoriesPreserveIdentityCodesAndSanitizedStages() {
        let cases: [(Int, IdentityErrorCode, OpenCvIdentityDiagnostic.Category)] = [
            (1, .invalidInput, .invalidInput), (2, .multipleFaces, .multipleFaces),
            (3, .cancelled, .stopped), (4, .backendUnavailable, .unavailable)]
        for (nativeCode, identityCode, category) in cases {
            let failure = OpenCvIdentityNativeFailure(NSError(domain: "io.ondeviceai.opencv.identity",
                code: nativeCode, userInfo: ["stage": "detection", NSLocalizedDescriptionKey: "/private/raw-image.png"]))
            var observed: [OpenCvIdentityDiagnostic] = []
            let error = failure.identityError(backendId: "opencv.identity.apple", modelBinding: nil,
                diagnostic: { observed.append($0) })
            XCTAssertEqual(error.code, identityCode)
            XCTAssertEqual(error.backendId, "opencv.identity.apple")
            XCTAssertEqual(observed.count, 1)
            XCTAssertEqual(observed.first?.stage, .detection)
            XCTAssertEqual(observed.first?.category, category)
            XCTAssertFalse(String(describing: observed).contains("raw-image"))
        }
    }

    func testUnknownStagesAndForeignDomainsCannotSupplyDiagnosticTextOrCodes() {
        let malformedStage = OpenCvIdentityNativeFailure(NSError(domain: "io.ondeviceai.opencv.identity",
            code: 4, userInfo: ["stage": "/private/model.onnx"]))
        XCTAssertEqual(malformedStage.diagnostic.stage, .unknown)
        let foreign = OpenCvIdentityNativeFailure(NSError(domain: "foreign", code: 1,
            userInfo: ["stage": "embedding", NSLocalizedDescriptionKey: "private native exception"]))
        XCTAssertEqual(foreign.code, .backendUnavailable)
        XCTAssertEqual(foreign.diagnostic.stage, .unknown)
        XCTAssertEqual(foreign.diagnostic.category, .unavailable)
        XCTAssertFalse(String(describing: foreign).contains("private"))
    }

    func testAllKnownNativeStagesAndUnknownCodeHaveStableAllowlistedValues() {
        for stage in ["detectorLoad", "recognizerLoad", "decode", "detection", "alignment", "embedding"] {
            let failure = OpenCvIdentityNativeFailure(NSError(domain: "io.ondeviceai.opencv.identity",
                code: 999, userInfo: ["stage": stage]))
            XCTAssertEqual(failure.diagnostic.stage.rawValue, stage)
            XCTAssertEqual(failure.code, .backendUnavailable)
            XCTAssertEqual(failure.diagnostic.category, .unavailable)
        }
    }
}
