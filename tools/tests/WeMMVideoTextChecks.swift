import Foundation

@main
struct WeMMVideoTextChecks {
    static func check(_ bytes: [UInt8], _ expected: String) {
        let data = Data([0xFF] + bytes).dropFirst()
        precondition(data.startIndex == 1)
        precondition(weMMVideoFinalText(data, reachedTokenLimit: true).utf8.elementsEqual(expected.utf8))
        precondition(weMMVideoFinalText(data, reachedTokenLimit: false).utf8.elementsEqual(String(decoding: bytes, as: UTF8.self).utf8))
    }

    static func main() {
        check([], "")
        check(Array("�".utf8), "�")

        for scalar in ["¢", "湍", "🌊"] {
            let bytes = Array(scalar.utf8)
            for n in 1..<bytes.count {
                check(Array("在".utf8) + Array(bytes.prefix(n)), "在")
            }
            check(Array(("在" + scalar).utf8), "在" + scalar)
        }

        let badTails: [[UInt8]] = [
            [0x80], [0xC0], [0xC1], [0xE0, 0x9F], [0xED, 0xA0],
            [0xF0, 0x8F], [0xF4, 0x90], [0xF5], [0xE2, 0x28],
            [0x80, 0x80, 0x80, 0x80]
        ]

        for tail in badTails {
            check(tail, String(decoding: tail, as: UTF8.self))
        }

        check([0xFF, 0x61, 0xE6, 0xB9], "�a")
        print("WeMM final text checks passed")
    }
}
