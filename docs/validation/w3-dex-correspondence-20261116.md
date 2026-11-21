# W3 DEX/JVM 运行时类对应性报告（2026-11-16，本地已接受 DEX 子项）

> 状态：本地 DEX 对应子项已验收。hy3 依据供应上下文起草并限定复核文稿，主代理另对实际材料逐字节验收；本报告不表示 W3 整体验收。提交与双端同步另留收据，源码分支未推送。

## 1. 结论与验收口径

本轮只接受一个新的 P1 子项：既有 Kotlin 单一编译所得 JVM payload，与四个既有 APK 中 124 个运行时类之间的完整 baksmali 原文本对应性。比较范围精确限定为 124 runtime classes，排除 18 个 SDK backend classes 以及任何其它 App/native classes，不把这个子项写成 P1 完整或 W3 完成。

每 APK 为 124 类和 1967 个 `.method` declaration；这些 declaration 包括 native/abstract，不能表述为 1967 个含指令的方法。四个 APK 合计 496 次类比较，类级原文本比较中包含 7868 条方法声明。相等标准是完整 raw baksmali text equality：metadata、registers、instructions、labels、debug information 全部纳入，无 normalization、无 stripping；这不证明 whole DEX binary 字节相等，也不证明 formal semantics 等价。

证据入口：[parent-acceptance.json](../../probes/sherpa-runtime-materials/dex-correspondence-20261116/parent-acceptance.json)、[final-class-correspondence.json](../../probes/sherpa-runtime-materials/dex-correspondence-20261116/final-class-correspondence.json)。`probes/sherpa-runtime-materials/dex-correspondence-20261116/` 下是受版本跟踪的 curated receipts；APK、重建 DEX、baksmali 输出等大件只是 local ignored 证据和路径引用，不是本次提交的二进制资产。

## 2. 固定 APK 身份

| 来源 | 变体 | SHA256 |
|---|---|---|
| demo | debug | 85aff749d85257b4e9368b618b4fa22ab0b0d47ff2d429a5aa7e2aa087ba6688 |
| demo | release | 43b2c5b9e4beebe1712747bf0943dbbcdcfb742f34d18800ea413b265f9010e1 |
| independent-consumer | debug | 33c3fdab3ac862f7b7645c5ff7c1820c7a5da806718a4317ff7b25ff43145edc |
| independent-consumer | release | affc7579eec2d2e61b7b4b7ec8e798f91f5183aed8549dabf0d7c4f09582608e |

## 3. D8 执行与对照

本任务实际执行 SIX D8 executions，使用 D8 9.1.31、min-api 26。新 Kotlin compile 为 0，新 APK build 为 0，download 为 0，device call 为 0，ASR inference 为 0，production edit 为 0。JVM 基线来自前序任务的唯一一次 Kotlin 编译：22 个真实源文件本次 freshly verified，125 个 JVM payload 原始成员已核；本轮没有重新编译 Kotlin。

直接比较中，Debug 为 124/124；Release 初次直接仅 49/124，先保留首批 75 类差异与原始失败收据。intermediate-only 为 14/124；带 Android bootclasspath 的 release intermediate 后接同一 no-desugaring merge 仍为 49/124。对照实验只改变一个变量：release intermediate 转换阶段移除 `android36 --lib` jar 参数；final `--no-desugaring` merge 本身没有 `--lib`，保留同一 merge 后两个 Release 均得到 124/124。AGP local javap 支持 intermediate/merge 机制，但 historical actual bootclasspath 未被证明，不能声称完整复现历史 AGP，也不再做参数 sweep。

## 4. 负向控制

负控在实际 debug DEX 上把 `OfflineRecognizer.finalize` 的 `const-wide/16` 立即数从 0 改为 1，指令偏移 101504，十六进制由 `16020000` 变为 `16020100`；随后修复 SHA1/Adler32 header。原与变异 DEX 均为 310224B，Android dexdump checksum 与 baksmali 均 exit 0，124 类数量保持不变，但一个类 raw text unequal 并被拒：`OfflineRecognizer.smali`。原始文件未变，无安装、无执行、无源码修改。该负控只证明这一特定行为字节码突变可被原文本比较检出，不是穷尽语义证明。

## 5. 保护、并发改动与错误恢复

保护结果为 161 frozen files unchanged。初始 176 protected paths 中，172 initial content unchanged；4 个 concurrent outside-slice files changed and preserved，并保留 before/after receipts：docs/agent-pitfalls.md、docs/HANDOFF-F08-BIRDS-EXPERT-2026-11-08.md、docs/roadmap/W6-video-understanding.md、docs/validation/f08-qwen35-video-sft-train36-valid18-plan-20261115.md。不能称 176 全部未变；这 4 个文件也不满足 old prefix 或 old suffix equality。共享文档未在本报告中重写全文；保护结论以前后收据为准。

错误与恢复：R8 Disassemble entrypoint 缺失，改用 installed baksmali 恢复；source-map 的 22 条 links 已核验，最初对 container 形状的猜测触发 TypeError 后已纠正，source 与 DEX 未改。没有 USB、ADB、mic、AiAi、download，也不重试已知 quota 失败或 native 429。

## 6. 向上报告的阻断与后续

以上 R4 Android 24/65 在 1.5GiB 停止、R5 预算草案待定、剩余 55 个 NDK body declarations 的 source-host 429 通道停止，作为尚未解决的阻断记录。P1、P2、P3、P4、R4、R5、W3 仍未完成，其中 P1 仅上述 DEX 子项被父端接受；本次只报告该子项，不扩大整体口径。下一步仍缺 P1 native/input 完整性、最终生产采纳、P2/P3/P4 的后续工作、完整 Android R4/R5 限制。本轮不新增阈值。
