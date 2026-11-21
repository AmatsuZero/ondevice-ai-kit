# Apple VideoToolbox 后端验证

当前后续包已支持有界AAC音轨preserve，见[音轨验证](apple-video-audio.md)；下文discard-only为首包历史验收边界。首包独立审查恢复仍429失败，无独立结论。

日期：2026-10-21；引擎提交 `e996d5d`，取消竞态修复 `51d98bf`，分发/验证改动随后提交。独立 reviewer 因模型容量返回 `Selected model is at capacity`，没有审查结论，保持 pending。

本包把 `apple.videotoolbox.sr` 接入 `VideoProcessingClient`，只支持非模拟器 iOS26+、VideoToolbox model status `ready`、固定 320×180/24fps/≤48 帧/BT.709 SDR 输入、4×和显式 `discard` 音轨策略。输出为后端临时 UUID 目录中的 1280×720 HEVC/MOV lease；调用方释放 lease，client.close 不删除已交付结果。没有模型下载、私人媒体、整文件 hash（验证 harness 的输入摘要仅用于证明输入未变）、云端或 Android 后端。

## 官方和 SDK 边界

来源：Apple [VTSuperResolutionScalerConfiguration](https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration) 官方文档 JSON，以及本机 `iPhoneOS.sdk/System/Library/Frameworks/VideoToolbox.framework/Headers/VTFrameProcessor*.h`。头文件确认：配置 API iOS26+且排除 simulator；必须先检查 `configurationModelStatus`，下载是显式 API；source/destination attributes 需合并；process 返回/回调前不能修改 buffer；所有帧后必须 `endSession`。模型 transfer function 仍未由 Apple 明确指定，报告保持 unverified。

## RED→GREEN 与本地构建

日志目录：`.superpowers/sdd/2026-10-21-apple-video-backend/`；忽略产物：`artifacts/verification/apple-video-backend-20261021/`。

| 门禁 | 结果 | 证据 |
| --- | --- | --- |
| Task1 workspace/lease | 3/3 | `task-1-red.log`, `task-1-green.log` |
| Task1 Swift 全套 | 429/429 | `task-1-full.log` |
| Task2 backend/client | 5/5 | `task-2-red.log`, `task-2-green.log` |
| Task2 Swift 全套 | 434/434 | `task-2-full.log` |
| Task3 profile | 3/3 | `task-3-red.log`, `task-3-green.log` |
| Task3 Swift 全套 | 437/437 | `task-3-full.log` |
| 取消后晚到 native error | 回归先失败再通过；修复后Swift438/438 | `review-cancel-error-red.log`, `review-cancel-error-green-full.log` |
| 基础架构交叉编译 | iOS15 与 iOS26 arm64 Swift build exit0 | `task-3-ios15-build.log`, `task-3-ios26-build.log` |
| 分发工具 | 6/6 target/dependency tests；工具全套79/79 | `task-4-green.log`, `task-4-tools.log` |
| Apple 分发 | 修复后fresh 9 XCFramework | `task-4-package-cancel-fix.log`, `.../distribution-cancel-fix/xcframeworks/` |
| 修复后工具全套 | 79/79 | `task-4-tools-cancel-fix-ruby34.log`，RUBY指定3.4.11 |
| 修复后Apple总门禁 | passed | `task-4-apple-cancel-fix.log` |
| Core+Video binary消费者 | iOS15 target设备/模拟器编译链接通过，未链接AppleVideo | `.../core-video-consumer-cancel-fix/{iphoneos,iphonesimulator}/{build.log,dependencies.txt}` |
| optional VT符号 | 设备11项VT相关符号，undefined项均weak；模拟器0项 | `.../core-video-consumer-cancel-fix/*-apple-video-symbols.txt` |
| 独立设备 harness build/install | 修复后signed iPhoneOS build/install通过；unsigned simulator build通过 | `task-4-device-build-cancel-fix.log`, `task-4-device-install-cancel-fix.log`, `task-4-simulator-build-cancel-fix.log` |

## 真实设备固定 fixture

设备：iPhone 16 Pro Max，iOS 27.0.1 (24A446)，修复后 PID 19252，runId `6BFB16C9-8000-44AF-9018-BD0F72F155CD`。输入是既有公开 Common swifts BT.709 fixture，SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`，只复制到 harness bundle，不读取用户 Documents。

`task-4-device-run-cancel-fix.log` 及导出 `device-results-cancel-fix/result.json` 的 JSON：

- availability `available`；首轮输出 48 帧、1280×720、2 秒；首轮 release 删除后端临时输出。
- 取消在第 1 帧后发生，`cancelled=true`、`cancelNoResidualWorkspace=true`。
- 同进程 reentry 再次输出 48 帧；close 后已交付 output 仍存在，随后由 harness release。
- `closeRejectsAdmission=true`、`inputUnchanged=true`、最终 workspace 与初始一致；整体 `state=success`。

导出产物在 `artifacts/verification/apple-video-backend-20261021/device-results-cancel-fix/`：`result.json`、`output.mov`、`ffprobe.json`、`frames.json`、`validation.txt`。ffprobe确认单视频轨HEVC、1280×720、48帧、24fps、2秒、BT.709三项元数据、无音轨，PTS从0连续；ffmpeg全文件decode exit0。console collector在完整success报告后手动结束（退出130），套件结果由导出JSON及媒体复核证明。

宿主全套与设备成功不能证明最低系统可装载；iOS15 target编译与weak符号属于静态证据，最低系统设备验收仍未执行。工具第一次误用系统Ruby2.6导致json3.1语法错误，显式RUBY3.4.11后全套79项通过；未修改依赖。

这不是质量基准：未建立 SR 模型 transfer function、逐帧主观阈值、音轨 preserve、HDR/VFR/旋转/多轨、峰值 GPU/系统内存或最低系统结论。设备本次使用现有签名配置构建测试 App，没有修改签名资产或原 Demo 工程。

## 后续边界

P7 仍未完全收口。下一包应补充 AppleVideo 的独立审查、音轨 preserve（若可安全支持）、更完整的 profile/错误注入和必要的 KMP/Demo 接入；Android 模型、最低系统、质量/性能 benchmark 仍独立。当前后端只为固定实验 profile 提供可复核处理，不能称为通用视频超分 SDK 或发布版本。
