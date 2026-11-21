import XCTest
import ODAIKitCore
final class AvailabilityTests: XCTestCase {
    func testAvailableCanBeDecoded() throws {
        XCTAssertEqual(try JSONDecoder().decode(CapabilityStatus.self, from: Data("\"available\"".utf8)), .available)
    }
}
