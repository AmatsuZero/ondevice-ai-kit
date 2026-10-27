package io.ondeviceai.probe.litertlm

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    companion object {
        private val nativeProbeStarted = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "LiteRT-LM probe installed. No inference started." }
        setContentView(status)
        if (!intent.getBooleanExtra("run-probe", false)) return

        Thread({
            val report = try {
                val model = LiteRtLmProbe.privateFile(filesDir, intent.getStringExtra("model") ?: "models/model.litertlm")
                val image = LiteRtLmProbe.privateFile(filesDir, intent.getStringExtra("image") ?: "fixture.png")
                when {
                    !model.exists() || !image.isFile -> JSONObject()
                        .put("status", "unverified-missing-app-private-assets")
                    !nativeProbeStarted.compareAndSet(false, true) -> JSONObject()
                        .put("status", "unavailable-restart-process-before-retry")
                    else -> reportJson(LiteRtLmProbe.run(
                        model.absolutePath,
                        File(cacheDir, "litertlm-probe").apply { mkdirs() },
                        image.absolutePath,
                        "Describe the image in several sentences.",
                    ))
                }
            } catch (failure: Exception) {
                JSONObject().put("status", "failed").put("errorClass", failure::class.java.simpleName)
            }
            report.put("schemaVersion", 2)
            File(filesDir, "litertlm-probe-report.json").writeText(report.toString())
            runOnUiThread { status.text = report.optString("status") }
        }, "litertlm-probe-worker").start()
    }

    private fun reportJson(value: LiteRtLmProbe.Report): JSONObject = JSONObject()
        .put("status", LiteRtLmProbe.candidateStatus(value))
        .put("callbackCount", value.callbackCount)
        .put("callbackMode", value.callbackMode)
        .put("callbackTextLengths", JSONArray(value.callbackTextLengths))
        .put("terminalCallbacks", value.terminalCallbacks)
        .put("sawDeadConversation", value.sawDeadConversation)
        .put("drainElapsedMillis", value.drainElapsedMillis)
        .put("drainProven", value.drain.isProven)
        .put("drainEvidence", "unsupported-conversation-lifetime-flag")
        .put("errorClass", value.errorClass ?: JSONObject.NULL)
}
