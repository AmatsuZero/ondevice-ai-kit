package io.ondeviceai.litertlm

import android.os.Binder
import android.os.Parcel
import android.os.RemoteException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmProcessTransportInstrumentation {
    @Test
    fun helloAcceptsMatchingIdentityAndPacksNonce() {
        val nonce = "hello_nonce"
        val binder = FakeBinder { request, reply ->
            reply.writeInt(Protocol.OK)
            reply.writeString(nonce)
            reply.writeInt(2718)
            reply.writeString("io.ondeviceai.litertlm:odai_litertlm")
            true
        }

        val result = LiteRtLmProcessTransport(binder).hello(nonce)

        assertTrue(result.accepted)
        assertEquals(nonce, result.nonce)
        assertEquals(2718, result.pid)
        assertEquals("io.ondeviceai.litertlm:odai_litertlm", result.processName)
        assertSingleRequest(binder, Protocol.HELLO, nonce)
    }

    @Test
    fun helloExposesExplicitRejection() {
        val nonce = "rejected_nonce"
        val binder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.REJECT)
            reply.writeString(nonce)
            reply.writeInt(0)
            reply.writeString("")
            true
        }

        val result = LiteRtLmProcessTransport(binder).hello(nonce)

        assertFalse(result.accepted)
        assertEquals(nonce, result.nonce)
        assertSingleRequest(binder, Protocol.HELLO, nonce)
    }

    @Test
    fun startPacksAllFieldsInContractOrderAndAcceptsOk() {
        val nonce = "start_nonce"
        val request = startRequest()
        val binder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            true
        }

        assertTrue(LiteRtLmProcessTransport(binder).start(nonce, request))

        assertSingleRequest(
            binder,
            Protocol.START,
            nonce,
            StartFields(
                request.modelAbsolutePath,
                request.imageAbsolutePath,
                request.cacheAbsolutePath,
                request.prompt,
                request.maxOutputTokens,
                request.backendPolicy.name,
            ),
        )
    }

    @Test
    fun startExposesExplicitRejection() {
        val nonce = "start_rejected"
        val request = startRequest()
        val binder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.REJECT)
            true
        }

        assertFalse(LiteRtLmProcessTransport(binder).start(nonce, request))
        assertSingleRequest(
            binder,
            Protocol.START,
            nonce,
            StartFields(
                request.modelAbsolutePath,
                request.imageAbsolutePath,
                request.cacheAbsolutePath,
                request.prompt,
                request.maxOutputTokens,
                request.backendPolicy.name,
            ),
        )
    }

    @Test
    fun statusDecodesAndPreservesRawChunks() {
        val nonce = "status_nonce"
        val chunks = listOf(" leading\nline", "cumulative text")
        val rawStatus = """
            {"nonce":"status_nonce","phase":"TERMINAL","generation":"SUCCEEDED",
            "snapshots":[{"sequence":0,"text":" leading\nline"},{"sequence":1,"text":"cumulative text"}],
            "nativeAttempted":true,"nativeInitialized":true,"errorCode":null,"callbackMode":"UNPROVEN"}
        """.trimIndent()
        val binder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            reply.writeString(rawStatus)
            true
        }

        val result = LiteRtLmProcessTransport(binder).status(nonce)

        assertEquals(nonce, result.nonce)
        assertEquals(chunks, result.rawChunks)
        assertSingleRequest(binder, Protocol.STATUS, nonce)
    }

    @Test
    fun statusRejectionIsNotInterpretedAsUnownedState() {
        val binder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.REJECT)
            true
        }

        expectInvalidReply {
            LiteRtLmProcessTransport(binder).status("status_rejected")
        }
        assertSingleRequest(binder, Protocol.STATUS, "status_rejected")
    }

    @Test
    fun simpleOwnerOperationsPackNonceAndDecodeBothResults() {
        listOf(
            Triple(Protocol.CANCEL, true, "cancel_ok"),
            Triple(Protocol.CANCEL, false, "cancel_rejected"),
            Triple(Protocol.RETIRE, true, "retire_ok"),
            Triple(Protocol.RETIRE, false, "retire_rejected"),
        ).forEach { (code, expected, nonce) ->
            val resultCode = if (expected) Protocol.OK else Protocol.REJECT
            val binder = FakeBinder { _, reply ->
                reply.writeInt(resultCode)
                true
            }
            val transport = LiteRtLmProcessTransport(binder)

            val actual = when (code) {
                Protocol.CANCEL -> transport.cancel(nonce)
                Protocol.RETIRE -> transport.retire(nonce)
                else -> error("unexpected test transaction")
            }

            assertEquals(expected, actual)
            assertSingleRequest(binder, code, nonce)
        }
    }

    @Test
    fun falseTransactFailsClosed() {
        val binder = FakeBinder { _, _ -> false }

        expectInvalidReply { LiteRtLmProcessTransport(binder).cancel("false_transact") }
        assertSingleRequest(binder, Protocol.CANCEL, "false_transact")
    }

    @Test
    fun unknownReplyCodeFailsClosed() {
        val binder = FakeBinder { _, reply ->
            reply.writeInt(73)
            true
        }

        expectInvalidReply { LiteRtLmProcessTransport(binder).retire("unknown_code") }
        assertSingleRequest(binder, Protocol.RETIRE, "unknown_code")
    }

    @Test
    fun malformedRepliesFailClosedForNullTruncatedAndTrailingData() {
        val nullStatusBinder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            reply.writeString(null)
            true
        }
        expectInvalidReply {
            LiteRtLmProcessTransport(nullStatusBinder).status("null_json")
        }
        assertSingleRequest(nullStatusBinder, Protocol.STATUS, "null_json")

        val truncatedHelloBinder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            true
        }
        expectInvalidReply {
            LiteRtLmProcessTransport(truncatedHelloBinder).hello("truncated_hello")
        }
        assertSingleRequest(truncatedHelloBinder, Protocol.HELLO, "truncated_hello")

        val trailingReplyBinder = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            reply.writeInt(42)
            true
        }
        expectInvalidReply {
            LiteRtLmProcessTransport(trailingReplyBinder).cancel("trailing_reply")
        }
        assertSingleRequest(trailingReplyBinder, Protocol.CANCEL, "trailing_reply")
    }

    @Test
    fun nonceMismatchesFailWithoutMaskingStatusDecoderFailure() {
        val mismatchedHello = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            reply.writeString("different_nonce")
            reply.writeInt(2718)
            reply.writeString("service")
            true
        }
        expectInvalidReply {
            LiteRtLmProcessTransport(mismatchedHello).hello("requested_nonce")
        }
        assertSingleRequest(mismatchedHello, Protocol.HELLO, "requested_nonce")

        listOf(0 to "service", 2718 to "").forEach { (pid, processName) ->
            val invalidIdentity = FakeBinder { _, reply ->
                reply.writeInt(Protocol.OK)
                reply.writeString("requested_nonce")
                reply.writeInt(pid)
                reply.writeString(processName)
                true
            }
            expectInvalidReply {
                LiteRtLmProcessTransport(invalidIdentity).hello("requested_nonce")
            }
            assertSingleRequest(invalidIdentity, Protocol.HELLO, "requested_nonce")
        }

        val mismatchedStatus = FakeBinder { _, reply ->
            reply.writeInt(Protocol.OK)
            reply.writeString(validStatus("different_nonce"))
            true
        }
        try {
            LiteRtLmProcessTransport(mismatchedStatus).status("requested_nonce")
            throw AssertionError("strict status decoder failure was not preserved")
        } catch (failure: IllegalArgumentException) {
            assertEquals("invalid process status", failure.message)
        }
        assertSingleRequest(mismatchedStatus, Protocol.STATUS, "requested_nonce")
    }

    @Test
    fun remoteExceptionFromTransactIsPropagatedUnchanged() {
        val expected = RemoteException("test transport failure")
        val binder = FakeBinder { _, _ -> throw expected }

        try {
            LiteRtLmProcessTransport(binder).hello("remote_failure")
            throw AssertionError("RemoteException was not propagated")
        } catch (actual: RemoteException) {
            assertSame(expected, actual)
        }
        assertSingleRequest(binder, Protocol.HELLO, "remote_failure")
    }

    private fun startRequest() = LiteRtLmProcessStart(
        modelAbsolutePath = "/test/model.litertlm",
        imageAbsolutePath = "/test/image.jpg",
        cacheAbsolutePath = "/test/cache",
        prompt = "describe the test image",
        maxOutputTokens = 37,
        backendPolicy = LiteRtLmBackendPolicy.CPU_ONLY,
    )

    private fun validStatus(nonce: String) = """
        {"nonce":"$nonce","phase":"TERMINAL","generation":"SUCCEEDED",
        "snapshots":[{"sequence":0,"text":"complete chunk"}],
        "nativeAttempted":true,"nativeInitialized":true,"errorCode":null,"callbackMode":"UNPROVEN"}
    """.trimIndent()

    private fun assertSingleRequest(
        binder: FakeBinder,
        code: Int,
        nonce: String,
        start: StartFields? = null,
    ) {
        assertEquals(1, binder.requests.size)
        assertEquals(CapturedRequest(code, nonce, start), binder.requests.single())
    }

    private fun expectInvalidReply(block: () -> Any?) {
        try {
            block()
            throw AssertionError("malformed process reply was accepted")
        } catch (failure: IllegalStateException) {
            assertEquals("invalid LiteRT-LM process reply", failure.message)
        }
    }

    private data class CapturedRequest(
        val code: Int,
        val nonce: String,
        val start: StartFields? = null,
    )

    private data class StartFields(
        val modelAbsolutePath: String,
        val imageAbsolutePath: String,
        val cacheAbsolutePath: String,
        val prompt: String,
        val maxOutputTokens: Int,
        val backendPolicyName: String,
    )

    private class FakeBinder(
        private val responder: (CapturedRequest, Parcel) -> Boolean,
    ) : Binder() {
        val requests = mutableListOf<CapturedRequest>()

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (reply == null) return false
            check(requests.isEmpty()) { "fake Binder is bounded to one transaction" }
            data.enforceInterface(Protocol.DESCRIPTOR)
            val nonce = requireNotNull(data.readString())
            val start = if (code == Protocol.START) {
                StartFields(
                    modelAbsolutePath = requireNotNull(data.readString()),
                    imageAbsolutePath = requireNotNull(data.readString()),
                    cacheAbsolutePath = requireNotNull(data.readString()),
                    prompt = requireNotNull(data.readString()),
                    maxOutputTokens = data.readInt(),
                    backendPolicyName = requireNotNull(data.readString()),
                )
            } else {
                null
            }
            assertEquals("request must contain exactly the contracted fields", 0, data.dataAvail())
            val request = CapturedRequest(code, nonce, start)
            requests += request
            return responder(request, reply)
        }
    }

    private object Protocol {
        const val DESCRIPTOR = "io.ondeviceai.litertlm.process.v1"
        const val HELLO = 1
        const val START = 2
        const val STATUS = 3
        const val CANCEL = 4
        const val RETIRE = 5
        const val OK = 0
        const val REJECT = 1
    }
}
