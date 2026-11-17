# W9 A/B 集成结果与 C 启动入口

2026-11-17；用户授权 A/B 集成与 C 启动 Prompt。本地集成完成，未 push，未开始 C 实施或真机验收。当前分支 `feat/w9-ab-integration-20261117`，位于 `.worktrees/foundation`；复用原隔离 worktree，既有 dirty/untracked 文件保持。

## 提交与变更

| 提交 | 内容 |
| --- | --- |
| `1d99dfb` | 原样 cherry-pick A 的 `b12cd53`，11 个 Apple 源码/测试文件 |
| `8f0834c` | 原样 cherry-pick B 的 `9629e5c`，11 个 Android 模块/源码/测试文件 |
| `46c7e4af7a1a34b140899893c2992cd9b34947b1` | 应用 A/B 原建议：Apple Package 新增两个产品/两个 target/一个 test target，Android settings 纳入 voice-effects |

A/B 实现字节未改；未新增依赖、改版本/锁文件或重构。默认构造由 C 的新平台 Host 使用已验构造方法；本次不凭空增加默认后端选择协议。旧 Host/路由/生成器未改，C 后续共享接线由主代理处理。

## 集成后真实验证

证据根 `.omx/artifacts/w9-ab-integration-20261117T100314Z/`：

- 真实 `apple/Package.swift` 全套 `swift test`：509 方法、0 失败，9 个 XCTest bundle；含变声25、Speech76、Identity322及其余专项。独立 scratch-path；不是只跑隔离两模块。
- 真实 Apple Package 的 `ODAIKitAppleVoiceEffects`，Release `arm64-apple-ios15.0`：成功；当前 iPhoneOS SDK27.2，非 iOS15实体设备运行。
- 真实根 settings 的 `:android:voice-effects:testDebugUnitTest :android:voice-effects:lintDebug :android:voice-effects:assembleRelease` 离线成功；111 tasks，71 executed/40 up-to-date。JUnit XML实核28方法、0失败/错误/跳过。
- lint：0 error、1条已有协程依赖新版本提示，未升级依赖；原测试 Thread.id 弃用及配置弃用提示保留。
- Release AAR：65,534B，classes.jar 包含 VoiceEffectsClient 等实际类，SHA见 `integrated-verification.json`。
- 各命令/exit/耗时、实际XML和AAR副本均保存在该证据根；不把旧XML或 NO-SOURCE 当新增源码通过。

前轮候选审查见 [A/B 核验](w9-ab-candidate-review-20261117.md)。独立复核仍 `independent review unavailable`（既有 native429停线，无自动重试），不标正式 APPROVE / release-ready。无设备/录音/私人媒体/模型下载/签名/发布操作。

## C 启动

完整可复制 [C Prompt](../agent-tasks/weekly-20261117/C-kmp-demo-start.md)。C 从代码基线 `46c7e4a` 在独立 worktree 实施，主代理不代其启动；状态为 prompt-ready / 等用户启动，不伪报 running。

C 覆盖公共合同与双端 adapter、共享状态/页面、平台录音/导入归一化/试听/导出。其新文件不与 A/B 冲突；现有 Host/入口、Gradle/SwiftPM/manifest、生成器仅交建议 patch，由主代理统一集成。Prompt 特别规定 Android 取消清理失败时 drain 与等待 process 的顺序，避免互相等待。

当前剩余：C 实施、共享路由/依赖/生成器接线、真实消费者/组合 App、实体双端核心链路、人声听感/首尾截断/爆音、资源与最终独立复核。W3/W6继续暂停，本周release仍 not-ready。
