# OnDevice AI Kit

面向多 App 复用的端侧 AI SDK。原生 SDK 独立分发，KMP 只承载桥接协议和 Demo 业务/UI。

分阶段状态与下一工作包见 [ROADMAP](docs/ROADMAP.md)。当前为开发快照，尚未完成独立代码审查和真机验收。

当前实现：iOS 15+ Vision 人脸检测/语义关键点、Android API 26+ bundled ML Kit 人脸检测；可选的 Apple AVSpeech TTS、Apple 文件 ASR、Android 系统 TTS，以及 Android on-device ASR 模块；任务取消/关闭、1+1 最新帧队列；带 SHA256 校验、原子安装、revision lease 的模型目录与 HTTPS 下载协调器；原生 benchmark JSON 与分组报告工具。

身份模块现已提供独立原生契约、加密存储/删除、1:1/1:N 引擎、活体会话、KMP bridge 与 Demo 控件；生产身份模型、PAD 后端和校准尚未启用。视频超分和 VLM 目前仍是独立探针；语音模块已提供系统后端的 SDK 契约与可裁剪分发，iOS ASR 当前是本地文件、强制 on-device 的可选后端，完整 KMP Demo 接入、麦克风 ASR 和第三方云端后端仍未交付。它们按[设计](docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)分阶段接入；高系统能力作为可选 backend，不提高基础 SDK 的 iOS 15 下限。动态加载指模型资产与已链接后端的实例生命周期，不下载执行代码。

当前 Demo 提供人脸检测与独立“身份与活体”控件；身份默认显示 UNVERIFIED，宿主注入真实授权、策略与后端后才可运行。后续 SDK/Demo 能力和调研→探针→模块接入的范围见 [功能路线图](docs/ROADMAP.md#当前范围与后续功能)。iOS15 与 Android 真机验收已按用户安排登记为 [等待设备 TODO](docs/ROADMAP.md#等待设备的-todo)，待设备提供后恢复。

## 原生接入

Swift Package 路径 `apple`：按需选择 `ODAIKitCore`、`ODAIKitModelStore`、`ODAIKitFace`、`ODAIKitAppleVision`、`ODAIKitSpeech`、`ODAIKitAppleSpeech`、`ODAIKitIdentity` products；参见 `examples/ios-native` 与 `examples/ios-speech-native`。`ODAIKitAppleSpeech` 才引入 AVFAudio，语音调用可用 `.default` 或 `.explicit(backendId)`，显式选择不会静默回退。`VisionFaceDetector.detect(input:options:)` 返回 upright、左上原点、归一化坐标；无人脸是成功空集合。调用结束前 SDK 保持输入引用；调用方不要修改输入。`close()` 等待实际底层工作结束。

Android 模块为 `android/{sdk-core,model-store,face,backend-mlkit,speech,backend-system-tts,backend-system-asr,identity}`；参见 `examples/android-native` 与 `examples/android-speech`。`examples:android-speech` 用 `contract`、`systemTts`、`systemAsr` 三个 flavor 证明依赖可裁剪；宿主负责 `RECORD_AUDIO` 权限。Bitmap 在 detect 返回前不可修改或 recycle。bundled ML Kit 包含模型，首次推理无需下载；无 Google Play Services 设备仍需真机验证。人脸 trackingId 不代表身份。

## 构建与测试

需要 Xcode、完整 JDK 17+、Android SDK platform 36；Gradle 9.4.1、Kotlin 2.4.0、AGP 9.1.0、Compose Multiplatform 1.11.1 已固定。设置 `ANDROID_HOME`/`local.properties` 与 `JAVA_HOME`。`scripts/gradle-local.py` 复用现有代理环境，不写全局配置；可用 `ODAI_GRADLE_BIN` 指向已缓存的固定 Gradle。普通入口也可使用 `./gradlew`。

```sh
./scripts/verify-apple.sh
./scripts/verify-android.sh
python3 -m unittest discover -s tools/tests -v
```

Xcode 工程已提交。需要重新生成时，使用已有 Ruby xcodeproj gem 执行 `ruby scripts/generate-xcode-projects.rb`。iOS Demo 构建前先执行 `./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64`（真机改为 `IosArm64`），再打开 `demo/iosApp/OnDeviceAIDemo.xcodeproj`。Demo 当前仅支持 Apple Silicon 模拟器；SDK 本身不依赖 KMP。真机签名由宿主配置。

Android Demo：`./gradlew :demo:androidApp:assembleDebug`。照片、相机和 benchmark 入口均调用真实原生后端。系统权限由 Demo 管理；SDK 不主动弹权限。benchmark 使用 5 次预热、30 次采样，记录实际后端和测量方法；未测内存/能耗为 null。

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

Maven 坐标为 `io.ondeviceai:speech:0.1.0-SNAPSHOT`、`io.ondeviceai:backend-system-tts:0.1.0-SNAPSHOT` 和 `io.ondeviceai:backend-system-asr:0.1.0-SNAPSHOT`；人脸坐标仍见上方命令。Swift products 是 `ODAIKitSpeech`（契约）和 `ODAIKitAppleSpeech`（Apple TTS 与文件 ASR 后端）。XCFramework 位于被忽略的 `artifacts/xcframeworks` 或指定的 `ODAI_ARTIFACTS_DIR`，含 iOS arm64 与 simulator arm64。打包脚本拒绝覆盖既有 XCFramework；重新打包前使用新的输出目录。当前只完成本地分发和独立消费者编译，没有发布远端制品或签名版本。

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

## Benchmark 与验收

```sh
python3 tools/benchmark_report.py run.json --output report.json
```

仅合并设备、OS、后端、模型、输入、预处理和测量方法一致的数据；缺失阶段保持 null，无综合评分。参见[协议](docs/validation/benchmark.md)、[验收记录](docs/validation/acceptance.md)、[设备矩阵](docs/validation/device-matrix.md)。构建通过不等于 iOS 15/API 26 真机验收通过。

GitHub 主仓：<https://github.com/AmatsuZero/ondevice-ai-kit>；极狐镜像：<https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit>。按[同步说明](docs/validation/remote-sync.md)显式同步分支/tag；未配置自动 CI 镜像。
