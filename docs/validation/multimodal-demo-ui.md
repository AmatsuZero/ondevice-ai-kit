# 单图 Demo shared Screen/UI 验证

更新：2026-10-22。设计/计划 `f88b43f`，Task1实现 `98a3891`，Task2验证/交接与本记录同次提交。前置state/bridge包不重做。

## 交付与边界

`MultimodalScreen`直接消费既有`MultimodalViewModel`：backend列表/可用性/minOS、Default/Explicit严格选择、输入displayName、原始prompt、token budget、生成/取消、phase、累计文本、实际backend/model/modelRevision/runtimeRevision与typed failure（含platform domain/code）。不显示opaque reference，不读媒体，不拥有native lease或模型。

`FaceScreen`新增可空multimodal参数，默认null，原App调用均不变；提供model时才出现第二行opt-in入口，避免挤压原Face/Identity/Speech/Video行。离页/组合dispose调用leavePage；close由host持有者负责。独立`multimodalMainViewController`接收现有Face/Identity/Speech/Video及multimodal model，不改变旧三个导出入口。没有平台host/picker或MLXVLM接线，默认真实UnavailableMultimodalBridge，不制造成功。

设计/计划：[spec](../superpowers/specs/2026-10-22-multimodal-demo-ui-design.md)、[plan](../superpowers/plans/2026-10-22-multimodal-demo-ui.md)。完整日志、ledger、author final-review、Swift编译probe保留于`.superpowers/sdd/2026-10-22-multimodal-demo-ui/`。

## 新鲜证据

| 门禁 | 结果与日志 |
| --- | --- |
| RED→GREEN | 实际Android测试APK编译因缺少FaceScreen multimodal参数失败；实现后APK/testAPK离线构建成功，`task-1-red.log`、`task-1-green-build.log` |
| 实际Android UI | TECNO KM9/API35，`task-1-final-device.log`的status=passed，全部具名场景均通过，见下文；不能只看INSTRUMENTATION_CODE=-1 |
| shared simulator | 60/60实际执行：Multimodal12、Speech15、Video8、Face5、Identity20，0失败/跳过；`task-1-shared.log`。后续task-done复跑同源码主要缓存复用，不称新执行 |
| iOS入口ABI | 实际Swift typecheck旧main/speech/video与新组合multimodal入口通过，target arm64-apple-ios15.0-simulator；`task-1-entry-combined-{red,green}.log`。初版新入口不接受Speech/Video参数，新增组合调用先失败再修复 |
| 更新后Demo ABI | native MultimodalBridgeTests重链/执行10/10，`task-2-demo-abi.log`，不等于UI验收 |
| 旧Swift Demo接线 | 不碰保护工程，生成`artifacts/verification/multimodal-ui-20261022/SpeechDemo.xcodeproj`，包含原Speech/Video host的simulator unsigned build成功，`task-2-ios-build.log` |
| Android lint | 0 error、9条既有warning，`task-2-android-lint.log`；不是零warning声明 |
| 工具 | 101/101，`task-2-tools.log`，已有Ruby3.4环境，无下载 |

Android instrumentation用实际shared Compose页面和真实VM，测试代码只在test APK；正常App不包含fixture或新自动入口。具名结果为：默认无入口；真实默认bridge查询/生成明确BACKEND_UNAVAILABLE；实际文本控件保留prompt换行/空白与Explicit原值、0/Int溢出预算禁用；累计update/取消保持CANCELLING直到终态；重新生成最终输出和实际backend/model/runtime身份；PLATFORM/domain/code；离页取消/返回不自动生成；活动Speech/Video切新页时取消旧任务（受控silent bridge，不播放/录音/读媒体）。

早期失败完整保留：文字子节点并非clickable父按钮；VM终态后的Compose重组/AX旧节点；滚动尚未稳定时取子节点。根据失败snapshot修正测试夹具：等待真实状态、visible/enabled控件，等待AX idle，API33+清缓存。没有为测试改VM协调器或注入生产成功。最终在扩展旧页导航检查后完整运行通过。

复跑UI：

```sh
rtk proxy python3 scripts/gradle-local.py :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest -PodaiTestRunner=io.ondeviceai.demo.MultimodalScreenInstrumentation --offline --console=plain
rtk proxy adb -s 15365375C9007986 install -r demo/androidApp/build/outputs/apk/debug/androidApp-debug.apk
rtk proxy adb -s 15365375C9007986 install -r demo/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
rtk proxy adb -s 15365375C9007986 shell am instrument -w io.ondeviceai.demo.test/io.ondeviceai.demo.MultimodalScreenInstrumentation
```

## 审查、未验与下一包

每Task范围作者自审、最终单独作者审查已执行，无本包阻断项；按用户要求不派子代理，独立review仍pending，无APPROVE/发布结论。

未验：iOS实际UI交互、双端picker/host与opaque输入生命周期、KMP→真实MLXVLM真机闭环、自然图片质量、Metal/GPU/energy、Android真实推理、最低系统设备与独立审查。现代simulator编译/物理Android fixture UI均不等于最低系统或推理通过。旧Video UI初始化超时、P6实录、Android ASR MISSING_ASSETS等缺口保留。roadmap继续5/12阶段完成，P9未关闭。

下一独立包选择：**双端单图host/picker与opaque input/lease生命周期**，默认unavailable，Apple固定模型/ModelStore接线与KMP真实设备闭环再分包。真实模型host之前另读apple-mlxvlm-backend验证和backend/benchmark设计/计划。短视频、Android真后端与自然质量/资源不扩入。

无push/merge/发布、模型/依赖下载、录音、私人媒体或签名资产变更。两个保护文件状态/sha256精确不变且从未暂存：pbxproj `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`；face-identity.md `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。scratch/artifacts/日志均保留。
