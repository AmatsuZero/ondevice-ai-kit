import UIKit
import XCTest

@MainActor final class AppAccessibilityTests: XCTestCase {
  func testWaitsForEnabledNodeAndActivatesOnce() async throws {
    let node = AccessibilityElement(identifier: "target", result: true)
    node.accessibilityTraits.insert(.notEnabled)
    Task { @MainActor in
      await Task.yield()
      node.accessibilityTraits.remove(.notEnabled)
    }
    let accepted = try await AppAccessibility.activateWhenEnabled(
      identifier: "target", roots: { [node] })
    XCTAssertTrue(accepted)
    XCTAssertEqual(node.activations, 1)
  }
  func testEnabledWaitDoesNotRetryFailedActionOrActivateDisabledTimeout() async throws {
    let node = AccessibilityElement(identifier: "target", result: false)
    let accepted = try await AppAccessibility.activateWhenEnabled(
      identifier: "target", roots: { [node] })
    XCTAssertFalse(accepted)
    XCTAssertEqual(node.activations, 1)
    node.accessibilityTraits.insert(.notEnabled)
    let timedOut = try await AppAccessibility.activateWhenEnabled(
      identifier: "target", roots: { [node] }, timeout: 0.001)
    XCTAssertFalse(timedOut)
    XCTAssertEqual(node.activations, 1)
  }
  func testFindsNestedNodeAndActivatesExactlyOnce() throws {
    let container = AccessibilityContainer()
    let node = AccessibilityElement(identifier: "target", result: true)
    container.elements = [node]
    XCTAssertTrue(try AppAccessibility.activate(identifier: "target", roots: [container]))
    XCTAssertEqual(node.activations, 1)
  }
  func testDeduplicatesSameObjectAcrossRoots() throws {
    let node = AccessibilityElement(identifier: "target", result: true)
    XCTAssertTrue(try AppAccessibility.activate(identifier: "target", roots: [node, node]))
    XCTAssertEqual(node.activations, 1)
  }
  func testRejectsAmbiguousExactIdentifier() {
    let roots = [
      AccessibilityElement(identifier: "target", result: true),
      AccessibilityElement(identifier: "target", result: true),
    ]
    XCTAssertThrowsError(try AppAccessibility.find(identifier: "target", roots: roots))
  }
  func testMissingOrFailedActivationIsFalse() throws {
    XCTAssertNil(try AppAccessibility.find(identifier: "missing", roots: []))
    let node = AccessibilityElement(identifier: "target", result: false)
    XCTAssertFalse(try AppAccessibility.activate(identifier: "target", roots: [node]))
  }
  func testCycleTerminatesAtFiniteDepth() {
    let a = AccessibilityContainer()
    let b = AccessibilityContainer()
    a.elements = [b]
    b.elements = [a]
    defer {
      a.elements = []
      b.elements = []
    }
    XCTAssertNil(try? AppAccessibility.find(identifier: "missing", roots: [a]))
  }
}

@MainActor private final class AccessibilityContainer: NSObject {
  var elements: [Any] = []
  override func accessibilityElementCount() -> Int { elements.count }
  override func accessibilityElement(at index: Int) -> Any { elements[index] }
  override func index(ofAccessibilityElement element: Any) -> Int {
    elements.firstIndex { ($0 as AnyObject) === (element as AnyObject) } ?? NSNotFound
  }
}

@MainActor private final class AccessibilityElement: UIAccessibilityElement {
  let result: Bool
  var activations = 0
  init(identifier: String, result: Bool) {
    self.result = result
    super.init(accessibilityContainer: NSObject())
    accessibilityIdentifier = identifier
    isAccessibilityElement = true
  }
  override func accessibilityActivate() -> Bool {
    activations += 1
    return result
  }
}
