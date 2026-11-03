import XCTest
import Foundation
@testable import ODAIKitIdentity

final class ExactCosineIdentityIndexTests: XCTestCase {
    private func embedding(_ values: [Float], revision: String = "os-build-fixture") throws -> IdentityEmbedding {
        try IdentityEmbedding(embeddingSpace: FixtureIdentityBackend.system(revision: revision).embeddingSpace, values: values)
    }

    func testExactScoresPreserveSnapshotOrder() throws {
        let retained = try [embedding([1, 0]), embedding([0.8, 0.6]), embedding([-1, 0])]
        let bytes = try ExactCosineIdentityIndex.build(retained, stopSignal: IdentityStopSignal())
        let scores = try ExactCosineIdentityIndex.scores(bytes, probe: retained[0], retained: retained, stopSignal: IdentityStopSignal())
        XCTAssertEqual(scores.count, 3)
        XCTAssertEqual(scores[0], 1, accuracy: 0.000001)
        XCTAssertEqual(scores[1], 0.8, accuracy: 0.000001)
        XCTAssertEqual(scores[2], -1, accuracy: 0.000001)
    }

    func testCorruptTruncatedAndReorderedSnapshotsAreRejected() throws {
        let retained = try [embedding([1, 0]), embedding([0, 1])]
        let bytes = try ExactCosineIdentityIndex.build(retained, stopSignal: IdentityStopSignal())
        var corrupted = bytes; corrupted[corrupted.count - 1] = 0
        var badHeader = bytes; badHeader[0] = 0
        for invalid in [Data(bytes.dropLast()), bytes + Data([0]), corrupted, badHeader] {
            assertCode(.featureFormatUnsupported) {
                _ = try ExactCosineIdentityIndex.scores(invalid, probe: retained[0], retained: retained, stopSignal: IdentityStopSignal())
            }
        }
        assertCode(.featureFormatUnsupported) {
            _ = try ExactCosineIdentityIndex.scores(bytes, probe: retained[0], retained: retained.reversed(), stopSignal: IdentityStopSignal())
        }
    }

    func testFullEmbeddingSpaceMustMatchProbeAndPayload() throws {
        let original = try embedding([1, 0]), other = try embedding([1, 0], revision: "foreign-space")
        let bytes = try ExactCosineIdentityIndex.build([original], stopSignal: IdentityStopSignal())
        assertCode(.embeddingSpaceMismatch) {
            _ = try ExactCosineIdentityIndex.scores(bytes, probe: other, retained: [original], stopSignal: IdentityStopSignal())
        }
        assertCode(.featureFormatUnsupported) {
            _ = try ExactCosineIdentityIndex.scores(bytes, probe: other, retained: [other], stopSignal: IdentityStopSignal())
        }
    }

    func testEmptyZeroNormMixedAndOversizedMatricesAreRejected() throws {
        let valid = try embedding([1, 0]), zero = try embedding([0, 0]), other = try embedding([1, 0], revision: "foreign-space")
        assertCode(.featureFormatUnsupported) { _ = try ExactCosineIdentityIndex.build([], stopSignal: IdentityStopSignal()) }
        assertCode(.invalidInput) { _ = try ExactCosineIdentityIndex.build([zero], stopSignal: IdentityStopSignal()) }
        assertCode(.featureFormatUnsupported) { _ = try ExactCosineIdentityIndex.build([valid, other], stopSignal: IdentityStopSignal()) }
        assertCode(.featureFormatUnsupported) {
            _ = try ExactCosineIdentityIndex.build(Array(repeating: valid, count: 4097), stopSignal: IdentityStopSignal())
        }
        let bytes = try ExactCosineIdentityIndex.build([valid], stopSignal: IdentityStopSignal())
        assertCode(.invalidInput) {
            _ = try ExactCosineIdentityIndex.scores(bytes, probe: zero, retained: [valid], stopSignal: IdentityStopSignal())
        }
    }

    func testUnsupportedDimensionAndScoreFormatsAreRejected() throws {
        let space = try FixtureIdentityBackend.system().embeddingSpace
        for (field, value): (String, Any) in [("dimension", 4097), ("dtype", "float16"), ("endianness", "big"),
                                             ("scoreMetric", "euclidean"), ("scoreDirection", "lowerIsBetter")] {
            var fields = space.fields; fields[field] = value
            let changed = try EmbeddingSpaceId.decodeJSON(identityJSON(fields))
            let vector = try IdentityEmbedding(embeddingSpace: changed, values: Array(repeating: 1, count: changed.dimension))
            assertCode(.featureFormatUnsupported) { _ = try ExactCosineIdentityIndex.build([vector], stopSignal: IdentityStopSignal()) }
        }
    }

    func testFiniteFloatExtremesKeepDoublePrecisionCosine() throws {
        for value in [Float.greatestFiniteMagnitude, Float.leastNonzeroMagnitude] {
            let vector = try embedding([value, value])
            let bytes = try ExactCosineIdentityIndex.build([vector], stopSignal: IdentityStopSignal())
            let scores = try ExactCosineIdentityIndex.scores(bytes, probe: vector, retained: [vector], stopSignal: IdentityStopSignal())
            XCTAssertEqual(try XCTUnwrap(scores.first), 1, accuracy: 0.000001)
        }
    }

    func testCancellationStopsBuildAndQuery() throws {
        let vector = try embedding([1, 0])
        let bytes = try ExactCosineIdentityIndex.build([vector], stopSignal: IdentityStopSignal())
        let stopped = IdentityStopSignal(); stopped.requestStop(.cancelled)
        assertCode(.cancelled) { _ = try ExactCosineIdentityIndex.build([vector], stopSignal: stopped) }
        assertCode(.cancelled) {
            _ = try ExactCosineIdentityIndex.scores(bytes, probe: vector, retained: [vector], stopSignal: stopped)
        }
    }

    private func assertCode(_ code: IdentityErrorCode, file: StaticString = #filePath, line: UInt = #line, _ body: () throws -> Void) {
        XCTAssertThrowsError(try body(), file: file, line: line) { error in
            XCTAssertEqual((error as? IdentityError)?.code, code, file: file, line: line)
        }
    }
}
