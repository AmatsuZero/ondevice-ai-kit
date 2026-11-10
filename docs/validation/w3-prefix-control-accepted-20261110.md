# W3 固定 1906 前缀判别对照 — 2026-11-10

状态：**单项诊断已完成；W3 / qualityVerdict 仍 open / unverified**。

用户在本轮明确批准主代理本地一次性修正两份隔离 Kotlin 探针。没有外发源码、
调用云端助手、重试 429 native reviewer，或修改生产 VAD、模型、依赖和 scorer。
旧 `/private/tmp` 两份完整候选现已不存在；仓库保留的原样 Plan/serializer 提取、
两份无效 JSON 和原摘要收据继续保留，不声称旧候选原件完整可用。

## 输入与结果

固定 `google/fleurs` / `cmn_hans_cn/test` / revision
`70bb2e84b976b7e960aa89f1c648e09c59f894dd`，CC-BY-4.0。
clip 1906 第二 slice 为 `[83040,166080)`，16 kHz / mono / int16 LE。
同一 TECNO KM9 / API35、Sherpa ONNX 2.0.1、tiny int8、CPU / 4 threads、
zh-CN → zh；构造 en 后 setConfig zh，与原 production/direct-native 方式一致。

| 两臂顺序 | slice-local crop | 实际 PCM SHA-256 | 实际输出 |
| --- | --- | --- | --- |
| baseline | `[2432,36480)` | `3488fa8276e5fd655b23ebcc99e8d4c5fe49808580d90d9ccdf80731a8d7c5a3` | 也是新天章的开始 |
| prefix restored | `[0,36480)` | `8e408ea77635aadf6e77dae482d3e3149a78461de587010e18aefab0547ca7c8` | 结束也是新天将的开始 |

两臂共享后缀逐字节相同；独立主机 int16→float32LE 转换摘要与设备逐臂匹配。
baseline 文本与既有 SDK 第二 slice 输出完全相同。
**恢复 2432 samples / 152 ms 前缀会改变本局部输入输出**，不支持将该前缀视为
对识别输出无影响。只做了这一个判别，没有扫 pre-roll、prompt 或模型。

整片 canonical reference 保留两端引号，UTF-8 SHA 为
`6f20dd84a6c686c60507428e037063762c5a15bb7e9e9777c1047049b62a9152`。
没有独立时间对齐的 crop reference，没有给局部 crop 算 CER，也没有重算整片 CER。
该对照不证明整体质量改善或模型根因；原 Apple **16/92**、Android **44/92**，
no-VAD **53/92** 和 base 拒绝结论不变。Apple 既有整片/Android 分段及后端差异仍在。

## 验收证据

本机证据根：`artifacts/verification/w3-prefix-control-20261110-local/`。

- 原样留存证据复现 3 RED：reference 漂移及 success/failure 无效 JSON。
- 修正版实际纯 Kotlin 逻辑通过 **62 host assertions**；含四份真实模型正向、实际 token
  文件同大小篡改/截断、缺文件、crop/suffix/reference、构造/解码/释放/持久 checkpoint
  失败。释放状态在成功返回后才置 true，后臂或清理失败保留先前 hypothesis。
  10 个 success/failure/escape JSON 严格解析通过；主机检查没有 JNI 推理。
- 独立隔离 Android 项目离线 build + lint 通过，**75 tasks executed / 27.126s**。
  lint 无 error，保留 8 warning（target/依赖版本、ChromeOS ABI、backup/icon/i18n）；
  不因此改依赖、API 或产品。APK 签名和全部四份 native library 摘要核验通过。
- 首 run `w3-prefix-20261110-local-01`：设备 Asleep，线程实测 `do_freezer_trap`；
  同 PID 唤醒后终态 `foreground interrupted`，**零模型校验/零解码**。普通 App 恢复。
  一次 shell 启动非 exported Activity 被系统拒绝；原错误保留，没有放宽 exported。
- 用户确认已解锁后，明确准备新 run `w3-prefix-20261110-local-02`；同一 APK/输入/模型。
  提交前 Asleep gate 曾阻断一次，**未提交 instrumentation**；唤醒并确认 keyguard
  showing=false 后仅提交一次。该 run 两次顺序真实 direct-native decode 完成，
  两 stream 和 recognizer 实际释放，前台 teardown/数组清零完成。
- **97 parent checks** 通过，原始报告两次回收字节一致。实际设备读取的主/测试 APK、
  JNI runtime 摘要与 host 绑定；四份模型逐文件实际读取核 size/SHA 后才进入 JNI。
  原始报告 SHA：`6b23710526d8c8d8dbde78075cff6d70f419fa7dd898b0f361ae4e7a2bba6d74`。
  证据为 `parent-acceptance.json`、`run02/device-report{,-confirm}.json`、
  `run02/collection.json`、两次 `restoration.json` 和保留的原始失败观察。

两次仅移除当次新别名及 manifest 绑定 staging，普通 App 路径和所检查的四个
ordinary-model 路径状态/摘要未变，已返回普通 App。实际录音次数和 VAD 调用为 0。
没有重跑 W8 已验 TTS/Sherpa 资源测量。native 独立复审仍缺：原 reviewer 429 终态未重试；
以上是主代理验收，不冒充独立 review 或整包发布批准。

后续按 R1 系统麦克风前置 → R2/R3 实际开始提示与场景 → R4 代表性真实噪声固定合同推进。
不得将本子项完成写成整包 W3 关闭。
