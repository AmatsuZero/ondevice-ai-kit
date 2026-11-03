# Android LiteRT-LM 同步图像生成接入

日期：2026-11-03。用户调整为功能优先，长期资源与罕见竞态后置。本轮承接 [实施增量](../superpowers/plans/2026-11-03-android-litertlm-synchronous.md)，补齐真实 runtime 调用和 Demo 接线；**没有模型权重，真实设备生成尚未验证**。

## 实现

- 公开 `LiteRtLm.createBackend(store, model, cacheRoot, environment)`；构造与 availability 不初始化 native engine。
- 固定 `litertlm-android:0.18.1`，每请求独立 engine/conversation；检查模型 text/vision、CPU 后端及已声明的最低 runtime，使用真实 `ImageFile + Text` 和请求 token budget。
- 使用同步 `Conversation.sendMessage`，先输出最终文本。正常返回才证明本轮 sender 结束；任何发送异常都不能据此证明 drain，仍 poison 并保留资源。旧异步 probe 的 `UNPROVEN` 路径不被改写为通过。
- 取消先丢弃结果，等待阻塞 sender 自然返回后关闭 conversation→engine、清理私有 cache 和释放 lease；首版没有快速 native cancellation 或 streaming，可能等待剩余生成时间。共享 UI 在等待期间保持取消中，不提前重入。
- Android Demo 显式 `-PenableLiteRtLm=true` 接上真实 image bridge，并自动显示图像理解入口；默认构建不引入该 runtime。当前不支持视频，opt-in 包隐藏视频选择。
- 本地导入包的 manifest、descriptor 与 files 经 ModelStore 安装、哈希校验后才能生成。没有自动下载、许可接受或 fixture 输出。模型包仍通过 app-private 目录预置，格式见 [Demo 说明](../../demo/androidApp/src/litertlm/README.md)；模型目录选择 UI 尚未实现。

同日下一切片已补 **ZIP 模型包导入 UI**：共享图像页显示可选“导入本地模型包”按钮，Android 系统 DocumentsUI 选择 ZIP 后，先在独立 staging 中验证文件集合/大小/SHA-256，再安装和切换模型，最后刷新后端。失败保留旧导入入口；导入期间禁用生成。默认包与 iOS host 不显示该可选入口。上段 app-private 预置是上一检查点，当前格式与使用方法以 [Demo 说明](../../demo/androidApp/src/litertlm/README.md)为准。

## 当前验证

所有本轮原始日志与冻结 APK 位于 ignored `artifacts/verification/android-litertlm-sync-20261103/`。

| 范围 | 结果 | 证据 |
| --- | --- | --- |
| 同步 session TDD | 缺类型/模式编译 RED → 3/3 GREEN | `red.log`、`session-green.log` |
| 公开构造入口 TDD | 缺 `createBackend` RED → GREEN | `public-red.log`、`backend-green2.log` |
| 后端完整 JVM、真实 AAR 编译/打包、lint | 29/29，build exit 0；lint 无 error，版本 warning 保留 | `backend-green2.log` |
| Demo 本地包安装/生成路由与缺资产 | 缺 loader RED → GREEN；合成文件只进入 host fake | `demo-red2.log`、`demo-optin-green3.log` |
| Opt-in Demo JVM、APK/test APK、lint | 40/40，build exit 0；lint warning 保留 | `demo-optin-green3.log`、`optin-test-results/` |
| ZIP 导入 UI 与模型切换增量 | 解包器 missing-type RED → GREEN；opt-in **42/42**、APK/test APK 与 lint 通过 | `bundle-red.log`、`import-ui-final.log`、`import-ui-optin-test-results/` |
| 导入 UI 后默认包与共享 iOS 源码 | 默认 **38/38**、APK/lint 与 `compileKotlinIosArm64` 通过 | `import-ui-default-ios-compile.log` |
| 默认 Demo JVM、APK、lint | 38/38，build exit 0；未加入 vendor runtime | `demo-default.log`、`default-test-results/` |
| 依赖隔离 | 默认图无 `com.google.ai.edge.litertlm`，opt-in 精确 `0.18.1` | `demo-default.log`、`optin-final-build-graph.log` |
| TECNO KM9 / API35 | App/test APK 安装成功；UI 未执行，设备锁屏且息屏 | `device-ui.log`、`device-preflight.log`、`device-state.txt` |

首次 UI runner 等待 Activity 时，系统报告 `showing=true`、`SCREEN_STATE_OFF`，instrumentation 尚在运行；只终止该次测试 PID `16347` 并确认缺席，留下 `Process crashed` 原始回执。随后为 runner 增加锁屏/息屏预检，先报告 blocked，不再进入 Activity 等待；没有解锁设备或把 shell exit 0 当 UI 通过。

独立 code-reviewer 请求返回 `429 Too Many Requests`，没有独立审查结论。本轮作者单独复读了 native 成功/异常所有权、取消等待、ModelStore 安装和默认依赖隔离；这不替代独立审查。既有受保护 iOS signing 工程、身份草稿与相机文件保持原值。

## 仍需完成

1. 提供实际获授权的 CPU+vision `.litertlm` 模型与真实 manifest，在设备上完成图像→生成输出；host fake 不证明这一步。
2. 解锁设备后验证系统 ZIP 选择、取消、安装提示和实际图像生成；当前新增导入 UI 只有编译/host 验证。
3. Android 视频理解仍不支持；快速取消、streaming 和长期资源测试后置。

F07 由“生产 factory 缺席”推进到“同步 factory 与 Demo 接线已有本地验证，模型/设备推理未验”。F07、P11 与发布保持开放，不改变基础里程碑计数。
