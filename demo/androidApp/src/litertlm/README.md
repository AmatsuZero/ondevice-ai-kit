# Android 图像理解 Demo（LiteRT-LM）

显式构建 `-PenableLiteRtLm=true :demo:androidApp:assembleDebug` 后，普通 Demo 会显示图像理解页面，使用真实 `litertlm.android` 后端。默认构建不引入 LiteRT-LM。该 Demo 通过显式公开的 `LiteRtLm.createProcessBackend` 接入「进程化」后端：生成在独立的 LiteRT-LM 进程中完成（固定 0.18.1 文本回调路径，官方 MessageCallback 仅回传最新分块，按 DELTA 拼接）。先支持单图+文本和最终结果；不显示视频选择，不假装有视频模型支持。生成任务取消后会抑制后续输出；清理会回收自有进程，并在释放输入/模型租约之前等待最初注册的 Binder death，可能需要等待剩余生成时间；取消或回收（retire）并不保证立即停止或立即回收 GPU delegate——仍在进行的生成可能继续跑完。本段只描述接线与取消/回收语义，不代表立即完成或资源（含 GPU delegate）即时释放已被验证。

## 本地模型包

模型由使用者提供并确认使用权限，App 不下载权重、不替使用者接受许可。准备以下内容：

```text
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

以上文件名只是格式示例，没有附带可用权重。`.litertlm` 本身封装运行时需要的 tokenizer 等数据，`metadataPaths` 用于 manifest 中的模型卡/来源等附属材料，不要求捏造外置 tokenizer 文件。缺省 `backendPolicy` 为 `CPU_ONLY`，要求模型支持 CPU 文本与 CPU 视觉。若已确认模型和设备支持 GPU 视觉，可在 `demo-model.json` 显式加入 `"backendPolicy": "CPU_TEXT_GPU_VISION"`，由 CPU 运行文本、GPU 运行视觉；不自动回退或自动切换。未知值会拒绝导入。实际模型必须同时支持 vision、text 及所选后端，且最低 runtime 不高于固定的 `0.18.1`。`ModelInfo` 的视觉 token budget 为 `-1` 表示未定义；生成时仍由真实模型元数据和 runtime 检查兼容性。安装后的 AVAILABLE 表示资产与设备基本条件满足，不能证明 GPU delegate 能初始化。当前引擎上下文上限为输入＋输出合计 512 tokens，适用于单图短提示；输出预算上限仍是 512，过长请求可能被 runtime 拒绝。

从上述文件所在目录打包，确保 `manifest.json` 是 ZIP 中的第一个 entry，ZIP 不包含额外顶层目录：

```sh
rtk proxy zip -r model-bundle.zip manifest.json demo-model.json files
```

在图像理解页点击“导入本地模型包（ZIP）”，通过系统选择器选取该 ZIP。App 校验文件集合、字节数和 SHA-256，再由 ModelStore 安装到 `noBackupFilesDir/litertlm/store`；已有相同 revision 不可被不同文件覆盖。导入失败保留原模型入口，安装成功后刷新后端。模型包也可按相同目录结构预置到 debug App 的 `files/litertlm/import/`。

缺文件显示 `MISSING_ASSETS`；安装后可选择图片、填写 prompt、点击“生成”，页面显示实际后端、模型 revision 和输出。没有真实模型时，编译和 host fake 测试只能证明接线，不能证明设备生成、速度或模型效果。
