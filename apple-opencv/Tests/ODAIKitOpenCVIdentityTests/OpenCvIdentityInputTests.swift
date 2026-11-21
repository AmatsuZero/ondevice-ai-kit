import XCTest
import Foundation
import CoreGraphics
import ImageIO
import ODAIKitIdentity
@testable import ODAIKitOpenCVIdentity

final class OpenCvIdentityInputTests: XCTestCase {
    func testAcceptsBoundedEncodedRasterAndRejectsBadOrOversizedInput() throws {
        XCTAssertNoThrow(try OpenCvIdentityInput.validate(png(width: 64, height: 64)))
        for invalid in [Data(), Data([1, 2, 3]), Data(repeating: 0, count: 10 * 1024 * 1024 + 1),
                        try png(width: 15, height: 64), try png(width: 4097, height: 16)] {
            XCTAssertThrowsError(try OpenCvIdentityInput.validate(invalid)) {
                XCTAssertEqual(($0 as? IdentityError)?.code, .invalidInput)
            }
        }
    }

    private func png(width: Int, height: Int) throws -> Data {
        let pixels = Data(repeating: 128, count: width * height)
        let provider = try XCTUnwrap(CGDataProvider(data: pixels as CFData))
        let image = try XCTUnwrap(CGImage(width: width, height: height, bitsPerComponent: 8,
            bitsPerPixel: 8, bytesPerRow: width, space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGBitmapInfo(rawValue: 0), provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent))
        let data = NSMutableData()
        let output = try XCTUnwrap(CGImageDestinationCreateWithData(data, "public.png" as CFString, 1, nil))
        CGImageDestinationAddImage(output, image, nil)
        XCTAssertTrue(CGImageDestinationFinalize(output))
        return data as Data
    }
}
