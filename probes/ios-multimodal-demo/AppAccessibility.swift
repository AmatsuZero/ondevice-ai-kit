import UIKit

/// App-local validation only. Activation does not prove physical touch or hit testing.
@MainActor enum AppAccessibility {
  enum LookupError: Error { case ambiguous, limitExceeded }

  static func find(identifier: String, roots: [NSObject]) throws -> NSObject? {
    var pending = roots.map { ($0, 0) }
    var visited = Set<ObjectIdentifier>()
    var match: NSObject?
    var index = 0
    while index < pending.count {
      let (node, depth) = pending[index]
      index += 1
      guard visited.insert(ObjectIdentifier(node)).inserted else { continue }
      guard visited.count <= 4096, depth <= 64 else { throw LookupError.limitExceeded }
      if (node as? UIAccessibilityIdentification)?.accessibilityIdentifier == identifier {
        guard match == nil else { throw LookupError.ambiguous }
        match = node
      }
      var children = (node as? UIView)?.subviews.map { $0 as NSObject } ?? []
      if let elements = node.accessibilityElements {
        children += elements.compactMap { $0 as? NSObject }
      } else {
        let count = node.accessibilityElementCount()
        if count != NSNotFound && count > 0 {
          guard count <= 4096 else { throw LookupError.limitExceeded }
          children += (0..<count).compactMap { node.accessibilityElement(at: $0) as? NSObject }
        }
      }
      guard pending.count + children.count <= 16384 else { throw LookupError.limitExceeded }
      pending += children.map { ($0, depth + 1) }
    }
    return match
  }

  static func activate(identifier: String, roots: [NSObject]) throws -> Bool {
    guard let node = try find(identifier: identifier, roots: roots) else { return false }
    return node.accessibilityActivate()
  }
}
