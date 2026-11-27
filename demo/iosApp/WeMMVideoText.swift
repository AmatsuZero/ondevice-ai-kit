import Foundation

func weMMVideoFinalText(_ data: Data, reachedTokenLimit: Bool) -> String {
    let trim = reachedTokenLimit ? incompleteScalarSuffixLength(data) : 0
    return String(decoding: data.dropLast(trim), as: UTF8.self)
}

private func incompleteScalarSuffixLength(_ data: Data) -> Int {
    guard let start = data.suffix(3).lastIndex(where: { ($0 & 0xC0) != 0x80 }) else { return 0 }
    let tail = data[start...]
    let lead = data[start]
    let width: Int
    switch lead {
    case 0xC2...0xDF: width = 2
    case 0xE0...0xEF: width = 3
    case 0xF0...0xF4: width = 4
    default: return 0
    }
    guard tail.count < width else { return 0 }
    // Check for overlong, surrogate, and out-of-range prefixes
    if let second = tail.dropFirst().first {
        if (lead == 0xE0 && second < 0xA0) ||
           (lead == 0xED && second > 0x9F) ||
           (lead == 0xF0 && second < 0x90) ||
           (lead == 0xF4 && second > 0x8F) {
            return 0
        }
    }
    return tail.count
}
