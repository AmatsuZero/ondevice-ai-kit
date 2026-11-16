import Foundation

enum PunctuationArm: String, Codable, Equatable {
    case A, B
    var configuration: String { self == .A ? "unset" : "true" }
}

struct PunctuationPlanEntry {
    let clip: Apple65Clip
    let arm: PunctuationArm
    let pairOrdinal: Int
    let requestOrdinal: Int
}

enum PunctuationPlan {
    private struct FrozenInput {
        let id: String
        let cohort: String
        let locale: String
        let samples: Int
        let pcmSHA256: String
        let referenceSHA256: String
    }

    // Selected by the frozen hash rule before this runner was implemented.
    private static let inputs: [FrozenInput] = [
        .init(id: "fleurs-cmn_hans_cn-1698", cohort: "mandarin", locale: "zh-CN", samples: 155520,
              pcmSHA256: "c0cf0ea82e08a6e7f364626fdecf3c4f7d8dedbf3d3b24f96ff029fc1ca312a4",
              referenceSHA256: "1313189fa8863bdde86a89eff5f19bbf42356268a191783b90d4c6cb17639c02"),
        .init(id: "fleurs-cmn_hans_cn-1871", cohort: "mandarin", locale: "zh-CN", samples: 111360,
              pcmSHA256: "03a066eba992c84246696180810c01b61d5b7d050bc41e5b8b3b8cc6c7e5fd62",
              referenceSHA256: "05c5be363356ac0d747eb8604127ed156c301890b3d056567266d335634082ac"),
        .init(id: "fleurs-cmn_hans_cn-1830", cohort: "mandarin", locale: "zh-CN", samples: 101760,
              pcmSHA256: "32201ea0511a0285c0da43ca492357524fc3af05e0682fddedb6663b1f4c8082",
              referenceSHA256: "2bd10a5a05006a8c2db021a7007c40a185e54564eed365544cfac4f31658b3b4"),
        .init(id: "fleurs-en_us-1858", cohort: "english-near", locale: "en-US", samples: 100800,
              pcmSHA256: "e8138f2c1b81addf0479f2abf0edbe38be2e2e5a2eb0a29fd12efdb602e53ff4",
              referenceSHA256: "6ca6e59d1b7d20521c1e36ef100f2bce15677b64383066bd6c654c1fd7617a16"),
        .init(id: "fleurs-en_us-1992", cohort: "english-near", locale: "en-US", samples: 138240,
              pcmSHA256: "9c9a81e00df5617314c141a676eb01447539b46697e6607e7cf11fc389ce7f75",
              referenceSHA256: "2b39b4054bdb0b7b5ff9dd4564d92d84cbdde1c7ae2d58127bd918477e5bf921"),
        .init(id: "fleurs-en_us-1909", cohort: "english-near", locale: "en-US", samples: 90240,
              pcmSHA256: "743a920543850ab40540466a1b7b896ce5281c7887390c5adb136d874c78f300",
              referenceSHA256: "79394b66eef8b7e5188acf5b6e54cd6abdfbec78df97aecd633341af6fe03467")
    ]

    static func make(cases: [Apple65Clip]) throws -> [PunctuationPlanEntry] {
        guard cases.count == 65, Set(cases.map { $0.id }).count == 65 else {
            throw PlanError.invalid("fixture-count-or-duplicate")
        }
        var result: [PunctuationPlanEntry] = []
        for (offset, frozen) in inputs.enumerated() {
            guard let clip = cases.first(where: { $0.id == frozen.id }),
                  clip.cohort == frozen.cohort, clip.locale == frozen.locale,
                  clip.samples == frozen.samples, clip.pcmSHA256 == frozen.pcmSHA256,
                  Apple65Core.sha256(Data(clip.reference.utf8)) == frozen.referenceSHA256 else {
                throw PlanError.invalid("selected-input-identity:" + frozen.id)
            }
            let arms: [PunctuationArm] = offset % 2 == 0 ? [.A, .B] : [.B, .A]
            for arm in arms {
                result.append(PunctuationPlanEntry(clip: clip, arm: arm, pairOrdinal: offset + 1,
                                                   requestOrdinal: result.count + 1))
            }
        }
        return result
    }

    private enum PlanError: Error { case invalid(String) }
}
