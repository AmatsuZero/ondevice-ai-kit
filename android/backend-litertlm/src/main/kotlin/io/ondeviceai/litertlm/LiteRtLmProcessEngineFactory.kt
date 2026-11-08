package io.ondeviceai.litertlm

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * Production internal process [LiteRtLmEngineFactory].
 *
 * [open] only connects to the dedicated LiteRT-LM process (bind + original-binder death link, no
 * HELLO/START); it returns a real [LiteRtLmProcessSession] adapter and defers the remote reserve
 * (HELLO) and START to [LiteRtLmProcessSession.generateImage]. A connect-time cancellation is
 * reported with the trusted factory-only [LiteRtLmPreOwnershipCancellation] marker after the
 * underlying [LiteRtLmProcessClient] has already completed its audited local cleanup off-main; the
 * bind/claim rejection markers are preserved by that client.
 *
 * The current explicit [LiteRtLmCallbackMode] is an INTERNAL test/proven-input configuration and is
 * NOT proof that an installed binary is proven. There is no public asynchronous backend-creation API
 * or default switch in this factory.
 */
internal class LiteRtLmProcessEngineFactory(
    context: Context,
    private val callbackMode: LiteRtLmCallbackMode,
    coordinator: LiteRtLmProcessCoordinator = LiteRtLmProcessCoordinator.shared,
) : LiteRtLmEngineFactory {
    private val appContext: Context = context.applicationContext ?: context
    private val client = LiteRtLmProcessClient(appContext, coordinator)

    override suspend fun open(modelPath: File, config: LiteRtLmEngineConfig): LiteRtLmSession {
        val nonce = UUID.randomUUID().toString()
        val connection = try {
            client.connect(nonce)
        } catch (cancellation: CancellationException) {
            // Connect was cancelled before any HELLO/native owner; the client already ran its
            // audited local cleanup off-main. Re-raise the trusted factory-only marker.
            throw LiteRtLmPreOwnershipCancellation(cancellation)
        }
        return LiteRtLmProcessSession(
            connection = connection,
            callbackMode = callbackMode,
            modelPath = modelPath,
            cacheDir = config.cacheDir,
            backendPolicy = config.backendPolicy,
        )
    }
}
