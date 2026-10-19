package io.ondeviceai.identity

/** Candidate-unit score after aggregation; no template vectors or host subject IDs. */
class IdentificationCandidate internal constructor(val candidateRef: CandidateRef, val score: Double, val rank: Int)

/** Pure ranking only; the query engine owns authorization, quality, leases and final publication. */
internal fun rankIdentificationCandidates(snapshot: GallerySnapshotRecord, scores: Map<String, Double>,
    topK: Int = 32): List<IdentificationCandidate> {
    if (topK !in 1..32 || snapshot.members.isEmpty() || scores.keys != snapshot.members.map { it.ref.id }.toSet() ||
        scores.values.any { !it.isFinite() || it !in -1.0..1.0 }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    val policy = snapshot.policy
    policy.validate(policy.embeddingSpace, IdentityPolicyKind.IDENTIFICATION,
        snapshot.operationGrant.authorizationEpoch, snapshot.members.map { it.candidate.id }.toSet().size)
    if (snapshot.aggregationPolicyId != "subject-max-v1" || policy.scoreMetric != "cosine" ||
        policy.scoreDirection != ScoreDirection.HIGHER_IS_BETTER)
        throw IdentityError(IdentityErrorCode.UNSUPPORTED_OPTION, policyId = policy.id)
    val maxima = mutableMapOf<String, Pair<CandidateRef, Double>>()
    for (member in snapshot.members) {
        val score = scores.getValue(member.ref.id)
        if (maxima[member.candidate.id]?.let { score > it.second } != false) {
            maxima[member.candidate.id] = member.candidate to score
        }
    }
    // Opaque IDs accept ASCII tokens only, so String ordering is their unsigned byte ordering.
    return maxima.values.filter { it.second >= requireNotNull(policy.threshold) }
        .sortedWith(compareByDescending<Pair<CandidateRef, Double>> { it.second }.thenBy { it.first.id })
        .take(topK).mapIndexed { index, (candidate, score) -> IdentificationCandidate(candidate, score, index + 1) }
}
