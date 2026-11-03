# Android 图像理解 Demo（LiteRT-LM）

显式构建 `-PenableLiteRtLm=true :demo:androidApp:assembleDebug` 后，普通 Demo 会显示图像理解页面，使用真实 `litertlm.android` 后端。默认构建不引入 LiteRT-LM。先支持单图+文本和最终结果；不显示视频选择，不假装有视频模型支持。生成任务取消后会丢弃结果、等待同步发送返回，再释放输入及模型资源，可能需要等待剩余生成时间。

## 本地模型包

模型由使用者提供并确认使用权限，App 不下载权重、不替使用者接受许可。把以下目录放入 debug App 的 `files/litertlm/import/`：

```text
import/
  manifest.json
  demo-model.json
  files/
    model.litertlm
    model-card.txt
```

`manifest.json` 是 SDK 原生 `ModelManifest` JSON，必须包含实际文件字节数和 SHA-256、来源 revision、许可及归属；`files/` 只能包含该 manifest 列出的文件。要求 `backendId=litertlm.android`、`capability=multimodal.image`、`formatVersion=litertlm-model-v1`、`processing.inputSignature=image-file+text-prompt-v1`，backend version 范围覆盖 `0.18.1`，OS/架构匹配设备。参见 [ModelManifest](../../../../android/model-store/src/main/kotlin/io/ondeviceai/modelstore/ModelManifest.kt)。

`demo-model.json`：

```json
{
  "schemaVersion": 1,
  "modelPath": "model.litertlm",
  "metadataPaths": ["model-card.txt"]
}
```

以上文件名只是格式示例，没有附带可用权重。`.litertlm` 本身封装运行时需要的 tokenizer 等数据，`metadataPaths` 用于 manifest 中的模型卡/来源等附属材料，不要求捏造外置 tokenizer 文件。实际模型必须支持 vision、text、CPU 两种模态后端，且声明的最低 runtime 不高于固定的 `0.18.1`。真正生成时由 `ModelInfo` 和 runtime 再检查兼容性。

首次刷新后端会校验并安装本地包到 App 的 `noBackupFilesDir/litertlm/store`，随后沿用 SDK ModelStore 的哈希与 lease 检查；已有相同 revision 不可被不同文件覆盖。缺文件显示 `MISSING_ASSETS`；安装后可选择图片、填写 prompt、点击“生成”，页面显示实际后端、模型 revision 和输出。

此入口用于原型的本地模型接入，目前没有模型目录选择 UI。没有真实模型时，编译和 host fake 测试只能证明接线，不能证明设备生成、速度或模型效果。
