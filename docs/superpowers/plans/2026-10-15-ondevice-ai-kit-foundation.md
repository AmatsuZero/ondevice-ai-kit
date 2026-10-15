# OnDevice AI Kit Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可独立接入的 Swift/Kotlin SDK 基座、模型安装生命周期、两端人脸检测、KMP Demo，以及可导出真实设备结果的 benchmark。

**Architecture:** 原生 SDK 承担平台推理与资源生命周期，KMP 只适配 DTO、业务状态和 Demo UI。基础 Apple Package 不依赖 MLX；高版本系统后端通过 availability 暴露，MLX 后续使用独立 package。模型安装、模型加载、会话关闭和文件删除各自独立。

**Tech Stack:** Swift Package、Vision、Foundation/CryptoKit；Kotlin/Android Library、bundled ML Kit Face Detection；Kotlin Multiplatform、Compose Multiplatform；JSON fixtures 与 benchmark 报告。依赖版本在 Task 1 编译探针中锁定，不使用动态版本或 main 分支依赖。

**Spec:** [已确认设计](../specs/2026-10-15-ondevice-ai-sdk-design.md)

## Global Constraints

- SDK Core、KMP 公共 API 和人脸模块的最低 iOS 版本为 iOS 15；Android API 26 作为 Demo 基线。
- 基础 package 不依赖 MLX；增强后端使用独立 package manifest/构建入口。
- Android 首版使用 bundled ML Kit Face Detection；系统/内置模型无需业务方手动安装 SDK 模型包。
- 每个成功接受的任务恰有一个终态；每个会话同一时间最多一个活跃任务；取消和 close 幂等。
- 相机端只保留一个处理中帧和一个最新待处理帧；图像缓冲区保留到推理真正结束。
- 原点左上、x 向右、y 向下、坐标范围 0–1；完成方向归一化，尚未做预览镜像。
- 模型权重不提交 Git，不进入 AAR/XCFramework；运行时只下载模型/资源，不下载执行代码。
- 各 App 独立沙盒；能力查询不下载、不弹权限、不加载模型；缺失指标显式为空。
- GitHub `AmatsuZero/ondevice-ai-kit` 为主仓；极狐镜像固定 `Daubert/ondevice-ai-kit`，初始均 private。
- 无实机、无可靠能耗测量或无可用后端时，报告明确标记未验证/未测，不填造数据。

## Review Focus

1. 完成/取消/close 竞争：只有一个终态，底层仍使用的缓冲区不提前释放（Task 2、6）。
2. 损坏、越界或中断的模型安装：不发布半成品，不损坏已安装 revision（Task 3、4）。
3. 旋转、镜像、窄图及无脸输入：结果与预览正确对齐，无脸返回成功空集合（Task 5、6）。
4. 低系统和未链接后端：App 正常启动，能力查询提供明确原因（Task 1、7、9）。
5. 性能样本缺失及远端分叉：报告不生成虚假统计，同步不覆盖任一远端提交（Task 8、10）。

## 文件与交付边界

物理目录按构建工具组织，product/module 对应设计中的逻辑边界；只创建本阶段使用的模块。

| 路径 | 责任 |
| --- | --- |
| `apple/Package.swift`, `apple/Sources/{ODAIKitCore,ODAIKitModelStore,ODAIKitFace,ODAIKitAppleVision}/` | iOS 15 原生 SDK，独立 products |
| `apple/Tests/{ODAIKitCoreTests,ODAIKitModelStoreTests,ODAIKitFaceTests}/` | 生命周期、安装、坐标与后端测试 |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/` | 锁定 Android/KMP 构建入口 |
| `android/{sdk-core,model-store,face,backend-mlkit}/` | Kotlin 原生 SDK，各目录对应 Android Library |
| `kmp-binding/src/{commonMain,androidMain,iosMain}/` | 跨平台 DTO、显式桥接协议及 Android 适配 |
| `demo/shared/`, `demo/androidApp/`, `demo/iosApp/` | Compose 业务/UI 与平台权限、相机、注入入口 |
| `fixtures/{contracts,models,face}/` | 两端共用的小型夹具，禁止使用未经授权人脸照片 |
| `benchmark/schema/`, `benchmark/fixtures/`, `tools/benchmark_report.py` | 报告格式、统计与合并规则 |
| `examples/{ios-native,android-native}/` | 不依赖 KMP 的最小消费者与接入说明 |
| `docs/{research,validation}/`, `scripts/` | 调研证据、设备验证、分发和串行同步 |

首期不创建 speech/SR/VLM 空实现，不发布尚未实现的能力按钮。模型生命周期使用小型非推理数据夹具验证，与无需自有权重的人脸路径分开。

## Task 1: 锁定构建矩阵与 Swift→KMP 编译探针

**Files:** Create `apple/Package.swift`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper；`kmp-binding/build.gradle.kts`, `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/FaceBridge.kt`；`examples/ios-native/`, `examples/android-native/`；`docs/validation/toolchain.md`。

**Interfaces:** Produces `interface FaceBridge { fun backendId(): String }`，由 Swift 类实现、KMP 消费；Android 示例直接依赖原生 Kotlin 模块。Swift Package products 固定为 `ODAIKitCore`、`ODAIKitModelStore`、`ODAIKitFace`、`ODAIKitAppleVision`。

- [ ] 记录 `xcodebuild -version`、`swift --version`、`java -version`、Android SDK 安装情况；从官方兼容矩阵选出 Kotlin/Compose/AGP/Gradle/JDK 组合，写入精确版本及来源；不改变全局 JDK/Xcode 配置。
- [ ] 建立 Swift 实现 `FaceBridge` 并注入 KMP 的最小调用，先构建确认缺少 binding 实现时失败，再补全；断言返回 `apple.vision`。探针成功后才扩充桥接类型，禁止直接从 Kotlin import Swift SDK。
- [ ] 配置 iOS 15 target、Android minSdk 26；创建原生最小消费者。提交的 wrapper 必须有分发 SHA-256，依赖无 `+`、`latest` 或 branch 版本。
- [ ] Run `rtk proxy ./gradlew :kmp-binding:compileKotlinIosSimulatorArm64 :kmp-binding:compileDebugKotlin`；Run `rtk proxy xcodebuild -project examples/ios-native/NativeConsumer.xcodeproj -scheme NativeConsumer -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`。Expected: 编译成功、消费者没有 MLX/KMP 依赖；命令与实际环境记录入 toolchain 文档。缺 SDK/工具链单独报告，不能计作通过。
- [ ] Review 依赖图和部署版本，提交 `build: pin native SDK and KMP toolchain probes`。

## Task 2: Core 能力状态与单任务生命周期

**Files:** Create `apple/Sources/ODAIKitCore/{Availability,TaskGate,LatestFrameQueue}.swift`；`android/sdk-core/src/main/kotlin/io/ondeviceai/core/{Availability,TaskGate,LatestFrameQueue}.kt`；对应 `apple/Tests/ODAIKitCoreTests/` 与 `android/sdk-core/src/test/`；`fixtures/contracts/lifecycle.json`。

**Interfaces:** Produces `CapabilityAvailability`（status=`available|unavailable|requiresAssets`、reason、backendId、minOS、offline）；`UnavailableReason` 包含 `requiresOSVersion, backendNotLinked, permissionDenied, unsupportedDevice`。Swift `TaskGate.accept(id: UUID) throws`、`finish(id: UUID, terminal: TaskTerminal) -> Bool`、`close() async`；Kotlin 同语义 `suspend fun accept(id: String)`、`finish(id: String, terminal: TaskTerminal): Boolean`、`close()`。关闭后 accept 返回 `closed`，忙时返回 `busy`。

- [ ] 先写契约测试：第二个 accept 为 busy；completed/cancelled 并发只有一次 finish 为 true；重复 cancel/close 不抛出；close 后不再接受任务；终态后无事件。
- [ ] Run `rtk proxy swift test --package-path apple --filter TaskGateTests` 和 `rtk proxy ./gradlew :android:sdk-core:testDebugUnitTest`，确认未实现失败；用 actor/Mutex 实现同语义状态转换。
- [ ] 实现 `LatestFrameQueue.offer(frame)` / `next()`：最多 processing+pending；提供释放回调与 dropped 计数。测试输入 100 帧时只保留最后 pending，正在处理的引用在完成前不释放。
- [ ] 重跑上述命令，确认契约夹具两端一致；review 竞争与资源释放后提交 `feat: add core availability and task lifecycle`。

## Task 3: Manifest 校验与本地原子安装

**Files:** Create `apple/Sources/ODAIKitModelStore/{ModelManifest,ModelStore,ModelLease}.swift`；`android/model-store/src/main/kotlin/io/ondeviceai/modelstore/{ModelManifest,ModelStore,ModelLease}.kt`；`fixtures/models/{valid,invalid}/`；对应 ModelStore tests。

**Interfaces:** Produces `ModelRef(modelId, revision)`；`ModelStore.status(ref)`、`install(manifest, source)`、`acquire(ref) -> ModelLease`、`remove(ref)`；Swift 为 async throws，Kotlin 为 suspend。Lease 提供安装根路径与幂等 `close`；remove 活跃 revision 返回 `inUse`。状态严格区分 absent/installing/installed/invalid，无 loaded 状态混入磁盘安装状态。

- [ ] 先写测试：正确 SHA 与大小才安装；`../`、绝对路径、重复路径、符号链接、错误 schema/后端版本/系统版本被拒绝；文件集缺失或多余被拒绝；旧 revision 在新版本失败后仍可 acquire。
- [ ] 用本地目录源先实现逐文件复制→验证→原子发布；fixture 为小型文本/二进制数据，无执行代码。Manifest 覆盖设计 §6 所有字段，未测内存用 nullable measured estimate 表达，禁止伪造默认值。首期拒绝压缩档案，不引入解压依赖。
- [ ] 测试 lease 期间 remove=inUse、lease 关闭后 remove 成功；安装/激活新 revision 不更换旧 lease；启动时只清理自身安装临时目录。
- [ ] Run `rtk proxy swift test --package-path apple --filter ModelStoreTests`、`rtk proxy ./gradlew :android:model-store:testDebugUnitTest`，先失败后通过；review 路径边界和原子移动，提交 `feat: add manifest validation and atomic model store`。

## Task 4: HTTPS 下载、合并观察者与故障恢复

**Files:** Create `apple/Sources/ODAIKitModelStore/{ModelDownloader,InstallCoordinator}.swift`；Kotlin 对应文件；`ModelDownloaderTests`、`InstallCoordinatorTests`；`docs/validation/model-store.md`。

**Interfaces:** Consumes Task 3；produces `install(manifest, observer) -> InstalledModel` 和 `cancel(observerId)`；同 revision 一个物理安装任务，不同观察者独立取消。下载器可注入 transport、空间查询与时钟以复现故障；生产仅使用随 SDK 打包的固定 manifest。

- [ ] 先写故障测试：同版本两观察者只下载一次；取消一个另一个完成；最后观察者取消清理临时文件；断流、错误 hash、空间不足、进程重启恢复均不发布部分文件；非 HTTPS 与未授权重定向目标拒绝。
- [ ] Apple 使用 URLSession、Android 使用平台 HTTPS transport；按配置限制目标主机、总字节数、单文件大小与临时空间；取消等待底层 I/O 停止后清理。首期中断后重新下载，不实现未验证的断点续传。
- [ ] Run Task 3 两端命令并包含新测试；在文档记录故障注入方法与结果。SDK 不存在远端任意 manifest 入口；没有下载代码加载路径。
- [ ] Review 并发安装和清理边界，提交 `feat: add cancellable verified model downloads`。

## Task 5: 人脸统一输出与坐标契约

**Files:** Create `apple/Sources/ODAIKitFace/{FaceDetector,FaceObservation,ImageGeometry}.swift`；`android/face/src/main/kotlin/io/ondeviceai/face/` 同名类型；`fixtures/face/geometry.json`；`ImageGeometryTests`。

**Interfaces:** Produces `FaceDetector.availability()`、`detect(input: ImageInput, options: FaceOptions) -> FaceResult`（Swift async throws / Kotlin suspend）；`FaceResult(frameId, timestampNs, orientedWidth, orientedHeight, previewTransform, faces)`。FaceObservation 包含 normalized box、语义 landmark regions、nullable yaw/pitch/roll、nullable quality、session trackingId。ImageInput 为平台持有的图像句柄，输入方向与镜像显式传递，不跨 KMP 复制像素。

- [ ] 先建立 0/90/180/270°、镜像、预览裁剪及窄图坐标夹具。示例：upright 100×200 图像左上像素框 `(10,20,30,40)` 对应 `(0.1,0.1,0.3,0.2)`；镜像只影响 previewTransform。
- [ ] 用夹具测试四角、框、landmark 映射；空 faces 是成功；不可用 pitch 为 null；trackingId 不表示身份。两端测试使用相同 JSON 与容差 `1e-6`。
- [ ] 实现几何转换和 DTO；Run `rtk proxy swift test --package-path apple --filter ImageGeometryTests`、`rtk proxy ./gradlew :android:face:testDebugUnitTest`，确认先失败后通过。
- [ ] Review 方向、坐标与空值语义，提交 `feat: define portable face observations and geometry`。

## Task 6: Vision 与 bundled ML Kit 真实人脸后端

**Files:** Create `apple/Sources/ODAIKitAppleVision/VisionFaceDetector.swift`；`android/backend-mlkit/src/main/kotlin/io/ondeviceai/mlkit/MLKitFaceDetector.kt`；平台后端集成 tests；`fixtures/face/README.md`；`docs/validation/face.md`。

**Interfaces:** Consumes Task 2/5；produces `VisionFaceDetector` backendId=`apple.vision.face`、`MLKitFaceDetector` backendId=`android.mlkit.face.bundled`，实现 FaceDetector。相机使用 LatestFrameQueue；图片一次调用在真正推理完成后归还引用。

- [ ] 用可注入 request runner 写测试：零脸成功空集合；后端异常 failed；取消与回调竞争只有一个终态；关闭后不能执行；取消后底层晚到回调不会释放已复用帧或发布结果。
- [ ] Apple 使用 Vision 人脸矩形/关键点请求；Android 固定 bundled ML Kit artifact 的精确版本并记录官方来源，配置仅开启公开支持的选项；映射现有几何与指标，不补造两端缺失字段。
- [ ] 为集成样本建立来源/授权清单；没有授权人脸素材时使用操作者显式提供的临时本机图片进行设备验收，不提交素材。空白合成图可作无脸夹具。
- [ ] Run `rtk proxy xcodebuild -project examples/ios-native/NativeConsumer.xcodeproj -scheme NativeConsumer -destination 'platform=iOS Simulator,name=iPhone 17' test`（执行前用 `-showdestinations` 替换为真实可用目的地）；Run `rtk proxy ./gradlew :android:backend-mlkit:testDebugUnitTest :android:backend-mlkit:connectedDebugAndroidTest`。模拟器、无设备跳过与真机结果分别记录。
- [ ] 断网图片检测与持续相机任务在真实设备执行；设备缺失保持未验证。Review 后提交 `feat: add native face detection backends`。

## Task 7: KMP 桥接与可运行 Demo

**Files:** Modify `kmp-binding/`；Create `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/{FaceScreen,FaceViewModel}.kt`；`demo/androidApp/`；`demo/iosApp/`；`FaceViewModelTest`。

**Interfaces:** 将 Task 1 探针扩为 `FaceBridge.availability(): BridgeAvailability`、`detect(inputId: String, callback: FaceCallback): BridgeTask`；`FaceCallback` 提供 onResult/onError/onCancelled，`BridgeTask.cancel()` 幂等。平台图像仓通过短期 inputId 持有图像，最终回调后释放；commonMain 只持有 DTO 与变换。Swift 入口注入 Vision bridge，Android 注入 ML Kit bridge；不将原生缓冲区放入 shared DTO。

- [ ] 先写 ViewModel 测试：权限拒绝有原因；取消后迟到结果不覆盖新任务；不可用后端显示 reason；旋转后框使用 previewTransform；页面离开调用 cancel/close。用 fake bridge 验证，不用延时猜测事件顺序。
- [ ] 实现图片选择与相机预览、人脸覆盖层、状态/耗时/取消和诊断导出；仅显示已实现能力。平台负责权限和相机，内存压力/前后台切换停止输入并等待任务收尾。
- [ ] Run `rtk proxy ./gradlew :demo:shared:allTests :demo:androidApp:assembleDebug`；Run `rtk proxy xcodebuild -project demo/iosApp/OnDeviceAIDemo.xcodeproj -scheme OnDeviceAIDemo -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`。
- [ ] 验证两个消费者仍无 KMP 依赖，Demo iOS deployment target=15.0；列出实机启动/相机/断网状态。Review 桥接引用释放与 UI 状态，提交 `feat: add KMP face demo with native bridges`。

## Task 8: Benchmark schema、采集和可复核报告

**Files:** Create `benchmark/schema/run.schema.json`, `benchmark/fixtures/{valid,invalid}/`；`tools/benchmark_report.py`, `tools/tests/test_benchmark_report.py`；`demo/shared/.../BenchmarkScreen.kt`；两端 native `BenchmarkRecorder`；`docs/validation/benchmark.md`。

**Interfaces:** Produces schemaVersion=1 JSON：runId、device、OS、sdkCommit、capability、actualBackend、modelRevision(nullable 系统模型)、preprocessRevision、fixtureHash、cold/load/warm/inference samples、units、quality、peakMemory、energy、thermal、measurementMethod、validationStatus。`benchmark_report.py INPUT... --output report.json` 按设备/系统/能力/后端/模型/输入/测量方法分组；质量无统一总分。

- [ ] 先写测试：无样本不生成 percentile；单样本 p50/p95 为该值；`[1,2,3,4,100]` 最近秩法 p50=3、p95=100；不同设备/OS 不合并；未知能耗输出 null+unmeasured；错误单位/负延迟拒绝。
- [ ] 实现 monotonic clock 采集与报告；区分首调用、模型加载、预热和纯推理，无法隔离的阶段标明 unavailable；系统模型不伪造 revision。iOS/Android 峰值内存测量方法不同则单独分组并注明含宿主开销。
- [ ] Run `rtk proxy python3 -m unittest discover -s tools/tests -p 'test_benchmark_report.py'`，先失败后通过；Demo 导出一份带设备元数据的 face run，实际数据保存在本机 artifacts，不提交用户输入。
- [ ] 发布固定测试协议：face 至少 5 次预热、30 次测量，报告样本数及统计法，统计阈值不是实时性承诺；其它能力随各阶段补充指标。Review 不同后端/设备比较边界，提交 `feat: add reproducible device benchmark reports`。

## Task 9: 模块分发与低系统接入验收

**Files:** Create `scripts/{verify-apple,verify-android,package-apple}.sh`；Android Maven publication 配置；`README.md`；`docs/validation/{acceptance,device-matrix}.md`；更新原生消费者。

**Interfaces:** Swift products 与 Task 1 一致；Android Maven group=`io.ondeviceai`，artifact=`sdk-core,model-store,face,backend-mlkit`，首个开发版本 `0.1.0-SNAPSHOT`。发布到本地 Maven 和本机 XCFramework artifacts；远端制品发布和签名凭据不属于本任务。

- [ ] 用全新临时消费者引用本地 Swift package / Maven artifacts，证明原生接入只依赖所需 product，没有 KMP/Compose/MLX；验证 iOS arm64 与 simulator arm64 切片及资源包含，Android bundled ML Kit 模型存在。
- [ ] Run `rtk proxy ./scripts/verify-apple.sh`、`rtk proxy ./scripts/verify-android.sh`。脚本封装前面实际可用的测试/构建命令，失败立即退出；Run `rtk proxy ./gradlew publishToMavenLocal` 与 `rtk proxy ./scripts/package-apple.sh` 验证消费者实际解析。
- [ ] 真机矩阵分别记录 iOS15、较新 iOS、Android API26、较新 Android；当前设备列表先用只读工具发现。无法取得的设备保持 missing-device，禁止用部署目标构建等同低系统启动。输入权限、无脸、旋转、取消、前后台、断网各单列状态。
- [ ] 每项证据记录命令/SDK commit/设备OS/时间/路径；只有实际通过项标 pass。Review 模块隔离和验收缺口，提交 `build: package native SDKs and document device acceptance`。

## Task 10: 建立私有远端与串行镜像同步

**Files:** Create `scripts/sync-remotes.sh`, `docs/validation/remote-sync.md`；不写任何 token 到仓库。

**Interfaces:** `sync-remotes.sh main` 只同步显式分支。tag 须使用独立显式参数，存在不同 SHA 时拒绝；不使用 `--mirror`、force push 或覆盖远端历史。

- [ ] 先用临时本地 bare repos 测试：相同 SHA 无操作；fast-forward 成功；分叉拒绝且两边引用不变；主仓成功镜像失败时记录待重试；重试重新读取两端 SHA。测试 fixture 限于临时目录，不能操作真实远端。
- [ ] 用 gh 查询 GitHub 当前身份和目标项目；用 glab `--hostname clients-gitlab.ugreeniot.com` 重新查询命名空间 `Daubert`，必须 full_path=Daubert、kind=user、id=572。已有项目则核对，不重复创建或更改可见性。
- [ ] 创建尚不存在的两个 private 项目：GitHub `AmatsuZero/ondevice-ai-kit`、极狐 `Daubert/ondevice-ai-kit`（显式 namespace_id=572）。配置 `origin` 与 `jihu`，从 API 响应取得真实 clone URL。
- [ ] 一个串行 writer 推送 GitHub→冻结该分支 SHA→极狐；用新鲜 `git ls-remote` 读两端，只有完全相等才报告同步完成。网络失败重试前对账；分叉不自动解决。
- [ ] 记录两个项目 URL、visibility、目标 namespace、分支 SHA 与验证时间；review 脚本后提交并再同步。当前阶段只承诺手动命令同步，自动 CI 镜像另待凭据/权限方案，不声称已经持续自动同步。

## 后续独立计划与研究交付

首期实现之外的能力不隐去，也不塞入首期空模块。每行下一阶段先交付独立调研与设计，再安排实现；资料必须记录查阅日期、固定源码版本、许可证、运行时/系统下限与实际证据等级。

| 次序 | 文档路径 | 对照与验收方向 |
| --- | --- | --- |
| R1 | `docs/research/speech-backends.md` | iOS15 Speech/AVSpeech、Android 本地 Speech/TTS、iOS26 SpeechTranscriber；中文/英文离线、CER/WER、取消/中断；系统资源独立于 ModelStore |
| R2 | `docs/research/video-sr-backends.md` | iOS26 VideoToolbox、移动 CoreML Real-ESRGAN、Android ONNX/LiteRT；时间轴/音轨/解码完整性与 PSNR/SSIM/VMAF；CrawlerBot macOS Process 不迁入手机 |
| R3 | `docs/research/mobile-vlm.md` | SmolVLM2 500M 首个 MLX 试验，256M/2.2B 对照；Android GGUF/llama；Florence-2 结构化输出；MiniCPM-V4.7 1B 与35B-A3B 分开；Qwen/Gemma 指定 checkpoint 后再比较 |
| R4 | `docs/research/face-identity.md` | 1:1、1:N、活体独立协议/特征生命周期；阈值、FAR/FRR 与授权/删除；不把 trackingId 当身份 |

iOS26 系统能力可以先做 benchmark probe，不等待低系统替代方案实现。MLX 使用独立 iOS17+ package 与暂定 iOS18 增强 Demo；是否支持单一 iOS15 宿主仅在编译、链接、低系统启动探针全部通过后决定。每条后端路线必须区别“官方支持”“转换成功”“目标设备推理成功”，失败结果也要保存。

## 完成标准与执行边界

- 每个 Task 依次执行：所需行为测试先失败→最小实现→针对性验证→task-scoped review→提交；review 问题修复后再进入依赖任务。
- Task 1→2；2→3→4；2→5→6；1/6→7；6/7→8；全部本地交付→9；远端 Task 10 可在首个可验证实现提交后执行并随各任务同步，无须等设备齐全。
- 首期完成意味着可复用产品、Demo 和报告链路确实可构建运行；真机缺口须单列，未覆盖最低系统不得宣称兼容性已验收。
- 本计划只规定验证，编写计划时没有执行实现、测试、设备推理、远端创建或同步。

## 计划自审

设计 §1–5 对应 Task 1/2/5/6/7；§6 对应 Task 3/4；§7.1 对应 Task 5/6；§7.2–7.4 明确归入 R1–R3 独立计划与 benchmark 扩展；§8–9 对应 Task 7–9；§10 对应 Task 10；§11–13 的风险、CrawlerBot 边界与官方证据进入各任务 review 和 R1–R4。五项 Review Focus 均有明确归属测试；平台签名、远端制品发布、CI token 与真实设备取得不是本地通过的替代项。
