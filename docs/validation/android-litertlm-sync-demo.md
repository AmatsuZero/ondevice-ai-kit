# Android LiteRT-LM 同步图像生成接入

日期：2026-11-03。用户调整为功能优先，长期资源与罕见竞态后置。本轮承接 [实施增量](../superpowers/plans/2026-11-03-android-litertlm-synchronous.md)，补齐真实 runtime 调用和 Demo 接线；**官方模型、显式 CPU 文本＋GPU 视觉与真实 TECNO 图像生成链路已验证；[普通模拟器 UI](android-litertlm-ui.md)的系统选择器、生成、取消重入已通过，物理设备 UI 仍未验**。

## 2026-11-04 普通模拟器 UI 补验

真实系统 ZIP/图片 picker、两次成功生成、中间取消和同 PID 重入、清除输入与文件清理已通过，57项证据检查及独立有界补审PASS。实际为SwiftShader软件GPU；真机UI/硬件GPU性能不在该结论内。见[环境、原始证据与边界](android-litertlm-ui.md)。以下保留各历史检查点，UI缺席表述已由本节更新。

## 2026-11-04 TECNO 真实模型图像生成通过

固定 Gemma3n E2B 权重已通过 `adb push -Z` 传入已授权的 TECNO KM9/API35：
3,655,827,456 B、110.85秒。设备端流式完整 SHA-256 匹配，真实 ModelInfo 为CPU文本可用、
GPU视觉可用、CPU视觉不可用。设备禁止hard link，故复制到本轮独立临时目录，未删除原文件。

显式 `CPU_TEXT_GPU_VISION` 请求经真实 Demo backend → ModelStore → client → Android bridge →
production factory → CPU/OpenCL GPU runtime，固定纯红256×256图片输出 **`Red`**。
runId `849795aa-2418-4be0-bd8a-7777acc1acba`，backend=`litertlm.android`、
modelRevision=`c03b6f60b8da6c5400b6838a2cf26420f80c0a01`、runtime=`litertlm-android:0.18.1`。
报告 `closed/modelLeaseReleased/privateInputsRemoved/generationCacheFilesRemoved` 均为true，
终态 `status=passed/stage=complete`；`verify-mixed-result.py` 新鲜通过，绑定运行APK/源码快照和实际报告。
`generationMs=83332` 含此次校验、初始化与生成，整条shell调用149.85秒；不是纯解码性能基线。
日志保留NPU缺席等warning，未切换runtime、后端或关闭校验来绕过。

证据：ignored `artifacts/verification/android-gemma-vision-20261104/` 中
`mixed-device-model-info.json`、`mixed-generation-report.json`、`mixed-generation.{stdout,stderr}`、
`mixed-generation-exit.json`、`mixed-build-identity.json`、`mixed-build-sources/`。
精确探针PID25760已退出，设备仍Asleep；普通Demo未覆盖，未启动Activity、解锁、调用相机/麦克风或读取私人媒体。
这是一次合成单图的真实后端/bridge功能证据，**系统ZIP选择/图片选择/UI、取消重入、自然质量和长期资源仍未验**。

独立有界review为REQUEST CHANGES，配置传递/所选后端gate未发现缺陷；两项Medium建议作者分级处理：
instrumentation增加显式 `-e backendPolicy CPU_TEXT_GPU_VISION` 参数，省略仍CPU_ONLY；
预置畸形descriptor的availability泛化诊断为既有corner case后置。实际ZIP导入会在安装/构造前拒绝未知策略，
并向UI报告模型格式/校验/兼容性失败，新回归验证零安装和零构造；未把拒绝改成自动回退。
没有复审APPROVE，也不扩大为全分支批准。
审查收尾后的最终离线结果：后端 **33/33**、Demo **55/55**，APK/testAPK与lint exit0
（`mixed-review-final-build.log`、`mixed-review-final-verification.json`）。
最终metadata测试APK在既有模拟器默认运行 **4 pass＋1 opt-in skip**，已读取raw runner状态码
（`mixed-review-emulator-admission.log`）；不执行模型推理，和上述真机权重生成证据分列。

## 2026-11-04 显式 CPU 文本＋GPU 视觉

用户已明确选择新增可选混合策略并继续实现。默认 `CPU_ONLY` 保持，公开
`LiteRtLmModel.backendPolicy` 与 Demo descriptor 可显式选择 `CPU_TEXT_GPU_VISION`；
未知 descriptor 值拒绝，spec→session→engine config 全程传递，无自动切换。
生产 factory 仍在创建 engine 前检查实际 text、vision、CPU 文本、所选视觉后端及最低 runtime。

固定官方 [ModelInfo.kt L194–195](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/ModelInfo.kt#L194-L195)
定义 vision budget `-1` 为未定义；现在允许 `-1` 或正数，仍拒绝0/其他负值。
[Config.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/Config.kt#L181-L219)
及 [Engine.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/kotlin/java/com/google/ai/edge/litertlm/Engine.kt#L75-L92)
确认文本与视觉后端独立传递。引擎总上下文仍512（输入＋输出）；本次只验证单图短提示＋32输出tokens，长上下文配置后置。

新鲜离线验证：后端 **33/33**、opt-in Demo **54/54**，APK/test APK及两模块lint通过
（`mixed-local-build.log`，exit0，warning保留）。测试先有缺API RED和丢失策略的行为RED，再GREEN；
覆盖默认/显式/未知descriptor、策略传递、所选后端、runtime与budget门槛。
新APK在既有API36模拟器读取完整权重并流式复核SHA后，实际报告
`cpuVisionCompatible=false`、`cpuTextGpuVisionCompatible=true`、`engineCreated=false`；
见 `mixed-emulator-model-info.json`，不等于GPU初始化或推理通过。
完整真实来源/大小/摘要/许可 manifest 已准备于 ignored `mixed-run/bundle/`，未分发模型。
上述为实现检查点；真机传输、推理与审查收尾结果以上节最新证据为准。

## 2026-11-04 权重输入门槛复核

有界 dependency-expert 调查的中间回报引用固定 `0.18.1` 源码
[`bda624851d74be34d4e1f7fac1fc4155939539c7` README](https://github.com/google-ai-edge/LiteRT-LM/blob/bda624851d74be34d4e1f7fac1fc4155939539c7/README.md)，
其中使用官方候选
[`google/gemma-3n-E2B-it-litert-lm`](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)。
既有元数据记录的 revision 为 `c03b6f60b8da6c5400b6838a2cf26420f80c0a01`，
文件 `gemma-3n-E2B-it-int4.litertlm`、3,655,827,456 B，标注
`gated=manual` / `license=gemma`。本轮已刷新官方元数据，LFS SHA-256 为
`2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`；
本地完整文件已核对相同摘要。
调查最终遇429，不能作为完成的独立兼容性结论。

候选受 [Gemma 条款](https://ai.google.dev/gemma/terms)与人工许可门槛约束。
官方示例/一般 vision 声明不证明本 SDK 固定 CPU+vision 组合；得到实际获授权
文件后，仍先检查 `ModelInfo` 的模态/后端/最低 runtime，再固定真实 hash/manifest，
最后运行图像请求。未找到已验证的非门控宽松许可替代项；不是不存在此类模型的证明。
用户已明确选择该候选，并回复“已完成官方许可确认”。现有 HF CLI 的固定 revision
`--dry-run` 成功，访问已确认；代理没有接受条款，也没有读取或输出 token。
权重下载已获授权。Xet 传输观察到约317秒未完成且反复提示连接困难，
CLI 内置 HTTP fallback 与小范围传输可取得数据，但完整响应断流、续传及尾段缓慢。
用户随后明确提供本机 aria2 RPC 和下载映射目录；仅创建专用模型任务（未改其他任务或全局配置），
复用 1,006,632,960-byte 连续前缀，尾段缩小分段并续传后完成。
aria2 `status=complete/errorCode=0`，文件 3,655,827,456 B；复制到项目验证目录时
独立计算完整 SHA-256，与上述官方 LFS 值一致。RPC secret、HF token 与签名 URL 不写入证据文件。
证据位于 ignored `artifacts/verification/android-gemma-vision-20261104/`。

新增独立 `LiteRtLmModelInfoInstrumentation`：默认跳过，只有显式
`-e authorizedModelProbe true` 才读取测试 App 私有的 `files/authorized-model.litertlm`。
它先流式校验固定 SHA-256，再读取 ModelInfo；不创建 engine、不启动 Activity，持久化摘要、模态、CPU 后端、vision token budget 与最低 runtime，
不兼容则测试失败且保留报告。真机测试 App 曾被系统冻结（`do_freezer_trap`），
限时 CPU wake lock 无效后已移除；增加同一 APK 的 `app_process` 入口作无 UI 运行。
同大小错误摘要的合成稀疏文件在完整流式摘要阶段被拒绝（约12秒），未进入 ModelInfo。
真机大文件 USB push 无回执，独立 shell 查询超时；只结束本轮 push 并按 serial 恢复连接，
设备未留下完整模型。现使用既有 `ODAI_MainFlow_20261104` / API36 / arm64 模拟器，
先独立验证 actual ModelInfo，不创建 engine；模拟器结果不能冒充 TECNO 真机验收。
本轮后端 JVM 29/29、Demo opt-in 52/52、离线 APK/test APK/lint 通过；早期 TECNO API35 无模型 admission 4/4，
模型检查默认1项跳过。旧公开 availability 用例的异步路径断言已实测失败，随后
测试已对齐现有同步公开 API 的资产可用语义；生产模型 gate 未改。
最终重复安装/推送尝试遇到 ADB 失败，没有新的设备通过结论；原始日志保留。

另新增显式 `LiteRtLmRealModelProbe`，只有固定模型摘要与 CPU+vision 预检查通过才进入
真实 Demo backend → ModelStore → client → Android bridge → production factory。
输入为仓库生成的 256×256 纯红 PNG（564 B），摘要
`991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa`，
流式限16MiB并固定摘要，输出保留实际模型 identity/文本与 close/drain/lease 清理结果。
当前仅构建验证，未把它记成真实生成通过。两次有界独立审查各1项 Medium 已补模型/图片摘要绑定，
并修正输入内存上限；没有最终独立 APPROVE。

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

1. 已完成固定模型、真实manifest、显式mixed策略及一次真机图像生成；后续补系统导入/图片选择的UI闭环。
2. 解锁设备后验证系统 ZIP 选择、取消、安装提示和实际图像生成；当前新增导入 UI 只有编译/host 验证。
3. Android 视频理解仍不支持；快速取消、streaming 和长期资源测试后置。

F07 由“生产 factory 缺席”推进到“同步 factory 与 Demo 接线已有本地验证，模型/设备推理未验”。F07、P11 与发布保持开放，不改变基础里程碑计数。
