# JVM raw reconstruction evidence metadata（2026-11-15）

本目录保存 W3 JVM 原始重建的 evidence metadata 与 README 说明。Git material dir 不包含 binaries/JARs；actual compiler class files、jars 与 raw logs 保留在 `.omx/artifacts/w3-jvm-rebuild-20261115T153000Z/`。最终报告见 [W3 JVM 原始重建证据报告](../../../docs/validation/w3-jvm-raw-reconstruction-20261115.md)。

## Provenance / preservation

- source resolution 基于 commit `af2d8d81dbfa61c4cea4c568da78f3982edca005`；22 个 resolved ordinary files 共 141269B，早前 1568B symlink text 未用。
- 完整 immutable original mapping 保留；primary driver 只消费 5 个 source identity fields，验证 22 个 resolved normal-source files 的 SHA256 与 Git blob，不读取或重新验证 symlink blob。link blob/target resolution 由先前独立 provenance audit 验证并保留在完整 mapping 中；sourceAndMapUnmodified=true，编译不修改源文件。
- 304 selected sources、68 production notices、161 frozen files 未变；protected baseline 175 files 在自身 handoff status prefix 之后保留原始内容字节，除该显式 prefix 外未改，snapshot 日期保留。

## Acquisition / compile evidence

- 默认代理 Kotlin compiler jar transfer readTimeout after 5505024B，partial SHA256=`ec89921aa26c37cb564224e85689027a00a501cd489c7dbba81f959677c1c94e`，`compilerStarted=false`，无 provider quota error，partial/error 保留。
- 一次 invocation-only direct same official Maven host recovery：189.285s，8 tools，60939801B，5 compiler-tool JAR downloads + 3 existing cache copies；per-file Maven sidecar/cache SHA1 与 local SHA256 校验。无 global config/cache 写改，production dependencies 未变；可说无新增 ASR/model/runtime product binary，但不可说零总体 binary download。
- direct CLI Kotlin `1.7.20`、Java `21.0.7`、JVM target `1.8`、module `sherpa_onnx_release`；compile classpath 为 android34 `android.jar` + stdlib `1.7.20` + annotations `13.0`，未重放 full Gradle/AGP/AndroidX classpath。direct CLI 的 8 tool jars 与隔离 compile classpath 足以复现 125 个 payload members；Kotlin stdlib `1.7.20` 不是 product runtime version change。
- actual compiler run ONCE，3.059s，exit 0，`automaticRetry=false`，`flagSweep=false`；compiler stderr 仅 `Tts.kt` 的 `dictDir` unused warning。

## Cross-validation result

- original AAR SHA256=`a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；内嵌 classes.jar 245045B，SHA256=`0ee4991c56c19ef5a8a573b01891193d7298ffe7ebcc68f42b944361d8869d93`。
- parent direct Info-ZIP extraction 后，按 archive order 对 full 125 member stream 与 actual rebuilt filesystem 比对：124 class 与 `META-INF/sherpa_onnx_release.kotlin_module`，total payload 553965B，全部 raw byte equal；original archive-order stream verification per receipt，不表示 rebuilt archive ordering proof，missing/extra/different 为空。
- 8 unit tests 与 4 parent real payload negative controls passed；supplied-context hy3 review 无 finding。parent 已接受 single-execution raw byte equality 证据；P1/P4/W3 仍 open，不设置唯一密码学证明要求。
- 边界：无 normalization/SMAP/annotation stripping/flag sweep；不声称 rebuilt JAR ZIP envelope equality、DEX equivalence、native、signed distribution、P4、full P1 closure 或 W3 completion。429 source/native routes 保持停止，无请求、重试、镜像或付费 lane。不请求额外下载或运行；实际范围为上述 5 个 compiler-tool JAR 下载、3 个 cache copies 与一次编译器调用。无设备、AiAi、mic、inference、model、ASR runtime product binary、新 APK 或 signing；生产依赖未变。
