# 本周阶段交付交接（2026-11-17）

当前主线：**暂停 W3/W6 后续研发，新增 W9 离线效果变声，11/20 候选、11/22 本周范围验收目标。** 这是用户本轮明确调整，不按旧“先 W3→W6→W8→W7、单包串行”继续。

## 接手顺序

1. 读[本周范围](superpowers/specs/2026-11-17-weekly-delivery-voice-effects-design.md)、[执行计划](superpowers/plans/2026-11-17-weekly-delivery-voice-effects.md)、[W9](roadmap/W9-voice-effects.md)与[任务状态](validation/weekly-delivery-status-20261117.md)。
2. 读根 `AGENTS.md` 与 `docs/agent-pitfalls.md`，核 foundation git 状态、候选实际终态和活跃写入；保留所有 dirty/untracked/普通 App/模型。
3. W9-0 冻结合同与双端可行性后，A Apple / B Android / C KMP-Demo 并行；R 发布材料先行。只读 native reviewer 至多一位，外部提供实现候选，主代理集成/验证。
4. 按真实派发收据继续同一任务；提示词已准备不算在运行，候选交付不算验收。CBC 已知 429 不自动重试/换模/付费，旧单次付费授权不续用。

## 暂停与延期

- W3 暂停识别质量提升、Qwen 替换、内存/ACK 专属实验。保留 Whisper 基础功能与 iOS 语音能力，真实质量失败与资源余项不改绿。最新 ACK 有本地修复接受增量也不构成 OEM/完整 W3 通过。
- W6 暂停视频理解模型评估、训练、parity、旧/新验证集和手机实验；不删除模型/证据、不自动改默认。实际本周入口不承诺未验通用视频理解。
- W7 多人生产校准、数值协议与 PAD 阶段外延期；既有未校准拒绝和数据保护继续。原目标仍未通过。
- W8 继续本次交付候选必要的稳定性/资源/通知/消费/复审。历史完整矩阵缺口另记，不能把采样或旧包指标当本次预算通过。
- 实际包仍包含的 Sherpa/MLX/OpenCV/KMP 等依赖，必要通知与消费链不能因暂停功能而豁免。

## 下一可领取任务

W9-A/B/C：双端SDK及KMP/Demo候选，ready / 尚未派发。W9-0已完成原型，macOS/JVM/Android ART模拟器/iOS模拟器各74项文件检查通过；[合同](voice-effects/contract-v1.md)已冻结，[结果](validation/w9-minimal-chain-20261117.md)保留全部边界。原型不含SDK生命周期/录音/UI，真机、消费者和听感未验。新依赖仍未授权。最终独立复核429无结论，不自动重试。

并行 R：本周候选清单、已知限制与通知差异候选；禁止回到 W3/W6 研究。具体所有权和可复制任务提示见[Agent 提示入口](agent-tasks/weekly-20261117/README.md)。

## 本轮完成边界

最初规划交付后，已补W9-0原型与四环境文件验收；后续A/B/C仍未启动，本周交付not-ready。没有生产SDK/Demo集成或真机验收。历史 P11 JSON 与 benchmark manifest 保持，不清理缓存、不提交/推送/上传/打 tag。文档检查与审查结果记录在[状态表](validation/weekly-delivery-status-20261117.md)。
