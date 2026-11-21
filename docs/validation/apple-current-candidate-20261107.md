# 当前 Apple 组合候选 — 2026-11-07

后续[当前候选真机链路](apple-current-runtime-20261107.md)已在保持原宿主代码的条件下完成
hosted非UI图片/视频/OpenCV及关闭释放验证，并恢复普通App。下文“未运行真机”描述本构建轮，
不再代表当前原生链路状态；普通交互与完整验收仍开放。

与[当前 Android 候选](android-current-candidate-20261107.md)使用同一份
`636a6eb8854db5dff74244c42c3ce0e82bd2be9f` committed 源码归档，完成
MLX＋OpenCV 组合设备 App 和 MLX arm64 模拟器 App 的构建，以及当前多模态桥接测试 **82/82**。
关闭当前 Apple 组合构建和变更桥接测试子项；当前新包真机运行、完整 F12、P4/P11 和发布验收继续开放。

## 源码与复用边界

重新提取并核对 1046 份归档文件、SHA 与执行位；归档 SHA-256 为
`5d147a918ccfdd99c193400de401eb0abbebe3d3e132f3f33ed2eaddbc5bb7a7`。
与旧 `e0438c0` 完整清单比较得到 137 份新增或变化文件，所有差异保留在 `inputs.json`。
Apple、MLX、OpenCV、KMP/shared 相关 211 份文件及相关构建/版本输入保持一致；
iOS Demo 的模型目录导入、失败重试、已安装模型复用和组合 generator 有变化，因此重新编译 App 并运行变更桥接测试。

两个 shared framework 复用[旧 Apple 候选](apple-committed-candidate.md)的已冻结产品，
复制前后完整文件树与旧 summary 匹配。它们仍记为 **e0438c0 构建、未重建**，
不因当前 App 的提交号而改写原始构建身份；其中 Skiko 仍为生产 `0.144.6`，不是 F02 `.6`。
现有 static OpenCV XCFramework 同样按完整文件树核验后复制，没有重新下载或编译 native 输入。

12 个固定 SwiftPM 依赖和 2 个 C 子模块从已有完整本地对象创建自包含 clone，
6878 份原始上游文件逐项核对 Git blob、SHA 和执行位，不使用 F09 overlay，未 fetch 或升级依赖。
新 workspace 的 revision、空 external artifacts/prebuilts、两工程 resolved 和生产 lock 均已核对。
九份受保护 dirty/untracked 文件保持原摘要，没有复制受保护的签名工程。

## 实际构建与产物

| 变体 | 结果与边界 |
| --- | --- |
| `io.ondeviceai.combined.demo` / generic iOS | 79.542 秒成功；speech/video/multimodal/MLX/OpenCV 同时启用，arm64、部署声明 18.0、完整 sdkCommit 匹配 |
| `io.ondeviceai.multimodalmlxdemo` / generic simulator | 128.919 秒成功；MLX arm64，部署声明 18.0、完整 sdkCommit 匹配；不含 OpenCV |
| 当前多模态 bridge / 既有 iOS simulator | 13.012 秒完成编译与测试；82 项执行、0失败/0跳过，逐用例日志与 xcresult 统计一致 |

OpenCV 现有制品只有 arm64 device/x86_64 simulator，仍不支持 arm64 simulator；
模拟器变体的能力范围单列，不把它当作组合 App 的模拟器验收。
两个 App 的六份当前模型导入相关 Swift 源码均出现在实际 Swift file lists，
设备 App 另包含 Identity host/session/bridge；合计固定 645 个不同路径的 Swift 编译输入。

设备 App 的实际 debug dylib 同时包含 `MLXVLMBackend`、MLX factory/preparation、
`importMultimodalModels`、OpenCV native runtime 和 Identity host/session 符号。
两个 App 均保留 `multimodal-import-model-folder` 标识符；这是编译证据，没有操作 UI。
OpenCV 为静态链接，未动态 embed `opencv2.framework`。

| 实际产物 | SHA-256 |
| --- | --- |
| 设备 debug dylib | `566d52831e574f1775877d17665e7c1385a24d73b3c48bf67be08ecf6130669d` |
| 模拟器 debug dylib | `1c80d584f43fb17a04eaceb529f65973cb23ed2f91adea73824cc07b0d612f97` |
| 两平台模型描述，5575 B | `c074e693cc0fb9cfb19d6e7a393ffb113108e88d1489a387e8cb06d309602d54` |
| 设备 Metal 库，2399192 B | `9661f090f215179bb3ba647a4a1cd347f8d8bebed8cbd7e2fdaf6f71fa7ea886` |
| 模拟器 Metal 库，2405144 B | `6edb92c540ae0577e7145d1c2a6a75666a9f889d356dedbbe7a74a1aedd71e85` |

设备 App 中 OpenCV/YuNet/SFace 三份 notice 与 committed 原件逐字节匹配；
不能由此推导所有依赖的许可适用或完整生产交付已通过。
构建保留既有上游和 AppIntents 等警告，设备/模拟器分别记录 32/33 条去重诊断；没有为警告改源码。

构建均指定 `CODE_SIGNING_ALLOWED=NO`。设备包实际 unsigned；模拟器主程序带
`adhoc,linker-signed` 标记、无 TeamIdentifier/资源封印。两者均无 provisioning 和 `_CodeSignature` 目录，
未调用开发者身份签名或操作签名资产。父检查最初错误要求模拟器也返回“not signed at all”，
在产物检查阶段失败；原检查源码、原错误和两平台 `codesign --display` 输出保留。
更正为按平台检查后读取同一产物通过，未重建 App，也不将该失败计作产品构建失败。

82 项现有桥接测试包含本次变化的 folder picker、preparation 失败重试、
已安装模型目录移除后的重开和 manifest 不匹配拒绝/lease 释放。
测试使用注入后端和合成临时输入，未执行 MLX/OpenCV 推理、麦克风、相机或物理触摸。
没有重复运行未变化的 Apple host、KMP/shared 或 Android 599 项测试。

## 助手与剩余门槛

cbc `hy3` 的四文件限定检查清单在 50.618 秒自然成功，0 工具事件，无任务超时。
主代理更正了动态 framework、unsigned 真机启动、OpenCV 切片和提交号字段等建议；
没有采纳产品缺陷或修改生产代码。原文及逐项处置保留在
`.omx/artifacts/cbc-apple-combined-checklist-20261107/`，不计完整分支或发布审查批准。
Qoder403、原生429和其他已停止供应商线路未重试，既有三个待答复项不重复询问。

本轮无手机操作、模型推理、新媒体采集、下载或上传，普通手机 App/模型没有被本轮操作。
新组合 App 的真实设备/普通交互和其余能力验收、自然质量、长期资源/GPU/能耗、
授权校准集、最低系统、完整复审/许可与生产交付仍缺。P11 没有新增样本，仍以现有 manifest 为准；
基础 **10/12、P4/P11 open、release not-ready** 保持。

ignored 本地证据：`artifacts/verification/apple-current-candidate-20261107/`。
`summary.json` 固定源码和复用身份、两 App/资源/编译输入、6 个终态阶段、测试统计、
首次检查失败以及助手原文/父处置摘要。只读复核，不重新构建、运行模型或操作设备：

```sh
rtk proxy python3 artifacts/verification/apple-current-candidate-20261107/freeze.py --verify
```
