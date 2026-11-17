# W9-A/B/C 派工模板（一次只实例化一条）

当前工程合同：`docs/voice-effects/contract-v1.md`，SHA-256 `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`。原型可行性接受、最终独立复核429未完成；本模板仍未派发。

Task：按主代理提供的已冻结 `contract-v1.md`、SHA、签名、可行性结果，实现本条独立候选。无合同不得自行设计跨端类型并开始大实现。

Select exactly one lane：

- A Apple：`apple/Sources/ODAIKitVoiceEffects/*`、`apple/Sources/ODAIKitAppleVoiceEffects/*`、`apple/Tests/ODAIKitVoiceEffectsTests/*`。
- B Android：`android/voice-effects/build.gradle.kts` 与 `android/voice-effects/src/*`。
- C Bridge/Demo：总计划 W9-C 明确列出的 `VoiceEffects*`、`AndroidVoiceEffectsBridge.kt`、`IOSVoiceEffectsBridge.swift` 文件；不得改现有 SpeechBridge 或 ASR/TTS 行为。

Only selected lane owns its supplied candidate files in an isolated directory. 主代理独占 `apple/Package.swift`、根settings/版本目录、已有Host/路由/factory、生成器和共享manifest；需要修改时提供单独建议补丁，不直接覆盖。你不是唯一Agent，不能整理其他人的工作。

Acceptance：实际实现与冻结合同一致，先交定向RED测试/预期，再交实现；覆盖坏/超限输入、取消/close/重入与输出所有权。C额外覆盖录音拒权、重选、后台/离页、迟到回调、试听/导出失败，最终必须接真实A/B，不用mock宣布变声成功。实际测试/编译由主代理统一执行，未执行不要填通过。

You are not alone in the codebase. Preserve other edits. Do not recursively delegate. Report blockers and scope changes.

Before work and on errors, consult docs/agent-pitfalls.md. For a no-tools task, the primary must supply applicable excerpts below. Preserve other edits, do not retry a known quota failure, and report concrete blockers upward.

Required supplied excerpts：§1成功/编译/采样边界；§11保护；所选平台相关编译/音频生命周期；当前供应商429与完整文件交付限制。主代理在每条新prompt中注入原文，禁止直接发送未补全模板。

Requested output：选定文件的小批完整候选或有实际沙盒证明的隔离文件、paths/bytes/SHA收据、检查/失败事件和blockers；不操作设备、不录音、不下载新模型、不新增依赖/锁、不改凭据/签名、不推送发布。复杂输出分小批，不静默重试截断结果。
