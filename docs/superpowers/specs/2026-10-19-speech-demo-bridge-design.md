# KMP 语音 bridge 与 Demo 设计

日期：2026-10-19。状态：用户已选择“现有后端先接 Demo”；本书面规格待审阅，尚未批准实施。

## 目标与已确认方向

将已交付的原生语音 SDK 接入现有 KMP Demo，让使用者可以输入文本并播放 TTS、选择系统默认或显式后端，以及运行当前平台已支持的 ASR 输入。成功标准是公共 bridge、原生适配、共享 UI、权限/输入所有权和取消/关闭形成真实调用闭环。

沿用已有决定：系统能力默认；调用参数可以严格指定后端；原生 SDK 模块可以裁剪；宿主负责权限。用户于本轮选择先接已有后端，再独立补齐 iOS 麦克风 ASR。后者仍是 roadmap 必交付项，本包完成不能将 P6 或整个 roadmap 标为完成。

## 方案取舍

1. **采用：统一语音 bridge，显式表达输入能力。** TTS 请求两端一致；ASR 用独立输入类型区分麦克风与宿主文件引用，由选中后端声明支持范围。既有原生 API 保持原样，适配层执行输入检查。
2. 两端先补齐相同输入再接 Demo：界面较一致，但会推迟已有能力进入 Demo；用户未选择此顺序。
3. 在共享层分别提供 iOS/Android 专用操作：接入较快，但会把平台差异散布到 ViewModel，不采用。

## 本包范围

| 能力 | iOS | Android |
| --- | --- | --- |
| TTS | 已有 AppleAVSpeechBackend | 已有 AndroidTextToSpeechBackend |
| ASR 文件 | 已有 AppleSpeechRecognizerBackend，强制 on-device | 本包不支持，返回 unsupportedInput |
| ASR 麦克风 | 本包不支持，后续独立工作包补齐 | 已有 AndroidSpeechRecognizerBackend，API31+ on-device |
| 第三方后端 | 宿主可注入已链接实现并显式选择 | 同左 |

本包不加入微软 SDK、凭据、云服务、后台录音、模型下载或自动语言资源安装。不把声明的 offline=true 当作已测断网结论。系统 ASR 缺资源时保留 MISSING_ASSETS，不回退网络识别。

## 共享边界

在 `kmp-binding` 新增与 Face/Identity 分开的语音契约；commonMain 不依赖平台语音 SDK。Swift 可见 DTO 使用普通类/枚举，避免 value class 或依赖泛型导出推断。

- `SpeechBridgeSelection`：Default 或 Explicit(backendId)，空白显式 ID 无效。Default 由宿主按能力注册为系统后端；明确选择未注册/不可用后端绝不回退。
- `SpeechBridgeCapability`：TTS、ASR；后端列表按能力独立查询。异步 `queryBackends(capability, callback)` 返回可取消任务，不将原生挂起查询包装成同步属性。
- 描述包含 backendId、availability/reason/minimumOS、支持的 ASR 输入、offline 声明。后端列表用于显示，实际请求仍由原生 Client admission 校验。
- TTS 请求包含 text、locale、可选 voiceId；结果包含实际 backendId 和可选 voiceId。成功仅表示原生播放任务完成，不声明质量。
- ASR 请求包含 locale 和 `Microphone` 或 `AudioFile(reference)`。文件 reference 是宿主进程内的 opaque token，不是 URL、路径或模型引用。
- ASR 结果包含实际 backendId、final transcript、可选 confidence。不增加部分转写或编造语言/词级字段。
- TTS、ASR、后端查询各有明确回调类型。终态为 result、failure 或 cancelled，单任务最多一次；不用一个泛化的 FaceResult/IdentityResult 表达语音。
- failure 包含稳定分类：closed、busy、invalidInput、unsupportedInput、backendNotRegistered、backendUnavailable(reason)、timedOut、noResult、platform(domain/code)。未知平台错误不降为“不可用”或成功；诊断不含文本/音频路径。

## 原生适配与所有权

Android adapter 只依赖 `android:speech` 契约和既有 coroutines；系统 TTS/ASR 由 Demo host 显式依赖、创建并注入。iOS Swift adapter 放在 `kmp-binding/apple`，依赖 Speech 契约，分别支持 Binding 与 Demo umbrella 编译，不同时链接两份 Kotlin framework。系统实现由 host 注入。

每个 adapter 独占宿主转交的语音 Client；宿主不再调用或关闭这些 Client，也不把其拥有的同一 backend 实例交给其他 owner。adapter close 拒绝新请求、取消本 adapter 的任务并等待 native close/drain；所有并发 close 等待同一个完成结果。关闭后 host 创建新的 adapter，不重新打开已关闭实例。

每项任务有 generation 和取消句柄：cancel 只发起停止；终态回调必须等到原生任务退出、输入释放和取消 drain 完成后发布。Swift 文件 ASR 取消返回不等于底层文件可释放，必须继续 await native drain；Android 遵循既有 session 清理边界。迟到成功/错误在终态或新 generation 后丢弃。后端查询取消不能关闭仍由 adapter 使用的 Client。

一个 adapter 同时只允许一个音频任务（TTS 或 ASR）；busy 请求明确拒绝，不自动替换。后端查询不占用音频任务槽。切换语音页面、输入或后端时，ViewModel 发起取消并保持 cancelling，直到原生终态后才允许下一个音频任务；不靠任意 sleep 估算释放完成。

## 权限与文件输入

- Android host 请求 RECORD_AUDIO；未授权时展示原因和恢复入口，不启动 recognizer。API26–30 保持 Demo 可启动，ASR 显示 requiresOSVersion；TTS 独立可用。
- iOS host 请求 Speech 授权，并提供 usage description；本包文件识别不请求麦克风权限。只在用户主动触发识别时申请，不在后台/启动时弹窗。
- iOS 通过系统文件选择器接收音频，使用安全访问范围将选中文件复制到本 App 临时输入目录；取消选择不改变当前输入，不读取其他文件。
- 文件仓库仅存在于 native host，token 查找失败返回 invalidInput。选中引用和在途 lease 独立持有；移除选择后仍需等在途 drain 才删私有副本。替换、取消、页面关闭和 App owner 释放均有确定清理路径。
- UI 只显示文件名，不显示绝对路径；音频、转写文本和 TTS 文本不写 Git、普通日志或 benchmark 报告。转写仅在当前页面内存展示，不自动保存。

## 共享 Demo

现有“人脸检测 / 身份与活体”旁增加“语音”入口，复用有界滚动布局；语音视图不显示人脸的后台状态。进入语音页停止已有相机流，不引入新的相机资源管理器。

语音页包含：locale、按能力独立的默认/显式后端选择与刷新状态；TTS 文本框、播放/取消；ASR 支持的输入入口、开始/取消、最终转写及实际后端。尚不支持的输入明确解释；不把文件 ASR 按钮命名为“录音”。缺资源/拒权/busy/closed 与成功状态分开显示。

ViewModel 状态为 idle、running、cancelling、succeeded、failed、cancelled；取消中的控件保持禁用。离开语音页或 App 进入后台会取消音频任务，前台不自动开始播放或录音；后续新操作等待上一轮 native drain。整个 App owner 销毁时再 close adapter；页面切换本身不永久关闭它。

## 工程与裁剪

原生 SDK 七个 Apple products 和 Android 原生模块的依赖不因 Demo 而扩大。已有 native contract-only consumers 继续证明不引入 KMP/Compose/系统语音 backend。

`demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` 和 `docs/validation/face-identity.md` 仍为受保护用户文件。本包不得覆盖生成器或改写该工程。新增可重复、拒绝覆盖的 **独立 Demo 工程生成入口**：在忽略的 artifacts 中引用同一 App sources、KMP framework 和所需 Swift products，复用既有 bundle ID/本地签名配置做验证，不创建账户资产。普通受保护工程通过不启用语音适配的默认注入路径继续编译；新增语音 Demo 由明确的编译选项启用，并在 README 提供生成/打开命令。该限制只影响演示 host 工程，不改变 SDK 的可选模块接口。

独立工程生成器必须同时覆盖 simulator 与 device KMP 产物，避免只刷新模拟器后用旧 device ABI 编译。所有验证产物保持 Git 忽略，不提交 framework、音频或大文件。

## 验收

1. common 测试验证严格选择、按输入支持 admission、busy、取消/drain 后重入、后台/页面切换 generation、迟到回调拒绝、明确不可用且不伪造成功。
2. native bridge 测试调用真实 SpeechClient 加可控平台边界，验证 metadata/error 映射、取消/close 等待及文件 lease；fixture 不替代真机音频结论。
3. iOS 同一 Swift adapter 对 Binding/Demo 两种 ABI 编译，生成工程/原有 Demo 均构建；Android TTS/ASR backend tests、Demo APK/lint 与裁剪 consumers 通过。
4. 新增生成器在缺少框架/源码、输出已存在时无覆盖副作用。核对受保护文件 hash 未变化。
5. 在线现代真机：两端 TTS 完成/活动取消/重入；iOS 选文件/取消选择/转写/取消后输入释放；Android ASR 在有资源时转写/取消/重入，无资源则只记录 unavailable。权限拒绝/恢复按真实设备结果记录。
6. 自然语音质量、断网验证、API26/iOS15、iOS 麦克风、微软后端和 P11 最终 benchmark 继续列为未完成项，不由本包代替。

## 规格检查

已核对：TTS 与 ASR 结果不混用；文件/麦克风差异明确；Default/Explicit 与裁剪不变；取消/关闭和输入释放边界一致；受保护工程有可执行隔离方案；本包未将后续必交付项缩为不可用占位完成。下一步是用户审阅本规格，获认可后编写实施计划。
