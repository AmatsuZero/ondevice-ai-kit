# AppleVideo 音轨保留验证

2026-10-21。实现 `0902e8e`（真实AAC passthrough）、`26dcd0f`（默认preserve引擎接入）、`7dcb25d`（审查后视频时间线复核）。规格/计划见 `docs/superpowers/{specs/2026-10-21-apple-video-audio-design.md,plans/2026-10-21-apple-video-audio.md}`；ledger `.superpowers/sdd/2026-10-21-apple-video-audio/progress.md`。

在既有320×180/24fps/1…48帧/BT.709 SDR→4×HEVC1280×720 profile下，默认preserve现支持无音轨或单条AAC（44.1/48kHz、单/双声道）。音轨须零起点、与视频等长、无空白编辑段；不兼容格式、多轨、超长/偏移/空白编辑明确拒绝。discard继续忽略音轨。基础iOS15/macOS12不变，真实SR仍仅非模拟器iOS26+、模型ready；不下载模型。文件必须在处理及取消drain结束前保持不变。

## 验证证据

日志：`.superpowers/sdd/2026-10-21-apple-video-audio/`。忽略产物：`artifacts/verification/apple-video-audio-20261021/`。

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Task1 真实音频封装 | missing mux RED；初版7/7、Swift445/445 | `task-1-red.log`, `task-1-green-final.log`, `task-1-full.log` |
| 默认preserve client/backend | 旧实现拒绝RED→GREEN；Swift446/446 | `task-2-red.log`, `task-2-full.log` |
| 审查补充输出视频验证 | missing validator RED→GREEN；mux8/8、Swift447/447 | `review-output-video-red.log`, `review-output-video-green-full.log` |
| Apple 总门禁 | passed，包含host/KMP/桥接/消费者/Demo | `task-3-apple.log` |
| 工具全套 | 79/79，RUBY3.4.11 | `task-3-tools.log` |
| 修复后fresh分发 | 9 XCFramework，device/simulator归档通过 | `task-3-package-reviewed.log`, `distribution-reviewed/` |
| Core+Video-only binary consumer | iOS15 target设备/模拟器编译链接通过，无AppleVideo/AVFoundation依赖 | `core-video-consumer/*/{build.log,dependencies.txt}` |
| optional VT symbols | device undefined项weak，模拟器无VT专用符号 | `core-video-consumer/*-symbols.txt` |
| device/simulator harness | 现有签名device build/install通过；unsigned simulator build通过 | `task-3-{device-build-reviewed,device-install,simulator-build-reviewed}.log` |

host测试使用本地ffmpeg生成的色块/440Hz合成AAC，不下载/录音。完整AVAssetReader读取压缩AAC、跳过无block的marker样本后比较payload；无音轨输出字节不变；PCM/多音轨/超长/偏移/非法duration、已有输出保护、预取消与不可读输入均覆盖。输出复核拒绝错误时长或非HEVC1280×720视频。44.1kHz/双声道属于format admission支持，当前真实passthrough与设备fixture为48kHz单声道，未宣称该组合完成设备验收。

## 真实设备

### 2026-10-28 原生 mux 导出阶段取消

使用 `probes/apple-video-export-cancellation/` 生成独立诊断 App，仅复制当前
`AppleVideoAudioMuxer` 并在真实 `AVAssetExportSession` 外层增加状态观察；生产
源、Demo 工程和签名配置均未修改。首轮 iPhone 16 Pro Max / iOS 27.0.1 报告
`runId=E5DC6A49-E240-4A04-A6B6-BAC7AAF40DE8`，使用仓库原始 7,464 字节视频，
3 轮取消点 `progress=0`；报告见
`artifacts/verification/video-export-cancellation-20261028/device-report.json`。

同一进程的3轮导出均观察到 native status `waiting → exporting → cancelled`（完整原始枚举
`0,1,2,5`）；3 次调用方均收到 `CancellationError`，export 返回时输出不存在，
source/processed video SHA-256 不变，随后同进程重入均为 1 条 AAC 且 payload 相同。
主机负向对照移除取消调用后以 native `completed=3` 被验收器拒绝，说明检查器没有把
正常完成误判为取消。

随后进行一次有界的非零进度专项。先在主机使用 31,608,443 字节、1280×720、
24 fps、2 秒 HEVC fixture 连续 3 轮通过，再清理独立 DerivedData 重新签名构建；
包内 manifest、AAC、视频 SHA-256 与诊断目录逐项一致。当前真机报告
`runId=8163CFEA-3940-4272-A5C4-267C858BE2DF`，原始 JSON 为
`artifacts/verification/video-export-nonzero-20261028/device-report-current.json`，
SHA-256 为 `a4f323525b592b7a92945a80ecf9821c63b6fe27f94c870ef0d2e22e14cc0b6a`。

三轮均在 native `exporting` 状态且 `progress>0` 后触发取消：进度分别为
`0.0746666640`、`0.0533333346`、`0.0533333346`，当时输出已存在且大小分别为
1,347、943、943 字节。调用方收到 `CancellationError` 时三份部分文件均已删除，
native 终态为 `cancelled`，输入哈希不变；随后三轮同进程重入均成功生成 1 条 AAC，
压缩 payload 与源相同。由此关闭 Apple mux 的“非零导出进度部分文件清理”子项；
这不覆盖 Android 后端、自然质量、GPU/能耗、持续资源或最低系统设备验收。

本轮诊断构建使用SDK文档仍支持的KVO status读取，存在status/progress及旧reader API的
deprecation warning，不声称 warning-free。设备结果以取回的 JSON、内嵌 manifest
SHA-256 和签名包资源哈希为依据。独立 Video foundation/AppleVideo 审查恢复仍因429
没有结论，不计 APPROVE。最低系统设备验收按用户指示暂缓；基础里程碑10/12、P4/P11
开放。

### 2026-10-21 原始 SR→mux 设备验收

iPhone16ProMax / iOS27.0.1 (24A446)，PID19288，runId `B06660C7-6E4D-4F13-AAED-F8DC83D2494F`。固定公开视频加本地440Hz/48kHz AAC；新输入SHA256 `6a1c2a7bb875c8d9502450e732901fcd21bca5be27da0ef6ceae58c5b64c0d00`。只读取测试bundle fixture；沿用本项目视频probe的已有签名，不变更签名资产或原Demo工程。

`task-3-device-run.log`与导出`device-results/result.json`均记录`state=success`：

- 首轮48帧/1280×720/2秒/1条音轨；首轮lease release后清理。
- 第1帧后取消，终态cancelled且workspace无残留；同进程重入48帧/1条音轨。
- 无音轨固定fixture的默认preserve成功，48帧、audioTrackCount=0。
- 已交付输出跨close存在，随后release；关闭拒绝新任务、输入未变、最终workspace与初始一致。

仅精确导出本次报告与输出至`device-results/`。`ffprobe.json`、`frames.json`与`validation.json`确认：单视频轨HEVC1280×720/48帧/24fps/2秒/BT.709、PTS从0连续；单AAC48kHz单声道、起点0、时长2秒。ffmpeg全文件decode exit0。输入/输出提取ADTS压缩音频逐字节相同（17036bytes，SHA256 `24da16409aeebae60c91d5a93be310772d69c86f6b13b7fce00e863e567d1fa4`），未重编码。console collector在success后结束，exit130只表示collector停止，不替代JSON/媒体结果。

## 审查、取舍与剩余项

本包独立code-reviewer完成15文件审查，1MEDIUM：导出后未复核视频timeRange，结论REQUEST CHANGES。按交付metadata可能失真归为Important；`7dcb25d`已加入视频轨数、HEVC/尺寸与零PTS/时长（2微秒容差）验证，真实媒体规则测试RED→GREEN，全套447/447。按执行流程未另做复审，不宣称APPROVE。原Video Foundation恢复审查仍capacity失败，VT首包恢复审查429，均没有独立结论。

实测偏移AAC含leading empty edit，composition passthrough导出时长变化约81微秒；设置naturalTimeScale未消除。首版因此提前拒绝偏移/空白编辑/非等长音轨，保留精确同步门槛；代价是更窄的输入兼容性。无音频重编码、裁剪、拉伸或静默降级。AVAssetTrack.asset为weak，asset与轨道一同保留到实际export结束。Apple SDK async export overlay用Task cancellation handler取消并await终态回调，之后才清理输出。

更广音频、SR传递函数/质量/峰值资源及Android生产后端仍未完成；最低系统设备验收暂缓。KMP bridge/Demo已在后续包接入，2026-10-25的真机UI结果见[Video bridge验证](video-bridge-demo.md)。P7原始探针停止点已完成，扩展交付仍开放，基础里程碑10/12、P4/P11未关闭。本轮仅提交诊断probe与验收记录，不发布制品。
