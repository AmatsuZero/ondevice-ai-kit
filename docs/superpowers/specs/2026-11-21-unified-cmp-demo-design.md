# 统一端侧智能体验 Demo 设计

状态：原型、统一风格与设计已获用户确认；2026-11-21 用户授权开始实施。

## 意图

把当前分散的端侧智能 Demo 收拢为一个用户可以自主探索的体验入口。用户从能力广场或场景引导进入能力详情，在同一套交互中完成输入、运行、取消、结果查看、重试和记录保存。

正式实现继续采用 KMP + Compose Multiplatform（CMP）：业务状态、能力目录、页面结构和主要视觉由 shared 模块维护；相机、麦克风、文件选择、权限、播放器、模型文件准备和生命周期由 Android/iOS Host 提供。

## 目标

- Android 与 iOS 使用同一套 CMP 视觉和交互结构。
- 将语音识别、语音合成、离线变声、人脸检测、图像理解、视频文件超分、播放时增强收拢到同一入口。
- 将视频理解、身份与活体保留为明确的“研发暂停 / 仅预览”入口，不恢复训练、评估、校准或生产认证能力。
- 让真实能力状态可被解释：平台、权限、模型、后端、回退和失败原因分开表达。
- 保留现有能力 ViewModel、Host 和测试边界，逐步替换入口与导航，而不是重写 SDK 桥接。

## 非目标

- 本阶段不实现新的 AI 模型、后端、质量增强、Qwen 替换、视频理解训练或身份生产校准/PAD。
- 不把原型中的模拟结果接入真实 SDK，不在原型阶段下载模型、访问相机/麦克风或读取用户文件。
- 不以统一视觉为理由抹平平台能力差异；系统选择器、权限弹窗和平台生命周期仍由各自 Host 处理。
- 本文设计阶段不执行正式代码改造、签装、分发或主干合并。正式 Demo 完成后纳入主干是用户的后续目标，合并准备见下文；不以内部演示完成推定正式发布就绪。
- 沿用已安装的 CMP、Material3、KMP binding 和 Host 依赖，不升级工具链或增加导航、播放器、模型下载依赖。

## 反目标

- 继续维护 Android/iOS 两套平行页面，导致入口、状态和文案漂移。
- 用“已就绪”“支持离线”等笼统标签替代真实模型、权限和后端状态。
- 把暂停能力展示成可认证、可训练或已具备生产结论的功能。
- 让旧异步任务在离页、取消、输入变化或平台切换后回填结果。

## 原型结论

用户已确认原型整体可用，随后明确选择双端统一风格。以下为原型结构；其中默认能力广场与记录保留策略由本文提出，随本文一起确认：

- 底部导航：探索、模型、记录。
- 首页方向：能力广场为正式默认首页；把场景引导中的推荐入口保留在首页，不把原型审阅用的结构切换开关带进正式 App。
- 统一详情页：输入方式、示例/文件入口、运行/取消、进度、结果、重试、保存记录。
- 右上角平台切换只用于展示平台场景；正式实现中平台差异由 Host 能力状态体现。

可离线查看的已确认原型副本：[index.html](../prototypes/2026-11-21-unified-cmp-demo/index.html)，SHA-256 为 `0cc830f7370524bc6a7e46d2bb516102561a4809d95366c31e26af68850fade2`。它是设计附件，不是正式 App 构建输入。原始本地位置为 `.superpowers/prototypes/2026-11-21-demo-unification/index.html`，浏览器副本为 `.superpowers/brainstorm/8664-1795239306/content/unified-demo-v4.html`。原型验证了 15 项主流程和 7 项状态边界；本轮未改正式 Demo 文件，现有工作树中他人的未提交改动保持。

## 方案比较与选择

### 方案 A：共享 CMP 页面 + 平台 Host（选择）

所有页面、导航、能力卡片、详情状态和结果布局放在 `demo/shared`。Android/iOS 实现输入、权限、播放器、模型准备、生命周期和能力回调，并把结果映射到现有 ViewModel。媒体预览使用 Host 提供的原生视图容器；帧、纹理、URL 路径和平台对象不进入共享状态。

优点是实现面最小、双端一致、状态修复只需改一处，也最符合当前已有的 `FaceScreen`、`SpeechScreen`、`VideoScreen` 和 `MainViewController` 结构。代价是需要为平台差异设计清晰的 capability/availability 状态，不能只靠平台名称判断。

### 方案 B：共享业务模型 + 双端 CMP 外观分支

保留 shared 状态和能力协议，但 Android/iOS 分别提供更贴近系统的控件、导航和页面装饰。

优点是可更强地贴合平台习惯；代价是会增加页面分支、视觉回归和双端验收成本，当前散乱问题容易重新出现。现阶段没有足够的用户反馈证明这笔复杂度值得投入。

## 目标架构

```text
CMP shared UI
  ├─ Capability catalog / navigation / shared copy
  ├─ Explore / Models / Records / Capability detail
  ├─ shared state rendering and cancellation rules
  └─ capability-specific ViewModel contracts
        │
        ├─ Android Host: Activity Result, permission, camera, player, model/files
        └─ iOS Host: UIKit/SwiftUI picker, permission, camera, player, model/files
```

现有 `FaceScreen` 已经承载可选的 speech、video、multimodal 和 identity 页面。新增一个共享应用容器管理顶层导航，把这些能力页面与变声组合进去；不继续给 Face 页面增加应用级职责。Android/iOS 的默认 App 接入该容器，取消普通入口中条件式 tabs、独立变声 Activity/sheet 和固定 380dp 控制区；已有公开 Kotlin/Swift 工厂签名和定向测试入口保留兼容包装。宿主仍可通过内部 Activity/子控制器承载必要的媒体视图，但不得形成另一套导航和业务页面。

现有 Host 的 generation、leavePage、background 和 close 语义继续作为异步生命周期边界。共享根容器管理路由和样式，现有 ViewModel 管理各自能力任务，不引入一套替代它们的通用推理执行器。

### 当前接线与交付边界

| 能力入口 | 正式 Demo 目标与当前约束 |
|---|---|
| 语音识别 / 语音合成 | 分成两个用户入口，复用现有 Speech ViewModel/Host，保留语言、后端、资源准备和 typed failures。Android 当前 Demo ASR 只有麦克风路径，不能因原型有文件页就实现或声称文件识别；iOS 按实际 Host 配置显示。 |
| 离线变声 | 接入已交付的原声/升调/降调、导入/录音、试听、导出和清理；保留 WAV、时长等原有限制，不扩大格式或算法范围。 |
| 人脸检测 | 预览、框和关键点在详情页内展示；相机离页停止，不把平台预览固定悬在所有能力页面上方。 |
| 图像理解 | 共用入口与页布局；按编译配置、Host 和模型状态显示可用/未接入/缺模型。当前内部签包的 iOS 未纳入多模态，不为对齐界面自动启用 MLX 或重启模型评估。 |
| 视频文件超分 | 复用 Video ViewModel/Host 的选取、处理、取消、播放/分享输出；原生后端和 OS 限制原样保留。前后对比只显示真实可获取的输入/输出，无法同步比较时明确使用分别预览，不用模拟增强图替代。 |
| 播放时增强 | 接入已有 `RealtimeVideoSR` / `SelectableRealtimeSRHost` 控制与状态；Android 先复用 `examples/android-realtime-sr` 已有播放器实现，iOS Demo 采用轻量 `AVPlayer` + `AVPlayerItemVideoOutput` Host。CMP 统一呈现能力入口和共享状态；iOS 控制项已由 CMP 承载，Android 既有播放器控件暂保留，待抽取可嵌入 Surface/时钟 Host 后再完全统一。原生 Host 只负责平台解码、逐帧取图、增强预览和渲染。iOS 的 Core Image 路径必须标注为 Demo 示意，不冒充生产 AI 超分，也不修改或依赖独立 UHPlayerSDK。 |
| 视频理解 / 身份与活体 | 默认提供冻结预览页，使用随包合成示意，不调用冻结模型。保留原有注册数据的删除/停用恢复入口（仅在已有 Host 管理能力存在时显示），不创建新注册或迁移数据，不给认证结论。 |

已有原生播放器的本地来源还未等同于统一 App 中已经完成正式算法验收；本 Demo 允许使用明确标注的轻量 Host 展示完整交互，但不得把示意增强写成生产后端能力。

## 页面与状态约定

- `Explore`：能力目录、分类、搜索、能力状态徽标和冻结能力分组。
- `Models`：统一汇总现有 capability 的资源状态，导入/准备按钮委派已有 Host 的模型选择与校验。展示进度、取消和失败；无 setup Host 时只解释原因，不新增通用下载器或模型格式，不擅自移除/替换已安装模型。后端未接入、系统资源、缺少模型和未查询分开显示；不把所有能力统一宣称离线。
- `Records`：首版只保留当前进程内的能力名称、平台、时间、成功/失败状态和非敏感结果摘要，最多 30 条，先进先出；支持清空。记录不额外持有媒体、输出文件或身份模板的 lease，不延长 Host 的资源保留时间；进程退出即清空。再次打开进入该能力并解释历史状态，需要重新选择输入才能运行；身份预览重新确认授权。
- `CapabilityDetail`：统一输入区和结果区；页面离开、输入替换、权限撤回均使旧任务失效。正式 App 不提供平台切换。切页停止相机/录音/播放并取消推理，等待既有清理终态后才允许新任务；进入后台沿用 Host 的停止与取消规则，回前台不自动续录、播放或推理。
- `FrozenPreview`：只渲染输入、授权、限制和结果布局，不输出验证成功、活体成功或已有模型结论。

页面表示区分 `idle / inputReady / running / cancelling / cancelled / failed / completed`，以及 `permissionDenied / modelMissing / backendUnavailable / unsupported` 等可解释原因；这是对已有能力状态的视图映射，不要求统一重写所有 ViewModel 枚举。输入和配置在运行/清理中按现有能力规则禁用；可编辑的文本变化必须使旧任务/结果失效。取消尚未完成时不能提前显示“已取消”或重新启用运行。任务绑定已有 generation/session token，迟到的输出由原有 Host/桥接释放，避免回填或泄漏。

底部导航只在顶层探索、模型、记录显示；详情使用明确返回入口，退出回到来源页并保留其筛选/滚动位置。Android 系统返回与页面返回走同一退出清理；iOS 宿主不要再叠加另一条导航栏。原型中的 iOS/Android、布局比较、错误场景开关均为审阅工具，不进入正式用户页面。

主视觉按原型的浅底、绿色强调、统一圆角/字阶/间距实现为 shared theme。主题跟随这次已确认的浅色视觉；暗色主题不作为本次交付门槛。控件支持平台文字缩放、键盘遮挡与安全区，交互使用语义标签；输入类型、语言、后端和取消/清理状态等现有有效控制不能因布局简化消失。基准检查窄屏 375px 对应布局，横屏及平板可用不溢出，不追求另一套专用大屏布局。

## 平台边界

| 能力 | shared CMP | Android Host | iOS Host |
|---|---|---|---|
| 页面、导航、结果和文案 | 负责 | 接入 | 接入 |
| 麦克风/相机/文件选择 | 展示状态与回调入口 | Activity Result / 权限 | UIKit/SwiftUI picker / 权限 |
| 播放与导出 | 播放状态与控制 | 系统播放器/文件 API | AVFoundation/文件 API |
| 模型导入与可用性 | 展示统一状态 | 本地文件/模型 Host | 本地文件/模型 Host |
| 生命周期取消 | 触发 shared leave/cancel | foreground/background/close | foreground/background/close |

平台切换仅作为原型审阅工具；正式 App 按当前编译目标和 Host 能力生成实际状态，不在运行时伪造另一平台结果。

播放时增强的选项从 `supportedModes()` 生成，用户选择、首选后端和 `presentedBackend` 分开显示；尚未首帧呈现时显示“尚未呈现”，逐帧回退显示原画。不把 Spatial 等一般增强统一写成“AI 推理”，不把偏好后端冒充实际后端，也不把文件超分可用推定为实时增强可用。状态经 KMP 传递，解码帧和纹理留在原生播放器。

## 冻结任务处理

- 语音质量增强、Qwen 替换和内存专项：保留现有 ASR/TTS 入口和限制说明，不扩展研发范围。
- 视频理解：入口可见，标为研发暂停，仅预览输入和结果布局。
- 身份与活体：入口可见，强调授权、本地管理和未校准，禁止认证结论。
- W8/W9 内部能力展示与正式发布状态分开，Demo 可演示不等于 release ready。

## 验收证据

正式 Demo 的完成条件（本轮只交设计文档，不执行设备/模型操作）：

1. 先为现有导航和关键生命周期缺口锁定回归，再改入口；shared commonTest 覆盖路由、能力状态、记录清空/上限/不持有媒体、取消、清理终态和离页。
2. Android/iOS 各有 Host 边界测试，覆盖 picker/permission/lifecycle 回调迟到与取消。
3. CMP 页面在 Android 和 iOS 构建目标下编译，关键可访问性标识一致。
4. 双端构建、适用 lint/static checks 与既有相关测试通过；真机验收分别记录平台能力、模型来源、权限和播放器结果，不能用另一平台或 headless 证据替代。具体设备操作/新采集沿用当次授权边界，不把批准设计当成开麦、相机或模型实验授权。
5. 冻结能力只验证展示边界，不把预览结果写成质量、校准或生产接受。

6. 两端统一根入口可访问九项能力的真实接线/不可用/冻结状态；语音识别、合成、变声、人脸、图像、文件超分和播放器保留各自已有有效操作与失败恢复。每个能力“真实成功 / 当前不可用 / 仅冻结预览”的证据逐项列出，不要求在缺 Host 或缺模型时伪造功能对齐。

## 主干合并目标

正式改造在独立任务范围中完成。验收后核对实时 `main`、`feat/sdk-foundation` 与远端祖先/差异，列清本次 Demo 及其所需既有能力提交；当前 foundation 有大量历史与受保护 dirty，不能把它们一起打包合并。按实际依赖形成可审阅的合并候选，保留 SDK 行为、暂停任务和内部/正式发布的既有裁定。主干集成、CI、设备证据和发布状态分别报告；本轮不执行 merge/push。

## 留给实现者的决定

- 共享根容器、主题、目录与各能力页面的具体文件拆分，优先复用现有组件，不引入新的通用框架。
- 平台 Host 能力状态的具体视图 DTO/回调映射及测试注入方式；不复制 SDK 后端 registry/router，不通过平台字符串硬编码可用性。
- 原生媒体视图接入容器、滚动/焦点保持和渐进式替换顺序；保留旧工厂与自动化入口，实际默认根界面只保留一套。
- 现有播放器版本的固定方式与构建接线细节；遇到未包含的外部改造需求向主代理报告，不暗中扩大为播放器研发或分发任务。

## 代码与事实依据

- `demo/shared/build.gradle.kts`：已使用 CMP/Material3 与 `kmp-binding`，没有另选 UI 框架的必要。
- `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/FaceScreen.kt`：条件式 tabs 与既有能力接线。
- `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/{Speech,Video,VoiceEffects,Multimodal}ViewModel.kt`：既有控制、取消和资源释放边界。
- `demo/iosApp/OnDeviceAIDemoApp.swift`、`demo/androidApp/src/main/kotlin/io/ondeviceai/demo/MainActivity.kt`：现有根 Host 与分散变声入口。
- `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/RealtimeVideoSR.kt`、`docs/validation/realtime-sr-selection-20261121.md`：后端选择与实际呈现分离。
- `docs/validation/ios-realtime-sr-demo-handoff.md`：独立 UHPlayerSDK 来源和 KMP/原生播放器边界。
- `docs/validation/w8-candidate-capability-scope-20261120.md`、`docs/HANDOFF-2026-11-17-weekly-delivery.md`：既有签包实际范围与暂停/内部交付裁定。它们是历史包事实，统一 Demo 的新包需重新按实际接线报告。
