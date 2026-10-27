# Benchmark protocol v1

人脸：固定输入 SHA256，至少5次预热+30次测量；冷启动单独记录。输出最近秩法 p50/p95 与样本数，未测阶段为空数组。系统模型 revision=null；禁止把下载或桥接往返误标成纯模型加载/推理时间。

按设备、OS、SDK commit、能力、实际后端、模型revision、预处理、输入、计时方法、证据类型和内存测量方法分组。跨组不合并分数。质量保留逐 run 人工可复核字段；不同能力不输出总分。

当前 schema v1 能耗固定 null/unmeasured，后续有可靠仪器后另行扩展 schema；热状态可记录原始平台标签。峰值内存没有可靠采集时为null，当前 resident size 不冒充峰值。

导出后：`python3 tools/benchmark_report.py run.json --output artifacts/report.json`。输入和设备原始报告默认仅留本地，不提交照片或设备唯一标识。

每次真实运行生成独立`runId`。一次汇总的所有输入必须具有不同的`runId`：重复文件、复制文件或同ID但元数据/测量值不同的报告都会被拒绝，避免重复计数或隐去冲突。不同ID的独立运行即使测量值相同，也保留各自样本。输出必须与每份输入分离；相同路径、软链接和现有硬链接别名均被拒绝，普通独立报告路径仍可重新生成。

## 2026-10-27 报告输入完整性修复

实际旧iPhone P5报告重复传入时，原工具将30次inference统计成60次。新增聚合与真实CLI回归先出现9项失败/子用例，修复后聚焦**9/9**通过。完整Python工具套件在显式`RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`及正常Swift缓存权限下**148/148**通过；首轮系统Ruby2.6混用3.4gems与缓存权限失败完整保留。独立code-reviewer对工具、测试和修复计划的限定审查为**APPROVE、0 findings**。

原`927b7749`双端报告分别及合并重算均与修复前有效输入的输出完全一致：iOS30样本、p50/p95=2.432/3.065ms；Android30样本、10.853538/18.088231ms。原始文件hash未变，重复真实输入现在拒绝且不创建输出。原始报告复核、RED/GREEN、完整套件和审查记录在`artifacts/verification/benchmark-report-integrity-20261027/`。这是现有报告工具修复与历史证据重算；P11冻结候选、统一设备任务/质量阈值、资源和最终发布结论仍待完成。

## P5 当前证据（2026-10-17）

- `python3 -m unittest discover -s tools/tests -p 'test_benchmark_report.py' -v`：4/4 通过，覆盖空样本、单样本、最近秩 p50/p95、分组隔离、单位/负延迟/能耗校验。
- Apple：`sh scripts/verify-apple.sh` 通过；Swift host tests、KMP simulator tests、bridge、PhotoSubmission、CameraLifecycle、原生消费者和 Demo 模拟器构建通过。冻结 `acf35731a155bb473ad326e774b75135108f4849` 后，在 iPhone 16 Pro Max / iOS 27.0.1 上用 `apple.vision.face` 完成 5 次预热+30 次 inference；原始报告为 `artifacts/P5/ios27-face-frozen.json`，汇总为 `artifacts/P5/ios27-face-frozen-report.json`，inference p50/p95=`2.732208/4.622583 ms`，thermal=`nominal`。
- Android：`sh scripts/verify-android.sh` 通过，290 tasks；本地 Maven 发布及 `examples:android-native:assembleDebug -PusePublishedSdk=true` 通过。冻结 `acf35731a155bb473ad326e774b75135108f4849` 后，在 TECNO KM9 / Android 15 上重新构建并运行 `android.mlkit.face.bundled`，原始报告为 `artifacts/P5/android-api35-face-frozen.json`，汇总为 `artifacts/P5/android-api35-face-frozen-report.json`，inference p50/p95=`11.446077/20.814462 ms`。
- Android 真机：TECNO KM9 / Android 15，实际后端 `android.mlkit.face.bundled`。原始报告为 `artifacts/P5/android-api35-face.json`，汇总为 `artifacts/P5/android-api35-report.json`（均为忽略目录）。固定输入为本机生成的合成 Orientation 6 JPEG，fixtureHash 为 `f71b7b19…e2f383`；5 次预热 p50/p95=`25.017538/39.179077 ms`，30 次 inference p50/p95=`16.876615/22.361923 ms`。该运行的 `sdkCommit` 为 `working-tree`，只能作为开发快照证据，不能作为冻结 SDK 发布基线。
- 历史提交复核：`74b6bbfa8e4b094590f942081a2ae0a916634777` 的 Android Demo 在同一 TECNO KM9 / Android 15 上重新安装运行。原始报告为忽略目录 `artifacts/P5/current/android-api35-face-final.json`，汇总为 `artifacts/P5/current/android-api35-face-final-report.json`；fixtureHash=`29296558c4b8c53dc1cc620daf00f14ae7e5b7dc8ea864673336599a72cfec83`，5 次预热 p50/p95=`20.912/35.354769 ms`，30 次 inference p50/p95=`17.103616/24.120385 ms`。报告通过 schema validator，实际后端为 `android.mlkit.face.bundled`；峰值内存、能耗和热状态按 schema 保持未测。同名导出在 Android DocumentsUI 中会追加 `(1)`、`(2)`，提取前必须按文件时间与报告内 SHA 核对；此前误取原始文件而记录的完整 SHA 和指标已在本条更正。
- 当前冻结复核：`927b7749df51b40a39c12bf6c04e2c078900478f` 在同一现代设备矩阵上重新运行。iPhone 16 Pro Max / iOS 27.0.1 的 `apple.vision.face` 原始报告为 `artifacts/P5/current/ios27-face-927b774.json`，报告为 `artifacts/P5/current/ios27-face-927b774-report.json`，fixtureHash=`55bc987e4be1a88ee5f1ab352d437dd1030542eab912653bc320349a3e064023`，inference p50/p95=`2.432/3.065 ms`。TECNO KM9 / Android 15 的 `android.mlkit.face.bundled` 原始报告为 `artifacts/P5/current/android-api35-face-927b774.json`，报告为 `artifacts/P5/current/android-api35-face-927b774-report.json`，fixtureHash=`29296558c4b8c53dc1cc620daf00f14ae7e5b7dc8ea864673336599a72cfec83`，inference p50/p95=`10.853538/18.088231 ms`；两组均为 5 次预热+30 次 inference，报告通过 schema validator。iOS thermal=`nominal`，Android thermal=null；两组峰值内存和能耗均为未测。两端输入均为合成空白图，未评估检出率或坐标质量；不同输入/设备的延迟不能直接比较，也不据此宣称性能提升。冷启动/加载没有独立采样，保留空数组。
- 当前分发复核：以 `927b7749df51b40a39c12bf6c04e2c078900478f` 为源码基线，加上本次仅补入已存在源文件的分发工程修复后，生成 Apple device/simulator archive 与四个 XCFramework，并由独立 BinaryConsumer 构建通过；补入此前遗漏的 `ODAIKitFace/FaceDetectionClient.swift` 源文件后，消费者可以显式调用 `FaceDetectionClient`。四个 Android 人脸 Maven artifact 重新发布到本地仓库，`examples:android-native:assembleDebug -PusePublishedSdk=true` 通过，消费者实际调用 `FaceDetectionClient` 的显式后端选择。具体日志：`artifacts/verification/p5-current-archive-{iphoneos,simulator}.log`、`p5-final-binary-consumer.log`、`p5-current-android-publish.log`、`p5-selected-maven-consumer.log`。新 archive 和 XCFramework 在 `artifacts/P5/current/{distribution,xcframeworks}`；消费者在同目录 `BinaryConsumer`。本次没有修改算法源码，不为后续文档提交反复刷新 benchmark 的冻结 SHA。
- P5 尚未完成：Android API26/iOS15 最低系统 benchmark、峰值内存/能耗测量仍未完成。两端现代设备样本均已绑定冻结 SDK commit；iOS 本次仅记录系统提供的热状态，未将其当作资源通过结论。

语音模块的 XCFramework/Maven 消费者尚未纳入本包；系统 TTS/ASR 属于 P6 的后续分发验收，微软或其他第三方后端仍保持可选插件边界。

导出遗漏的回归证据：未修复的当前源码 archive 使真实 Swift 消费者 typecheck 报 `cannot find FaceDetectionClient/FaceDetectionRegistration`（`artifacts/verification/p5-current-client-export-red.log`）；补入 target 后同一消费者通过（`p5-current-client-export-green.log`），最终 BinaryConsumer（`artifacts/verification/p5-final-binary-consumer.log`）进一步验证编译和链接。Apple/Android 原生示例均改为显式后端调用，防止只验旧 detector API。

本次统一检查日志为 `artifacts/verification/p5-current-verify-{apple,android}.log`：Apple 33/33 XCTest、KMP 测试、桥接/照片/相机检查与构建通过；Android 369 tasks（29 executed、340 up-to-date），不声称全部 JVM 测试重跑。Python benchmark 工具测试 4/4。源码文件哈希、工程配置哈希和分组元信息见本地 `artifacts/P5/current/p5-verification-manifest.json`。独立消费者的证据限于编译/链接，不是分发 App 的真机运行或正式仓库发布验收。

最终源码检查：补全 iOS 示例的 Client 清理后，独立 BinaryConsumer 重新同步源码并构建通过，Client 创建成功后统一通过 `client.close()` 清理；构造失败才直接关闭尚未转交的 detector。Android 显式消费者仍以 `p5-selected-maven-consumer.log` 为最终证据；随后重复全套 Android 脚本因自动审批服务 HTTP 429 未执行，不追加通过记录。

本包独立审查已通过：修复 reviewer 指出的 iOS 示例清理入口后完成最终复核，无阻塞项。该结论限于分发 target、两端原生消费者与本包证据文档，不扩大为全 SDK 或 P6 审查通过。
