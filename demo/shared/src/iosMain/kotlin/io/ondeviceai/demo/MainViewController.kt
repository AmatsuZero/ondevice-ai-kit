package io.ondeviceai.demo
import androidx.compose.ui.window.ComposeUIViewController
import io.ondeviceai.binding.DemoActions
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/** Default app entry. Legacy exported factories below remain source compatible. */
@OptIn(ExperimentalForeignApi::class)
fun unifiedDemoViewController(bindings: UnifiedDemoBindings, facePreview: UIViewController,
    voiceEffects: UIViewController?): UIViewController = ComposeUIViewController {
    val model = remember { UnifiedDemoModel(onLeave = bindings::leaveCapability, isIdle = bindings::isIdle) }
    UnifiedDemoScreen(model, bindings,
        facePreview = { UIKitViewController(factory = { facePreview }, modifier = Modifier.fillMaxWidth().height(220.dp)) },
        voiceContent = voiceEffects?.let { controller ->
            { UIKitViewController(factory = { controller }, modifier = Modifier.fillMaxSize()) }
        })
}
fun mainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions)=ComposeUIViewController { FaceScreen(model,actions,identity,identityActions) }

fun speechMainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions,speech:SpeechViewModel,speechActions:SpeechDemoActions)=
    ComposeUIViewController { FaceScreen(model,actions,identity,identityActions,speech,speechActions) }

fun videoMainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions,speech:SpeechViewModel,speechActions:SpeechDemoActions,
    video:VideoViewModel,videoActions:VideoDemoActions)=
    ComposeUIViewController { FaceScreen(model,actions,identity,identityActions,speech,speechActions,video,videoActions) }

/** Explicit opt-in; legacy entry points and their export labels stay unchanged. */
fun multimodalMainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions,speech:SpeechViewModel,speechActions:SpeechDemoActions,
    video:VideoViewModel,videoActions:VideoDemoActions,multimodal:MultimodalViewModel)=
    ComposeUIViewController { FaceScreen(model,actions,identity,identityActions,speech,speechActions,video,videoActions,multimodal) }

/** Host action opt-in has its own entry, preserving every earlier exported signature. */
fun multimodalHostMainViewController(model:FaceViewModel,actions:DemoActions,identity:IdentityViewModel,
    identityActions:IdentityDemoActions,speech:SpeechViewModel,speechActions:SpeechDemoActions,
    video:VideoViewModel,videoActions:VideoDemoActions,multimodal:MultimodalViewModel,multimodalActions:MultimodalDemoActions)=
    ComposeUIViewController { FaceScreen(model,actions,identity,identityActions,speech,speechActions,video,videoActions,multimodal,multimodalActions) }
