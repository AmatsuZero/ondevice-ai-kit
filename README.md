# OnDevice AI Kit

面向多 App 复用的端侧 AI SDK。原生 SDK 独立分发，KMP 只承载桥接协议和 Demo 业务/UI。

分阶段状态与下一工作包见 [ROADMAP](docs/ROADMAP.md)。按原始阶段停止点复核，当前基础里程碑 **10/12**（P4、P11 未完成）；历史检查点中的 5/12 已被此口径取代，详见[逐阶段证据](docs/validation/roadmap-progress-audit-20261026.md)。完整 SDK/Demo 扩展交付、独立审查和统一真机验收仍在进行，10/12 不表示整体完成百分比。

当前实现：iOS 15+ Vision 人脸检测/语义关键点、Android API 26+ bundled ML Kit 人脸检测；可选的 Apple AVSpeech TTS、Apple 文件/麦克风 ASR、Android 系统 TTS，以及 Android on-device ASR 模块；任务取消/关闭、1+1 最新帧队列；带 SHA256 校验、原子安装、revision lease 的模型目录与 HTTPS 下载协调器；原生 benchmark JSON 与分组报告工具。

身份模块现已提供独立原生契约、加密存储/删除、1:1/1:N 引擎、活体会话、KMP bridge 与 Demo 控件；生产身份模型、PAD 后端和校准尚未启用。视频超分已有双端独立契约/client、可选 Apple VideoToolbox 固定 profile 后端和 KMP bridge/Demo；Android 明确 unavailable，iOS 新 Demo UI 因自动化初始化超时尚未验收，见[视频 Demo 验证](docs/validation/video-bridge-demo.md)。VLM已新增双端Multimodal纯契约/client与可裁剪本地分发，可选Apple MLXVLM后端已在独立apple-mlx包接入并通过固定单图SDK设备套件，见[MLXVLM后端验证](docs/validation/apple-mlxvlm-backend.md)；语音已有双端 KMP bridge、共享页面和原生 host，iOS 使用独立生成工程。iOS ASR 已实现文件与可主动结束输入的麦克风路径，均强制 on-device；麦克风真机录音尚未验收，见[麦克风验证](docs/validation/ios-microphone-asr.md)。第三方云端后端未交付。现代真机的双端 TTS 与 iOS 文件 ASR Demo 闭环已通过；Android ASR 仍缺资源，最低系统/自然质量与追加独立审查待补，见[语音 Demo 验证](docs/validation/speech-demo-bridge.md)。它们按[设计](docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)分阶段接入；高系统能力作为可选 backend，不提高基础 SDK 的 iOS 15 下限。动态加载指模型资产与已链接后端的实例生命周期，不下载执行代码。

当前 Demo 提供人脸检测、独立“身份与活体”、语音与视频控件（iOS 语音/视频使用独立生成工程）；身份默认显示 UNVERIFIED，宿主注入真实授权、策略与后端后才可运行。后续 SDK/Demo 能力和调研→探针→模块接入的范围见 [功能路线图](docs/ROADMAP.md#当前范围与后续功能)。iOS15 与 Android 真机验收已按用户安排登记为 [等待设备 TODO](docs/ROADMAP.md#等待设备的-todo)，待设备提供后恢复。

## 原生接入

Swift Package 路径 `apple`：按需选择 `ODAIKitCore`、`ODAIKitModelStore`、`ODAIKitFace`、`ODAIKitAppleVision`、`ODAIKitSpeech`、`ODAIKitAppleSpeech`、`ODAIKitIdentity`、`ODAIKitVideo`、`ODAIKitAppleVideo`、`ODAIKitMultimodal` products；参见 `examples/ios-native` 与 `examples/ios-speech-native`。`ODAIKitAppleSpeech` 才引入 AVFAudio，语音调用可用 `.default` 或 `.explicit(backendId)`，显式选择不会静默回退。`VisionFaceDetector.detect(input:options:)` 返回 upright、左上原点、归一化坐标；无人脸是成功空集合。调用结束前 SDK 保持输入引用；调用方不要修改输入。`close()` 等待实际底层工作结束。

Android 模块为 `android/{sdk-core,model-store,face,backend-mlkit,speech,backend-system-tts,backend-system-asr,identity,video,multimodal}`；参见 `examples/android-native` 与 `examples/android-speech`。`examples:android-speech` 用 `contract`、`systemTts`、`systemAsr` 三个 flavor 证明依赖可裁剪；宿主负责 `RECORD_AUDIO` 权限。Bitmap 在 detect 返回前不可修改或 recycle。bundled ML Kit 包含模型，首次推理无需下载；无 Google Play Services 设备仍需真机验证。人脸 trackingId 不代表身份。

## 构建与测试

需要 Xcode、完整 JDK 17+、Android SDK platform 36；Gradle 9.4.1、Kotlin 2.4.0、AGP 9.1.0、Compose Multiplatform 1.11.1 已固定。设置 `ANDROID_HOME`/`local.properties` 与 `JAVA_HOME`。`scripts/gradle-local.py` 复用现有代理环境，不写全局配置；可用 `ODAI_GRADLE_BIN` 指向已缓存的固定 Gradle。普通入口也可使用 `./gradlew`。

```sh
./scripts/verify-apple.sh
./scripts/verify-android.sh
python3 -m unittest discover -s tools/tests -v
```

Xcode 工程已提交。需要重新生成时，使用已有 Ruby xcodeproj gem 执行 `ruby scripts/generate-xcode-projects.rb`。iOS Demo 构建前先执行 `./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64`（真机改为 `IosArm64`），再打开 `demo/iosApp/OnDeviceAIDemo.xcodeproj`。Demo 当前仅支持 Apple Silicon 模拟器；SDK 本身不依赖 KMP。真机签名由宿主配置。

Android Demo：`./gradlew :demo:androidApp:assembleDebug`。照片、相机和 benchmark 入口均调用真实原生后端。系统权限由 Demo 管理；SDK 不主动弹权限。benchmark 使用 5 次预热、30 次采样，记录实际后端和测量方法；未测内存/能耗为 null。

语音 Demo：Android APK 的“语音”页包含系统 TTS 与仅麦克风 ASR，宿主管理 `RECORD_AUDIO`。iOS 不修改现有 Xcode 工程，先构建两份共享 framework，再生成到尚不存在的目录：

```sh
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64 :demo:shared:linkDebugFrameworkIosArm64
ODAI_SPEECH_DEMO_DIR=artifacts/SpeechDemo-new ruby scripts/generate-speech-demo.rb
open artifacts/SpeechDemo-new/SpeechDemo.xcodeproj
```

生成器可用 `ODAI_DEVELOPMENT_TEAM` 传入已有 team；不创建签名账户/资产。新工程包含实际 App UI 测试，旧工程仍保留旧入口。后端列表不等于语言资源就绪；取消按钮会等待 native drain 后才恢复操作。生成的 iOS 工程支持文件/麦克风输入：文件只请求 Speech 权限，麦克风依次请求 Speech 和录音权限；“结束录音并识别”等待最终结果，“取消”丢弃结果。宿主显式提供独占 audio-session lease，采集结束并 drain 后恢复 category/mode/options。当前真机证据、Android 资源与审查缺口见[语音 Demo 验证](docs/validation/speech-demo-bridge.md)和[麦克风验证](docs/validation/ios-microphone-asr.md)。

视频 Demo：Android APK 包含“视频”页，目前只显示明确 unavailable；文件选择不会读取媒体内容。iOS 在上述生成命令增加 `ODAI_VIDEO_DEMO=1`，新工程才链接可选 Video/AppleVideo 并加入视频 host/adapter；默认 Speech 工程保持原行为。视频当前支持固定 Apple profile，取消等待 native drain，输出由显式清理或离页释放，播放/分享有独立副本。新 Demo UI 尚未通过真机验收，构建、fixture 与设备缺口见[视频 Demo 验证](docs/validation/video-bridge-demo.md)。

## 本地分发

```sh
./gradlew :android:sdk-core:publishToMavenLocal :android:model-store:publishToMavenLocal :android:face:publishToMavenLocal :android:backend-mlkit:publishToMavenLocal
./gradlew :examples:android-native:assembleDebug -PusePublishedSdk=true
./scripts/package-apple.sh
ruby scripts/generate-binary-consumer.rb
```

语音模块的本地分发和消费者验证：

```sh
python3 scripts/gradle-local.py :android:speech:publishToMavenLocal :android:backend-system-tts:publishToMavenLocal :android:backend-system-asr:publishToMavenLocal
python3 scripts/gradle-local.py :examples:android-speech:assemble -PusePublishedSdk=true
ODAI_ARTIFACTS_DIR=artifacts/P6/current/apple sh scripts/package-apple.sh
ODAI_ARTIFACTS_DIR=artifacts/P6/current/apple ruby scripts/generate-binary-consumer.rb --speech
xcodebuild -project artifacts/P6/current/apple/SpeechBinaryConsumer/SpeechBinaryConsumer.xcodeproj -scheme SpeechBinaryConsumer -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/P6/current/apple/SpeechBinaryBuild CODE_SIGNING_ALLOWED=NO build
```

Maven 坐标为 `io.ondeviceai:speech:0.1.0-SNAPSHOT`、`io.ondeviceai:backend-system-tts:0.1.0-SNAPSHOT` 和 `io.ondeviceai:backend-system-asr:0.1.0-SNAPSHOT`；人脸坐标仍见上方命令。Swift products 是 `ODAIKitSpeech`（契约）和 `ODAIKitAppleSpeech`（Apple TTS 与文件/麦克风 ASR 后端）。XCFramework 位于被忽略的 `artifacts/xcframeworks` 或指定的 `ODAI_ARTIFACTS_DIR`，含 iOS arm64 与 simulator arm64。打包脚本拒绝覆盖既有 XCFramework；重新打包前使用新的输出目录。当前只完成本地分发和独立消费者编译，没有发布远端制品或签名版本。

身份模块的本地分发与独立消费者：

```sh
./gradlew :android:sdk-core:publishToMavenLocal :android:model-store:publishToMavenLocal :android:identity:publishToMavenLocal
./gradlew :examples:android-identity-native:assembleDebug -PusePublishedSdk=true
ruby scripts/generate-distribution-project.rb
ODAI_ARTIFACTS_DIR=artifacts/P10/identity-example sh scripts/package-apple.sh
ODAI_ARTIFACTS_DIR=artifacts/P10/identity-example ruby scripts/generate-binary-consumer.rb --identity
xcodebuild -project artifacts/P10/identity-example/IdentityBinaryConsumer/IdentityBinaryConsumer.xcodeproj -scheme IdentityBinaryConsumer -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/P10/identity-example/IdentityBinaryBuild CODE_SIGNING_ALLOWED=NO build
```

Identity 只依赖 Core 与 ModelStore，独立消费者见 `examples/ios-identity-native`、`examples/android-identity-native`。`identity-artifact.json` 记录真实二进制/interface hash、模型与策略绑定类型、当前模型/校准空缺；生产后端、模型资产和校准策略由独立工作包接入。生成 consumer 和打包均拒绝覆盖既有输出。Ruby/xcodeproj 与 json gem 需要使用同一 Ruby 环境；工具测试可用 `RUBY` 指定该环境的可执行文件。分发证据见 [P10 验证](docs/validation/p10-face-identity.md)。

视频基础模块：Swift `ODAIKitVideo` 只依赖 Core，Android `io.ondeviceai:video:0.1.0-SNAPSHOT` 使用 Core 与现有协程库。`VideoProcessingClient` 接收默认 ID 和宿主提供的后端，支持严格 Default/Explicit 选择、进度、取消/drain 与共享 close。请求默认保留音轨，不支持的倍率或音轨策略会拒绝。成功结果中的 output lease 由调用方持有，使用结束后调用 `release()`；client.close 不删除已交付的输出。输入文件需保持到处理及取消排空结束。分发与验证见[视频 SDK 基础记录](docs/validation/video-sdk-foundation.md)。可选 `ODAIKitAppleVideo` 只依赖 Core/Video，注册 `AppleVideoToolboxBackend()` 后提供 `apple.videotoolbox.sr`：仅非模拟器 iOS26+、模型已 ready、320×180/24fps/1…48帧/BT.709 SDR 输入、4×，输出1280×720 HEVC/MOV。默认 `.preserve` 支持无音轨或单条44.1/48kHz单/双声道AAC（零起点、与视频等长、无空白编辑段）；不兼容音频明确拒绝，`.discard` 显式丢弃。其他系统/模拟器返回 unavailable，不下载模型、不提高基础系统下限。九XCFramework与真实设备成功/取消/重入证据见[AppleVideo 验证](docs/validation/apple-video-backend.md)；音轨保留与设备证据见[音频验证](docs/validation/apple-video-audio.md)；基础/VT首包独立审查继续保留；KMP/Demo 已实现且本地门禁通过，新 Demo UI、独立审查与质量验收仍待补。

多模态基础模块：Swift ODAIKitMultimodal 与 Android `io.ondeviceai:multimodal:0.1.0-SNAPSHOT` 只依赖 Core（Android 另用既有协程库）。单图+prompt、默认128输出预算、model/runtime identity、累计文本、严格后端选择、cancel/drain/共享close已实现；构造请求不读图片，close不撤销返回文本。独立 Apple MLXVLM 后端/ModelStore lease 已接入，并有固定单图设备生成/取消/重入/close、1 warm-up + 30 samples benchmark，以及固定合成短视频 native/KMP/Demo 生命周期证据。Android 当前完成类型化 video contract/client 与 Demo 的明确 unavailable 路径，尚无生产 VLM/video backend；自然质量、Metal/GPU 峰值、energy、最低系统、增强 binary archive 与独立全范围审查仍未收口。见[后端验证](docs/validation/apple-mlxvlm-backend.md)、[视频 Demo 验证](docs/validation/multimodal-video-demo.md)和[Android 视频契约验证](docs/validation/android-multimodal-video-contract.md)。

    rtk proxy ./gradlew :android:sdk-core:publishToMavenLocal :android:multimodal:publishToMavenLocal
    rtk proxy ./gradlew :examples:android-multimodal-native:assembleDebug -PusePublishedSdk=true
    rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/Multimodal-new sh scripts/package-apple.sh
    rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/Multimodal-new ruby scripts/generate-binary-consumer.rb --multimodal

十XCFramework、Core+Multimodal-only iOS15 device/simulator编译链接及Android Maven真实消费见[多模态基础验证](docs/validation/multimodal-sdk-foundation.md)。样例位于examples/ios-multimodal-native、examples/android-multimodal-native，演示链接与明确unavailable。Apple总门禁可用ODAI_MULTIMODAL_CONSUMER_PROJECT指定新MultimodalBinaryConsumer.xcodeproj，未指定会报告binary consumer未验。

## Benchmark 与验收

```sh
python3 tools/benchmark_report.py run.json --output report.json
```

仅合并设备、OS、后端、模型、输入、预处理和测量方法一致的数据；缺失阶段保持 null，无综合评分。参见[协议](docs/validation/benchmark.md)、[验收记录](docs/validation/acceptance.md)、[设备矩阵](docs/validation/device-matrix.md)。构建通过不等于 iOS 15/API 26 真机验收通过。

GitHub 主仓：<https://github.com/AmatsuZero/ondevice-ai-kit>；极狐镜像：<https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit>。按[同步说明](docs/validation/remote-sync.md)显式同步分支/tag；未配置自动 CI 镜像。

可选MLX增强source package：路径apple-mlx，product ODAIKitAppleMLXVLM，仅arm64 iOS18+物理设备实际推理。使用SmolVLM2Model.manifest()安装已有本地资产到ModelStore，acquire后传入MLXVLMBackend(lease:)；成功初始化后lease由backend持有，调用方不能提前close。增强包保持与基础apple包分离。用 `python3 tools/generate_mlxvlm_consumer.py artifacts/MLXVLMConsumer-fresh` 生成独立消费者；生成不会下载依赖或模型，离线构建需现有固定缓存。详见[验证和限制](docs/validation/apple-mlxvlm-backend.md)。
