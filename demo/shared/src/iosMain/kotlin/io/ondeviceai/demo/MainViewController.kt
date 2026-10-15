package io.ondeviceai.demo
import androidx.compose.ui.window.ComposeUIViewController
import io.ondeviceai.binding.DemoActions
fun mainViewController(model:FaceViewModel,actions:DemoActions)=ComposeUIViewController { FaceScreen(model,actions) }
