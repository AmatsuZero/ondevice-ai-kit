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

尚未可靠触发并单独观察native export阶段中途取消，不能拿第1帧SR取消或预取消冒充；其系统取消/完成边界有SDK实现依据，设备专项仍未验。更广音频、SR传递函数/质量/峰值资源、最低系统、Android与KMP/Demo仍未完成。下一本地包为KMP Video bridge与Demo接入，P7保持部分完成；无远端push/制品发布。
