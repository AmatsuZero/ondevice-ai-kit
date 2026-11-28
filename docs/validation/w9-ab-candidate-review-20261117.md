# W9 A/B 提交核验与本地验证

2026-11-17；主代理审查与隔离验证完成，候选源码未修改、未合入 foundation。结论：本轮未确认需要退回 A/B 修改的源码缺陷，两端候选通过所列本地检查；正式独立复核不可用，不能标为 APPROVE / merge-ready / release-ready。

## 提交与范围

| 任务 | 分支 | 提交 | 范围 |
| --- | --- | --- | --- |
| A | `w9-a/apple-offline-sdk-candidate` | `b12cd53db9edfa57ba0165d33d23247e6d32495d` | 11 个 Swift 源码/测试文件 |
| B | `w9-b/android-offline-sdk-candidate` | `9629e5cc14c71a63df1fe247465d1482bdfe2627` | 11 个 Android 模块/源码/测试文件 |

共同基线 `8a186f7ef3a737da36d4765747f4a68f3a9eaaed`；冻结合同 `docs/voice-effects/contract-v1.md` SHA-256 `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`。两候选分支工作树始终干净，22 个交付文件摘要保持。

审查覆盖公开字段、canonical WAV 实际有界读取、原声独立复制、实际 DSP、单任务准入、取消/交付裁定、drain/close、输出租约、失败清理与无覆盖发布。同步 Apple 后端在返回前结束资源；Android render/drain 接缝要求实际等待后端释放输入，不允许私自遗留后台工作。两者均依赖宿主遵守输入不变和 SDK 输出目录独占合同。

## 真实执行结果

证据根：`.omx/artifacts/w9-ab-review-20261117T094539Z/`。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| A 原字节源码与测试，最小隔离 SwiftPM 包 | 编译成功；25 XCTest，0 失败 | `swift-test.log`、`swift-test-result.json` |
| A 实际 Apple DSP | 27 组纯音矩阵、27 组静音/1 帧/30 秒组合及取消测试通过；包含于上述 25 方法 | 同一 XCTest 日志与候选测试源码 |
| A Release | macOS Release 与 iOS 15 arm64 目标构建成功 | `swift-release.log`、`swift-ios15.log`、`swift-build-results.json` |
| B Kotlin 2.4 宿主检查 | 精确候选源码及原 sdk-core Availability 编译；28 JUnit，0 失败 | `kotlin/compile.log`、`kotlin/test.log`、`kotlin/result.json` |
| B 正式 Gradle 模块检查 | testDebugUnitTest、lintDebug、assembleRelease 成功；111 task executed | `gradle-root-parity.log`、`gradle-root-parity-result.json` |
| B Gradle 实际 XML | 16+6+6=28 方法，0 失败/错误/跳过 | `verification-receipt.json`、隔离模块 test-results |
| B lint | 0 error；1 条依赖新版本提示，未升级既有依赖 | 隔离模块 lint-results-debug.xml |
| B Release AAR | 65,534 B；classes.jar 含 VoiceEffectsClient 等 38 个 class | `verification-receipt.json`、隔离模块 build/outputs/aar |
| 文件保护 | foundation 1252 份既有 dirty/untracked 文件摘要全部保持；两候选源码及工作树保持 | `protected-before.json`、`verification-receipt.json` |

Android 隔离工程首轮只声明 AGP，遗漏原根 build.gradle.kts 的 Kotlin 2.4 插件 classpath，实际落到内建编译器 2.2，UnitTest 报 metadata 2.4 不兼容。首失败保留于 `gradle-checks.log` / `gradle-result.json`。按 `agent-pitfalls.md` 已有“独立LiteRT-LM验收壳”条目核因后，只恢复隔离根的原项目 build.gradle.kts，源码/版本/锁文件不改，第二轮通过。此失败属于验证壳配置，不能归为 B 源码缺陷。另保留 SDK XML 工具版本提示与测试 Thread.id 弃用警告。

隔离范围：SwiftPM 仅两个新 target 及其测试；Android 使用原根插件声明、wrapper、版本目录、sdk-core 和 B 模块，settings 仅纳入这两个 Android 模块。结果不外推为完整仓库/消费者/组合 App 回归。A iOS 15 是部署目标交叉编译，未运行于实体 iOS 15。

## 复核结论与后续

- 已确认源码问题：CRITICAL 0 / HIGH 0 / MEDIUM 0 / LOW 0；这是本轮主代理有限审查结论，不是独立审核意见。
- 独立 code-reviewer/architect：`independent review unavailable`。既有 native 429 停线及 native≤1 限制保持；本轮未重试。按 code-review SKILL 的双独立线路门，正式 APPROVE 仍阻塞，不把自查写成独立通过。
- 共享 Package/settings、默认工厂、KMP 桥接及 Demo 尚未集成；用户本次要求核验提交，未在 dirty foundation 静默合并两个分支。
- 下一步先在独立集成分支统一接线 A/B；C 按冻结合同推进文件导入/录音归一化、转换、原声/结果试听、导出与 release 生命周期。发布材料可并行，避免其他 Agent 同改共享清单/Host/factory。
- 实体 iPhone/Android、Android 应用沙箱实际硬链接权限（尤其 TECNO，旧 W3 shell 权限观察不能外推新 App）、最低系统实体设备、人声听感/头尾截断/爆音、资源预算和完整消费者/最终复核仍待验证。W3/W6 继续暂停。

本轮无生产源码改动、依赖升级、设备操作、录音、模型下载、签名、push 或发布。
