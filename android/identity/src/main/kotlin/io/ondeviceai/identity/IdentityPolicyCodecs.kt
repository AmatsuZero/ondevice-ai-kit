package io.ondeviceai.identity

/** Version 1 keeps finite Double values as 16 lowercase IEEE-754 hex digits.
 * This preserves exact calibration bits without weakening the integer-only reference codec.
 * Decoding a VERIFIED declaration does not establish calibration provenance.
 */
internal object IdentityPolicyCodecs {
    private val thresholdKeys = setOf("version", "id", "revision", "embeddingSpace", "scoreMetric", "scoreDirection",
        "calibrationStatus", "kind", "threshold", "qualityFloor", "operatingPoint", "datasetRevision",
        "gallerySizeRange", "enrollmentRule", "effectiveAuthorizationEpoch")
    private val padKeys = setOf("version", "id", "revision", "backendId", "backendVersion", "modelBinding",
        "calibrationStatus", "mode", "challengePolicyRevision", "minimumFrames", "maximumDurationNanos", "qualityFloor",
        "scoreMetric", "threshold", "classificationPolicyRevision", "datasetRevision", "operatingPoint", "attackTypes", "effectiveAuthorizationEpoch")

    fun encode(p: ThresholdPolicy): String = IdentityContractJSON.encode(mapOf(
        "version" to 1, "id" to p.id, "revision" to p.revision,
        "embeddingSpace" to p.embeddingSpace.fields(), "scoreMetric" to p.scoreMetric,
        "scoreDirection" to p.scoreDirection.wire, "calibrationStatus" to p.calibrationStatus.name.lowercase(),
        "kind" to p.kind.name.lowercase(), "threshold" to bits(p.threshold), "qualityFloor" to bits(p.qualityFloor),
        "operatingPoint" to p.operatingPoint, "datasetRevision" to p.datasetRevision,
        "gallerySizeRange" to p.gallerySizeRange?.let { mapOf("lower" to it.first, "upper" to it.last) },
        "enrollmentRule" to p.enrollmentRule, "effectiveAuthorizationEpoch" to p.effectiveAuthorizationEpoch))

    fun threshold(json: String): ThresholdPolicy {
        val f = fields(json, thresholdKeys); version(f)
        return ThresholdPolicy(f.string("id"), f.string("revision"),
            EmbeddingSpaceId.decodeJSON(IdentityContractJSON.encode(f.objectValue("embeddingSpace"))),
            f.string("scoreMetric"), enumValue(f.string("scoreDirection")) { it.wire },
            enumValue(f.string("calibrationStatus")) { it.name.lowercase() },
            enumValue(f.string("kind")) { it.name.lowercase() }, number(f, "threshold"), number(f, "qualityFloor"),
            f.optionalString("operatingPoint"), f.optionalString("datasetRevision"), range(f),
            f.optionalString("enrollmentRule"), f.long("effectiveAuthorizationEpoch"))
    }

    fun encode(p: PADPolicy): String = IdentityContractJSON.encode(mapOf(
        "version" to 1, "id" to p.id, "revision" to p.revision, "backendId" to p.backendId,
        "backendVersion" to p.backendVersion, "modelBinding" to p.modelBinding.fields(),
        "calibrationStatus" to p.calibrationStatus.name.lowercase(), "mode" to p.mode.name.lowercase(),
        "challengePolicyRevision" to p.challengePolicyRevision, "minimumFrames" to p.minimumFrames,
        "maximumDurationNanos" to p.maximumDurationNanos, "qualityFloor" to bits(p.qualityFloor),
        "scoreMetric" to p.scoreMetric, "threshold" to bits(p.threshold),
        "classificationPolicyRevision" to p.classificationPolicyRevision, "datasetRevision" to p.datasetRevision,
        "operatingPoint" to p.operatingPoint, "attackTypes" to p.attackTypes.sorted(),
        "effectiveAuthorizationEpoch" to p.effectiveAuthorizationEpoch))

    fun pad(json: String): PADPolicy {
        val f = fields(json, padKeys); version(f)
        val attacks = (f["attackTypes"] as? List<*>)?.map { it as? String ?: invalid() } ?: invalid()
        if (attacks.distinct().size != attacks.size) invalid()
        return PADPolicy(f.string("id"), f.string("revision"), f.string("backendId"), f.string("backendVersion"),
            ModelBinding.decodeJSON(IdentityContractJSON.encode(f.objectValue("modelBinding"))),
            enumValue(f.string("calibrationStatus")) { it.name.lowercase() },
            enumValue(f.string("mode")) { it.name.lowercase() }, f.optionalString("challengePolicyRevision"),
            positiveInt(f.long("minimumFrames")), f.long("maximumDurationNanos"), number(f, "qualityFloor"),
            f.optionalString("scoreMetric"), number(f, "threshold"), f.optionalString("classificationPolicyRevision"),
            f.optionalString("datasetRevision"), f.optionalString("operatingPoint"), attacks.toSet(), f.long("effectiveAuthorizationEpoch"))
    }

    private fun version(f: Map<String, Any?>) { if (f.long("version") != 1L) invalid() }
    private fun positiveInt(value: Long): Int = if (value in 1..Int.MAX_VALUE.toLong()) value.toInt() else invalid()
    private fun range(f: Map<String, Any?>): IntRange? {
        if (f["gallerySizeRange"] == null) return null
        val r = f.objectValue("gallerySizeRange")
        if (r.keys != setOf("lower", "upper")) invalid()
        val lower = positiveInt(r.long("lower")); val upper = positiveInt(r.long("upper"))
        if (lower > upper) invalid()
        return lower..upper
    }
    private fun bits(value: Double?): String? = value?.toRawBits()?.toULong()?.toString(16)?.padStart(16, '0')
    private fun number(f: Map<String, Any?>, key: String): Double? {
        if (f[key] == null) return null
        val text = f.string(key)
        if (!Regex("[0-9a-f]{16}").matches(text)) invalid()
        return Double.fromBits(text.toULong(16).toLong()).takeIf { it.isFinite() } ?: invalid()
    }
    private fun invalid(): Nothing = throw IdentityError(IdentityErrorCode.INVALID_INPUT)
}
