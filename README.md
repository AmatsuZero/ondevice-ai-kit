# OnDevice AI Kit

面向多 App 复用的端侧 AI SDK。原生 SDK 独立分发，KMP 只承载桥接协议和 Demo 业务/UI。

当前实现：iOS 15+ Vision 人脸检测/语义关键点、Android API 26+ bundled ML Kit 人脸检测；任务取消/关闭、1+1 最新帧队列；带 SHA256 校验、原子安装、revision lease 的模型目录与 HTTPS 下载协调器；原生 benchmark JSON 与分组报告工具。

**当前不包含**身份识别、活体、ASR/TTS、超分和 VLM 推理实现。它们按[设计](docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)分阶段接入；高系统能力作为可选 backend，不提高基础 SDK 的 iOS 15 下限。动态加载指模型资产与已链接后端的实例生命周期，不下载执行代码。

## 原生接入

Swift Package 路径 `apple`：选择 `ODAIKitCore`、`ODAIKitModelStore`、`ODAIKitFace`、`ODAIKitAppleVision` 中所需 products；参见 `examples/ios-native`。`VisionFaceDetector.detect(input:options:)` 返回 upright、左上原点、归一化坐标；无人脸是成功空集合。调用结束前 SDK 保持输入引用；调用方不要修改输入。`close()` 等待实际底层工作结束。

Android 模块为 `android/{sdk-core,model-store,face,backend-mlkit}`；参见 `examples/android-native`。Bitmap 在 detect 返回前不可修改或 recycle。bundled ML Kit 包含模型，首次推理无需下载；无 Google Play Services 设备仍需真机验证。人脸 trackingId 不代表身份。

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

Maven 坐标 `io.ondeviceai:{sdk-core,model-store,face,backend-mlkit}:0.1.0-SNAPSHOT`。XCFramework 位于被忽略的 `artifacts/xcframeworks`，含 iOS arm64 与 simulator arm64。打包脚本拒绝覆盖既有 XCFramework；重新打包前自行归档旧产物。尚未发布远端制品或签名版本。

## Benchmark 与验收

```sh
python3 tools/benchmark_report.py run.json --output report.json
```

仅合并设备、OS、后端、模型、输入、预处理和测量方法一致的数据；缺失阶段保持 null，无综合评分。参见[协议](docs/validation/benchmark.md)、[验收记录](docs/validation/acceptance.md)、[设备矩阵](docs/validation/device-matrix.md)。构建通过不等于 iOS 15/API 26 真机验收通过。

GitHub 主仓：<https://github.com/AmatsuZero/ondevice-ai-kit>；极狐镜像：<https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit>。按[同步说明](docs/validation/remote-sync.md)显式同步分支/tag；未配置自动 CI 镜像。
