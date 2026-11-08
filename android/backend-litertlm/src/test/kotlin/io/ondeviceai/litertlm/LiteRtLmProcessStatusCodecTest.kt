package io.ondeviceai.litertlm

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LiteRtLmProcessStatusCodecTest {
    @Test
    fun acceptsReservedAndInitializingBeforeNativeAttempt() {
        assertStatus(status("RESERVED"), "RESERVED", "NONE", emptyList())
        assertStatus(
            status("INITIALIZING", nativeAttempted = false, nativeInitialized = false),
            "INITIALIZING",
            "NONE",
            emptyList(),
        )
    }

    @Test
    fun acceptsGeneratingWithoutAnyCallbackChunks() {
        assertStatus(
            status(
                "GENERATING",
                generation = "RUNNING",
                nativeAttempted = true,
                nativeInitialized = true,
            ),
            "GENERATING",
            "RUNNING",
            emptyList(),
        )
    }

    @Test
    fun preservesExactRepeatedChunksAndAcceptsPartialCanceledOutput() {
        val chunks = listOf("  first,\n", "same!", "same!", "\tlast? ")
        val decoded = LiteRtLmProcessStatusCodec.decode(
            status(
                "TERMINAL",
                generation = "CANCELED",
                nativeAttempted = true,
                nativeInitialized = true,
                chunks = chunks,
            ),
            NONCE,
        )

        assertEquals(chunks, decoded.rawChunks)
        assertEquals("CANCELED", decoded.generation)
        assertEquals(true, decoded.nativeInitialized)
        assertFailsWith<UnsupportedOperationException> {
            (decoded.rawChunks as MutableList<String>).add("later")
        }
    }

    @Test
    fun acceptsSuccessfulAndErrorTerminalStatuses() {
        assertStatus(
            status(
                "TERMINAL",
                generation = "SUCCEEDED",
                nativeAttempted = true,
                nativeInitialized = true,
                chunks = listOf(" result \n", "result"),
            ),
            "TERMINAL",
            "SUCCEEDED",
            listOf(" result \n", "result"),
        )
        val error = LiteRtLmProcessStatusCodec.decode(
            status("TERMINAL", generation = "ERROR", errorCode = "NativeFailure"),
            NONCE,
        )
        assertEquals("NativeFailure", error.errorCode)
    }

    @Test
    fun acceptsCanceledAndLostWithoutInventingAnError() {
        for (generation in listOf("CANCELED", "LOST")) {
            val decoded = LiteRtLmProcessStatusCodec.decode(
                status("TERMINAL", generation = generation),
                NONCE,
            )
            assertEquals(generation, decoded.generation)
            assertEquals(null, decoded.errorCode)
        }
    }

    @Test
    fun rejectsUnknownModesNoncesKeysAndPhases() {
        val valid = status("RESERVED")
        val wrongMode = JSONObject(valid).put("callbackMode", "DELTA").toString()
        val wrongNonce = JSONObject(valid).put("nonce", "different").toString()
        val extraKey = JSONObject(valid).put("prompt", "private").toString()
        val unknownPhase = JSONObject(valid).put("phase", "WAITING").toString()

        listOf(wrongMode, wrongNonce, extraKey, unknownPhase).forEach(::assertInvalid)
        assertInvalid(status("RESERVED"), expectedNonce = "bad nonce")
    }

    @Test
    fun rejectsInvalidPhaseCombinationsAndAttemptFlags() {
        val cases = listOf(
            status("RESERVED", generation = "RUNNING"),
            status("RESERVED", nativeAttempted = true),
            status("INITIALIZING", generation = "NONE", chunks = listOf("early")),
            status("GENERATING", generation = "RUNNING", nativeAttempted = false),
            status(
                "TERMINAL",
                generation = "SUCCEEDED",
                nativeAttempted = true,
                nativeInitialized = true,
            ),
            status("TERMINAL", generation = "ERROR"),
            status("TERMINAL", generation = "CANCELED", errorCode = "Canceled"),
            status("TERMINAL", generation = "NONE"),
            status("TERMINAL", nativeInitialized = true),
        )
        cases.forEach(::assertInvalid)
    }

    @Test
    fun rejectsCoercedPrimitiveTypesAndUnexpectedChunkFields() {
        val wrongAttemptType = JSONObject(status("RESERVED"))
            .put("nativeAttempted", "false")
            .toString()
        val wrongSnapshotType = JSONObject(
            status(
                "TERMINAL",
                generation = "CANCELED",
                chunks = listOf("x"),
            ),
        ).put("snapshots", "[]").toString()
        val wrongTextType = withSnapshots(
            status("TERMINAL", generation = "CANCELED"),
            JSONArray().put(JSONObject().put("sequence", 0).put("text", 7)),
        )
        val extraChunkKey = withSnapshots(
            status("TERMINAL", generation = "CANCELED"),
            JSONArray().put(
                JSONObject().put("sequence", 0).put("text", "x").put("other", true),
            ),
        )

        listOf(wrongAttemptType, wrongSnapshotType, wrongTextType, extraChunkKey)
            .forEach(::assertInvalid)
    }

    @Test
    fun requiresActualIntegralSequenceNumbersInOrder() {
        val stringSequence = withSnapshots(
            status("TERMINAL", generation = "CANCELED"),
            JSONArray().put(JSONObject().put("sequence", "0").put("text", "x")),
        )
        val floatingSequence = status("TERMINAL", generation = "CANCELED").replace(
            "\"snapshots\":[]",
            "\"snapshots\":[{\"sequence\":0.0,\"text\":\"x\"}]",
        )
        assertEquals(
            listOf("0.0"),
            Regex("\"sequence\"\\s*:\\s*([^,}\\s]+)")
                .findAll(floatingSequence)
                .map { it.groupValues[1] }
                .toList(),
        )
        val outOfOrder = withSnapshots(
            status("TERMINAL", generation = "CANCELED", chunks = listOf("a", "b")),
            JSONArray()
                .put(JSONObject().put("sequence", 1).put("text", "a"))
                .put(JSONObject().put("sequence", 0).put("text", "b")),
        )

        listOf(stringSequence, floatingSequence, outOfOrder).forEach(::assertInvalid)
    }

    @Test
    fun enforcesChunkAndAccumulatedUtf8ByteBoundaries() {
        val exactChunk = "é".repeat(4_096)
        val exactTotal = listOf(exactChunk, exactChunk, exactChunk, exactChunk)
        val accepted = LiteRtLmProcessStatusCodec.decode(
            status("TERMINAL", generation = "CANCELED", chunks = exactTotal),
            NONCE,
        )
        assertEquals(exactTotal, accepted.rawChunks)

        assertInvalid(
            status("TERMINAL", generation = "CANCELED", chunks = listOf("é".repeat(4_097))),
        )
        assertInvalid(
            status(
                "TERMINAL",
                generation = "CANCELED",
                chunks = listOf(exactChunk, exactChunk, exactChunk, exactChunk, "x"),
            ),
        )
    }

    @Test
    fun enforcesSnapshotCountAndNonblankChunkRules() {
        val maximumChunks = List(256) { "x" }
        val maximumCount = LiteRtLmProcessStatusCodec.decode(
            status("TERMINAL", generation = "CANCELED", chunks = maximumChunks),
            NONCE,
        )
        assertEquals(256, maximumCount.rawChunks.size)

        assertInvalid(status("TERMINAL", generation = "CANCELED", chunks = List(257) { "x" }))
        assertInvalid(status("TERMINAL", generation = "CANCELED", chunks = listOf("")))
        assertInvalid(
            status(
                "TERMINAL",
                generation = "SUCCEEDED",
                nativeAttempted = true,
                nativeInitialized = true,
                chunks = listOf(" \n\t"),
            ),
        )
    }

    @Test
    fun enforcesRawUtf8ByteLimitIncludingBoundary() {
        val base = status("RESERVED")
        val atLimit = base + " ".repeat(262_144 - base.toByteArray(Charsets.UTF_8).size)
        assertEquals(
            "RESERVED",
            LiteRtLmProcessStatusCodec.decode(atLimit, NONCE).phase,
        )
        assertInvalid(atLimit + " ")
    }

    @Test
    fun rejectsMalformedJsonTrailingContentAndUnsafeErrorCodes() {
        listOf(
            "{",
            "[]",
            status("RESERVED") + " {}",
            status("TERMINAL", generation = "ERROR", errorCode = "_unsafe"),
            status("TERMINAL", generation = "ERROR", errorCode = "bad\ncode"),
        ).forEach(::assertInvalid)
    }

    private fun assertStatus(
        raw: String,
        phase: String,
        generation: String,
        chunks: List<String>,
    ) {
        val decoded = LiteRtLmProcessStatusCodec.decode(raw, NONCE)
        assertEquals(NONCE, decoded.nonce)
        assertEquals(phase, decoded.phase)
        assertEquals(generation, decoded.generation)
        assertEquals(chunks, decoded.rawChunks)
    }

    private fun assertInvalid(raw: String) = assertInvalid(raw, NONCE)

    private fun assertInvalid(raw: String, expectedNonce: String) {
        val exception = assertFailsWith<IllegalArgumentException> {
            LiteRtLmProcessStatusCodec.decode(raw, expectedNonce)
        }
        assertEquals("invalid process status", exception.message)
    }

    private fun status(
        phase: String,
        generation: String = "NONE",
        nativeAttempted: Boolean = false,
        nativeInitialized: Boolean = false,
        chunks: List<String> = emptyList(),
        errorCode: String? = null,
    ): String {
        val snapshots = JSONArray()
        chunks.forEachIndexed { index, text ->
            snapshots.put(JSONObject().put("sequence", index).put("text", text))
        }
        return JSONObject()
            .put("nonce", NONCE)
            .put("phase", phase)
            .put("generation", generation)
            .put("snapshots", snapshots)
            .put("nativeAttempted", nativeAttempted)
            .put("nativeInitialized", nativeInitialized)
            .put("errorCode", errorCode ?: JSONObject.NULL)
            .put("callbackMode", "UNPROVEN")
            .toString()
    }

    private fun withSnapshots(raw: String, snapshots: JSONArray): String =
        JSONObject(raw).put("snapshots", snapshots).toString()

    private companion object {
        const val NONCE = "valid_nonce-1"
    }
}
