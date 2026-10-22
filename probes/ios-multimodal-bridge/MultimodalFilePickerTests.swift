import UIKit
import UniformTypeIdentifiers
import XCTest

@MainActor final class MultimodalFilePickerTests: XCTestCase {
  func testSinglePngJpegSelectionDeliversFirstUrlOnlyOnce() throws {
    var presented: UIDocumentPickerViewController?
    let picker = MultimodalFilePicker(present: {
      presented = $0
      return true
    })
    var replies = [URL?]()
    picker.present { replies.append($0) }
    XCTAssertTrue(picker.isPresenting)
    let controller = try XCTUnwrap(presented)
    XCTAssertFalse(controller.allowsMultipleSelection)
    XCTAssertEqual(controller.documentPickerMode, .open)
    let source = URL(fileURLWithPath: "/synthetic.png")
    picker.documentPicker(
      controller, didPickDocumentsAt: [source, URL(fileURLWithPath: "/ignored.jpg")])
    picker.documentPickerWasCancelled(controller)
    XCTAssertEqual(replies.count, 1)
    XCTAssertEqual(replies[0], source)
    XCTAssertFalse(picker.isPresenting)
  }

  func testCancelCloseAndUnavailablePresenterAreOnceOnly() throws {
    var presented: UIDocumentPickerViewController?
    let picker = MultimodalFilePicker(present: {
      presented = $0
      return true
    })
    var replies = [URL?]()
    picker.present { replies.append($0) }
    let controller = try XCTUnwrap(presented)
    picker.documentPickerWasCancelled(controller)
    picker.documentPicker(controller, didPickDocumentsAt: [URL(fileURLWithPath: "/late.png")])
    XCTAssertEqual(replies.count, 1)
    XCTAssertNil(replies[0])
    picker.present { replies.append($0) }
    picker.close()
    picker.close()
    picker.documentPicker(controller, didPickDocumentsAt: [URL(fileURLWithPath: "/late.png")])
    XCTAssertEqual(replies.count, 2)
    XCTAssertNil(replies[1])
    picker.present { replies.append($0) }
    XCTAssertEqual(replies.count, 3)
    XCTAssertNil(replies[2])
    let unavailable = MultimodalFilePicker(present: { _ in false })
    unavailable.present { replies.append($0) }
    XCTAssertFalse(unavailable.isPresenting)
    XCTAssertEqual(replies.count, 4)
    XCTAssertNil(replies[3])
  }

  func testConcurrentPresentationDoesNotReplacePendingReply() throws {
    var presented: UIDocumentPickerViewController?
    let picker = MultimodalFilePicker(present: {
      presented = $0
      return true
    })
    var first = 0
    var second = 0
    picker.present { _ in first += 1 }
    picker.present { url in
      XCTAssertNil(url)
      second += 1
    }
    XCTAssertEqual(second, 1)
    XCTAssertEqual(first, 0)
    picker.documentPickerWasCancelled(try XCTUnwrap(presented))
    XCTAssertEqual(first, 1)
  }
}
