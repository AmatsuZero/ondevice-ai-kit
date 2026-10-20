# Handoff — Speech Demo Bridge

更新时间：2026-10-20

## 恢复位置

- 仓库：`/Users/daubert/Github/ondevice-ai-kit`
- 工作树：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`
- 分支：`feat/sdk-foundation`
- 当前实现提交：`8719a17 feat: adapt Android speech clients to KMP bridge`
- 实施计划：`docs/superpowers/plans/2026-10-19-speech-demo-bridge.md`
- 规格：`docs/superpowers/specs/2026-10-19-speech-demo-bridge-design.md`
- 执行 ledger：`.superpowers/sdd/2026-10-19-speech-demo-bridge/progress.md`

本 checkpoint 仅完成并提交到本地分支，尚未推送 GitHub 或极狐。

## 已完成的计划任务

### Task 1 — 公共 Speech Bridge 契约

提交：`6788b1b feat: define typed speech bridge contracts`

已提供 Default/Explicit 后端选择、TTS/ASR capability、Microphone/AudioFile opaque 输入、后端描述、类型化请求/结果/回调、稳定 failure code、close callback 与 `UnavailableSpeechBridge`。Swift 导出 ABI 已通过实际 typecheck。

### Task 2 — 能力级后端查询

提交：`331de92 feat: expose capability-scoped speech backend queries`

Apple 与 Android 的 TTS/ASR facade 均可独立 `listBackends()`；结果按 backend ID 排序，保留 unavailable reason、minOS、offline，并继续由真实 operation admission 决定是否可执行。

### Task 3 — Android Speech adapter

提交：`8719a17 feat: adapt Android speech clients to KMP bridge`

新增 `AndroidSpeechBridge(tts, asr, scope)`，实现以下边界：

- TTS 与 ASR 共用一个音频任务槽；query 不占该槽。
- Explicit 选择严格执行，不回退 Default。
- 缺少单项 client 只影响对应 capability。
- Android ASR 只声明 `MICROPHONE`；`AudioFile` 在进入 native client 前返回 `UNSUPPORTED_INPUT`。
- cancel 等 native `finally`/drain 完成后清槽并发布一次 `cancelled`；drain 期间重入返回 `BUSY`。
- native 忽略取消后产生的迟到 success 不会覆盖 `cancelled`。
- adapter 的 worker Job 与宿主 scope 的 Job 解耦，宿主提前取消不会跳过 drain 或 client close。
- close 立即拒绝 query/TTS/ASR admission，等待所有 query/audio worker，再关闭拥有的 clients；并发 close waiter 共享结果。
- callback 交付在注入 scope 的 dispatcher；未知异常只输出稳定的 `android.speech/1`，不包含文本、转写或文件路径。

集成测试使用真实 `SpeechSynthesisClient` / `SpeechRecognitionClient` 和可控 backend，覆盖计划列出的九个生命周期、选择、输入和隐私用例。

## 最新验证

提交 `8719a17` 后重新执行：

```sh
rtk proxy ./gradlew \
  :android:speech:testDebugUnitTest \
  :android:speech:lintDebug \
  :kmp-binding:compileAndroidMain \
  --console=plain
```

结果：`BUILD SUCCESSFUL`，214 tasks，2 executed / 212 up-to-date。Task 3 定向 `AndroidSpeechBridgeIntegrationTest` 也独立通过。

## 受保护的用户文件

以下改动属于用户，Task 1–3 没有暂存或提交：

- `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj`
  - 状态：modified
  - SHA-256：`b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`
- `docs/validation/face-identity.md`
  - 状态：untracked
  - SHA-256：`6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`

后续不得覆盖、暂存或提交这两个文件。Task 4–7 的生成工程、framework、APK、音频、模型和真机报告继续放入已忽略的 `artifacts/`。不要把超过 1 GB 的文件交给 Git 或 Git LFS；需要模型或大型资产时使用下载脚本。

## 下一步：Task 4 iOS adapter 与输入 lease

从计划 Task 4 开始，不重复 Task 1–3。先运行：

```sh
/Users/daubert/.codex/plugins/cache/openai-curated-remote/superpowers/6.4.2/skills/executing-plans/scripts/task-start \
  docs/superpowers/plans/2026-10-19-speech-demo-bridge.md 4
```

Task 4 目标文件：

- `kmp-binding/apple/IOSSpeechBridge.swift`
- `kmp-binding/apple/IOSSpeechAudioInput.swift`
- `demo/iosApp/SpeechInputStore.swift`
- `probes/ios-speech-bridge/IOSSpeechBridgeTests.swift`
- `scripts/generate-speech-bridge-tests.rb`
- `tools/tests/test_speech_bridge_project.py`

先按 TDD 写生成器与 XCTest 的 RED。首个计划命令：

```sh
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby \
  python3 -m unittest discover -s tools/tests -p test_speech_bridge_project.py -v
```

实现必须保持这些约束：

- `IOSSpeechBridge` 在 MainActor admission，分别拥有可选 TTS/ASR client。
- iOS ASR 本包只支持 `AUDIO_FILE`；Microphone 继续作为后续独立 roadmap 项。
- 文件 token 只在 native host 解析；lease 必须保持到 ASR native drain 结束后才 release。
- 未知 Swift selection 不能变成 Default。
- cancel query 不关闭 clients；close join 所有 task 和输入 lease，再关闭 clients。
- 测试工程生成器同时支持 `ODAIKitBinding` 与 `ODAIKitDemo` ABI，缺输入或输出已存在时不得产生覆盖副作用。
- 不改受保护的现有 Xcode 工程；生成 fresh 工程到 `artifacts/`。

Task 4 完成后继续 Task 5 共享 ViewModel/UI、Task 6 双端 host/权限/独立工程、Task 7 真机与裁剪验收。

## 仍需保留的 roadmap 缺口

这些项目不能因 Task 1–3 通过而标为完成：

- iOS 15 真机、Android API 26 真机。
- Android ASR 真实转写；当前 TECNO KM9 / API 35 的 en-US 返回 `MISSING_ASSETS`。
- iOS 麦克风 ASR。
- 两端自然语音质量与断网验证。
- P7 Android 视频超分与完整 SDK/Demo 接入。
- P9 Android 第二平台与质量基准。
- P10 生产人脸模型、PAD、授权数据、阈值校准与最终独立审查。
- P11 统一 benchmark 与发布结论。

可用现代 Android 真机：TECNO KM9，API 35，ADB serial `15365375C9007986`。iPhone 16 Pro Max / iOS 27 已用于现有 iOS 验证；连接状态需在新会话重新检查。
