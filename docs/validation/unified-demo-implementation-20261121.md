# 统一 CMP Demo 本地交付 — 2026-11-21

当前工作树为 `.worktrees/foundation`，分支 `feat/sdk-foundation`。统一 Demo 已接入 Android 默认 Activity 与 iOS 默认根控制器，双端共用 CMP 能力广场、模型页、记录页、详情结构和主题。当前结果是本地实现与验收候选，尚未合并主干、推送、签名或发布。

设计依据：`docs/superpowers/specs/2026-11-21-unified-cmp-demo-design.md`；原型与计划位于相邻 `prototypes/2026-11-21-unified-cmp-demo/` 和 `plans/2026-11-21-unified-cmp-demo.md`。首批模型提交为 `48e76d8`，其余本轮改动保留在工作树供审阅。

## 交互与代码

- `UnifiedDemoModel.kt`：九项能力目录、搜索/分类、来源页返回、等待清理的导航门、最多 30 条 FIFO 摘要记录，以及 Host 状态映射。
- `UnifiedDemoScreen.kt` / `UnifiedDemoTheme.kt`：共用探索、模型、记录和详情界面；研发暂停项目为预览，不提供注册、验证、训练或推理入口。已有删除/停用/恢复入口保留。
- Android `MainActivity.kt` / 新 `VoiceEffectsHost.kt`：统一入口绑定既有 Host，变声嵌入详情页；旧 `VoiceEffectsActivity` 兼容入口保留。
- iOS `OnDeviceAIDemoApp.swift` / shared `MainViewController.kt` / `VoiceEffectsHost.swift`：默认根入口替换，既有工厂保留，媒体与系统操作仍由原 Host 管理。`DemoController.swift` 只追加本轮多模态准备取消扩展，原有 dirty 内容保持。
- Speech / Video / Multimodal / Voice ViewModel：提供脱敏终态摘要；配置修改使旧结果失效。Face ViewModel 补齐图片任务成功、失败、取消事件及重复回调保护，不记录连续相机帧。

任务终态先暂存每项能力最近一次摘要，用户点击“保存记录”后才加入记录历史；同一摘要不能重复保存，取消不会自动保存。记录不保存输入正文、媒体、文件引用、输出 lease 或身份模板；清空与进程退出会清空历史。播放状态、相机连续帧、冻结预览不生成任务记录。

普通离开身份页时，等待已接纳的删除/恢复调用结束；平台身份 Host 的 `busy` 同时纳入导航门。主动取消或替换归还旧 generation 的管理任务槽，旧回调不能改变新任务状态或重复扣减计数。`PURGE_PENDING`、`PURGE_FAILED` 仍显示为实际删除状态，不声称已完成物理删除，后续刷新/停用入口保留。

## 能力范围

| 能力 | 统一入口中的实际范围 |
| --- | --- |
| 语音识别 | 复用双端语音 Host，按后端与权限状态展示；本轮未开麦采集 |
| 语音合成 | 复用双端语音 Host；本轮验证文本与 locale 输入，不新增人声听验 |
| 离线变声 | 双端嵌入详情；iOS 区分准备中、准备失败、就绪；本轮 UI 验证未启动麦克风 |
| 人脸检测 | 复用图片/相机 Host；单图任务支持保存摘要；本轮无新相机采集 |
| 图像理解 | 可选 Host/模型配置决定可用性，默认构建缺失时明确展示；未重启模型评估 |
| 视频文件超分 | 复用现有 Host/模型入口，按构建配置与后端查询展示；本轮未新增画质/性能验收 |
| 播放时增强 | 当前双端统一入口尚未接入播放器 Host，明确展示未接入；既有独立播放器验收不被视为此入口已集成 |
| 视频理解 | 研发暂停，仅预览 |
| 身份与活体 | 研发暂停，仅预览与已有数据删除/停用/恢复，不提供认证结论 |

## 新鲜验收证据

证据根目录：`/private/tmp/odai-unified-demo-design-20261121/`。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| shared `iosSimulatorArm64Test` | 105 tests，0 failures/errors/skips | `final-validation.log` 与 shared JUnit XML |
| shared 模拟器与真机 Debug framework | 两目标链接通过；metadata task 被 Gradle 标记 SKIPPED，不单独宣称编译执行 | `final-validation.log` |
| Android Debug 编译与单测 | 编译通过，67 tests，0 failures/errors/skips | `final-validation.log` 与 Android JUnit XML |
| Android `lintDebug` | 0 errors，11 warnings | `demo/androidApp/build/reports/lint-results-debug.xml` |
| iOS 组合 App 构建及 UI | 4 tests，0 failures；探索/模型/记录/冻结预览、TTS 两个输入框、变声页进出 | `ios-ui-final.log`；`ios-derived/Logs/Test/Test-SpeechDemo-2026.11.21_15-50-01-+0800.xcresult` |
| 生成器回归 | 21 tests，OK，使用本次子进程 Ruby 3.4.11 | `generator-tests-ruby34.log` |
| 空白检查 | `git diff --check` 通过 | 主代理本地检查 |

三项首轮原生审查阻塞已修复：变声就绪状态、记录保存策略/人脸覆盖、身份管理清理导航门。只读原生复核原件保留于 `.superpowers/sdd/2026-11-21-unified-cmp-demo/{final-review,final-review-followup,final-review-terminal}.md`；终轮对具体身份计数问题给出 `OKAY`，没有执行构建或设备操作。

证据订正：`final-review-terminal.md` 中把 iOS UI 结果误指向 `final-validation.log` 的 1364–1490 行；该日志实际只有 797 行且仅包含 Gradle 验证。iOS UI 的 4 项通过证据以 `ios-ui-final.log` 第 640、650 行及对应 `.xcresult` 为准；这项订正不改变代码或测试结果。

首错保留：新增合同的缺类型 RED 在 `review-fixes-red.log`；更严格的物理 purge 导航试验未通过后已回退，见 `review-fixes-green2.log`，不能用它宣称 pending 等于 purged；生成器拒绝覆盖原验证目录，随后使用新的输出目录；Python 生成器测试首次误用系统 Ruby 2.6 配合继承的 3.4 gem，命中 `docs/agent-pitfalls.md` 第 7 节，仅为测试子进程对齐 Ruby 后相同 21 项通过。旧 UI 标识/空列表节点失败保留在早期 UI 日志，最终四项通过以 `ios-ui-final.log` 为准。

## 保留与后续整合边界

初始保护快照 `before.json` 中 30 个已修改 tracked 文件：29 个字节一致；`DemoController.swift` 移除本轮新增扩展后 SHA-256 与初始快照一致。原 iOS `project.pbxproj` 保持，验证工程生成到临时目录。没有删除 untracked 文件、reset 或 stash。初始 Git index 原始哈希随已提交的 Task 1 与后续 Git 操作发生变化；当前暂存区为空，不宣称原始 index 字节未变。

当前没有新增真实媒体采集、真机全能力走查、Android 新界面设备冒烟、发布包/签名验收或远端 CI 结果。已冻结的 W3/W6/W7 实验与校准任务不因界面收拢而恢复。合入主干需要单独整理本轮提交及 foundation 已有工作，当前报告不将整个 dirty 工作树视为可直接合并的单一提交。
