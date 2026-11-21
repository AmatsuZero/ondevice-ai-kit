# iOS ASR 固定文件探针

日期：2026-10-17。范围属于 P6；不新增 SDK 公共 API，不代表麦克风识别或 KMP Demo 已交付。

## 输入与调用约束

使用本机系统 Samantha voice 合成固定英文句子 `The blue bicycle is beside the green house.`。音频只生成至忽略目录 `artifacts/P6IOSASR/fixture/asr-fixture.caf`，不追踪或上传音频。首次生成后保留同一文件以冻结输入；报告记录实际 SHA256、帧数、采样率、通道。该合成短句只验证文件 ASR 链路，不代表自然语音的 CER/WER 质量。

先执行：

```sh
sh tools/probes/prepare_ios_asr_fixture.sh
sh scripts/check-ios-asr-probe.sh
xcodebuild -project probes/ios-speech/IOSSpeechProbe.xcodeproj -scheme IOSSpeechProbe -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/P6IOSASR/SimulatorBuild CODE_SIGNING_ALLOWED=NO build
```

工程将该生成文件作为 bundle resource。缺少输入时先执行生成脚本；不从仓库下载二进制，也不自动使用用户录音。真机使用已有签名配置构建；不创建或修改证书/profile。

## 操作与结果

- 查询状态不弹权限。点击 `Run ASR file probe` 或显式传入 `--run-asr` 才启动 ASR（未授权时请求 Speech 授权）；不请求麦克风，不采集环境音频。
- 仅在已授权、recognizer 可用并支持本机识别时启动。URL request 设置 `requiresOnDeviceRecognition=true`；任何不可用/失败都不会回退网络 recognizer。
- 识别等待 task delegate 的最终完成；空转写与固定句子不匹配分别记录。文本归一化只忽略大小写、标点及空白，不接受空串或部分句子作为匹配。
- 依次记录正常识别、立即取消请求、平台 task 完成状态、后续重入。固定 URL 很短，延迟取消可能在任务完成后才执行，因此取消分支在任务创建后立即调用 `cancel()`；取消请求不等于资源释放，平台尚未 completed 时禁止重入。超时有界，输入资源始终保留在 bundle 中。
- 每次报告写入 App Documents 的 `ios-asr-file-*.json`，保留原始错误 domain/code；`validationStatus` 区分 simulator/device。只依据实际报告更新设备矩阵。

已授权的探针自动运行入口（`DEVICE_ID` 使用本地已连接设备；App 需先安装）：

```sh
xcrun devicectl device process launch --terminate-existing \
  --device "$DEVICE_ID" io.ondeviceai.probe.speech -- --run-asr
```

自动入口在该视图生命周期只触发一次，手动按钮继续可用；不传参数时维持原有手动 ASR 流程。本次普通启动后导出的 ASR 报告集合和摘要与启动前一致（3 份），没有新增 ASR 报告。`resourceRelease=confirmed` 依据平台 task completed 并释放探针持有的 task/delegate，不表示测得全进程、音频或系统服务内存释放。

## 当前证据与缺口

固定本机音频：单声道、22050 Hz、Int16，54538 frames，约 2.473 秒；SHA256 `ea1e97d99f859f4bb1eafb75f6090bb9fe98f89ab86bd903f7c325013f3dd98e`。生成脚本和探针在识别前均校验该 SHA；不匹配即失败。系统 voice 版本变化可能无法生成同一输入，此时须审查并显式更新固定输入基线，不能将不同输入结果直接比较。

2026-10-17 已在在线 iPhone（iOS 27.0.1）完成现有签名配置构建、安装和启动；首轮报告 `ios-asr-file-954DD586-1154-4AE5-9251-32ABAD0E4F4A.json`（runId `C8D78CDD-CCAD-4203-A1A1-12C73FD70184`）确认授权、固定文件转写与重入成功，均为 task completed；转写为 `The blue bicycle is beside the greenhouse`，归一化匹配。150ms 延迟取消前任务已完成，整体如实记为 incomplete。随后加入 `--run-asr` 启动参数并以自动入口重跑，报告 `ios-asr-file-D21F6395-35CC-4828-A907-BE903E5E981A.json`（runId `168889A8-9FBE-4444-B7F5-8C9D4A5E0F76`）确认正常识别、立即取消和重入均完成；取消分支为 `status=cancelled`、`platformCancelled=true`、平台状态 `4`、`kAFAssistantErrorDomain/1110`，且 `resourceRelease=confirmed`。整体报告为 `status=success`。最低 iOS15 验收保持未验证。文件探针即使成功，也不能替代宿主麦克风权限、音频 session 或长期听写验证。

本地状态回归和完整 Apple 验证脚本均通过；KMP 测试为 up-to-date。模拟器与真机探针构建成功。日志位于忽略目录 `artifacts/verification/`，包括 `p6-ios-asr-final-build.log`、`p6-ios-asr-device-build.log` 和 `p6-ios-asr-final-verify-apple.log`。

## 官方 API 边界

本轮读取 Xcode 27.1 SDK 官方头文件：`SFSpeechRecognitionRequest.h` 说明 on-device flag 仅在 recognizer 支持时受支持；`SFSpeechRecognitionTask.h` 说明 URL request 的 finish 无效，delegate 的 didFinishSuccessfully 是请求完成界线，task 不持有 delegate。授权回调不保证主队列。

- <https://developer.apple.com/documentation/speech/sfspeechrecognitionrequest/requiresondevicerecognition>
- <https://developer.apple.com/documentation/speech/sfspeechrecognizer/requestauthorization(_:)>
- <https://developer.apple.com/documentation/speech/sfspeechrecognitiontask>

官方网页本轮 DNS 不可达，API 约束依据本机 SDK 声明核对；未声称抓取网页正文。

独立评审初轮 APPROVE，固定输入可复现性建议已通过生成脚本与运行时的 SHA guard 修正；增量复核无新增代码问题。SHA 不匹配场景在独立临时目录验证拒绝且保留输入；本地 ASR 状态检查和最终 Apple 统一验证再次通过。第二轮立即取消的实际设备结果见上文。

本次最终单次启动 guard 版本的真机构建成功，状态检查通过；日志为 `artifacts/P6IOSASR/immediate-cancel-device-build-20261017.log`。最终自动运行报告在 `artifacts/P6IOSASR/ImmediateCancelReports-final-20261017/`；普通启动对照在 `artifacts/P6IOSASR/DefaultLaunchReports-20261017/`。报告和固定音频继续仅保留在忽略目录，不进入 Git/LFS。
