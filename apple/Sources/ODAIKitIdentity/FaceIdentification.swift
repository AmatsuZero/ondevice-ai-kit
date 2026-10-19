import Foundation

/// Candidate-unit score after aggregation; never includes template vectors or host subject IDs.
public struct IdentificationCandidate: Sendable {
    public let candidateRef: CandidateRef
    public let score: Double
    public let rank: Int
}

/// Pure ranking only. The query engine must own authorization, quality, leases,
/// and final publication; this helper cannot authorize a snapshot or a result.
func rankIdentificationCandidates(snapshot: GallerySnapshotRecord, scores: [String: Double],
                                  topK: Int = 32) throws -> [IdentificationCandidate] {
    guard (1...32).contains(topK), !snapshot.members.isEmpty,
          Set(scores.keys) == Set(snapshot.members.map { $0.ref.id }),
          scores.values.allSatisfy({ $0.isFinite && (-1...1).contains($0) }) else {
        throw IdentityError(.invalidInput)
    }
    let policy = snapshot.policy
    try policy.validate(embeddingSpace: policy.embeddingSpace, kind: .identification,
        epoch: snapshot.operationGrant.authorizationEpoch, gallerySize: Set(snapshot.members.map { $0.candidate.id }).count)
    guard snapshot.aggregationPolicyId == "subject-max-v1", policy.scoreMetric == "cosine",
          policy.scoreDirection == .higherIsBetter else { throw IdentityError(.unsupportedOption, policyId: policy.id) }
    var maxima: [String: (CandidateRef, Double)] = [:]
    for member in snapshot.members {
        let score = scores[member.ref.id]!
        if maxima[member.candidate.id].map({ score > $0.1 }) ?? true {
            maxima[member.candidate.id] = (member.candidate, score)
        }
    }
    let threshold = policy.threshold!
    return maxima.values.filter { $0.1 >= threshold }.sorted {
        if $0.1 != $1.1 { return $0.1 > $1.1 }
        return $0.0.id.utf8.lexicographicallyPrecedes($1.0.id.utf8)
    }.prefix(topK).enumerated().map {
        IdentificationCandidate(candidateRef: $0.element.0, score: $0.element.1, rank: $0.offset + 1)
    }
}
