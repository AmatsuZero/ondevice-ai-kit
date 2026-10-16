# Benchmark protocol v1

人脸：固定输入 SHA256，至少5次预热+30次测量；冷启动单独记录。输出最近秩法 p50/p95 与样本数，未测阶段为空数组。系统模型 revision=null；禁止把下载或桥接往返误标成纯模型加载/推理时间。

按设备、OS、SDK commit、能力、实际后端、模型revision、预处理、输入、计时方法、证据类型和内存测量方法分组。跨组不合并分数。质量保留逐 run 人工可复核字段；不同能力不输出总分。

当前 schema v1 能耗固定 null/unmeasured，后续有可靠仪器后另行扩展 schema；热状态可记录原始平台标签。峰值内存没有可靠采集时为null，当前 resident size 不冒充峰值。

导出后：`python3 tools/benchmark_report.py run.json --output artifacts/report.json`。输入和设备原始报告默认仅留本地，不提交照片或设备唯一标识。

## P5 当前证据（2026-10-16）

- `python3 -m unittest discover -s tools/tests -p 'test_benchmark_report.py' -v`：4/4 通过，覆盖空样本、单样本、最近秩 p50/p95、分组隔离、单位/负延迟/能耗校验。
- Apple：`sh scripts/verify-apple.sh` 通过；Swift host tests、KMP simulator tests、bridge、PhotoSubmission、CameraLifecycle、原生消费者和 Demo 模拟器构建通过。冻结 `acf35731a155bb473ad326e774b75135108f4849` 后，在 iPhone 16 Pro Max / iOS 27.0.1 上用 `apple.vision.face` 完成 5 次预热+30 次 inference；原始报告为 `artifacts/P5/ios27-face-frozen.json`，汇总为 `artifacts/P5/ios27-face-frozen-report.json`，inference p50/p95=`2.732208/4.622583 ms`，thermal=`nominal`。
- Android：`sh scripts/verify-android.sh` 通过，290 tasks；本地 Maven 发布及 `examples:android-native:assembleDebug -PusePublishedSdk=true` 通过。冻结 `acf35731a155bb473ad326e774b75135108f4849` 后，在 TECNO KM9 / Android 15 上重新构建并运行 `android.mlkit.face.bundled`，原始报告为 `artifacts/P5/android-api35-face-frozen.json`，汇总为 `artifacts/P5/android-api35-face-frozen-report.json`，inference p50/p95=`11.446077/20.814462 ms`。
- Android 真机：TECNO KM9 / Android 15，实际后端 `android.mlkit.face.bundled`。原始报告为 `artifacts/P5/android-api35-face.json`，汇总为 `artifacts/P5/android-api35-report.json`（均为忽略目录）。固定输入为本机生成的合成 Orientation 6 JPEG，fixtureHash 为 `f71b7b19…e2f383`；5 次预热 p50/p95=`25.017538/39.179077 ms`，30 次 inference p50/p95=`16.876615/22.361923 ms`。该运行的 `sdkCommit` 为 `working-tree`，只能作为开发快照证据，不能作为冻结 SDK 发布基线。
- P5 尚未完成：Android API26/iOS15 最低系统 benchmark、峰值内存/能耗测量仍未完成。两端现代设备样本均已绑定冻结 SDK commit；iOS 本次仅记录系统提供的热状态，未将其当作资源通过结论。
