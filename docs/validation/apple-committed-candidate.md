# 精确提交 Apple/shared 候选 — 2026-11-05

状态：同一源码候选的 Apple host、KMP/shared iOS 单测、基础 Demo/消费者构建和注入后端桥接测试通过。
后续已补同源码增强 MLX 双平台与 OpenCV Identity 设备目标整 App 构建，见下节。
已补精确编译代码的隔离签名变体真机 smoke、模型安装及两变体稳定首屏截图；触摸入口仍被自动化工具阻断，多模态生成未执行。
发布仍 `open/not-ready`。

## 两种隔离变体的真机首屏渲染

复用既有 OpenCV Identity device App，只在新副本修改 bundle ID 为
`io.ondeviceai.candidate.e0438c0.identity`、显示名为 `ODAI Identity Candidate` 并沿用既有签名。
Info.plist 仅这两项元数据不同；3份 Mach-O 的 UUID 和有效 section 内容与原编译产物一致，未重建。
同一 iPhone17,2 / iOS27.2 上使用 Xcode 官方 `devicectl device capture screenshot` 获取项目 App 截图：

- `candidate-screen.png`：OpenCV 普通首屏稳定显示，含空预览、YuNet/SFace 导入提示、原型授权及未校准声明；
  导入按钮可见，启用注册按钮呈灰色。这只证明显示，不证明点击拒绝、系统 picker、同意授权或 native 模型执行。
- `mlx-candidate-settled.png`：既有 MLX alias 的普通首屏稳定显示，人脸、Identity、语音、视频、图像理解页签可见；
  没有可见的模型准备 spinner 或失败信息。没有切换页签、TTS/ASR 或生成验收。
- `mlx-candidate-screen.png` 捕获于系统 App 切换动画中，保留但不计稳定渲染证据。

CUA 原生入口报 `Sky Computer Use native pipe startup failed`；一次受支持的 reset 后同样失败，未发送 UI 输入。
此前 XCTest automation mode 和移动测试代理超时未重试；本轮 UI 测试仍0执行。没有导入模型、注册模板、采音、拍照、
私人媒体、新依赖/模型下载或 benchmark 样本，也不能把 alias 显示升级为原 bundle 或全能力验收。

21个阶段全部 terminal、0 pending；两候选进程已停止，原 `io.ondeviceai.opencv.demo` 已恢复。
41个既有 App 安装 URL 未变，两隔离候选保留安装，既有 MLX 模型存储保持；9保护路径和生产 lock 不变。
原始证据为 ignored `artifacts/verification/apple-identity-candidate-runtime-20261105/`，
summary SHA-256 `3c65545c4a570525456a482e0499d02dc538c354582b631610c2cf6eab8654b6`。
截图、视觉观察边界、签名及恢复收据均保留；只读复核：

```sh
rtk proxy python3 artifacts/verification/apple-identity-candidate-runtime-20261105/verify.py
```

## 隔离签名变体真机 smoke 与系统 UI 门槛

复用上述 e0438c0 的既有 device 编译产物，不重新构建或修改源码。原 bundle ID 已在手机安装，
因此仅在新的副本中将 App ID 改为 `io.ondeviceai.candidate.e0438c0.multimodal`，
UI runner 也使用独立 ID；沿用现有 wildcard profile 和唯一匹配的本机签名身份。
App 的 Info.plist 除 bundle ID 外完全相同；7份 Mach-O（含通用二进制及 dSYM）各架构 UUID、
全部有效 section 内容均与原编译产物一致。原 unsigned App、签名资产、生产 lock、9保护路径不变。

在 iPhone17,2 / iOS27.2 / 24B119 上得到以下限定证据：

- 既有 `--smoke-blank` 入口完成 Swift→KMP/共享 VM→Apple Vision→回调：64×64 合成白图、
  `apple.vision.face`、0张脸、完整 sdkCommit 匹配，runId `A3B566E9-F6ED-4402-A337-03CB259169CD`。
  这是固定负样本链路，不是自然人脸质量或真实触摸验收。
- 既有 `--multimodal-mlx-local-model` 入口完成 ModelStore 安装；设备 manifest 与 committed 描述逐字段一致，
  14个实际安装文件回读后字节数/SHA全部匹配。进程 PID2439 与 host factory 的视频临时目录已观察到；
  **没有请求多模态生成，不能据此声称权重推理或普通 UI 可用**。
- 4项既有普通 UI/TTS/文件取消用例计划执行，但 runner 在系统 `enabling automation mode` 初始化超时，
  **实际执行0项、通过0项**。触摸工具也等待测试代理超时。失败后的 Xcode 诊断收集未结束，
  经完整命令/PID核对后 SIGINT、SIGTERM 收尾，host exit143；不把该退出码当产品用例失败或通过。

首轮签名误选 profile 中没有本机私钥的第一张证书，安装前失败；保留失败副本/收据，
fresh retry1 改为与既有可用身份求唯一交集，没有创建证书或修改 keychain/profile。
报告和安装 manifest 的首次读取早于生成，第二次读取成功，没有重复启动同一请求。

候选已停止，独立 UI runner 已卸载；新候选和模型保留，原 `io.ondeviceai.opencv.demo` 已恢复。
所有既有 App 的安装 URL 均未改变。TECNO 当前 ADB unauthorized，本轮未操作其可用 Demo 或模型。
无采音/拍摄/私人媒体、新依赖下载或 benchmark 样本；23个设备/测试阶段全部 terminal，无pending。
这些是**改变 bundle ID 的隔离签名变体**证据，不能升级为原 bundle 或全部能力验收。

原始证据：ignored `artifacts/verification/apple-candidate-runtime-20261105/`；
summary SHA-256 `79896e19274765045aa0591919900e6967531c37ffe109591ee4821d6edd68fa`。
完整失败日志、签名输入、安装模型回读、设备与恢复收据均保留；只读复核：

```sh
rtk proxy python3 artifacts/verification/apple-candidate-runtime-20261105/verify.py
```

下一为系统自动化入口恢复后的当前候选普通 UI/模型生成、独立 OpenCV 候选运行、Android 当前候选
支持 TTS 引擎的真机验证，以及完整审查/生产交付/许可。未改变原9行gap或P4/P11发布门槛。

## 增强 MLX 与 OpenCV Identity 整 App 构建

继续使用下方同一953文件归档和已验证KMP framework，没有重跑基础718项或F09打包。
12个固定依赖、2个C子模块从此前恢复的完整本地Git对象创建自包含clone；
**6,878份上游文件逐一匹配原始Git blob/SHA/执行位**，没有F09 overlay或生产依赖修改。
历史SourcePackages的绝对alternate已失效，新隔离缓存不依赖它；原缓存保持不变，未联网fetch。
构建前后均复核源码、依赖、九保护路径与生产lock；构建后的workspace仍为12个固定revision，无外部artifact/prebuilt下载记录。

| 独立变体 | 构建与产物检查 |
| --- | --- |
| MLX完整Demo / generic iOS | unsigned构建成功，arm64、部署声明18.0、完整sdkCommit匹配；真实`MLXVLMBackend`符号及5575字节模型描述、2399192字节Metal库存在 |
| MLX完整Demo / generic simulator | unsigned构建成功，arm64、部署声明18.0、完整sdkCommit匹配；同一模型描述与2405144字节模拟器Metal库存在 |
| OpenCV Identity Demo / generic iOS | 新提取同归档并复制既有static native XCFramework及同候选shared framework；unsigned构建成功，arm64、部署声明15.0、完整sdkCommit匹配；ObjC/Swift runtime符号存在，未动态embed OpenCV |
| 可选OpenCV包host契约 | 显式`ODAI_OPENCV_CONTRACTS_ONLY=1`，6/6、0失败/跳过；不包含native运行验证 |

三份App文件树、664个实际Swift编译输入路径及内容哈希已固定。两平台模型描述均为
`c074e693…602d54`，与committed resource完全相同；Metal库按实际平台分别记录，没有复用F09或旧工具链硬pin。
App debug dylib SHA分别为`8ea82da6…a6d2ce`（MLX device）、`8cae8af2…b6ba1f`（MLX simulator）、
`56146e30…29c7a3`（Identity device）；UUID、动态链接表与实际Swift file lists也保留。

这是**两个独立Demo变体**，不声称合并成一个App。OpenCV既有制品只有arm64 device/x86_64 simulator，
不提供arm64 simulator，未据此声称该目标通过。MLX scheme同时编译了UITest target，但没有执行UI测试。
没有签名、安装、模型推理、采集、benchmark新增或操作普通手机Demo；部署声明也不是最低系统运行验收。
原上游Metal/Swift、AppIntents及方向警告保留。初次native符号收集把private Swift名当作global原名匹配而失败，
改用实际符号反解后仅重新读取既有App，没有重建产品。

证据为 ignored `artifacts/verification/apple-enhanced-candidate-20261105/`；`summary.json` SHA-256
`67802fd939f6e67e0f89cccc10d5babd5a168b6d9d7c551f6961af3e866591d8`。
8个阶段全部terminal，无pending；`inputs.json`、`identity/inputs.json`、原始logs/xcresults、
`product-inspection.json`和完整产品树保留。只读复核：

```sh
rtk proxy python3 artifacts/verification/apple-enhanced-candidate-20261105/verify.py
```

## 前一轮基础 host/shared/桥接验证

与 [Android 候选](android-committed-candidate.md) 使用同一份已核验归档：
提交 `e0438c030a987ceeff9f9207c2475684749e4d8b`，tree `1a79aabdc997ca99fe441a02b9cb54982ce2c34c`，
归档 SHA-256 `c3b5966e09fdd44a957e3da1f3a2674a289857f99e02211a18f1f772155373b4`。
重新独立提取953份 committed 文件，逐文件 SHA/执行位与既有 Git blob 清单一致；
没有复制受保护的 dirty iOS project。所有阶段结束后再次核对953源文件、9保护路径和生产 lock。

使用现有 Xcode27.2/Swift6.4.2、JDK21、Gradle9.4.1、Kotlin/Native2.4.0 与依赖缓存。
Swift package 无外部依赖；Gradle 使用 offline/no-build-cache/rerun-tasks 和显式本地 Native 工具链，
35个构建任务全部执行。没有下载、采音、拍照、签名资产变更或发布，也没有操作 TECNO 的可用 Demo。

| 验证 | 实际结果 |
| --- | --- |
| `apple` macOS host XCTest | 8目标、481项，0失败/跳过；按唯一 test case 和各目标日志核对 |
| `kmp-binding` iOS simulator | 24项，0失败/错误/跳过；6份XML报告 |
| `demo/shared` iOS simulator | 80项，0失败/错误/跳过；6份XML报告 |
| KMP frameworks | Binding simulator、shared simulator/device Debug静态framework链接通过 |
| 基础 Demo | 既有generator开启speech/video/multimodal UI，unsigned generic iOS与simulator构建均成功；Info.plist内完整sdkCommit匹配 |
| 原生消费者 | 同归档 `NativeConsumer` generic simulator构建成功 |
| Swift→shared Demo ABI | Speech39、Video9、Identity10、Multimodal75，共133项；实际iOS27.2/24B8117 simulator、0失败/跳过/预期失败，逐组xcresult与日志一致 |
| 既有辅助检查 | Swift bridge、camera lifecycle7场景、photo submission、合成ImageIO方向1/2/6/7、ASR状态机、Swift5/6严格并发均通过 |

桥接测试使用注入后端、合成输入和临时store，验证取消/drain、权限晚到不启动、locale选择、
输入/输出lease、host关闭及未校准Identity策略拒绝等契约；不是麦克风、相机、PAD模型或自然质量验收。
macOS host通过也不代表仅iOS可用分支全部运行。基础Demo含多模态UI，但没有启用MLX模型factory或OpenCV Identity，
不能将它表述为这些真实后端的整App候选。

六组iOS编译/测试配置的外部artifact共120条引用、80个实际文件，逐文件记录坐标/大小/SHA；
Binding无外部Klib，shared每组40条。两平台Skiko均仍为生产 `0.144.6`，没有替换为F02补丁版本。
收据不声称冻结了整个系统SDK/Kotlin工具链内容。F02生产分发与资源门槛继续开放。
六个framework/App产品完整文件树已冻结；unsigned构建不是签名、安装或设备运行证据。

原日志保留Kotlin Native系统cache archive路径警告、既有无用cast和AppIntents提示，
多模态注入semaphore用例仍有XCTest QoS priority-inversion警告；没有为这些提示修改产品或放宽检查。
首次process inventory与xcresulttool报告缓存读取被sandbox限制；普通权限提升后收集成功，未重跑测试。

原始证据位于 ignored `artifacts/verification/apple-committed-candidate-20261105/`。
`summary.json` SHA-256 为 `79c0bca1bb9c9c1829ac68bf7dd50f6a75c2c0eaa919b577b58950d6d3d3f5ff`。
`run.py`/各阶段started-result-log、12份KMP XML、四份xcresult统计、resolution与完整产品树均保留；
所有请求已terminal。只读复核：

```sh
rtk proxy python3 artifacts/verification/apple-committed-candidate-20261105/verify.py
```

本轮新增718项不同层级测试执行（481+24+80+133），不将它们合成为功能覆盖率或benchmark样本。
下一为同源码增强Apple整App在适当设备的运行、当前Android候选TTS支持引擎设备/真机、
独立完整审查与生产交付/许可决策；真实采集、质量/资源、最低系统、F02/PAD校准及P4/P11缺口保持。
