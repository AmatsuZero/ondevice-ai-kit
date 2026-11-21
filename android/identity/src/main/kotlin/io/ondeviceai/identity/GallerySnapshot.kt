package io.ondeviceai.identity

import java.security.MessageDigest
import java.util.Collections

/** Persisted membership facts; current Store ownership/authenticity is checked separately. */
internal data class GallerySnapshotMember(
    val ref: TemplateRef,
    val candidate: CandidateRef,
    val grant: AuthorizationGrant
) {
    fun canonicalJSON(): String = IdentityContractJSON.encode(mapOf(
        "candidate" to candidate.canonicalJSON(), "grant" to grant.canonicalJSON(), "template" to ref.canonicalJSON()
    ))
    companion object {
        fun restore(json: String): GallerySnapshotMember {
            val f = fields(json, setOf("candidate", "grant", "template"))
            return GallerySnapshotMember(TemplateRef.decodeJSON(f.string("template")), CandidateRef.decodeJSON(f.string("candidate")), AuthorizationGrant.decodeJSON(f.string("grant")))
        }
    }
}

@ConsistentCopyVisibility
internal data class GallerySnapshotRecord private constructor(
    val indexStoreId: String, val identityStoreId: String, val ref: GalleryRef, val parent: GalleryRef?,
    val members: List<GallerySnapshotMember>, val operationGrant: AuthorizationGrant,
    val policy: ThresholdPolicy, val aggregationPolicyId: String, val domainEpochs: Map<String, Long>,
    val contentDigest: String
) {
    val canonicalJSON: String
        get() = IdentityContractJSON.encode(contentFields() + ("contentDigest" to contentDigest))

    companion object {
        fun create(indexStoreId: String, identityStoreId: String, lineageId: String, parent: GalleryRef?,
                   members: List<GallerySnapshotMember>, operationGrant: AuthorizationGrant,
                   policy: ThresholdPolicy, aggregationPolicyId: String): GallerySnapshotRecord {
            listOf(indexStoreId, identityStoreId, lineageId, aggregationPolicyId).forEach(::token)
            if (members.isEmpty() || members.map { it.ref.id }.toSet().size != members.size || parent?.id?.let { it != lineageId } == true)
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            if (IdentityScope.IDENTIFICATION !in operationGrant.scope) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
            val sorted = members.sortedBy { it.ref.id }
            policy.validate(policy.embeddingSpace, IdentityPolicyKind.IDENTIFICATION, operationGrant.authorizationEpoch, sorted.map { it.candidate.id }.toSet().size)
            val epochs = linkedMapOf(operationGrant.revocationDomainId to operationGrant.authorizationEpoch)
            val candidateDomains = mutableMapOf<String, String>()
            val grants = mutableMapOf(operationGrant.grantId to operationGrant)
            sorted.forEach { member ->
                if (member.candidate.storeId != identityStoreId) throw IdentityError(IdentityErrorCode.CANDIDATE_NOT_FOUND)
                if (member.ref.embeddingSpaceId != policy.embeddingSpace) throw IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
                if (member.grant.purpose != operationGrant.purpose || IdentityScope.IDENTIFICATION !in member.grant.scope)
                    throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
                if (member.ref.authorizationEpoch != member.grant.authorizationEpoch) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED)
                candidateDomains[member.candidate.id]?.let { if (it != member.grant.revocationDomainId) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED) }
                grants[member.grant.grantId]?.let { if (it.canonicalJSON() != member.grant.canonicalJSON()) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED) }
                epochs[member.grant.revocationDomainId]?.let { if (it != member.grant.authorizationEpoch) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REVOKED) }
                epochs[member.grant.revocationDomainId] = member.grant.authorizationEpoch
                candidateDomains[member.candidate.id] = member.grant.revocationDomainId
                grants[member.grant.grantId] = member.grant
            }
            val provisional = GallerySnapshotRecord(indexStoreId, identityStoreId, GalleryRef(lineageId, "pending"), parent, sorted, operationGrant, policy, aggregationPolicyId, epochs, "pending")
            val digest = provisional.digest()
            val immutableMembers = Collections.unmodifiableList(sorted.toList())
            val immutableEpochs = Collections.unmodifiableMap(epochs.toMap())
            if (IdentityContractJSON.encode(provisional.contentFields()).toByteArray().size > 65000) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val result = GallerySnapshotRecord(indexStoreId, identityStoreId, GalleryRef(lineageId, digest), parent, immutableMembers, operationGrant, policy, aggregationPolicyId, immutableEpochs, digest)
            return result
        }

        fun restore(json: String): GallerySnapshotRecord {
            val f = fields(json, setOf("aggregationPolicyId", "contentDigest", "domainEpochs", "identityStoreId", "indexStoreId", "lineageId", "members", "operationGrant", "parent", "policy", "version"))
            if (f.long("version") != 1L) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val rawMembers = f["members"] as? List<*> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val parent = if (f["parent"] == null) null else GalleryRef.decodeJSON(f.string("parent"))
            val rawEpochs = f["domainEpochs"] as? Map<*, *> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            val epochs = rawEpochs.entries.associate { (k, v) ->
                val key = k as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                token(key); key to (v as? Long ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT))
            }
            val record = create(f.string("indexStoreId"), f.string("identityStoreId"), f.string("lineageId"), parent,
                rawMembers.map { GallerySnapshotMember.restore(it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)) },
                AuthorizationGrant.decodeJSON(f.string("operationGrant")), restoreGalleryPolicy(f.string("policy")), f.string("aggregationPolicyId"))
            if (record.contentDigest != f.string("contentDigest") || record.domainEpochs != epochs ||
                record.members.map { it.canonicalJSON() } != rawMembers.map { it as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) })
                throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return record
        }
    }

    private fun contentFields() = mapOf<String, Any?>("aggregationPolicyId" to aggregationPolicyId, "domainEpochs" to domainEpochs,
        "identityStoreId" to identityStoreId, "indexStoreId" to indexStoreId, "lineageId" to ref.id,
        "members" to members.map { it.canonicalJSON() }, "operationGrant" to operationGrant.canonicalJSON(),
        "parent" to parent?.canonicalJSON(), "policy" to policy.galleryJSON(), "version" to 1)
    private fun digest(): String = MessageDigest.getInstance("SHA-256").digest(IdentityContractJSON.encode(contentFields()).toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun ThresholdPolicy.galleryJSON() = IdentityContractJSON.encode(mapOf("calibrationStatus" to calibrationStatus.name.lowercase(), "datasetRevision" to datasetRevision,
    "effectiveAuthorizationEpoch" to effectiveAuthorizationEpoch, "embeddingSpace" to embeddingSpace.canonicalJSON(), "enrollmentRule" to enrollmentRule,
    "gallerySizeRange" to gallerySizeRange?.let { mapOf("lower" to it.first, "upper" to it.last) }, "id" to id, "kind" to kind.name.lowercase(),
    "operatingPoint" to operatingPoint, "qualityFloor" to qualityFloor?.let(::galleryDoubleBits), "revision" to revision,
    "scoreDirection" to scoreDirection.wire, "scoreMetric" to scoreMetric, "threshold" to threshold?.let(::galleryDoubleBits)))

private fun restoreGalleryPolicy(json: String): ThresholdPolicy {
    val f = fields(json, setOf("calibrationStatus", "datasetRevision", "effectiveAuthorizationEpoch", "embeddingSpace", "enrollmentRule", "gallerySizeRange", "id", "kind", "operatingPoint", "qualityFloor", "revision", "scoreDirection", "scoreMetric", "threshold"))
    if (f.string("calibrationStatus") != "verified" || f.string("kind") != "identification") throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    val range = f["gallerySizeRange"] as? Map<*, *> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    if (range.keys != setOf("lower", "upper")) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    val lower = range["lower"] as? Long ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT); val upper = range["upper"] as? Long ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    if (lower <= 0 || lower > upper || upper > Int.MAX_VALUE) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    val direction = enumValues<ScoreDirection>().firstOrNull { it.wire == f.string("scoreDirection") } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    return ThresholdPolicy(f.string("id"), f.string("revision"), EmbeddingSpaceId.decodeJSON(f.string("embeddingSpace")), f.string("scoreMetric"), direction, CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, galleryDouble(f, "threshold"), galleryDouble(f, "qualityFloor"), f.string("operatingPoint"), f.string("datasetRevision"), lower.toInt()..upper.toInt(), f.string("enrollmentRule"), f.long("effectiveAuthorizationEpoch"))
}
private fun galleryDouble(fields: Map<String, Any?>, key: String): Double { val value = fields[key] as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT); if (!Regex("[0-9a-f]{16}").matches(value)) throw IdentityError(IdentityErrorCode.INVALID_INPUT); return try { java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(value, 16)).takeIf { it.isFinite() } ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) } catch (_: NumberFormatException) { throw IdentityError(IdentityErrorCode.INVALID_INPUT) } }
private fun galleryDoubleBits(value: Double) = java.lang.Long.toUnsignedString(value.toRawBits(), 16).padStart(16, '0')
