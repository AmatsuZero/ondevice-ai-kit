# Multimodal SDK Foundation 验证

更新：2026-10-22。工作树 .worktrees/foundation / feat/sdk-foundation；设计基线ae76aa3，实现基线43929e4。这里只交付P9纯SDK契约、协调器与裁剪分发，不是完整多模态功能。

## 实现与边界

| Task | 提交 | 交付 |
| --- | --- | --- |
| 1 | cf3a0e6 | Swift ODAIKitMultimodal 契约与actor client，仅Core依赖 |
| 2 | 8476dd8 | Android multimodal对等契约/client，Core与既有协程 |
| 3 | e1ff006 | 第十个XCFramework、Maven local、双端Core+Multimodal-only消费者 |
| 4 | 本记录及同次提交 | 总门禁、自审、README/roadmap/handoff与明确缺口 |

[设计](../superpowers/specs/2026-10-22-multimodal-sdk-foundation-design.md)；[计划](../superpowers/plans/2026-10-22-multimodal-sdk-foundation.md)。原始日志/brief/ledger/final-review.md保留于忽略目录.superpowers/sdd/2026-10-22-multimodal-sdk-foundation/。

MultimodalRequest是一张图片文件、原始prompt和正数maxOutputTokens（默认128）；构造时不读取图片，实际backend负责解码/格式及生成预算。ModelIdentity三个版本字段非空，字符串本身不证明资产已校验。TextUpdate是累计文本快照，sequence是更新序号，不是token数。TextOutput不可变，无输出文件lease，close后已返回结果有效。

Default/Explicit严格选择，未知/不可用不回退。单槽包含availability和generate/drain；取消后晚到成功或普通错误均以取消结束。回调串行、只接受递增序号，旧操作/取消/关闭后的更新被过滤；不保证UI dispatcher，回调不得阻塞。close封闭admission、join生成及已接纳查询，再逐backend关闭一次，重复close共享终态。Android意外backend.close失败仍尝试其余项，重复close保持同一失败。

基础下限仍为iOS15/macOS12/API26。没有MLX、权重、资源下载、录音、私人媒体读取、签名资产变更或远端发布。

## 测试证据

| 门禁 | 结果与日志 |
| --- | --- |
| Swift契约 RED→GREEN | 缺类型失败→4/4；task-1-contract-{red,green}.log |
| Swift client RED→GREEN | 缺client失败→10/10；task-1-client-{red,green}.log |
| Swift全套 | 461/461 XCTest、0失败；task-1-swift-full.log，Task4 artifacts/verification/swift-tests.log |
| Android RED→GREEN | 缺类型失败→12/12、0失败/错误/跳过，lintDebug通过；task-2-{red,final}.log及android/multimodal/build/test-results/testDebugUnitTest |
| 分发生成器 | 五处预期失败→11/11；task-3-{red,green-final}.log |
| 工具全套 | 88/88；task-3-tools-full.log；之后产品源码未改，不重复相同全套 |
| Apple总门禁 | task-4-apple-gate.log exit0；Swift461、KMP测试/framework、分发工具18/18、bridge/相机/照片/方向/ASR probe、原生consumer/原Demo与新binary consumer构建均成功 |
| Android总门禁 | task-4-android-gate.log exit0；716tasks（15执行、701 up-to-date），新模块测试/lint、既有JVM/lint/APK及consumer |

双端均用真实client与受控backend验证：注册/排序/不回退、实际backend/model/text/原输入、错误drain、availability占槽、取消后busy/晚到success/error、drain不主动取消、倒序/重复/旧更新、query/close join和共享close。Android另验取消close等待者、多backend关闭失败继续cleanup。

保留的失败与修复：

- 首次Android GREEN因表达式体测试返回assertFailsWith异常对象触发JUnit initializationError；显式runBlocking<Unit>修正后才执行真实测试，初始化失败不算12项验收。
- availability挂起时取消、立即开放gate可晚启动，断言expected0/actual1真实失败；保存caller Job并在admission/update读取取消状态，最终12/12通过。
- 生成器测试遗漏xcodeproj默认Foundation link，修正期望保留系统依赖。
- 首次iOS consumer误指空artifact目录，前置检查拒绝；改用fresh分发目录后双端构建成功。
- Android SDK XML版本提示是既有环境提示，不计设备推理证据。

## 分发与真实消费者

fresh目录：artifacts/verification/multimodal-foundation-20261022/distribution/。task-3-package.log成功生成十个XCFramework，每个含iOS arm64与simulator arm64；没有x86_64声明。父目录foundation-evidence.json记录源提交e1ff006、binary/interface SHA256、slice、otool依赖及Maven POM hash/依赖。

两份Multimodal binary只链接Core与系统/Swift库，swiftinterface为iOS15 target，只import Core，没有MLX/Speech/Video/Identity/Face/ModelStore。Core+Multimodal-only unsigned device/simulator编译链接均通过：task-3-consumer-{device,simulator}.log；Task4环境变量门禁日志artifacts/verification/multimodal-consumer-{device,simulator}.log。不是iOS15真机执行。

复跑使用未存在目录，打包/生成器拒绝覆盖：

    rtk proxy ruby scripts/generate-distribution-project.rb
    rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/Multimodal-fresh sh scripts/package-apple.sh
    rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/Multimodal-fresh ruby scripts/generate-binary-consumer.rb --multimodal
    rtk proxy env ODAI_MULTIMODAL_CONSUMER_PROJECT=artifacts/Multimodal-fresh/MultimodalBinaryConsumer/MultimodalBinaryConsumer.xcodeproj sh scripts/verify-apple.sh

Ruby/xcodeproj/json需同一环境；本机工具测试指定RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby，ruby命令也使用该wrapper。未传ODAI_MULTIMODAL_CONSUMER_PROJECT时，Apple脚本明确打印binary consumer未检查，不能算分发验收。

Android坐标io.ondeviceai:multimodal:0.1.0-SNAPSHOT。POM仅Core、Kotlin标准库、既有coroutines1.10.2。task-3-maven-project.log 175tasks通过local发布/project consumer/lint；task-3-maven-consumer.log 35tasks通过实际Maven消费，debugRuntimeClasspath使用坐标而非project依赖，不含其他SDK能力/模型。

    rtk proxy ./gradlew :android:sdk-core:publishToMavenLocal :android:multimodal:publishToMavenLocal
    rtk proxy ./gradlew -PusePublishedSdk=true :examples:android-multimodal-native:assembleDebug :examples:android-multimodal-native:dependencies --configuration debugRuntimeClasspath --console=plain

消费者均为链接样例，明确unavailable，不用伪造模型结果宣称成功；未安装或启动设备App。

## 审查与下一入口

每Task作者范围自审，最终另行按Review Focus审读43929e4..e1ff006并保留final-review.md，未发现自审范围内阻断项。用户要求当前会话执行、禁止子代理，独立审查仍pending，不声明APPROVE/可发布。

下一独立工作包：可选Apple MLXVLM后端。先复核SmolVLM2/MLX固定revision与现有资产、模型lease/加载/释放/取消，再设计与接入；基础模块不引入MLX，不下载新资源。后续仍需ModelStore adapter、真实图片/短视频、KMP bridge/Demo、Android第二平台、质量/延迟/峰值资源、最低系统与独立审查。本包不关闭P9或roadmap。

[视频Demo](video-bridge-demo.md)的iOS runner初始化超时/新UI未通过、旧Video/AppleVideo/音轨复审、P6实录/Android ASR资源、P7质量/Android/native export中途取消及其余roadmap缺口保留，不重复已确认外部门槛的重试。

两个保护文件hash不变且未暂存：demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj为b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5；未追踪docs/validation/face-identity.md为6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822。无push/远端制品发布。
