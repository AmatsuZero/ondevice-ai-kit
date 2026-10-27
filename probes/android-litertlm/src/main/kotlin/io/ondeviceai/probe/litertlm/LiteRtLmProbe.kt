package io.ondeviceai.probe.litertlm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Read-only probe for the pinned LiteRT-LM 0.18.1 Android surface.
 *
 * It intentionally does not own a model asset. A caller must provide one before
 * invoking [run]; the returned report contains lengths and state only, never
 * prompts, paths, or generated text.
 */
object LiteRtLmProbe {
    private const val MAX_OUTPUT_TOKENS = 512
    private const val MAX_IMAGES = 1
    private const val DRAIN_TIMEOUT_MILLIS = 30_000L

    data class DrainObservation(
        val terminalCallbacks: Int,
        val cancelAtNanos: Long,
        val terminalAtNanos: Long?,
        val deadAtNanos: Long?,
    ) {
        // In 0.18.1, isAlive is cleared by close() before native deletion returns.
        // It is object-lifetime state, not a native-work completion barrier.
        // Keep these raw observations, but never certify drain from them.
        val isProven: Boolean
            get() = false
    }

    data class Report(
        val callbackCount: Int,
        val terminalCallbacks: Int,
        val sawDeadConversation: Boolean,
        val drainElapsedMillis: Long,
        val callbackMode: String,
        val callbackTextLengths: List<Int>,
        val errorClass: String?,
        val drain: DrainObservation,
    )

    data class InputSpec(val imagePath: String, val prompt: String)

    fun candidateStatus(report: Report): String = when {
        report.errorClass != null || report.terminalCallbacks != 1 -> "unverified-drain-or-callback"
        report.callbackMode != "cumulative" -> "unverified-callback-mode"
        else -> "unverified-native-drain-contract"
    }

    fun inputSpec(imagePath: String, prompt: String): InputSpec = InputSpec(imagePath, prompt)

    fun privateFile(root: File, relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute)
        val base = root.canonicalFile
        val resolved = File(base, relative).canonicalFile
        require(resolved.path.startsWith(base.path + File.separator))
        return resolved
    }

    internal fun readyToCancel(callbackCount: Int, terminalCallbacks: Int, elapsedMillis: Long): Boolean =
        callbackCount >= 3 || terminalCallbacks > 0 || elapsedMillis >= DRAIN_TIMEOUT_MILLIS

    fun inputContents(imagePath: String, prompt: String): Contents =
        Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))

    fun userMessage(imagePath: String, prompt: String): Message =
        Message.user(inputContents(imagePath, prompt))

    fun engineConfig(modelPath: String, cacheDir: File): EngineConfig = EngineConfig(
        modelPath = modelPath,
        backend = Backend.CPU(),
        visionBackend = Backend.CPU(),
        maxNumTokens = MAX_OUTPUT_TOKENS,
        maxNumImages = MAX_IMAGES,
        cacheDir = cacheDir.absolutePath,
    )

    fun run(modelPath: String, cacheDir: File, imagePath: String, prompt: String): Report {
        require(File(modelPath).exists()) { "model asset is required for the probe" }
        require(File(imagePath).exists()) { "fixture asset is required for the probe" }

        val engine = Engine(engineConfig(modelPath, cacheDir))
        val owner = ProbeNativeOwner { engine.close() }
        try {
        engine.initialize()
        val conversation = engine.createConversation(ConversationConfig(maxOutputToken = MAX_OUTPUT_TOKENS))
        owner.acquiredConversation { conversation.close() }
        val callbackCount = AtomicInteger(0)
        val terminalCallbacks = AtomicInteger(0)
        val firstTerminalAt = AtomicLong(0)
        val cancelled = AtomicBoolean(false)
        val snapshots = mutableListOf<String>()
        var errorClass: String? = null

        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                if (cancelled.get()) return
                val text = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                synchronized(snapshots) {
                    snapshots += text
                    callbackCount.incrementAndGet()
                }
            }

            override fun onDone() {
                terminalCallbacks.incrementAndGet()
                firstTerminalAt.compareAndSet(0, System.nanoTime())
            }

            override fun onError(throwable: Throwable) {
                errorClass = throwable::class.java.simpleName
                terminalCallbacks.incrementAndGet()
                firstTerminalAt.compareAndSet(0, System.nanoTime())
            }
        }

        owner.beginSend()
        conversation.sendMessageAsync(userMessage(imagePath, prompt), callback)
        val sampleStart = System.nanoTime()
        while (!readyToCancel(callbackCount.get(), terminalCallbacks.get(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sampleStart))) {
            Thread.sleep(25)
        }
        val cancelAt = System.nanoTime()
        cancelled.set(true)
        conversation.cancelProcess()
        val deadline = cancelAt + TimeUnit.MILLISECONDS.toNanos(DRAIN_TIMEOUT_MILLIS)
        var deadAt: Long? = null
        while (System.nanoTime() <= deadline && (firstTerminalAt.get() == 0L || deadAt == null)) {
            try {
                if (deadAt == null && !conversation.isAlive) deadAt = System.nanoTime()
            } catch (failure: Throwable) {
                errorClass = failure::class.java.simpleName
                break
            }
            if (firstTerminalAt.get() != 0L && deadAt != null) break
            Thread.sleep(25)
        }
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelAt)
        val drain = DrainObservation(terminalCallbacks.get(), cancelAt, firstTerminalAt.get().takeIf { it != 0L }, deadAt)
        // No verified native drain barrier is exposed by these observations.
        // Retain the post-send resources; never close based on isAlive alone.
        val mode = synchronized(snapshots) { classifyCallbackMode(snapshots) }
        val lengths = synchronized(snapshots) { snapshots.map(String::length) }
        return Report(callbackCount.get(), terminalCallbacks.get(), deadAt != null, elapsed, mode, lengths, errorClass, drain)
        } finally {
            owner.closeIfSafe()
        }
    }

    internal fun classifyCallbackMode(snapshots: List<String>): String = when {
        snapshots.filter { it.isNotEmpty() }.distinct().size < 3 -> "insufficient-samples"
        snapshots.zipWithNext().all { (previous, current) -> current.length > previous.length && current.startsWith(previous) } -> "cumulative"
        else -> "delta-or-reordered"
    }
}
