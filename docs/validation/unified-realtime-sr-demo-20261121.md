# 统一 CMP 实时超分 Demo 验证

2026-11-21，分支 `feat/unified-realtime-sr-demo`，起点 `0f11a036041624f6de8942cc369cac49be7d54c4`。

Android/iOS 的“视频 → 播放时增强”现使用同一个 CMP 控制页：系统选片、播放/暂停、原画/AUTO/可用后端、输入文件和播放状态。平台侧只承载像素与会话。此记录取代[早期接入记录](unified-demo-playback-enhancement-20261121.md)中的当前状态说明；早期失败原件保留。

## 实现与状态语义

- Android 从独立 `PlayerActivity` 提取 `RealtimePlaybackSession`，共用原 MediaCodec、音频时钟、GLES、CuNNy/Spatial 及 EGL 呈现匹配；独立播放器仍保留。CMP 直接嵌入 SurfaceView，不跳转第二套原生控制页。
- iOS 使用 AVPlayer 音频时钟、full-range NV12 VideoOutput 与 MTKViewDelegate/Metal；可用时 AUTO 按 System → Spatial → CuNNy 选择。540p/1080p、≤30 fps、无旋转且三个 BT.709 颜色标签符合条件才尝试增强。每帧 18ms 决策窗口，超时/忙时原画回退；Metal 提交、纹理或 GPU 失败时隐藏 Metal，显示底层 AVPlayerLayer。
- 用户请求、确认上屏和回退分别记录。Android Direct Surface 不提供 EGL 显示证据时，保留原请求、仅支持新的 ORIGINAL 选择、明确回退，`presentedBackend` 保持 null。SDK 原有“动态撤销可用性后保留旧选择”的语义不变。
- `CodecPlayer.open()` 将配置工作排到内部线程；调用返回不是成功。当前 request/token 的 `format` 配置完成回调才清除旧错误；配置失败异步回调可触发一次 Direct Surface 重开。前后台、Surface 重建、换片和离页使旧回调失效；metadata 单独失效并在重开后刷新。
- iOS 真机使用 drawable presented handler；模拟器只显示实际完成的渲染后端，仍保留“等待播放器确认上屏”。GPU completion 和截图变化都不冒充物理上屏或增强性能证据。

## 本地构建与回归

原始日志与截图根目录：`/private/tmp/odai-unified-sr-acceptance-20261121/`。

| 项目 | 结果与原件 |
| --- | --- |
| Android Demo JVM | 67 项，0 失败/错误/跳过；`android-final-build.log` |
| Android 独立 realtime JVM | 45 项，0 失败/错误/跳过，含 4 项打开状态回归；同上 |
| KMP shared iOS simulator | 107 项，0 失败/错误/跳过；`android-state-green.log` |
| Android lint、两个 debug APK、回归测试 APK | 构建通过；`android-final-build.log` |
| KMP simulator/device debug framework | 链接通过；`shared-simulator-final.log`、`android-state-green.log` |
| Swift 帧决策 gate | 11 个实际断言通过，覆盖 deadline、重复决定、迟到完成、跨代次和 clear |
| Xcode 工程生成器 | 两次执行字节一致；`generator-stability.json` |

Android 状态回归先真实失败：4 项中 Direct Surface 回退和旧错误清除两项失败，见 `android-state-red.log`；修复后 45 项完整套件通过。shared snapshot 测试只证明读取 Host 状态；CMP 真实重组由下面 UI 验收覆盖。

## iOS 模拟器

专用设备 `ODAI Unified CMP 20261121`，UDID `1BF8DC23-08A1-435E-90AE-A091A90E3DC1`，iOS 27.2。`ios-final2.log/.xcresult`：2 项，0 失败；测试体 76.471s，外层 80.827s。

系统 Files 实际选片，验证原画 → AUTO → CUNNY → SPATIAL 的 CMP 选择与连续画面、暂停/恢复、Home/activate、离页后重新进入空状态和禁用播放；短视频另测 EOS → 播放 → 从头连续画面。AUTO 渲染为 spatial，显式 CUNNY/SPATIAL 渲染各自后端；所有模式均未伪造 presented。

预览截图 RGB 字节差异阈值为绝对差 >12，变化比例须 >0.005；最终原画 242757、AUTO 382658、CUNNY 267730、SPATIAL 266203、前台恢复 222887 / 4795200 字节变化；EOS 重播 234654 / 4795200。它们证明画面继续变化，不证明每帧增强或 30fps。

此前失败记录全部保留：`ios-red*`、`ios-green1~4` 涉及滚动后点击、编译与低变化夹具；`ios-final` 的 EOS 用例通过，另一项对暂停按钮作即时断言，未等待 CMP 250ms 刷新；改为等待真实按钮状态后 `ios-final2` 两项通过。`ios-green6-path-error.log` 是工作目录错误，另一次 green6 路径已存在；均未冒充通过。

## Android 模拟器

仅使用本任务 `emulator-5582` / `Medium_Phone`，read-only、无窗口、SwiftShader；不操作其他设备。

`android-session-probe1.log` 的实际回执 `probeStatus=pass`：缺失文件导致打开失败 → 在相同 URI 放入有效合成片 → stop/start 重开并清掉旧错误；请求 CUNNY、可用 ORIGINAL、fallback=true、presented=null；随后 EOS 重播与 leave 后无 source、无播放、无 pending cleanup。测试直接使用生产 session；文件路径仅在 androidTest 中，无生产后门。CLI exit 与回执分别检查。

`android-cmp-run2/` 保留系统 Downloads 选片、全部模式、暂停、Home/前台恢复、离页/重新进入的 XML、截图和动作记录。SurfaceView 截图变化比例：原画 0.7001、AUTO 0.6018、CUNNY 0.5529、SPATIAL 0.6284、前台恢复 0.6014，均高于 0.005。该模拟器没有确认上屏时间，UI 保留等待状态。

原脚本末端因读取 Compose 按钮的 Text 子节点 `enabled=true` 而 exit 1，实际 `toggle-realtime-player` 按钮节点为 `enabled=false`；保留 `android-cmp-run2.log` 和原 XML。独立核验器复核全部已完成步骤与截图，再读取当前空页的真实按钮，结果见 `android-cmp-run2/result-verified.json`。首轮脚本的 NumPy 缺失与 fixture 传输失败也保留，没有改依赖或放宽沙盒。

## 实包与来源

`package-closure.json` 绑定 APK SHA-256 `430b03553e3106fa6fce962c9c095b41ff3fe7a4322a79330aa03dab4025b24d`。四个 ABI 的 `libsrtiming.so` 均实际入包，所有 LOAD 对齐为 `0x4000`。既有第三方 so 仍可能触发 16KB 兼容提示，不据此声称整包已完成 16KB 适配。

iOS CuNNy/Spatial 及 Swift 引擎固定来源 `comp-uhplayersdk@7d40dfe94f007c808c30cdb3b8004444f0405cdc`；原 shader 字节与源提交一致。`RealtimeSRNotices` 的 PROVENANCE、CuNNy 原文及 LGPL/GPL 全文均从实际 App 回读一致。Swift 改动限于模拟器编译边界及提取帧 gate；不链接 UHPlayerSDK。此为 Demo 来源与实包核验，不等于全部分发许可审批完成。

暂存后的完整 `git diff --cached --check` 对 CuNNy shader 和其原文副本各报 6 处继承的行尾空格；为保持来源原字节而保留。两文件已与固定源 SHA 核实，排除这两份原文后的全部自写差异检查通过，见 `whitespace-upstream.log` / `whitespace-result.json`，不将完整检查写成无告警。

60s H.264/AAC BT.709 合成片 `odai-sr-motion-540-v2.mp4`：45293280 B，SHA-256 `f1da3f527c69fd2ef64dec1b042c228e59d538ba513bfeecae07a4ab6e633532`。6s 同源短片：4522695 B，SHA-256 `8db8457fbf94f63576d2edb4fa9528602adb5a32aee9d4682a4ec64147124be5`。均只用于本地验收，不入 Git。

## 独立原生复审

最终只读复审结论为 **ACCEPT**，未发现新增 correctness blocker；Android Direct Surface 回退、同源失败后的异步重开恢复，以及 iOS 生命周期、EOS 和呈现语义均在本次内部 Demo 验收范围内通过。报告原件为 `/private/tmp/odai-unified-sr-acceptance-20261121/native-final-review.md`，先前 REJECT 与失败证据保持。

复审原件中 `git diff --check` 通过的描述仅覆盖当时未暂存差异。父代理随后检查完整暂存内容，发现并记录上述 12 处固定来源原文空格；不把该描述扩大为完整暂存检查无告警。复审不替代下面的真机、性能或分发验收。

## 验证边界

- `iphoneos` 无签名构建通过，验证 System SR 与真机 presented 分支可编译；本轮未执行真机、旧系统、System SR 运行、持续帧率/能耗或监控/夜视画质验收。
- iOS 提交容量、纹理创建及 GPU 错误的系统原画恢复路径经代码审查，未注入真实 GPU 故障；11 个纯 Swift 断言不替代这些硬件故障测试。
- 未发布签名包或远端制品。主 checkout 的个人 IDE 文件、SDKDistribution scheme、模型和无关工作保持原状。
