# W3 CPU arena-off 单变量设备诊断合同

本合同准备一次新的 allocator 假设诊断；只有独立源码及父工具复核通过、`diagnostic-frozen.json` 完整绑定实际文件后才冻结。当前文档本身不是已执行证明。用户持续要求改进 W3 已覆盖该公开文件、隔离 alias 路线；不开启新录音、AiAi、模型下载或生产修改。

## 假设与停止条件

旧 FILE run `w3-qwen-file-20261111-01` 已消耗，在首片时采样 RSS 超过 1.5GiB，零完整片段、实际最终 decode 次数未知。新 run **`w3-qwen-arena-20261111-01`** 只请求关闭 CPU arena，使用不同 app/report 身份；不将改 ID 当旧 run 重试，不改旧报告，不提高预算，不扫参，不在失败后自动重放。

只改变实际 provider：`cpu:<framework canonical no_backup>/cpu-arena-off/cpu.config`，配置精确 20B `EnableCpuMemArena=0\n`。内存 pattern 保持 runtime 默认。三个模型 session、输入、顺序、音量、两阶段准入、CPU2、128mel、greedy、seed42、temperature1e-6、topP0.8、512/128 均保持。固定 AAR SHA `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；六模型及其既有摘要不变。

成功条件是整个固定文件合同及其资源/清理门通过。任何采样 RSS **>1610612736B**、系统 available **<536870912B**、load/单片 **>120s**、整批 **>7920s**、进程身份改变、观察失败或前台门失败均停止并只收取同 run；构造/stream/decode 最多 1/130/130。强停后不能把最后持久化计数当精确最终次数，不能补造 release/destroy ack。RSS 是采样最大值，未测 true peak。

## 固定输入与评分

65 份 PCM/manifest SHA `8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f`：20 中文、20 近场英文、12 AMI 远场、9 annotation-negative、4 全零输入。保持完整原幅度，无 VAD、增益、裁剪、后处理。auto 只判严格空准入；非空则 fresh stream 以 Chinese/English 请求语言识别。

52 speech 必须全部完成并非空，中文 CER≤20%、近场 WER≤20%、远场 WER≤35%；13 challenge 严格空。未完成只报告子集，不能判全量通过。annotation-negative 未经人工听审，不能外推所有真实噪声。Mac 的既有通过不替代手机。

## 实包与身份

- sourceFingerprint `d3d1a844ebd7e0c9cfe1347c194770e04bc60e11b7300bbee254cd9eb51ec9c1`。
- app/test `io.ondeviceai.acceptance.qwenarena[.test]`，runner `io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation`；Activity 保持旧全名且 non-exported。
- main.apk SHA `8e2d54922f613dccab409de537b348b77ec1aa6363966bec1f6f99bfdff9c4f6`；test.apk SHA `b8faa014904a352c02d78af1469b7f7dff1de18183f20fa9c233ae0b72f298ef`。
- 仅 TECNO KM9/API35/arm64，current user=0；安装前和提交前 fresh Awake/unlocked，无其他 instrumentation；运行前 available≥2GiB。遇安全锁屏等待用户解锁，不能绕过。

## 父端执行顺序

1. 冻结摘要绑定：新源码/实包、run-plan、生产保护清单、父端新旧设备/评分工具及检查、独立源码/父复核收据和本协议。缺项或字节变化一律阻断；文件内的 review 标记只用于门控实际已有证据，不授予外部权限。
2. `preflight` 只读身份与设备门；`prepare` 仅在门通过后安装精确两个新 alias、分块摘要核对既有六模型。记录普通 App 路径/原 Whisper 四模型摘要；同 UID `/proc` 可读是硬前提。
3. `run` 单次登记后只提交一次 instrumentation，以 PID/startTicks 绑定采样，复用旧父端预算监控。prepare 不调用模型；run 不请求系统 ASR/网络/麦克风。
4. `collect` 读取同 run 报告两次并与设备 SHA 对照，复核六模型与普通 App/模型；另从 canonical alias no_backup 取最多 21B 政策文件，精确 20B 内容/SHA 与实际 provider/receipt 绑定。报告始终 `nativePolicyEffectVerified=false`：文件/参数正确或 RSS 下降都不足以证明 ORT 内部设置读回或唯一根因。
5. 父评分深拷贝后仅将 baseline evaluator 的 provider 字面值适配；原始报告不改。实际 provider 另独立核定，政策文件收取失败不能验收。继承完整覆盖、计数、错误、清理、固定阈值检查。
6. `restore` 只卸本次精确 alias，按分块传输清单核定并清除本次 staging，普通 App/模型摘要保持，实际恢复普通 Demo 前台；保留本机源码/APK/报告/raw/失败证据。collect 中途失败先查同 run、续取或恢复，不再运行识别。

本机根：`artifacts/verification/w3-qwen-arena-wrapper-20261111/`。父入口为 [device_acceptance.py](../../probes/android-qwen-arena/device_acceptance.py) 和 [verify_report.py](../../probes/android-qwen-arena/verify_report.py)；设备未过门时，不调用 run。即使本文件诊断通过，生产通知保留、运行时传递性许可、发布复审和其他适用验收仍独立；W3 不由本协议直接关闭。
