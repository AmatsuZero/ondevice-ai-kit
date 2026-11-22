# CMP 能力详情页与 iOS 桥接恢复验收（2026-11-22）

基线 `7b8065a93d8e07bcbe6c331aa9cc9cbb7cb5e2b1`；任务 worktree `/Users/daubert/.codex/worktrees/3e3c/ondevice-ai-kit`。用户已确认原型并授权实施、主干集成和两个远端普通推送；本次外发的 Demo 源码、必要设计/测试/合同摘录也获得明确授权。实现由免费 CodeBuddy `hy4flash-free` 候选交付，主代理审查原字节集成并执行验收，单个 native 子代理只读复核。

## 交付行为

- shared CMP 持有详情页、状态、导航和控件；native Host 保持媒体选择、解码与渲染。未改 SDK、模型、锁文件、签名或真机安装。
- 共用导航按返回键实际测量宽度预留对称空间，标题相对整屏居中，允许大字换行。用户指出首版 Row 标题偏右后已修正，并加入坐标回归。
- Scaffold 单独避让安全区，SwiftUI 背景延伸到系统边缘，辅助设置/报告内容仍保留安全区。移除额外外层 padding 与导航竖向占位；白卡和浅色背景保持一致。
- 文件页分为输入、处理设置、运行/取消、真实结果和默认折叠高级项；主路径展示 Host profile、后端不可用原因与恢复入口。严格后端选择、音轨、取消等待、清理、输出 lease 和记录语义沿用既有状态模型。
- 播放预览按有限视口拟合16:9，仅占实际尺寸；余下控件可滚动。空态容器使用 `playback-preview-container`，播放后原生渲染器保留唯一 `playback-preview`。模式来自 supportedModes，实际呈现仍来自 Host；模拟器不伪造实际上屏确认。

## hasVideo 崩溃

用户10:05:40的堆栈在 Kotlin→Objective-C `hasVideo` getter forwarding 后 SIGABRT。当前协议和 Swift 源码已有该属性；旧 `PlaybackEnhancementHost.o` 缺少 hasVideo/isPlaying/inputName/playbackStatus 四个 ObjC getter thunk，旧 App Host 方法表14项。源码不变、当前 CMP framework 链接后使用全新隔离 DerivedData 重建，四个 thunk 齐全、方法表18项，原EOS/重播及模式/后台两个UI回归通过。没有播放器源码 workaround。Xcode为何保留旧对象的内部依赖机制未证实，Kernel Triage VM行不构成本次内存耗尽的证据。

证据：`/private/tmp/odai-hasvideo-crash-20261122/diagnosis.md`、`getter-comparison.json`、旧/新对象符号与元数据、`fresh-playback.xcresult`、`fresh-modes-background.xcresult`。后续布局验收沿用该隔离 DerivedData，未再使用root旧framework软链。

## 最终验证

| 检查 | 结果 | 证据（位于 /private/tmp/odai-detail-acceptance-20261122/） |
| --- | --- | --- |
| shared iOS simulator 单测 | 107项，0失败/0跳过 | centered-test-counts.json、centered-gradle.log |
| Android Demo JVM | 67项，0失败/0跳过 | 同上 |
| iOS simulator framework link；Android lintDebug/assembleDebug | exit0，30.769秒，既有工具链警告保留 | centered-gradle-result.json |
| iOS默认App UI | 5项，0失败，外层exit0/159.316秒 | centered-ui.log、centered-ui.xcresult |
| iOS accessibility-large | 1项，0失败，exit0/23.331秒；字号恢复large | large-font-ui-result.json、large-font-ui.xcresult |
| Android实际播放UI | 空态禁用、16:9、合成视频运动、原画/自动/CUNNY/SPATIAL、暂停/继续、后台返回、离页重入通过 | android-final/functional-result.json、XML/截图 |
| Android几何中心/适配 | 竖屏、横屏、2倍字、375dp+2倍字通过；标题中心误差0–0.5px，返回不重叠，媒体16:9 | android-final/geometry-result.json |
| 设置/个人文件保护 | Android字体/方向/尺寸及iOS字号原值恢复；root19份原保护文件SHA一致 | android-final/settings-{before,restored}.json、ios-content-size-before.json、protected-after.json |
| 源码空白检查 | git diff --check exit0 | 主代理执行记录 |

五项iOSUI覆盖：空态整屏标题中心/返回不重叠与横竖屏16:9；有视频唯一原生预览横竖屏16:9和运动；EOS重播；模式/暂停/后台/退出重入；文件页首次选择按钮位置、系统选片取消、空态禁用、无结果不保存、默认折叠后端及真实unsupportedDevice。

iOS截图已从通过结果包导出至 `centered-ui-attachments/`；竖屏标题截图 `E46210E3-D7EC-4400-95B1-0E7C5E5CA530.png` 与横屏 `D13E54F9-4872-40F4-AA81-C0608EC2E796.png`，已目视核对标题/白卡/统一背景。Android截图和实际节点坐标与收据共同保留。

## 保留的失败及修正

- 最初ui-red曾使用旧framework，不是当前源的可靠证据；随后ui-source-red真实复现旧导航minY136pt（要求≤100pt），但同轮hasVideo桥接崩溃独立处理，不能混为布局故障。
- 原hy4free自然终态exit0但 `empty_model_response`，无候选。其provider modelUsage记hy4std，原因/收费未推断。用户选择当前help核实的hy4flash-free并明确授权源码外发后，新候选实际modelUsage均为hy4flash-free，无付费回退。
- 首实现UI横屏定位frame比值2.30128，且新增CMP标识与native同名；不能据此唯一归属native frame。修正使用不同标识，最终分别验证空态CMP容器与播放后唯一native frame。文件测试改为先选视频分类再等LazyColumn入口并同步方向。
- 四文件修正中预留半屏槽造成潜在竖屏空白，未接受该结构；下一独立候选只占实际媒体高度。白卡token单独修正。
- final-ui四项中3项通过，空态错误要求隐藏的MTKView存在而失败。既有Host在空态明确isHidden=true；测试改查CMP容器，同时新增真实播放native比例和横屏运动检查，未改生产renderer可见性或削弱比例断言。
- 用户截图指出自然宽返回+剩余空间居中的Row标题偏右；换为实际测量的对称布局，新增整屏中心与不重叠断言后五项UI通过。
- Android首几何脚本旋转后因既有Activity配置重建回首页而找不到标题；保留首失败，按实际分类/列表重入后验布局。未声称旋转保持路由。每次finally均恢复原设置。
- 帮手raw工具事件保留：预览修正最初两个候选相对context路径不存在，之后正确绝对路径读取成功；另有写DELIVERY之前读不存在文件。其余终态结果、实际候选摘要和精确集成独立核实，未照抄“无错误”自报。

## 边界

仅专用iOS27.2/Android模拟器。模拟器截图运动不证明真机超分质量、实际上屏时序、持续帧率、能耗或性能；本次未做真机SR质量验收。Android默认构建文件超分Host仍未接入，不声称Android文件转换、导出或选片完整验收。iOS后端unsupportedDevice保持真实结果，未扩展Apple iOS26+/320×180/24fps/1–48帧/BT.709 SDR/4×能力；Android主路径限制来自既有Host profile。键盘、无Home指示条设备、其他OEM及SwiftUI辅助报告/权限内容运行态未在本轮单独验收。Activity配置重建仍会回到首页，这是现有行为。

最终只读复核 **PASS**：见 [最终审核](cmp-capability-details-review-20261122.md)，原件 `/private/tmp/odai-detail-acceptance-20261122/final-native-review.md`。最终提交/普通推送回读结果另见交付终态收据。
