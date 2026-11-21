package io.ondeviceai.litertlm

import android.app.ActivityManager
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Dedicated-process LiteRT-LM generation service (manual Binder protocol, pinned 0.18.1 API).
 *
 * Ownership is held for the whole process lifetime by [LiteRtLmProcessServiceState]; a semantic
 * terminal never releases it. The future client (via the supplied coordinator) keeps its ModelStore
 * lease until the exact Binder death produced by RETIRE / dedicated-process self-termination. This
 * service therefore never claims that a terminal/callback/isAlive implies native drain.
 *
 * No invented API/Flow/upstream changes, no public factory change, no production async advertising.
 */
class LiteRtLmProcessService : Service() {

    companion object {
        const val DESCRIPTOR = "io.ondeviceai.litertlm.process.v1"

        const val HELLO = 1
        const val START = 2
        const val STATUS = 3
        const val CANCEL = 4
        const val RETIRE = 5

        const val OK = 0
        const val REJECT = 1

        private const val PROCESS_SUFFIX = ":odai_litertlm"
        private const val MAX_PROMPT_UTF8_BYTES = 8192
        private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
    }

    // Defaults (256 / 32768 / 8192) bound the STATUS payload to <=32KiB UTF8 / 256 chunks / 8KiB chunk.
    private val state = LiteRtLmProcessServiceState()
    private val executor = Executors.newSingleThreadExecutor()

    // Strong refs kept until the exact dedicated process dies; never closed after an async attempt.
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var messageCallback: MessageCallback? = null

    override fun onBind(intent: Intent?): IBinder? =
        if (isExactProcess()) BinderImpl() else null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        // Stop accepting work. Do NOT call conversation.close()/engine.close() (uncertain destructors)
        // after an async attempt. Self-retire is guarded to the exact dedicated process only.
        executor.shutdownNow()
        super.onDestroy()
        retireExactProcess()
    }

    // Post self-termination on a raw thread (not the executor) so it works during initialization
    // and never queues behind a stuck init/send. Guard the exact process immediately before the
    // kill; kill only my own PID, never a main/foreign process.
    private fun retireExactProcess() {
        Thread {
            if (isExactProcess()) {
                Process.killProcess(Process.myPid())
            }
        }.start()
    }

    // ---- exact process identity (never kill the main/foreign process) ----

    private fun expectedProcessName(): String = packageName + PROCESS_SUFFIX

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pid = Process.myPid()
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun isExactProcess(): Boolean = currentProcessName() == expectedProcessName()

    // ---- manual Binder protocol ----

    private inner class BinderImpl : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            // `data` and `reply` are caller-owned; never recycled here. No internal Parcel is allocated.
            if (reply == null) return false
            try {
                data.enforceInterface(DESCRIPTOR)
            } catch (_: Throwable) {
                return false
            }
            if (Binder.getCallingUid() != Process.myUid()) return false
            return when (code) {
                HELLO -> { handleHello(data, reply); true }
                START -> { handleStart(data, reply); true }
                STATUS -> { handleStatus(data, reply); true }
                CANCEL -> { handleCancel(data, reply); true }
                RETIRE -> { handleRetire(data, reply); true }
                else -> false // reject malformed/unknown codes
            }
        }
    }

    private fun handleHello(data: Parcel, reply: Parcel) {
        val nonce = data.readString() ?: ""
        val reserved = data.dataAvail() == 0 && state.reserve(nonce)
        reply.writeInt(if (reserved) OK else REJECT)
        reply.writeString(nonce)                 // echoNonce
        reply.writeInt(Process.myPid())          // processPID
        reply.writeString(currentProcessName())  // exactProcessName
    }

    private fun handleStart(data: Parcel, reply: Parcel) {
        val nonce = data.readString()
        val modelPath = data.readString()
        val imagePath = data.readString()
        val cachePath = data.readString()
        val prompt = data.readString()
        val maxTokens = data.readInt()
        val policyName = data.readString()

        // Complete Parcel + no trailing data; all validation happens BEFORE state.begin.
        if (nonce == null || modelPath == null || imagePath == null || cachePath == null ||
            prompt == null || policyName == null || data.dataAvail() != 0
        ) {
            reply.writeInt(REJECT)
            return
        }
        val policy = when (policyName) {
            LiteRtLmBackendPolicy.CPU_ONLY.name -> LiteRtLmBackendPolicy.CPU_ONLY
            LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION.name -> LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION
            else -> null
        }
        if (policy == null ||
            !validAbsolutePath(modelPath) || !validAbsolutePath(imagePath) ||
            !validAbsolutePath(cachePath) ||
            !regularNonemptyFile(modelPath, Long.MAX_VALUE) ||
            !regularNonemptyFile(imagePath, MAX_IMAGE_BYTES) ||
            prompt.isBlank() || prompt.toByteArray(Charsets.UTF_8).size > MAX_PROMPT_UTF8_BYTES ||
            maxTokens !in 1..512
        ) {
            reply.writeInt(REJECT) // explicit REJECT, no native initialization
            return
        }

        // state.begin enforces the exact owner (from HELLO) and single (non-repeat) START.
        if (!state.begin(nonce)) {
            reply.writeInt(REJECT)
            return
        }
        executor.execute {
            runInference(nonce, modelPath, imagePath, cachePath, prompt, maxTokens, policy)
        }
        reply.writeInt(OK)
    }

    private fun handleStatus(data: Parcel, reply: Parcel) {
        val nonce = data.readString()
        val snapshot = if (nonce != null && data.dataAvail() == 0) state.snapshot(nonce) else null
        if (snapshot == null) {
            reply.writeInt(REJECT)
            reply.writeString(buildRejectJson())
        } else {
            reply.writeInt(OK)
            reply.writeString(buildStatusJson(snapshot))
        }
    }

    private fun handleCancel(data: Parcel, reply: Parcel) {
        val nonce = data.readString()
        val changed = nonce != null && data.dataAvail() == 0 && state.cancel(nonce)
        reply.writeInt(if (changed) OK else REJECT)
    }

    private fun handleRetire(data: Parcel, reply: Parcel) {
        val nonce = data.readString()
        if (nonce == null || data.dataAvail() != 0 || state.snapshot(nonce) == null) {
            reply.writeInt(REJECT)
            return
        }
        reply.writeInt(OK) // write OK before self-termination; client handles reply/death race
        retireExactProcess()
    }

    // ---- validation (Binder thread, before any state/native work) ----

    private fun validAbsolutePath(path: String): Boolean =
        path.isNotBlank() && path.startsWith("/") && '\u0000' !in path

    private fun regularNonemptyFile(path: String, maxBytes: Long): Boolean = try {
        val file = File(path)
        file.isFile && file.length() > 0 && file.length() <= maxBytes
    } catch (_: Throwable) {
        false
    }

    // ---- STATUS JSON (fresh object per call, serialized from an immutable snapshot copy) ----

    private fun generationOf(s: LiteRtLmServiceStateSnapshot): String = when {
        s.terminal != null -> s.terminal!!.name
        s.phase == LiteRtLmServicePhase.GENERATING -> "RUNNING"
        else -> "NONE"
    }

    private fun buildStatusJson(s: LiteRtLmServiceStateSnapshot): String {
        val obj = JSONObject()
        obj.put("nonce", s.ownerNonce)
        obj.put("phase", s.phase.name)
        obj.put("generation", generationOf(s))
        val arr = JSONArray()
        s.rawChunks.forEachIndexed { index, text ->
            val item = JSONObject()
            item.put("sequence", index)
            item.put("text", text) // exact raw chunk, no trimming/normalizing
            arr.put(item)
        }
        obj.put("snapshots", arr)
        obj.put("nativeAttempted", s.nativeAttempted)
        obj.put("nativeInitialized", s.nativeInitialized)
        if (s.errorCode == null) {
            obj.put("errorCode", JSONObject.NULL)
        } else {
            obj.put("errorCode", s.errorCode)
        }
        obj.put("callbackMode", "UNPROVEN")
        return obj.toString()
    }

    // Wrong nonce: safe empty object, no owner nonce / private paths / prompt.
    private fun buildRejectJson(): String {
        return "{}"
    }

    // ---- worker: native calls off Binder/main thread, never under the state monitor ----

    private fun runInference(
        nonce: String,
        modelPath: String,
        imagePath: String,
        cachePath: String,
        prompt: String,
        maxTokens: Int,
        policy: LiteRtLmBackendPolicy,
    ) {
        try {
            // Cache path is created/validated on the worker (caller-selected directory).
            val cacheDir = File(cachePath)
            if ((!cacheDir.isDirectory && !cacheDir.mkdirs()) || !cacheDir.isDirectory) {
                state.fail(nonce, "CacheDir")
                return
            }

            // Real ModelInfo/support validation, copied from LiteRtLmProductionEngineFactory.
            val supported = ModelInfo.from(modelPath).use { info ->
                val modalities = info.inputModalities()
                val text = info.supportedBackends(Modality.TEXT)
                val vision = info.supportedBackends(Modality.VISION)
                val support = LiteRtLmModelSupport(
                    modalities.text, modalities.vision,
                    BackendType.CPU in text, BackendType.CPU in vision, BackendType.GPU in vision,
                    info.maxVisionTokenBudget(), info.minRuntimeVersion(),
                )
                support.supports(policy)
            }
            if (!supported) {
                state.fail(nonce, "InvalidModel")
                return
            }

            // Native attempt gate, recorded BEFORE engine construction.
            if (!state.markNativeAttempted(nonce)) return // e.g. canceled before the attempt

            val eng = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = when (policy) {
                        LiteRtLmBackendPolicy.CPU_ONLY -> Backend.CPU()
                        LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION -> Backend.GPU()
                    },
                    maxNumTokens = 512,
                    maxNumImages = 1,
                    cacheDir = cacheDir.absolutePath,
                )
            )
            engine = eng // strong ref kept until exact process death
            eng.initialize()

            // Initialization recorded truthfully even if CANCEL arrived meanwhile; retains CANCELED.
            state.markNativeInitialized(nonce)
            // Never begin a generation for a canceled state.
            if (!state.beginGeneration(nonce)) return

            val conv = eng.createConversation(ConversationConfig(maxOutputToken = maxTokens))
            conversation = conv // strong ref kept until exact process death
            val message = Message.user(Contents.of(Content.ImageFile(imagePath), Content.Text(prompt)))
            val callback = object : MessageCallback {
                override fun onMessage(msg: Message) {
                    val text = extractText(msg) ?: return
                    state.append(nonce, text) // one raw chunk; no delta/cumulative inference
                }

                override fun onDone() {
                    state.complete(nonce)
                }

                override fun onError(error: Throwable) {
                    state.fail(nonce, safeFailCode(error))
                }
            }
            messageCallback = callback // strong ref for the async native callback
            conv.sendMessageAsync(message, callback)
        } catch (t: Throwable) {
            // Preserve initialization/worker failures as ERROR; no data/prompt/path logging.
            state.fail(nonce, safeFailCode(t))
        }
    }

    private fun extractText(message: Message): String? = try {
        val sb = StringBuilder()
        for (content in message.contents.contents) {
            if (content is Content.Text) sb.append(content.text)
        }
        if (sb.isEmpty()) null else sb.toString()
    } catch (_: Throwable) {
        null
    }

    // Safe class identifier; the state maps anything non-conforming to RuntimeFailure.
    private fun safeFailCode(t: Throwable): String = t.javaClass.simpleName
}
