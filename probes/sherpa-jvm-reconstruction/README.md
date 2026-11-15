# sherpa JVM reconstruction probe

本目录记录 W3 JVM 重建 probe 的 actual driver/test evidence，替换旧 preparation-only README；其他已有编辑保持不变。最终报告见 [W3 JVM 原始重建证据报告](../../docs/validation/w3-jvm-raw-reconstruction-20261115.md)。

## Input / source boundary

- sourceCommit=`af2d8d81dbfa61c4cea4c568da78f3982edca005`。22 个 source 是 REAL resolved ordinary files，共 141269B；早前 1568B symlink text 未使用。
- driver 接收 exact-field projection：只用 `resolvedEvidencePath`、`resolvedMode`、`resolvedBytes`、`resolvedSHA256`、`resolvedGitBlobSHA1` 五个 identity fields；验证 22 个 resolved normal-source files 的 SHA256 与 Git blob，不读取或重新验证 symlink blob。link blob/target resolution 由先前独立 provenance audit 验证并保留在完整 mapping 中；source/map 未改，编译未改源文件。
- parent preflight：sourceBytes=141269，originalMembers=125，originalClassCount=124，4 个 parent real payload negative cases rejected，self-comparison control passed，preflight 阶段 `newCompileStarted=false`。
- unit tests=8，exit 0，0.118s；integrated source matches candidate；frozen=161，protected=175。protected baseline 175 files 在自身 handoff status prefix 之后保留原始内容字节，除该显式 prefix 外未改，snapshot 日期保留。pre-compile integrated snapshot 中 `byteReconstructionVerified=false`、`W3Complete=false` 属于历史时点，不覆盖后续 raw verification。

## Toolchain / process

- direct CLI Kotlin compiler `1.7.20` version confirmed；Java `21.0.7`；JVM target `1.8`；module-name `sherpa_onnx_release`。
- 默认代理传输 timeout after 5505024B，partial/error 保留。恢复为一次 invocation-only direct same official Maven endpoint，189.285s，8 tools 共 60939801B：5 下载、3 cache copies；Maven sidecar/cache SHA1 与 local SHA256 校验，无 global config 或 production dependency 改动，无 provider quota error。网络原因不可唯一归因。
- actual compile 仅一次：elapsed 3.059s，exit 0，started `2026-11-15T15:50:49.559729+00:00`，ended `2026-11-15T15:50:52.619256+00:00`，`automaticRetry=false`，`flagSweep=false`。stderr 仅 `dictDir` unused warning，stdout 空。
- compile classpath 边界：android34 `android.jar` + Kotlin stdlib `1.7.20` + annotations `13.0`；未重放原始 Gradle/AGP/AndroidX full classpath。direct CLI 的 8 tool jars 与隔离 compile classpath 足以复现 125 个 payload members；Kotlin stdlib `1.7.20` 是编译工具版本，不代表产品 runtime version change。

## Byte result / boundary

- fixed AAR SHA256=`a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；内嵌 classes.jar 245045B，SHA256=`0ee4991c56c19ef5a8a573b01891193d7298ffe7ebcc68f42b944361d8869d93`。
- parent 使用 direct Info-ZIP extraction，并按 archive order 对 original member stream 与 rebuilt filesystem 的 125 个 payload members 做 full stream comparison：124 class + `META-INF/sherpa_onnx_release.kotlin_module`，共 553965B，全部 raw byte equal，missing/extra/different 为空。
- 无 normalization、SMAP、annotation stripping、flag sweep；不声称 rebuilt JAR ZIP envelope equality，不推断 DEX/native/signed distribution/P4/full P1 closure/W3 completion；不设置唯一密码学证明要求。
- 不请求额外下载或运行；实际范围为上述 5 个 compiler-tool JAR 下载、3 个 cache copies 与一次编译器调用。无设备、AiAi、mic、inference、model、ASR runtime product binary、新 APK 或 signing；consumer APK rebuild 不在范围。
