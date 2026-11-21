package io.ondeviceai.litertlm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BackendType
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The only production boundary that imports vendor runtime types. */
internal object LiteRtLmProductionEngineFactory : LiteRtLmEngineFactory {
    override suspend fun open(modelPath: File, config: LiteRtLmEngineConfig): LiteRtLmSession =
        withContext(Dispatchers.IO) {
            ModelInfo.from(modelPath.absolutePath).use { info ->
                val modalities = info.inputModalities()
                val text = info.supportedBackends(Modality.TEXT)
                val vision = info.supportedBackends(Modality.VISION)
                val support = LiteRtLmModelSupport(modalities.text, modalities.vision,
                    BackendType.CPU in text, BackendType.CPU in vision, BackendType.GPU in vision,
                    info.maxVisionTokenBudget(), info.minRuntimeVersion())
                if (!support.supports(config.backendPolicy)) throw LiteRtLmError.InvalidModel()
            }
            val engine = Engine(EngineConfig(
                modelPath = modelPath.absolutePath,
                backend = Backend.CPU(), visionBackend = when (config.backendPolicy) {
                    LiteRtLmBackendPolicy.CPU_ONLY -> Backend.CPU()
                    LiteRtLmBackendPolicy.CPU_TEXT_GPU_VISION -> Backend.GPU()
                },
                maxNumTokens = config.maxNumTokens, maxNumImages = config.maxNumImages,
                cacheDir = config.cacheDir.absolutePath,
            ))
            try { engine.initialize() }
            catch (failure: Throwable) {
                runCatching { engine.close() }
                throw failure
            }
            LiteRtLmSynchronousSession(object : LiteRtLmSynchronousEngine {
                private var conversation: Conversation? = null

                override fun generate(image: File, prompt: String, maxOutputTokens: Int): String {
                    check(conversation == null)
                    val current = engine.createConversation(ConversationConfig(maxOutputToken = maxOutputTokens))
                    conversation = current
                    val response = current.sendMessage(
                        Contents.of(Content.ImageFile(image.absolutePath), Content.Text(prompt)),
                        maxOutputToken = maxOutputTokens,
                    )
                    return response.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                }

                override fun close() {
                    conversation?.close()
                    engine.close()
                }
            })
        }
}
