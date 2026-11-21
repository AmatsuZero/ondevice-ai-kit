import CoreImage
import CoreVideo
import Foundation
import XCTest
@testable import ODAIKitAppleVideo

final class AppleVideoOpaqueFrameTests: XCTestCase {
    func testNonfiniteAlphaPreservesSRGBColorAndSource() throws {
        try checkOpaqueConversion(colorSpace: XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB)), transfer: kCVImageBufferTransferFunction_sRGB)
    }

    func testNonfiniteAlphaDoesNotBlackenLinearColorConversion() throws {
        try checkOpaqueConversion(colorSpace: XCTUnwrap(CGColorSpace(name: CGColorSpace.linearSRGB)), transfer: kCVImageBufferTransferFunction_Linear)
    }

    private func checkOpaqueConversion(colorSpace: CGColorSpace, transfer: CFString) throws {
        let source = try fixture(alpha: .nan)
        let reference = try fixture(alpha: 1)
        for buffer in [source, reference] {
            CVBufferSetAttachment(buffer, kCVImageBufferCGColorSpaceKey, colorSpace, .shouldPropagate)
            CVBufferSetAttachment(buffer, kCVImageBufferTransferFunctionKey, transfer, .shouldPropagate)
            CVBufferSetAttachment(buffer, kCVImageBufferColorPrimariesKey, kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
        }
        let before = try snapshot(source)
        let context = CIContext(options: [.useSoftwareRenderer: true, .workingColorSpace: colorSpace])
        let outputSpace = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let actual = try renderedCenter(AppleVideoOpaqueFrame.image(from: source), context: context, colorSpace: outputSpace)
        let expected = try renderedCenter(CIImage(cvPixelBuffer: reference), context: context, colorSpace: outputSpace)
        XCTAssertEqual(actual[3], 255, "Opaque video encoding must not inherit undefined model alpha")
        XCTAssertTrue(expected.prefix(3).allSatisfy { $0 > 0 }, "Independent opaque fixture must be nonblack")
        for channel in 0..<4 { XCTAssertLessThanOrEqual(abs(actual[channel] - expected[channel]), 1, "Opaque conversion changed channel \(channel)") }
        XCTAssertEqual(try snapshot(source), before, "Processor previousOutput must remain unchanged")
    }
}

private func failure(_ message: String, _ code: Int = 1) -> NSError {
    NSError(domain: "OpaqueSRAlphaChecks", code: code,
            userInfo: [NSLocalizedDescriptionKey: message])
}

private func makeBuffer(_ format: OSType) throws -> CVPixelBuffer {
    var optionalBuffer: CVPixelBuffer?
    let status = CVPixelBufferCreate(nil, 8, 8, format, nil, &optionalBuffer)
    guard status == kCVReturnSuccess, let buffer = optionalBuffer else {
        throw failure("CVPixelBufferCreate failed", Int(status))
    }
    return buffer
}

private func fixture(alpha: Float16) throws -> CVPixelBuffer {
    let buffer = try makeBuffer(kCVPixelFormatType_64RGBAHalf)
    let status = CVPixelBufferLockBaseAddress(buffer, [])
    guard status == kCVReturnSuccess else {
        throw failure("Could not lock source buffer", Int(status))
    }
    defer { CVPixelBufferUnlockBaseAddress(buffer, []) }

    guard let base = CVPixelBufferGetBaseAddress(buffer) else {
        throw failure("Source buffer has no base address")
    }
    let rowBytes = CVPixelBufferGetBytesPerRow(buffer)
    for y in 0..<8 {
        let row = base.advanced(by: y * rowBytes).assumingMemoryBound(to: Float16.self)
        for x in 0..<8 {
            let pixel = row.advanced(by: x * 4)
            pixel[0] = 0.25
            pixel[1] = 0.5
            pixel[2] = 0.75
            pixel[3] = alpha
        }
    }
    return buffer
}

private func snapshot(_ buffer: CVPixelBuffer) throws -> Data {
    let status = CVPixelBufferLockBaseAddress(buffer, .readOnly)
    guard status == kCVReturnSuccess else {
        throw failure("Could not lock buffer for snapshot", Int(status))
    }
    defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
    guard let base = CVPixelBufferGetBaseAddress(buffer) else {
        throw failure("Buffer has no base address")
    }
    return Data(bytes: base, count: CVPixelBufferGetBytesPerRow(buffer) * 8)
}

private func centerBGRA(_ buffer: CVPixelBuffer) throws -> [Int] {
    let status = CVPixelBufferLockBaseAddress(buffer, .readOnly)
    guard status == kCVReturnSuccess else {
        throw failure("Could not lock rendered buffer", Int(status))
    }
    defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
    guard let base = CVPixelBufferGetBaseAddress(buffer) else {
        throw failure("Rendered buffer has no base address")
    }
    let offset = 4 * CVPixelBufferGetBytesPerRow(buffer) + 4 * 4
    let pixel = base.advanced(by: offset).assumingMemoryBound(to: UInt8.self)
    return [Int(pixel[0]), Int(pixel[1]), Int(pixel[2]), Int(pixel[3])]
}

private func renderedCenter(_ image: CIImage, context: CIContext, colorSpace: CGColorSpace) throws -> [Int] {
    let output = try makeBuffer(kCVPixelFormatType_32BGRA)
    context.render(image, to: output, bounds: CGRect(x: 0, y: 0, width: 8, height: 8),
                   colorSpace: colorSpace)
    return try centerBGRA(output)
}
