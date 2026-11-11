package io.ondeviceai.acceptance.qwenfile

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JVM fixture-input verifier for the Qwen acceptance harness.
 *
 * No Android APIs, no network, no files written, no model/native work.
 * Every byte array handed back by the [read] callback is owned by this
 * component for the duration of the call and is zero-filled in `finally`
 * after use. Frozen supplied files on disk are never touched.
 */
data class InputCase(
    val id: String,
    val locale: String,
    val cohort: String,
    val samples: Int,
    val sha256: String,
    val reference: String,
    val asset: String
)

object QwenFileInputs {

    const val MANIFEST_SHA256 = "8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f"

    private const val EXPECTED_CASES = 65
    private const val SCHEMA_VERSION = 1
    private const val MAX_SAMPLES = 480000
    private const val MIN_SAMPLES = 1

    private val ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
    private val SHA_REGEX = Regex("[0-9a-f]{64}")
    private val LOCALES = setOf("en-US", "zh-CN")
    private val COHORTS = setOf(
        "mandarin", "english-near", "english-far", "nonspeech-noise", "silence"
    )
    private val HEX = "0123456789abcdef"

    // ---- shared digest / metadata / pcm helpers ----------------------------

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private fun checkSha256(bytes: ByteArray, expected: String, what: String) {
        val actual = sha256Hex(bytes)
        if (actual != expected) {
            throw IllegalArgumentException("sha256 mismatch for $what: got $actual expected $expected")
        }
    }

    private fun validateId(id: String) {
        if (id.contains('/') || id.contains('\\')) {
            throw IllegalArgumentException("id must not contain slash/backslash: $id")
        }
        if (!ID_REGEX.matches(id)) {
            throw IllegalArgumentException("id fails safe-basename regex: $id")
        }
    }

    private fun validateLocale(locale: String) {
        if (locale !in LOCALES) throw IllegalArgumentException("unsupported locale: $locale")
    }

    private fun validateCohort(cohort: String) {
        if (cohort !in COHORTS) throw IllegalArgumentException("unsupported cohort: $cohort")
    }

    private fun validateSamples(samples: Int) {
        if (samples < MIN_SAMPLES || samples > MAX_SAMPLES) {
            throw IllegalArgumentException("samples out of range [1,480000]: $samples")
        }
    }

    private fun validateSha(sha: String) {
        if (!SHA_REGEX.matches(sha)) throw IllegalArgumentException("sha256 not lowercase hex64: $sha")
    }

    private fun validateAsset(id: String, asset: String) {
        val expected = "inputs/$id.pcm"
        if (asset != expected) {
            throw IllegalArgumentException("asset $asset does not match expected $expected")
        }
    }

    /** Validate InputCase metadata without touching the asset provider. */
    private fun validateMetadata(c: InputCase) {
        validateId(c.id)
        validateLocale(c.locale)
        validateCohort(c.cohort)
        validateSamples(c.samples)
        validateSha(c.sha256)
        validateAsset(c.id, c.asset)
    }

    /**
     * Read + sha-verify a PCM asset, then zero-fill its buffer. Does not
     * convert samples. Returns nothing; throws on size/hash mismatch.
     */
    private fun verifyPcm(asset: String, samples: Int, sha: String, read: (String) -> ByteArray) {
        val pcm = read(asset)
        try {
            val expectedSize = samples * 2
            if (pcm.size != expectedSize) {
                throw IllegalArgumentException(
                    "size mismatch for $asset: got ${pcm.size} expected $expectedSize"
                )
            }
            checkSha256(pcm, sha, asset)
        } finally {
            pcm.fill(0)
        }
    }

    // ---- public API --------------------------------------------------------

    /**
     * Read cases.json, verify its fixed manifest hash, parse exactly 65
     * cases under schemaVersion 1, and sha/size-verify every PCM once.
     * Returns the cases in original order with no transformation of id,
     * reference, cohort or asset. Every buffer returned by [read] is
     * zero-filled in `finally`; original errors are never swallowed.
     */
    fun verifyAll(read: (String) -> ByteArray): List<InputCase> {
        val manifest = read("cases.json")
        try {
            checkSha256(manifest, MANIFEST_SHA256, "manifest cases.json")
            val root = JSONObject(String(manifest, Charsets.UTF_8))
            val schema = root.optInt("schemaVersion", -1)
            if (schema != SCHEMA_VERSION) {
                throw IllegalArgumentException("unsupported schemaVersion: $schema")
            }
            val arr: JSONArray = root.optJSONArray("cases")
                ?: throw IllegalArgumentException("missing cases array")
            if (arr.length() != EXPECTED_CASES) {
                throw IllegalArgumentException("expected $EXPECTED_CASES cases, got ${arr.length()}")
            }
            val seen = LinkedHashSet<String>()
            val out = ArrayList<InputCase>(EXPECTED_CASES)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val samples = o.getInt("samples")
                val locale = o.getString("locale")
                val cohort = o.getString("cohort")
                val sha = o.getString("sha256")
                val reference = o.getString("reference")
                val asset = o.getString("asset")

                validateId(id)
                validateLocale(locale)
                validateCohort(cohort)
                validateSamples(samples)
                validateSha(sha)
                if (!seen.add(id)) throw IllegalArgumentException("duplicate id: $id")
                validateAsset(id, asset)

                verifyPcm(asset, samples, sha, read)

                out.add(InputCase(id, locale, cohort, samples, sha, reference, asset))
            }
            return out
        } finally {
            manifest.fill(0)
        }
    }

    /**
     * Fresh-read one case's PCM, verify size + sha, and convert the whole
     * s16LE buffer to a FloatArray of exactly [InputCase.samples] values as
     * short/32768f with exact raw-float bits. No gain/crop/VAD/normalize.
     * Metadata is validated before any [read] call; on failure the temporary
     * buffer is still zero-filled and the original error is rethrown.
     */
    fun readSamples(case: InputCase, read: (String) -> ByteArray): FloatArray {
        validateMetadata(case)
        val pcm = read(case.asset)
        try {
            val expectedSize = case.samples * 2
            if (pcm.size != expectedSize) {
                throw IllegalArgumentException(
                    "size mismatch for ${case.asset}: got ${pcm.size} expected $expectedSize"
                )
            }
            checkSha256(pcm, case.sha256, case.asset)
            val n = case.samples
            val out = FloatArray(n)
            for (i in 0 until n) {
                val b0 = pcm[2 * i].toInt() and 0xFF
                val b1 = pcm[2 * i + 1].toInt() and 0xFF
                val s = (b0 or (b1 shl 8)).toShort()
                out[i] = s.toFloat() / 32768f
            }
            return out
        } finally {
            pcm.fill(0)
        }
    }
}
