package io.ondeviceai.example.litertlm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import io.ondeviceai.litertlm.LiteRtLm
import io.ondeviceai.litertlm.LiteRtLmModel
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Opt-in availability consumer. It never downloads, opens an engine, or starts generation. */
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { text = "Checking LiteRT-LM availability…" }
        setContentView(text)
        val architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val store = ModelStore(File(filesDir, "litertlm/store"), ModelCompatibility(
            "litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), architecture))
        val model = LiteRtLmModel(
            ModelRef("authorized-model", "r1"),
            "model.bin",
            setOf("tokenizer.json"),
        )
        val environment = LiteRtLmRuntimeEnvironment(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet())
        scope.launch {
            text.text = LiteRtLmAvailabilityText.format(LiteRtLm.availability(store, model, environment))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
