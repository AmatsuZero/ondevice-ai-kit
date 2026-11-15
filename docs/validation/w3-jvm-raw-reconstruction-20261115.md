# W3 JVM 原始重建证据报告（2026-11-15）

状态：parent 已接受有界证据范围：单次实际编译后固定 AAR 的 124 class + `META-INF/sherpa_onnx_release.kotlin_module` 共 125 个原始载荷成员逐字节相等；本次 evidence review 在该范围内完成。本增量实际下载 5 个 compiler-tool JAR、复制 3 个 cache tool（8 tools，60939801B）并调用编译器一次（3.059s）。生产依赖未变；无 ASR runtime/model/device/AiAi/mic/inference、新 APK 或 signing。

阻断项：无。范围变化：由编译前准备快照更新为单次实际编译后的 JVM class/module 原始载荷逐字节证据闭环；仅接受有意义的 P1 JVM 子组，不代表 P1 closure、P4 或 W3 完成。preparation-receipt 与 integrated-verification 是 compile 前历史快照，时间边界保持不变。

相关证据：[source resolution](w3-jvm-source-resolution-20261115.md)、[app variants delivery](w3-app-variants-delivery-20261115.md)、[closure scope](w3-closure-scope-20261115.md)、[compiler probe README](../../probes/sherpa-jvm-reconstruction/README.md)、[evidence metadata README](../../probes/sherpa-runtime-materials/jvm-raw-reconstruction-20261115/README.md)。

## Source 与不变量

- sourceCommit=`af2d8d81dbfa61c4cea4c568da78f3982edca005`；22 个 source 均为 REAL resolved ordinary files，总计 141269B。早前 1568B symlink text 未用于编译。
- 完整 immutable original mapping 保留于 `probes/sherpa-runtime-materials/2.0.1-jvm-inputs-20261115/source-link-resolution.json`，SHA256=`dbfd433461badad891692963c16496bbe062ba5f03a1c322567f01cd118128db`。
- primary exact-field projection 22 hashes 只消费 5 个源身份字段：`resolvedEvidencePath`、`resolvedMode`、`resolvedBytes`、`resolvedSHA256`、`resolvedGitBlobSHA1`；projection 为 `.omx/artifacts/w3-jvm-rebuild-20261115T153000Z/source-map-projection.json`，SHA256=`fe829a5ab832b7ba9e65e290285ccfc7717780c297e50d8d7158ad40bc4a7423`；`sourceAndMapUnmodified=true`。
- 该 projection 验证的是 22 个 resolved normal-source files 的 SHA256 与 Git blob；不读取或重新验证 symlink blob。link blob/target resolution 已由先前独立 provenance audit 验证并保留在完整 mapping 中；编译未修改这些文件。
- 304 selected sources、68 production notices、161 frozen files 未变；protected baseline 175 files 在自身 handoff status prefix 之后保留原始内容字节，除该显式 prefix 外未改，snapshot 日期保留。

## Toolchain 获取与配置边界

- 默认代理传输 Kotlin compiler jar 以 readTimeout after 5505024B 终止：partialPath=`.omx/artifacts/w3-jvm-rebuild-20261115T153000Z/toolchain/kotlin-compiler-embeddable-1.7.20.jar`，partialSHA256=`ec89921aa26c37cb564224e85689027a00a501cd489c7dbba81f959677c1c94e`；`compilerStarted=false`，`providerQuotaErrorObserved=false`，partial/error 保留。
- 一次 distinct invocation-only DIRECT same official Maven endpoint recovery：`ProxyHandler({})`，无 global changes，用时 189.285s；8 tools 共 60939801B，其中 5 个 compiler-tool JAR 下载、3 个 existing cache copy；per-file 以 Maven sidecar SHA1 或 cache content-id SHA1 加 local SHA256 校验，`allVerified=true`，`automaticRetry=false`，`automaticFallback=false`。网络 route 与 chunk/change parameters 不同，不能唯一归因原因。无 ASR/model/runtime product binary 下载，不能表述为零总体 binary download。
- compile-prerequisites：`toolArtifactsVerified=8`，`compilerVersionExitCode=0`，version=`1.7.20`，`configSHA256=20b343277856f9e1f61b92855c777572873df078eb37e782baccaabe50b5c23a`，`globalConfigOrProductionDependenciesChanged=false`，`newASRRuntimeOrModelDownloads=0`。
- 直接 CLI（非 AGP）：Kotlin `1.7.20`，Java `21.0.7`，JVM target `1.8`，module-name `sherpa_onnx_release`。未重放原始 Gradle/AGP/AndroidX 完整 classpath；direct CLI 的 8 tool jars 与隔离 compile classpath 足以复现 125 个 payload 成员，Kotlin stdlib `1.7.20` 不是 product runtime version change。
- run config：repoRoot=`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`；outputRoot=`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation/.omx/artifacts/w3-jvm-rebuild-20261115T153000Z/run-01`；java=`/Users/daubert/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.7+6/Contents/Home/bin/java`。
- compilerClasspath：`.omx/artifacts/w3-jvm-rebuild-20261115T153000Z/direct-recovery-01/toolchain/` 下 `kotlin-compiler-embeddable-1.7.20.jar`、`kotlin-stdlib-1.7.20.jar`、`kotlin-script-runtime-1.7.20.jar`、`kotlin-reflect-1.7.20.jar`、`kotlin-daemon-embeddable-1.7.20.jar`、`trove4j-1.0.20200330.jar`、`jna-5.6.0.jar`、`annotations-13.0.jar`。
- compileClasspath：`/Users/daubert/Library/Android/sdk/platforms/android-34/android.jar`、上述 `kotlin-stdlib-1.7.20.jar`、上述 `annotations-13.0.jar`。sourceMap=`.omx/artifacts/w3-jvm-rebuild-20261115T153000Z/source-map-projection.json`。
- runtimeAAR=`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation/artifacts/verification/sherpa-asr-20261105/inputs/m2/com/k2fsa/sherpa/onnx/sherpa-onnx/2.0.1/sherpa-onnx-2.0.1.aar`，expectedAARSHA256=`a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；其中 classes.jar 245045B，SHA256=`0ee4991c56c19ef5a8a573b01891193d7298ffe7ebcc68f42b944361d8869d93`。

## Process 与 cross-validation

- 驱动/测试上下文：8 meaningful unit tests，exit 0，0.118s；4 parent real payload negative controls rejected；`exactSelfComparisonControlPassed=true`；`integratedSourceMatchesCandidate=true`；frozen=161，protected=175。supplied-context hy3 review 无 finding，`accept_for_single_execution`，33.881s；driver candidate 106.001s exact source integrated；primary executed tests/compile/crosscheck。
- integrated-verification 与 preparation-receipt 均为 compile 前历史快照，记录 `compilerStarted=false`、`byteReconstructionVerified=false`、`W3Complete=false`；这些字段不否定后续 parent raw verification。
- actual compiler run ONCE：started `2026-11-15T15:50:49.559729+00:00`，ended `2026-11-15T15:50:52.619256+00:00`，elapsed 3.059s，exit 0，`automaticRetry=false`，`flagSweep=false`；compiler invocations=1。compiler stdout 空；stderr 仅有 `Tts.kt` 中 `dictDir` never used 的 warning。
- parent cross-check：direct Info-ZIP original extraction=true；从 fixed AAR 的 classes.jar 取 original member stream，与 actual rebuilt filesystem 按 archive order 对 125 个 payload members 全流比较；`originalMemberListingEqualsRebuiltFileSet=true`，`classMembersByteEqual=124`，`moduleMetadataByteEqual=true`，`totalPayloadMembers=125`，`totalPayloadBytes=553965`，`allByteEqual=true`，missing/extra/different 为空，`noNormalizationOrStripping=true`。该比较由 parent 完成。
- per receipt 保存每个成员的 path、bytes、SHA256 与 direct Info-ZIP equality；无 normalization、SMAP rewrite、annotation stripping 或 flag sweep。

## 结论边界

接受：22 源精确投影、单次编译、固定 AAR 的 124 class + `META-INF/sherpa_onnx_release.kotlin_module` 共 125 个原始载荷成员逐字节相等；这关闭了 JVM class+module 子组的 SourceFile/name-only evidence gap，不只是 class-name comparison。

不接受/不推断：完整 AGP/environment input closure、DEX equivalence、native source build、signed distribution、P4、rebuilt JAR ZIP envelope equality、signing、consumer APK rebuild、device/mic/AiAi/inference、model 或 ASR runtime download。生产 JVM dependency versions 未变。原始目标未缩小；R4/R5/P1-P4/W3 仍 open；本结论不设置唯一密码学证明要求，也不提出新的下载或运行。
