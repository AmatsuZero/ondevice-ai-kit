# W3 补充模拟器文件比对观测（w3-qwen-arena-emulator-20261111-01）

## 范围与性质
本文件是 W3 语音任务的**补充模拟器证据**，非 W3 完成、非 R4/R5 验收、非手机验收、非独立 held-out 确认。它在新环境（Android36 模拟器）上重放原始固定语料，仅证明模拟器工程质量门在固定语料上通过。结论仅适用于模拟器/固定语料。源分支未推送。

## 运行配置（未变更）
- 原始 Arena APK/源码未变；sourceFingerprint `d3d1a844ebd7e0c9cfe1347c194770e04bc60e11b7300bbee254cd9eb51ec9c1`；同六模型/四 arm64 库。
- 环境：Android36/default/arm64-v8a revision2；隔离 `W3_Qwen_Arena_20261111` / `emulator-5580` / 4096MiB / 2CPU / no-window / no-audio / no-cameras / no-snapshot。
- 参数：CPU2/128mel/seed42/temperature1e-6/topP0.8/maxTotalLen512/maxNewTokens128/greedy/auto admission -> requested-language pass/arena-off/memory pattern 默认。
- 无镜像/模型/运行时下载、无麦克风/AiAi/网络请求、无生产/默认服务/普通 App/模型变更。
- 新冻结 327 文件 SHA `4d6b8ba92805e381347c4a98b8a787a3f4e38cb606f73745c494f0b006184102`；终端报告 SHA `696ee418d3de9c6fa7b51b8854ff163788e248495bb8d1d8728a826b1d5266b2`。

## 输入与打分
- 65 个有序公开输入全部完成：20 普通话、20 近场英文、12 AMI 远场英文、4 静音、9 标注阴性会议间隔噪声。
- 同 65 ID/PCM 哈希/样本/区域/参考；原始 NFC/casefold/空白打分器保留标点与数字。
- 9 噪声片段为标注阴性，未做人工听审。

## 质量门结果（仅模拟器/固定语料）
| 指标 | 结果 | 门限 | 通过 |
|---|---|---|---|
| 普通话 CER | 40/590=6.78% | ≤20% | 是 |
| 近场英文 WER | 37/375=9.87% | ≤20% | 是 |
| AMI 远场 WER | 64/226=28.32% | ≤35% | 是 |
| 静音/噪声严格空 | 13/13（4 静音、9 噪声） | 严格空 | 是 |

实际 1 个构造器、117 条流、117 次解码，终端计数 52 条 speech 非空、13 条 challenges 空。构造 1.917291501s，识别器释放 0.065306042s（显式释放与 Activity 拆除确认）。主机 145.463517292s、574 RSS 采样、采样峰值 1539006464B≈1.433GiB、最小可用 1466806272B；stopReason=null、terminalObservationGap=null。原始门限（1.5GiB RSS/512MiB 可用/各 120s/批 7920s）未变。逐案墙钟 RTF 0.1061303042…0.5106159261；既非 warm 亦非 sustained，亦非手机/功耗/真峰值证据。18 项父执行检查全 true。

## 跨环境比对（非因果）
| 集合 | 模拟器 | Mac 原始 | 一致原始串 |
|---|---|---|---|
| 普通话 | 6.78% | 7.12% | 16/20 |
| 近场 | 9.87% | 8.53% | 14/20 |
| 远场 | 28.32% | 27.43% | 7/12 |

同 24 个已完成的 TECNO 文件原始串与模拟器完全一致：普通话 20/20、近场 4/4，近场子集 WER 9/67=13.43%。TECNO 无远场/挑战覆盖，不可将其 4 项近场率与完整 20 项混比。跨环境差异不证明运行时唯一成因。`nativePolicyEffectVerified=false` 保持 false。

## 采集恢复（只读）
首次采集在报告双读与六模型后哈希后，于冻结父文件 line130 断言 `collector.count(old)==1` 失败：完整堆源码含两处精确报告目录字面量（prior_collection + 未用 main REPORT 赋值）。保留原 327 冻结/适配/APK/报告与 stderr。独立只读 `collect_policy_report_path.py` 仅改这两处字面量后重绑新 OUT/RUN/APP/带引号 adb，仅调用 preflight+collect_policy，无模型重放。三个测试用例的一次离线回归加一次同 run 只读续取成功；同运行报告双读/当前报告 SHA/APK/六模型绑定、精确 UID/device/inode，bounded21B 返回精确 20B 且 SHA `df3e015e389b92d6c5b60d02bef3023485693ee0186c872cf3ce2c5b5dc7470b`。

## 边界（未证明）
- 非手机验收、非功耗/峰值、非独立 held-out、非原生策略有效性。
- 免费 hy3 运行前静态审阅接受/无发现，但仅审阅局限，非审阅者观测到运行时执行，亦非完整运行后批准（漏掉适配计数错误）。
- 原始物理 arena108 哈希与物理 heap120 哈希完好；原 TECNO 运行已耗/失败 24/65；物理堆 `w3-qwen-heap-20261111-01` 未耗、未准备/未启动/未采集。
- W3/R4/R5 仍 OPEN；本项为补充证据，不请求用户接受 R4/R5。

## 证据
原始证据见 `../../artifacts/verification/w3-qwen-arena-emulator-20261111/`（本地忽略证据，非 Git 交付）：diagnostic-frozen.json、independent-review.json、host-result.json、device-report{,-confirm}.json、collection.json、allocator-policy-collected.json、parent-arena-acceptance.json、cross-platform-comparison.json、collect.stderr、policy-continuation-frozen.json、restoration.json、emulator-stopped-confirmed.json、cleanup-receipt.json、observed-summary.json。

父端验收补充：37 组定向检查（4 组模拟器、3 组续取回归、30 组原父检查）通过；完整 tools 691 项通过。原测试发现目录错误的零测试结果及 Ruby 2.6 混用造成的 41 项失败均保留，仅将测试子进程切到已安装 Ruby 3.4.11 后重验，未修改测试或全局环境。
