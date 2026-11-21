package io.ondeviceai.systemasr

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.*
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.speech.SpeechBackendUnavailableException

/** Explicit resource management. Neither construction nor check() requests recording or download. */
class AndroidAsrResources(context: Context) {
    private val appContext = context.applicationContext
    val canObserveDownload: Boolean get() = Build.VERSION.SDK_INT >= 34

    suspend fun check(locale: String): AsrAssetStatus = session(download = false).check(locale)

    /** May use the network and present system approval UI. Call only after explicit user action.
     * Cancelling this call detaches monitoring; it does not cancel the system's download.
     */
    suspend fun download(locale: String, onProgress: (Int) -> Unit = {}): AsrAssetStatus =
        session(download = true).download(locale, onProgress)

    private fun session(download: Boolean) = AsrResourceSession(
        factory = {
            if (Build.VERSION.SDK_INT < 33)
                throw SpeechBackendUnavailableException(UnavailableReason.REQUIRES_OS_VERSION)
            if (download && Build.VERSION.SDK_INT < 34)
                throw SpeechBackendUnavailableException(UnavailableReason.REQUIRES_OS_VERSION)
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext))
                throw SpeechBackendUnavailableException(UnavailableReason.UNSUPPORTED_DEVICE)
            Engine(appContext)
        },
        timeoutMillis = if (download) 600_000 else 30_000,
    )

    @android.annotation.TargetApi(33)
    private class Engine(context: Context) : AsrResourceEngine {
        private val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        private val executor = context.mainExecutor
        private fun intent(locale: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

        override fun check(locale: String, listener: AsrResourceListener) {
            recognizer.checkRecognitionSupport(intent(locale), executor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) = listener.onSupport(AsrLanguageSupport(
                    support.installedOnDeviceLanguages, support.pendingOnDeviceLanguages, support.supportedOnDeviceLanguages))
                override fun onError(error: Int) = listener.onError(error)
            })
        }

        @android.annotation.TargetApi(34)
        override fun download(locale: String, listener: AsrResourceListener) {
            recognizer.triggerModelDownload(intent(locale), executor, object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) = listener.onProgress(completedPercent)
                override fun onSuccess() = listener.onInstalled()
                override fun onScheduled() = listener.onScheduled()
                override fun onError(error: Int) = listener.onError(error)
            })
        }

        override fun destroy() = recognizer.destroy()
    }
}
