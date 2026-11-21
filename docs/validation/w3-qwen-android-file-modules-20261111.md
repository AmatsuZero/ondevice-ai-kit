# W3 Android FILE supporting modules

Three free external CBC candidates are integrated byte-for-byte into the isolated probe. They do not change the ordinary App, production backend, model files or dependencies. Android instrumentation, APK inspection and physical file execution remain separate gates.

| Module | SHA-256 | Parent acceptance |
| --- | --- | --- |
| `QwenFileInputs.kt` | `02567c135edb359b98ed4466501e58d6557c82f0a02dc42d008698c0db52e6d1` | Actual Kotlin, 8 groups / 406373 assertions |
| `QwenFileActivity.kt` | `dd859f76faff8e222207798294ff91a0598a064c9ab4a3e1b009b87eadaecd8a` | Actual Kotlin with Android lifecycle doubles, 6 assertions |
| `QwenFileReport.kt` | `74cb2ab4b94bbf5f75e61f084983556fa511f6b3a56be75d05b95b2003fa6736` | Actual Kotlin / existing org.json, 22 assertions |

Inputs verify the frozen manifest and all 65 original PCM files before inference, preserve their IDs/order/reference text, reject corruption, and clear owned temporary byte buffers. Sample conversion checks compare complete float bit patterns with original s16LE PCM. The wrapper must clear returned float arrays after each case.

Activity is a renamed version of the accepted visible load probe, with per-instance resume/pause/destroy acknowledgements. It performs no capture, inference, service operation or settings change. Report serialization preserves explicit nulls, raw strings, actual stage counters, integer timing precision above 2^53 and independent snapshots.

All three missing implementations were checked RED before dispatch. The first report candidate omitted `FileRow` and `FileRunState`; actual Kotlin compilation failed. A distinct 27.037-second hy3 correction added only those declarations and passed the same checks. Original candidate SHA `7b818b8f67f495d644a92a286baf60bd357bf2f673c0707e4d8d871ff1467ef8`, compile errors and raw helper events are retained. The initial report request completed in 69.371 seconds. Inputs had two missing-path Read errors before delivery; the Activity receipt's estimated byte count was corrected to the actual 2224 bytes.

The earlier two-file hy4free wrapper request naturally ended after 2119.316 seconds with CLI0/result success but only a DSML tool marker, eight Read calls, zero writes and zero files. It was rejected without a timeout or quota claim. The remaining wrapper is a distinct thin instrumentation task consuming these modules, not a replay of that request.

Local evidence: `.omx/artifacts/w3-qwen-file-20261111/{inputs-candidate-first,activity-candidate-first,report-candidate-first,report-candidate-dto-fixed,helpers}/`. Each helper used its own tested macOS write sandbox restricted to its task root and actual CLI PID trace directory. Parent checks made zero real JNI/device calls. No new alias is installed by these checks; the previously consumed load-only run is not repeated.
