# W3 Qwen KV288 模拟器固定文件结果（2026-11-11）

KV288 在独立 Android ARM64 模拟器完成原 65 个公开文件，输出与原 KV512 批次逐条一致，既定工程质量门通过。**未观察到采样峰值 RSS 降低**，不能把静态 KV 少 49 MiB 写成实际内存修复，也不能关闭 W3/R4/R5。

## 固定合同与执行

- 本地实现提交 `c66ece0`；独立 wrapper `QwenKv288Instrumentation`，alias `io.ondeviceai.acceptance.qwenkv288`，variant `cpu-arena-off-kv288-v1`。只改变 constructor 容量及真实报告元数据，原模型、65 文件顺序、两阶段准入、128 生成上限、CPU2、arena-off 和评分规则保持。
- 唯一 run `w3-qwen-kv288-emulator-20261111-01` 已消耗，禁止重放。独立 AVD `W3_Qwen_Kv288_20261111`、`emulator-5582`、Android36/default/arm64-v8a revision2、4096 MiB/CPU2，无窗口/音频/相机/快照，使用既有系统镜像。
- 冻结 448 项文件，合同 SHA256 `a844a4bf8146af9aaf36c4dae51d7677445a8a2d28706a1d6c78c4a18ba3e20f`；sourceFingerprint `ed42cdce172883052e0c56c102f258eddb667495d8fb29475fc9aaa7dfdbc3f0`。
- 原 arena108、heap120、emulator327 守卫重新核验通过；实体 heap run 仍未消耗。本轮没有麦克风、AiAi、模型/runtime 下载、生产默认或普通 App 变更。

## 质量与逐条对照

真实终态为 **65/65 completed、1 constructor、117 streams、117 decode**，识别器释放与前台 teardown 成功，无根错误。父验收 18 项均通过，同 run 报告双读一致、六模型后哈希一致，实际 config 为 288/128。

| 场景 | 覆盖 | 固定指标 | 工程门 |
| --- | --- | --- | --- |
| 中文 | 20/20 | CER 40/590 = 6.78% | ≤20%，通过 |
| 英文近场 | 20/20 | WER 37/375 = 9.87% | ≤20%，通过 |
| AMI 英文会议远场 | 12/12 | WER 64/226 = 28.32% | ≤35%，通过 |
| 静音及会议间隙 | 13/13 | 严格空字符串 13 条 | 通过 |

父端重跑原 `tools/speech_error_metrics.py` 评分，与两份原验收 aggregate 完全一致。逐一核对 65 个 ID/PCM SHA/sample/locale/cohort/reference，65 条最终文本和 117 个阶段文本均逐字相同；准入、错误及释放等全部持久化字段也一致，仅排除测量耗时。这里是对同批输入的配置比较，不是新增独立 held-out 质量确认。

## 资源观测与裁定

| 观测 | 原 KV512 | 新 KV288 |
| --- | --- | --- |
| host 秒数 | 145.4635 | 113.4863 |
| load 秒数 | 1.9173 | 1.4565 |
| recognizer release 秒数 | 0.0653 | 0.0465 |
| RSS 样本数 | 574 | 440 |
| 采样最高 RSS | 1,539,006,464 B | 1,542,725,632 B |
| 设备可用内存最小值 | 1,466,806,272 B | 1,547,964,416 B |
| 每片 RTF 范围 | 0.10613–0.51062 | 0.08472–0.40171 |

KV288 最高采样 RSS 比 KV512 **高 3,719,168 B（约 3.55 MiB）**。两次均未触发原 1.5 GiB RSS / 512 MiB available / 120s 每片 / 7920s 总批停止门，但独立运行与不同采样时序不能建立受控加速，也未测 true peak。静态基础 KV 的 112→63 MiB 只说明容量算术，不能由此推断整体 RSS 或真机收益。

政策配置真实同 inode、有界 21 B 双读得到精确 20 B，报告 SHA 和 provider 均匹配；`nativePolicyEffectVerified=false` 保持。释放、移除本次两个 alias、删除精确 staging、返回 Launcher 与关闭精确 AVD 均已核验，普通 Medium_Phone 配置 SHA 保持。原始报告、源码、APKs、日志和配置保留；任务生成磁盘的精确清理另有路径/哈希收据。

## 验证与边界

实现前旧容量 fixture 真实 16 断言失败；候选 32 组/6003 host 断言通过，离线 Android75 任务和 lint（0 error/7 既有 warning）通过。实际 DEX constructor=288、snapshot=288、生成上限128，16 类/65 PCM/四原 arm64 库/签名/零权限均核验。完整 tools691 项通过。父端首稿政策 variant 归一化实际 RED 后改为保留原报告，最终 7 组父端检查含完整冻结变异全部通过；两份免费 hy3 运行前限定复核均接受。

运行后免费 hy3 在 62.739s 完成供应材料限定复核，零工具，接受、无阻断代码发现；其条目是确认和范围提醒。父端订正复核中的两个单位错误：RTF 无量纲，3,719,168 B 是约 3.55 MiB。复核未独立读取未供应的原始包/完整冻结文件，不能视为实体设备或发布通过。原响应和父裁定均保留，见[复核收据](w3-qwen-kv288-final-review-20261111.json)。

运行前 87 份 dirty/untracked 保护记录中，事后 85 份哈希相同，2 份并行文档发生漂移并保留：`docs/agent-pitfalls.md`、`docs/HANDOFF-F08-BIRDS-EXPERT-2026-11-08.md`。此后本任务对 shared 文档只追加自身前缀并精确暂存该前缀，不包含其他修改。

**剩余门保持**：TECNO 完整 65 文件质量及资源、原 heap 单片诊断、true peak/GPU/energy/生产预算，以及 exact runtime 递归来源/静态组成、生产通知和独立发布复审。R1–R3 的 TECNO 系统 ASR 限制裁定保持，不再请求系统资源或反复要求解锁。模拟器成功不替代实体设备验收，W3/R4/R5 和当前目标继续 open/active。

机器逐条对照：[JSON](w3-qwen-kv288-emulator-observed-20261111.json)。原件：`artifacts/verification/w3-qwen-kv288-emulator-20261111/`，包括 `kv512-comparison.json`、`device-report{,-confirm}.json`、`parent-arena-acceptance.json`、`allocator-policy-collected.json`、`restoration.json` 与 `emulator-stopped-confirmed.json`。新报告 SHA256 `c0dea0ab799229b952d6d089be986b77f9be3d9bd366da27db271a69834a8132`；原512报告 `696ee418d3de9c6fa7b51b8854ff163788e248495bb8d1d8728a826b1d5266b2`。
