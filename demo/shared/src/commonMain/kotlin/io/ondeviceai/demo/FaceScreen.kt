package io.ondeviceai.demo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ondeviceai.binding.DemoActions
@Composable fun FaceScreen(model:FaceViewModel,actions:DemoActions) {
 val state=model.state;val availability=model.bridge.availability()
 MaterialTheme {
  Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text("OnDevice AI Kit",style=MaterialTheme.typography.headlineSmall)
   Text(availability.backendId+if(availability.available)" · offline" else " · "+availability.reason)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Button(onClick={actions.selectPhoto()},enabled=state.canSelectInput){Text("选择图片")}
    Button(onClick={actions.startCamera()}){Text("相机")}
    TextButton(onClick={actions.stopCamera();model.cancel()}){Text("停止")}
   }
   if(state.showProgress) {LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={actions.stopCamera();model.cancel()}){Text("取消")}}
   state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
   if(state.streaming || state.result!=null) {
    Text(state.result?.let {"人脸 "+it.faces.size+" · "+it.elapsedMs.toInt()+" ms · "+it.width+"×"+it.height}?:"等待首帧结果")
   }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick={actions.runBenchmark()},enabled=state.canSelectInput){Text("Benchmark")}
    state.result?.benchmarkJson?.let {json-> TextButton(onClick={actions.exportReport(json)}){Text("导出报告")}}
   }
  }
 }
}
