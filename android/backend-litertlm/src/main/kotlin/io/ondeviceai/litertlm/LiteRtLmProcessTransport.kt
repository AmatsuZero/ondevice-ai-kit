package io.ondeviceai.litertlm

import android.os.IBinder
import android.os.Parcel

internal data class LiteRtLmProcessHello(
    val accepted: Boolean,
    val nonce: String,
    val pid: Int,
    val processName: String,
)

internal data class LiteRtLmProcessStart(
    val modelAbsolutePath: String,
    val imageAbsolutePath: String,
    val cacheAbsolutePath: String,
    val prompt: String,
    val maxOutputTokens: Int,
    val backendPolicy: LiteRtLmBackendPolicy,
)

/** Synchronous Binder calls; callers must use a worker thread, not the main or a Binder event thread. */
internal class LiteRtLmProcessTransport(private val binder: IBinder) {
    fun hello(nonce: String): LiteRtLmProcessHello =
        transact(HELLO, nonce, readReply = { reply ->
            val result = reply.readRequiredInt()
            if (result != OK && result != REJECT) invalidReply()
            val replyNonce = reply.readRequiredString()
            val pid = reply.readRequiredInt()
            val processName = reply.readRequiredString()
            reply.requireNoTrailingData()
            if (replyNonce != nonce) invalidReply()

            val accepted = result == OK
            if (accepted && (pid <= 0 || processName.isBlank())) invalidReply()
            LiteRtLmProcessHello(accepted, replyNonce, pid, processName)
        })

    fun start(nonce: String, request: LiteRtLmProcessStart): Boolean =
        transact(
            START,
            nonce,
            writeRequest = { data ->
                data.writeString(request.modelAbsolutePath)
                data.writeString(request.imageAbsolutePath)
                data.writeString(request.cacheAbsolutePath)
                data.writeString(request.prompt)
                data.writeInt(request.maxOutputTokens)
                data.writeString(request.backendPolicy.name)
            },
            readReply = ::readBooleanResult,
        )

    fun status(nonce: String): LiteRtLmProcessStatus {
        val rawStatus = transact(STATUS, nonce, readReply = { reply ->
            if (reply.readRequiredInt() != OK) invalidReply()
            val raw = reply.readRequiredString()
            reply.requireNoTrailingData()
            raw
        })
        return LiteRtLmProcessStatusCodec.decode(rawStatus, nonce)
    }

    fun cancel(nonce: String): Boolean =
        transact(CANCEL, nonce, readReply = ::readBooleanResult)

    fun retire(nonce: String): Boolean =
        transact(RETIRE, nonce, readReply = ::readBooleanResult)

    private fun <T> transact(
        code: Int,
        nonce: String,
        writeRequest: (Parcel) -> Unit = {},
        readReply: (Parcel) -> T,
    ): T {
        val request = Parcel.obtain()
        var reply: Parcel? = null
        try {
            val response = Parcel.obtain()
            reply = response
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeString(nonce)
            writeRequest(request)
            if (!binder.transact(code, request, response, 0)) invalidReply()
            return try {
                readReply(response)
            } catch (_: RuntimeException) {
                invalidReply()
            }
        } finally {
            try {
                reply?.recycle()
            } finally {
                request.recycle()
            }
        }
    }

    private fun readBooleanResult(reply: Parcel): Boolean {
        val result = reply.readRequiredInt()
        reply.requireNoTrailingData()
        return when (result) {
            OK -> true
            REJECT -> false
            else -> invalidReply()
        }
    }

    private fun Parcel.readRequiredInt(): Int {
        if (dataAvail() < Int.SIZE_BYTES) invalidReply()
        return readInt()
    }

    private fun Parcel.readRequiredString(): String {
        if (dataAvail() < Int.SIZE_BYTES) invalidReply()
        return readString() ?: invalidReply()
    }

    private fun Parcel.requireNoTrailingData() {
        if (dataAvail() != 0) invalidReply()
    }

    private fun invalidReply(): Nothing =
        throw IllegalStateException(INVALID_REPLY)

    private companion object {
        const val DESCRIPTOR = "io.ondeviceai.litertlm.process.v1"
        const val HELLO = 1
        const val START = 2
        const val STATUS = 3
        const val CANCEL = 4
        const val RETIRE = 5
        const val OK = 0
        const val REJECT = 1
        const val INVALID_REPLY = "invalid LiteRT-LM process reply"
    }
}
