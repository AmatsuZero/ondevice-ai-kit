import Foundation

/// Foundation validates the grammar; this pass preserves the reference wire rules
/// before Foundation's dictionaries discard duplicate keys or nested numeric types.
/// Reference and authorization DTOs have integer-only numeric fields.
func validateIdentityWire(_ json: String) throws {
    let bytes = Array(json.utf8)
    var objects: [Set<String>] = []
    var depth = 0
    var index = 0
    while index < bytes.count {
        switch bytes[index] {
        case 34: // string
            let start = index
            index += 1
            while index < bytes.count {
                if bytes[index] == 92 { index += 2; continue }
                if bytes[index] == 34 { break }
                index += 1
            }
            guard index < bytes.count else { throw IdentityError(.invalidInput) }
            index += 1
            var next = index
            while next < bytes.count, [9, 10, 13, 32].contains(bytes[next]) { next += 1 }
            if next < bytes.count, bytes[next] == 58 {
                guard !objects.isEmpty,
                      let key = try JSONSerialization.jsonObject(with: Data(bytes[start..<index]), options: .fragmentsAllowed) as? String,
                      objects[objects.count - 1].insert(key).inserted else { throw IdentityError(.invalidInput) }
            }
        case 123: // object
            depth += 1; objects.append([]); index += 1
        case 125:
            depth -= 1; objects.removeLast(); index += 1
        case 91:
            depth += 1; index += 1
        case 93:
            depth -= 1; index += 1
        case 45, 48...57:
            let start = index
            index += 1
            while index < bytes.count, (48...57).contains(bytes[index]) { index += 1 }
            guard index == bytes.count || ![46, 69, 101].contains(bytes[index]),
                  Int64(String(decoding: bytes[start..<index], as: UTF8.self)) != nil else { throw IdentityError(.invalidInput) }
        default:
            index += 1
        }
        guard depth <= 16 else { throw IdentityError(.invalidInput) }
    }
}
