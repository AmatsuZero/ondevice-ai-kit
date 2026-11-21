package io.ondeviceai.probe.litertlm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.File
import java.nio.file.Files

class LiteRtLmProbeTest {
    @Test
    fun preSendFailureClosesConversationBeforeEngineOnce() {
        val order = mutableListOf<String>()
        val owner = ProbeNativeOwner { order += "engine" }
        owner.acquiredConversation { order += "conversation" }
        owner.closeIfSafe()
        owner.closeIfSafe()
        assertEquals(listOf("conversation", "engine"), order)
    }

    @Test
    fun sendAttemptRetainsNativeResourcesWithoutDrainProof() {
        val order = mutableListOf<String>()
        val owner = ProbeNativeOwner { order += "engine" }
        owner.acquiredConversation { order += "conversation" }
        owner.beginSend()
        owner.closeIfSafe()
        assertEquals(emptyList(), order)
    }

    @Test
    fun initializationFailureStillClosesConstructedEngine() {
        val order = mutableListOf<String>()
        ProbeNativeOwner { order += "engine" }.closeIfSafe()
        assertEquals(listOf("engine"), order)
    }

    @Test
    fun inputMappingSpecKeepsImageBeforePrompt() {
        val input = LiteRtLmProbe.inputSpec("/data/local/tmp/fixture.png", "describe the image")
        assertEquals("/data/local/tmp/fixture.png", input.imagePath)
        assertEquals("describe the image", input.prompt)
    }

    @Test
    fun terminalAndClosedConversationDoNotProveNativeDrain() {
        val observation = LiteRtLmProbe.DrainObservation(
            terminalCallbacks = 1,
            cancelAtNanos = 1_000_000_000L,
            terminalAtNanos = 2_000_000_000L,
            deadAtNanos = 3_000_000_000L,
        )
        // 0.18.1 clears isAlive before nativeDeleteConversation returns.
        // Even ordered terminal/lifetime observations cannot establish completion.
        assertFalse(observation.isProven)
        assertFalse(observation.copy(deadAtNanos = 1_500_000_000L).isProven)
        assertFalse(observation.copy(terminalCallbacks = 2).isProven)
        assertFalse(observation.copy(deadAtNanos = null).isProven)
        assertFalse(observation.copy(deadAtNanos = 31_000_000_001L).isProven)
    }

    @Test
    fun oneOrDuplicateSnapshotCannotProveCallbackMode() {
        assertEquals("insufficient-samples", LiteRtLmProbe.classifyCallbackMode(listOf("a")))
        assertEquals("insufficient-samples", LiteRtLmProbe.classifyCallbackMode(listOf("a", "a")))
        assertEquals("cumulative", LiteRtLmProbe.classifyCallbackMode(listOf("a", "ab", "abc")))
        assertEquals("delta-or-reordered", LiteRtLmProbe.classifyCallbackMode(listOf("a", "b", "c")))
    }

    @Test
    fun callbackClassificationCannotPromoteLifetimeStateToDrainProof() {
        val drain = LiteRtLmProbe.DrainObservation(1, 1_000L, 2_000L, 3_000L)
        val report = LiteRtLmProbe.Report(3, 1, true, 1L, "delta-or-reordered",
            listOf(1, 1, 1), null, drain)
        assertEquals("unverified-callback-mode", LiteRtLmProbe.candidateStatus(report))
        assertEquals("unverified-native-drain-contract", LiteRtLmProbe.candidateStatus(
            report.copy(callbackMode = "cumulative", callbackTextLengths = listOf(1, 2, 3))))
    }

    @Test
    fun probeAssetPathsStayInsideAppFiles() {
        val root = Files.createTempDirectory("litertlm-probe-files").toFile()
        try {
            assertEquals(File(root, "models/model.litertlm").canonicalFile,
                LiteRtLmProbe.privateFile(root, "models/model.litertlm"))
            assertFailsWith<IllegalArgumentException> { LiteRtLmProbe.privateFile(root, "../outside") }
            assertFailsWith<IllegalArgumentException> { LiteRtLmProbe.privateFile(root, "/private/model") }
            assertFailsWith<IllegalArgumentException> { LiteRtLmProbe.privateFile(root, "") }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun samplingWaitsForThreeCallbacksOrTerminalOrDeadline() {
        assertTrue(!LiteRtLmProbe.readyToCancel(2, 0, 10_000))
        assertTrue(LiteRtLmProbe.readyToCancel(3, 0, 10_000))
        assertTrue(LiteRtLmProbe.readyToCancel(1, 1, 10_000))
        assertTrue(LiteRtLmProbe.readyToCancel(0, 0, 30_000))
    }
}
