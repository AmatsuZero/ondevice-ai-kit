package io.ondeviceai.demo
import androidx.compose.ui.window.ComposeUIViewController
import io.ondeviceai.binding.DemoActions
fun mainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions)=ComposeUIViewController { FaceScreen(model,actions,identity,identityActions) }

fun speechMainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions,speech:SpeechViewModel,speechActions:SpeechDemoActions)=
    ComposeUIViewController { FaceScreen(model,actions,identity,identityActions,speech,speechActions) }

fun videoMainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions,speech:SpeechViewModel,speechActions:SpeechDemoActions,
    video:VideoViewModel,videoActions:VideoDemoActions)=
    ComposeUIViewController { FaceScreen(model,actions,identity,identityActions,speech,speechActions,video,videoActions) }
