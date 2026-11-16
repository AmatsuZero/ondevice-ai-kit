import Foundation

@main
struct PunctuationPlanChecks {
    static func main() throws {
        guard CommandLine.arguments.count == 3 else {
            throw CheckFailure.failed("usage: checks manifest.json input-selection.json")
        }
        let raw = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let manifest = try Apple65Core.loadManifest(raw, expectedSHA256: Apple65Core.sha256(raw))
        let selection = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))) as! [String: Any]
        let inputs = selection["inputs"] as! [[String: Any]]
        let expectedIDs = inputs.map { $0["id"] as! String }
        var count = 0
        func check(_ condition: Bool, _ label: String) throws {
            guard condition else { throw CheckFailure.failed(label) }
            count += 1
            print("PASS: " + label)
        }
        func rejects(_ clips: [Apple65Clip], _ label: String) throws {
            do { _ = try PunctuationPlan.make(cases: clips) }
            catch { try check(true, label); return }
            throw CheckFailure.failed(label + " unexpectedly accepted")
        }
        func mutation(_ field: String, _ value: Any) throws -> [Apple65Clip] {
            var object = try JSONSerialization.jsonObject(with: raw) as! [String: Any]
            var cases = object["cases"] as! [[String: Any]]
            let index = cases.firstIndex { $0["id"] as? String == expectedIDs[0] }!
            cases[index][field] = value
            object["cases"] = cases
            return try JSONDecoder().decode(Apple65Manifest.self, from: JSONSerialization.data(withJSONObject: object)).cases
        }

        let plan = try PunctuationPlan.make(cases: manifest.cases)
        try check(plan.count == 12, "exactly twelve requests")
        try check(Set(plan.map { $0.clip.id }).count == 6, "exactly six distinct inputs")
        try check(plan.map { $0.requestOrdinal } == Array(1...12), "request ordinals are contiguous")
        for i in 0..<6 {
            let pair = Array(plan[i * 2..<i * 2 + 2])
            try check(pair.map { $0.clip.id } == [expectedIDs[i], expectedIDs[i]], "pair \(i + 1) uses frozen input twice")
            try check(pair.map { $0.arm.rawValue } == (i % 2 == 0 ? ["A", "B"] : ["B", "A"]), "pair \(i + 1) preserves counterbalance")
            try check(pair.allSatisfy { $0.pairOrdinal == i + 1 }, "pair \(i + 1) ordinal")
            try check(pair.map { $0.arm.configuration }.sorted() == ["true", "unset"], "pair \(i + 1) reports distinct treatment settings")
        }
        let reversed = try PunctuationPlan.make(cases: Array(manifest.cases.reversed()))
        try check(reversed.map { $0.clip.id } == plan.map { $0.clip.id } && reversed.map { $0.arm } == plan.map { $0.arm }, "selection order is independent of fixture enumeration")
        try rejects(Array(manifest.cases.dropFirst()), "wrong total count rejected")
        var missing = manifest.cases.filter { $0.id != expectedIDs[0] }
        missing.append(manifest.cases.first { !expectedIDs.contains($0.id) }!)
        try rejects(missing, "duplicate replacing selected input rejected")
        var duplicateUnselected = manifest.cases
        let unselected = duplicateUnselected.indices.filter { !expectedIDs.contains(duplicateUnselected[$0].id) }
        duplicateUnselected[unselected[0]] = duplicateUnselected[unselected[1]]
        try rejects(duplicateUnselected, "duplicate outside selection rejected")
        try rejects(try mutation("id", "fleurs-cmn_hans_cn-9999"), "missing frozen input rejected")
        try rejects(try mutation("cohort", "english-near"), "cohort mismatch rejected")
        try rejects(try mutation("locale", "en-US"), "locale mismatch rejected")
        try rejects(try mutation("samples", 155519), "sample count mismatch rejected")
        try rejects(try mutation("pcmSHA256", String(repeating: "0", count: 64)), "PCM identity mismatch rejected")
        try rejects(try mutation("reference", "changed reference"), "reference identity mismatch rejected")
        print("PASSED \(count) punctuation plan checks")
    }

    enum CheckFailure: Error { case failed(String) }
}
