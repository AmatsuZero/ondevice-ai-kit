package io.ondeviceai.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Dedicated compatibility entry; ordinary navigation embeds the same Host. */
class VoiceEffectsActivity : ComponentActivity() {
    private lateinit var host: VoiceEffectsHost
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = VoiceEffectsHost(this)
        setContent { UnifiedDemoTheme { VoiceEffectsScreen(host.model, host) } }
    }
    override fun onStart() { super.onStart(); host.foreground() }
    override fun onStop() { host.background(); super.onStop() }
    override fun onDestroy() { host.close(); super.onDestroy() }
}
