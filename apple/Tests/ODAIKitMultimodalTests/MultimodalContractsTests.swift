import Foundation
import XCTest
import ODAIKitCore
import ODAIKitMultimodal

final class MultimodalContractsTests: XCTestCase {
    func testRequestRejectsNonFileImageBlankPromptAndNonPositiveBudget() throws {
        let file = URL(fileURLWithPath: "/nonexistent/multimodal-fixture.png")
        XCTAssertThrowsError(try MultimodalRequest(imageFile: URL(string: "https://example.invalid/image.png")!, prompt: "Describe"))
        for prompt in ["", " \n\t"] {
            XCTAssertThrowsError(try MultimodalRequest(imageFile: file, prompt: prompt))
        }
        for limit in [0, -1] {
            XCTAssertThrowsError(try MultimodalRequest(imageFile: file, prompt: "Describe", maxOutputTokens: limit))
        }
        let request = try MultimodalRequest(imageFile: file, prompt: " Describe this image. ")
        XCTAssertEqual(request.maxOutputTokens, 128)
        XCTAssertEqual(request.prompt, " Describe this image. ", "Validation must not rewrite the prompt")
        XCTAssertEqual(request.imageFile, file, "Request construction must not open the file")
    }

    func testModelIdentityRejectsEachBlankVersionWithoutAssumingGitSha() throws {
        for fields in [("", "revision", "runtime"), ("model", " \n", "runtime"), ("model", "revision", "\t")] {
            XCTAssertThrowsError(try MultimodalModelIdentity(modelId: fields.0, modelRevision: fields.1, runtimeRevision: fields.2))
        }
        let identity = try MultimodalModelIdentity(modelId: "fixture", modelRevision: "1.2", runtimeRevision: "runtime-3")
        XCTAssertEqual(identity.modelRevision, "1.2")
    }

    func testUpdateRejectsNegativeSequenceAndAllowsInitialEmptySnapshot() throws {
        XCTAssertThrowsError(try MultimodalTextUpdate(sequence: -1, text: "late"))
        let update = try MultimodalTextUpdate(sequence: 0, text: "")
        XCTAssertEqual(update.text, "")
    }

    func testOutputAndResultRejectEmptyTextAndBackendIdentifier() throws {
        let identity = try MultimodalModelIdentity(modelId: "fixture", modelRevision: "r1", runtimeRevision: "v1")
        for text in ["", " \n"] { XCTAssertThrowsError(try MultimodalTextOutput(text: text, model: identity)) }
        let output = try MultimodalTextOutput(text: "A red square.", model: identity)
        XCTAssertThrowsError(try MultimodalGenerationResult(backendId: "\n", output: output))
        let result = try MultimodalGenerationResult(backendId: "fixture.multimodal", output: output)
        XCTAssertEqual(result.output.text, "A red square.")
        XCTAssertEqual(result.output.model.runtimeRevision, "v1")
    }
}
