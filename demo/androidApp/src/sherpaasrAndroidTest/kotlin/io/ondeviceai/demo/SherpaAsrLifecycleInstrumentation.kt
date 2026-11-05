package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import io.ondeviceai.core.BusyException
import io.ondeviceai.core.ClosedException
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Operator-only replay of an existing synthetic fixture. Never opens a microphone. */
class SherpaAsrLifecycleInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val report = Bundle().apply {
            putString("scope", "sdk-fixed-synthetic-native-lifecycle")
            putInt("microphoneStarts", 0)
            putBoolean("audioPersisted", false)
            putBoolean("transcriptPersisted", false)
            putBoolean("threadStacksPersisted", false)
        }
        try {
            startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val bytes = context.assets.open("english.pcm").use { it.readBytes() }
            try {
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                check(digest == "fc0f3f5bb9b06a4c21e12321d5fb0bc7d3e3151dca855e5ed9693907ce39df46")
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val samples = FloatArray(buffer.remaining()) { buffer.get() / 32768f }
                val original = samples.copyOf()
                try {
                    runBlocking {
                        val model = DemoOfflineAsrFactory.prepareModel(targetContext)
                        model.verify()
                        val backend = SherpaAsrBackend(targetContext, model)
                        try {
                            val delivered = AtomicBoolean(false)
                            val request = async(Dispatchers.Default) {
                                backend.transcribe(samples, "en-US").also { delivered.set(true) }
                            }
                            try {
                                awaitNativeDecode(request)
                                report.putBoolean("cancel.nativeDecodeObserved", true)
                                expectBusy { backend.transcribe(samples, "en-US") }
                                report.putBoolean("cancel.busyWhileNativeDecode", true)
                                request.cancel()
                                // The same backend must remain admitted until synchronous JNI exits.
                                expectBusy { backend.transcribe(samples, "en-US") }
                                report.putBoolean("cancel.busyBeforeDrain", true)
                                withTimeout(15_000) { request.join() }
                                check(request.isCancelled && !delivered.get())
                                report.putBoolean("cancel.noResultDelivered", true)
                                val again = withTimeout(15_000) { backend.transcribe(samples, "en-US") }
                                check(again.backendId == "sherpa.onnx.whisper")
                                check(again.transcript.lowercase().filter { it.isLetterOrDigit() } ==
                                    "thisisanondevicespeechtest")
                                report.putBoolean("cancel.sameBackendReentryMatched", true)
                                check(samples.contentEquals(original))
                                report.putBoolean("cancel.callerInputUnchanged", true)
                            } finally {
                                withContext(NonCancellable) { request.cancelAndJoin() }
                            }
                        } finally { backend.close() }

                        val closingBackend = SherpaAsrBackend(targetContext, model)
                        try {
                            val delivered = AtomicBoolean(false)
                            val request = async(Dispatchers.Default) {
                                closingBackend.transcribe(samples, "en-US").also { delivered.set(true) }
                            }
                            try {
                                awaitNativeDecode(request)
                                report.putBoolean("close.nativeDecodeObserved", true)
                                val closing = async(start = CoroutineStart.UNDISPATCHED) { closingBackend.close() }
                                check(!closing.isCompleted)
                                report.putBoolean("close.waitedForActiveNativeRequest", true)
                                expectClosed { closingBackend.transcribe(samples, "en-US") }
                                report.putBoolean("close.rejectedDuringDrain", true)
                                withTimeout(15_000) { closing.await(); request.join() }
                                check(request.isCancelled && !delivered.get())
                                report.putBoolean("close.noResultDelivered", true)
                                expectClosed { closingBackend.transcribe(samples, "en-US") }
                                report.putBoolean("close.rejectedAfterClose", true)
                                closingBackend.close()
                                report.putBoolean("close.idempotent", true)
                                check(samples.contentEquals(original))
                                report.putBoolean("close.callerInputUnchanged", true)
                            } finally {
                                withContext(NonCancellable) { request.cancelAndJoin() }
                            }
                        } finally { closingBackend.close() }
                    }
                    report.putString("status", "passed")
                } finally { samples.fill(0f); original.fill(0f) }
            } finally { bytes.fill(0) }
        } catch (error: Exception) {
            report.putString("status", "failed")
            report.putString("errorClass", error.javaClass.name)
        }
        finish(-1, report)
    }

    private suspend fun awaitNativeDecode(request: Deferred<*>) = withTimeout(15_000) {
        while (true) {
            check(!request.isCompleted) { "request ended before native decode observation" }
            // Inspect only this process. Persist neither stack contents nor other thread identities.
            if (Thread.getAllStackTraces().values.any { frames -> frames.any {
                    it.className == "com.k2fsa.sherpa.onnx.OfflineRecognizer" &&
                        it.methodName == "decode" && it.isNativeMethod
                } }) break
            delay(5)
        }
    }

    private suspend fun expectBusy(block: suspend () -> Unit) {
        try { block() } catch (_: BusyException) { return }
        error("active request did not reject reentry")
    }

    private suspend fun expectClosed(block: suspend () -> Unit) {
        try { block() } catch (_: ClosedException) { return }
        error("closed backend accepted a request")
    }
}
