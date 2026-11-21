package io.ondeviceai.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun voiceEffectsMainViewController(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions): UIViewController =
    ComposeUIViewController { MaterialTheme { VoiceEffectsScreen(model, actions) } }

fun voiceEffectsEmbeddedViewController(model: VoiceEffectsViewModel, actions: VoiceEffectsDemoActions): UIViewController =
    ComposeUIViewController { UnifiedDemoTheme { VoiceEffectsScreen(model, actions, showBack = false) } }
