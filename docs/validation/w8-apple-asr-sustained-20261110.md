# W8 Apple FILE ASR 持续观测 — 2026-11-10

**单批物理观测已验收并恢复；质量、GPU、能耗、true peak 和生产预算没有通过结论。**
此项补齐 W3/R5 所引用的 Apple 文件 ASR 持续观测，不关闭 W3 或 W8 整包。

## 冻结协议与真实结果

目标为 iPhone17,2 / iOS 27.2.1，独立 bundle
`io.ondeviceai.probe.w8appleasrsustained20261109`，实际 PID **942**，唯一 run
`4EEA3B34-370C-4830-98B3-A892EE2F8C6B`。固定 LibriSpeech 三份公开文件、revision、大小和
SHA-256 与原协议不变，和仓库既有夹具逐字相同；en-US / Apple backend / on-device requirement。

20 个周期中分别创建新的实际 backend/client，每周期两次 measured recognition，首周期另有
一次 excluded warmup；每周期实测至少 30 秒。原测量函数、采样器、生命周期和释放逻辑逐字保持。

| 指标 | 本批观测 |
| --- | ---: |
| 完成周期 / 成功测量请求 | 20 / 40，全成功 |
| excluded warmup | 1 |
| 总 monotonic 时长 | 626.211590 s |
| 实际 paced idle | 593.928 s |
| 报告 activeWork | 10.036 s |
| 40 次 recognition duration 合计 / 中位数 | 9549.142 ms / 189.321 ms |
| p95（nearest rank）/ 最大 recognition duration | 434.798 ms / 533.311 ms |
| measured RSS 样本数 / warmup 样本数 | 898 / 37 |
| measured RSS 最大采样值 | 113082368 B，约 107.84 MiB |
| 每周期 close 后立即 RSS 范围 | 112623616–113098752 B |
| 最终进程 RSS（立即及 +1s） | 均 112869376 B |
| 麦克风启动 / 生命周期中断 | 0 / 0 |

总时长包含 paced idle、每周期 close 后的 1 秒等待、校验/报告及调度开销；不能用 activeWork
加 idle 代替所有 wall 时间，也不能称连续解码 10 分钟。recognition duration 不是用户端到端延迟。

RSS 只在识别调用期间采样，量化同一 App PID（包含 UI）；**没有测系统 Speech 服务内存或 idle
期间连续 RSS**。min/max/count 对实际采样精确，reservoir quantiles 为近似值，sampled max 不是真峰值。
每周期 close RSS 和最后 process RSS 分开。backend/client 新建、drain/close 和 post-close hash 路径
由本次冻结源码及成功完成路径绑定，没有额外独立的释放追踪。API 要求 on-device，未测网络隔离。

## 前置修正、验收与恢复

实包预检发现原生成工程没有 AudioFixtures，且 Info.plist 缺 source identity/Speech usage。
父用仓库现有 folder-resource 方式和显式 Info.plist 修正包装；免费 hy3 35.900s 交付独立授权
准备 UI，唯一替换 App struct。无新依赖、模型、生产源码或测量循环修改。

真实 UI 先显示 notDetermined / Run disabled；仅准备 Speech 授权后 authorized / Ready。
未请求麦克风权限，也未用一次额外识别来授权。一次点击异常返回 Combined；同 PID 仍 Ready、
0%、Run enabled、报告为空，结合 run() 同步 guard 确认零批次。重新激活同 PID 并读取界面后，
才开始这唯一真实批次；具体 UI 路由原因未证，原观察保留，不是识别重放。

实际 iPhoneOS 构建、三 WAV/manifest/metadata 和既有 profile/签名核验通过；签名前后 Mach-O
sections 保持。初次 build_device 参数验证失败和首次缺资源构建/副本均保留，未装机运行缺资源包。
**428 项父检查通过**，两次终态原始回读逐字相同，SHA-256：
`1d6f4185cee55521f0f87d4a4d9077a4a3a245af552774ccfebf63dfd02b5862`。
10 份生产 Swift 源及受保护文件的前后 SHA 保持；原 native reviewer 429 没有重试，不冒充独立批准。

停止 PID942 后只卸载本次 alias。安装前后/恢复清单显示 44 个原 App 的完整记录和安装路径相同，
已返回普通 Combined，XcodeBuildMCP 恢复 default profile。未操作普通 App 的模型目录；本批没有
重新校验普通模型内容 hash，不把安装清单一致说成模型逐字证明。没有资源下载、TTS/Sherpa 重跑或新采集。

[源码、协议和可跟踪收据](../../probes/ios-file-asr-sustained/README.md)已归档；原始报告、构建、签名、
完整父检查和恢复在本机 `artifacts/verification/w3-apple-asr-sustained-20261110/`，不保证随 Git 分发。
source identity 为 `sha256:4bcf5c6764ed9ffd9ebf21d6d2898fe2a72a47dd28bf48368519c776c434cc35`。

此批不再重放。质量沿用 R4 真实失败和分数，资源预算因没有批准阈值仍 unverified；不由观测倒推通过线。
