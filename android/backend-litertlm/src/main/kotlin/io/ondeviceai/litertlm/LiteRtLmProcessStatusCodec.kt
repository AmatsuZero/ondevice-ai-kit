package io.ondeviceai.litertlm

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Collections

internal data class LiteRtLmProcessStatus(
    val nonce: String,
    val phase: String,
    val generation: String,
    val nativeAttempted: Boolean,
    val nativeInitialized: Boolean,
    val rawChunks: List<String>,
    val errorCode: String?,
)

internal object LiteRtLmProcessStatusCodec {
    private const val INVALID_STATUS = "invalid process status"
    private const val MAX_RAW_BYTES = 262_144
    private const val MAX_CHUNKS = 256
    private const val MAX_CHUNK_BYTES = 8_192
    private const val MAX_TOTAL_CHUNK_BYTES = 32_768

    private val noncePattern = Regex("[A-Za-z0-9_-]{1,128}")
    private val errorCodePattern = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    private val rootKeys = setOf(
        "nonce",
        "phase",
        "generation",
        "snapshots",
        "nativeAttempted",
        "nativeInitialized",
        "errorCode",
        "callbackMode",
    )
    private val snapshotKeys = setOf("sequence", "text")

    fun decode(raw: String, expectedNonce: String): LiteRtLmProcessStatus {
        if (!noncePattern.matches(expectedNonce)) invalid()
        if (raw.length > MAX_RAW_BYTES || raw.indexOf('\u0000') >= 0) invalid()
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_RAW_BYTES) invalid()

        val tokener = JSONTokener(raw)
        val root = try {
            tokener.nextValue() as? JSONObject ?: invalid()
        } catch (_: JSONException) {
            invalid()
        }
        try {
            if (tokener.nextClean() != '\u0000') invalid()
            requireKeys(root, rootKeys)

            val nonce = root.requiredString("nonce")
            if (!noncePattern.matches(nonce) || nonce != expectedNonce) invalid()
            val phase = root.requiredString("phase")
            val generation = root.requiredString("generation")
            val nativeAttempted = root.requiredBoolean("nativeAttempted")
            val nativeInitialized = root.requiredBoolean("nativeInitialized")
            val errorCode = root.optionalSafeCode("errorCode")
            if (root.requiredString("callbackMode") != "UNPROVEN") invalid()
            if (nativeInitialized && !nativeAttempted) invalid()

            val rawChunks = root.requiredChunks()
            when (phase) {
                "RESERVED" -> {
                    if (generation != "NONE" || nativeAttempted || nativeInitialized ||
                        rawChunks.isNotEmpty() || errorCode != null
                    ) {
                        invalid()
                    }
                }

                "INITIALIZING" -> {
                    if (generation != "NONE" || rawChunks.isNotEmpty() || errorCode != null) {
                        invalid()
                    }
                }

                "GENERATING" -> {
                    if (generation != "RUNNING" || !nativeAttempted || !nativeInitialized ||
                        errorCode != null
                    ) {
                        invalid()
                    }
                }

                "TERMINAL" -> when (generation) {
                    "SUCCEEDED" -> {
                        if (!nativeAttempted || !nativeInitialized || errorCode != null ||
                            rawChunks.none { it.isNotBlank() }
                        ) {
                            invalid()
                        }
                    }

                    "ERROR" -> if (errorCode == null) invalid()
                    "CANCELED", "LOST" -> if (errorCode != null) invalid()
                    else -> invalid()
                }

                else -> invalid()
            }

            return LiteRtLmProcessStatus(
                nonce = nonce,
                phase = phase,
                generation = generation,
                nativeAttempted = nativeAttempted,
                nativeInitialized = nativeInitialized,
                rawChunks = Collections.unmodifiableList(ArrayList(rawChunks)),
                errorCode = errorCode,
            )
        } catch (_: JSONException) {
            invalid()
        }
    }

    private fun JSONObject.requiredString(key: String): String =
        get(key) as? String ?: invalid()

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: invalid()

    private fun JSONObject.optionalSafeCode(key: String): String? {
        val value = get(key)
        if (value === JSONObject.NULL) return null
        val code = value as? String ?: invalid()
        if (!errorCodePattern.matches(code)) invalid()
        return code
    }

    private fun JSONObject.requiredChunks(): List<String> {
        val snapshots = get("snapshots") as? JSONArray ?: invalid()
        if (snapshots.length() > MAX_CHUNKS) invalid()

        val chunks = ArrayList<String>(snapshots.length())
        var totalBytes = 0
        for (index in 0 until snapshots.length()) {
            val snapshot = snapshots.get(index) as? JSONObject ?: invalid()
            requireKeys(snapshot, snapshotKeys)
            val sequence = snapshot.get("sequence")
            val actualSequence = when (sequence) {
                is Int -> sequence.toLong()
                is Long -> sequence
                else -> invalid()
            }
            if (actualSequence != index.toLong()) invalid()

            val text = snapshot.get("text") as? String ?: invalid()
            if (text.isEmpty() || text.length > MAX_CHUNK_BYTES) invalid()
            val byteCount = text.toByteArray(Charsets.UTF_8).size
            if (byteCount > MAX_CHUNK_BYTES) invalid()
            totalBytes += byteCount
            if (totalBytes > MAX_TOTAL_CHUNK_BYTES) invalid()
            chunks.add(text)
        }
        return chunks
    }

    private fun requireKeys(value: JSONObject, expected: Set<String>) {
        val keys = value.keys()
        val actual = HashSet<String>()
        while (keys.hasNext()) actual.add(keys.next())
        if (actual != expected) invalid()
    }

    private fun invalid(): Nothing = throw IllegalArgumentException(INVALID_STATUS)
}
