# 当前 Android 候选 TTS 真机验收（2026-11-07）

冻结产品源码 `636a6eb8854db5dff74244c42c3ce0e82bd2be9f` 的同一主 APK，
在 TECNO KM9 / API35 完成一次固定英文 TTS 生命周期验证。正常播放、活动取消并等待、
取消后重入、关闭与关闭后拒绝均通过。原 App/test/runner 和私有状态已恢复，临时文件已清理。
本项关闭当前修复的 TTS 真机终态缺口；基础仍 **10/12**，P4/P11 open，release not-ready。

## 构建与身份

- 复用 [当前候选](android-current-candidate-20261107.md) 的原始主 APK，未重建或重签。
  456,350,170 B，SHA-256 `dfccaa95a809b4f37b8d384cd32f92c37c7422178803dc1ff5d44a88654879a7`。
- 新副本保留 1046 份原源码字节，仅用既有 `probes/android-tts-report/generate.py` 生成测试 overlay，
  将其中预期完整 commit 从旧提交绑定到本次冻结提交，不改原探针或生产源码。
- 仅执行 `assembleDebugAndroidTest`，离线 397 个任务实际执行，35 秒构建成功；
  没有重复主 APK 构建、599 项 JVM 测试、ASR/SR 或 Apple 已完成验收。
- 测试 APK 202,774 B，SHA-256 `aabc770b0c5a3d86efdf3d13a2963549e16c96a953b03ce85137ce305ce2eef7`。
  Manifest 的目标包、PersistentTtsProbeInstrumentation、DEX 中完整 commit 和主/测试签名兼容性均核验。

## 同一次原生运行

两个独立 `recorder-pass` / `recorder-fail` 自检均符合预期，且被实际 TTS 校验器拒绝为推理成功。
随后仅发起一次实际 TTS：`current-tts-tts-20261107-636a6eb`，PID 26132。

主机结果流退出 255；第一次回读只到 `normal-start`，原始非终态已保留，未重发。
同一 runId 第二次回读取得终态，十个阶段顺序完整：

`started → normal-start → normal-done → cancel-request completed=false → cancel-joined → followup-done → close-start → close-done → close-rejected → finished`。

原生固定诊断为 `probeStatus=pass`、`sdkStatus=success`、
`sdkCancellationStatus=cancelled-followup-success`、`sdkAfterCloseStatus=closed`。
实际后端 `android.text-to-speech`，Google TTS 引擎，voice `en-US-language`，
临时合成 WAV 为 129,404 B；没有麦克风、录音、新媒体采集或下载请求。

终态原始字节 SHA-256：
`916ba565b4b1d17d4730dcd82de39277200bd1a3e0d86328cbdcd712cf903541`。
收集前后设备摘要、原始字节摘要相等；恢复前独立再次回读并保留同一摘要。
报告绑定完整提交及 runId，发起和收集均核验精确主/测试 APK，终态时无活动 instrumentation。

## 传输、恢复与助手

沿用已验证的字节计划，手机原安装包加 9,849,005 B literal 精确还原主 APK，
仅上传两份不超过 8 MiB 的 literal、小测试包及脚本。新协议预先使用 `set -C` 的禁止覆盖复制，
复制前后均核验完整 SHA，重建退出 0，没有再走设备不支持的硬链接。安装前全部四份
候选/回滚/原测试/TTS 测试 APK 的本地、收据与远端全量摘要相符。

34 项离线协议检查通过：恢复 16、传输 9、观察 5、TTS 身份及一次发起边界 4。
其中新增三项实际 shell 检查覆盖禁止覆盖已有文件、损坏 partial 拒绝及成功发布；
四项既有仓库 TTS 报告测试也通过，分开计数。观察性 USB 断连只复读同一设备，
安装/重建/实际 TTS 不因丢失响应自动重放。

cbc hy3 对两份收集/校验脚本的限定审查自然完成，用时 295.361 秒，完整 JSON、零工具事件。
第一条缺文件 IndexError 的推断不符合实际 `d.adb` 的 check_output 错误传播；
第二条是直接存在性探测遇断连会明确拒绝的可用性限制，不会误判不存在或通过。
父代理核对后未改源码，保留原意见与处置；本结果不计生产架构、整分支或发布批准。
助手证据在 `.omx/artifacts/cbc-current-tts-collector-review-20261107/`。
Qoder 最新 403、原生 429 等失败线路均未重启，三个既有待答复项未重复询问。

原 `afed156b…` App、原 `7b9e47ce…` test、Identity runner 已恢复；未运行原 Identity instrumentation。
25 项私有模型/journal 摘要、四份 SR 文件、相机/麦克风权限、默认 TTS 值及服务清单前后一致。
私有报告先独立留存，再精确删除；四份暂存 APK、literal、脚本和重建元数据按摘要清理，
唯一暂存目录确认空后删除，WAV 不存在，普通 MainActivity 已返回。
九份受保护路径和生产 Apple lock 保持，无清数据、卸载、上传或 push。

## 证据与停止点

本机忽略目录 `artifacts/verification/android-current-tts-20261107/` 保存 110 份冻结证据文件、
原始报告、构建/身份/协议收据、恢复保全副本与可只读复核的 `freeze.py`。
`summary.json` SHA-256：`d54653039f7e3878711849a3b7f0da58d849c0fbd80e83547a49e9710db91076`。

```sh
rtk proxy python3 artifacts/verification/android-current-tts-20261107/freeze.py --verify
```

该命令已通过，重新核对精确 APK、1046 源码、overlay、原始终态、恢复、保护文件与 lock，不操作设备。
本项不新增 benchmark 样本；P11 仍 7 项能力/10 组。自然语音质量、统一资源/能耗预算、其他未验能力、
独立架构审查、交付及 F10 校准仍按原退出标准推进，最低系统设备保持 deferred/unverified。
不要重跑本次构建、自检或 TTS；下一步推进其他尚未证明的质量/资源或交付门槛。
