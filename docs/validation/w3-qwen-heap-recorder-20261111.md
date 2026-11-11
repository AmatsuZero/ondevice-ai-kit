# W3 heap记录器中间验收

状态：纯记录器片段13组100断言通过；整份wrapper、Android构建/实包、独立复核及新设备合同仍未完成。W3/R4/R5保持open。

用户选免费`hy3`接续后，新整份wrapper调用自然939.547520875s结束。CLI退出0，真实result为`error_during_execution/empty_model_response`，15Read/0Write/零文件、无工具错误、context未变；不是429，也没有父端时长终止。内部原因仍未知，保留原调用，没有重发。原hy4free429保持原件。

按已记录的小交付恢复方式拆成纯heap记录器和后续接线，最终仍只集成一份`QwenHeapInstrumentation.kt`，未扩大输入/调用/设备范围。记录器独立调用198.536061041s实际交付3982B，原稿Kotlin `-Werror`和13组80断言通过；源码复核发现`JSONObject.isNull`也接受缺key，补存在性及首错身份检查后原稿12组失败。独立hy3定点修正67.667290209s，6Read/1Write、无工具错误、context保持，最终4349B、SHA256 `267e8049709b4d948ba83b3fce39d016b32d564733705706ef4beb1898b4996e`。

最终真实host13组100断言通过，覆盖四个回调逐个失败、已读数值前缀与未读显式null、零值/负值、时钟倒退、stage快照不被后续改动污染、快照不重新采样、清理期后续故障不覆盖首错、128上限及溢出后原异常对象身份。依赖真实既有`CaseState/StageState`和本机JSON-java；没有Android或JNI调用，不能据此证明平台API行为、wrapper清理接线或真实内存收益。

父验收入口：`probes/android-qwen-heap/check_recorder.py`、`RecorderChecks.kt`。本机证据：`.omx/artifacts/w3-qwen-ort-lifecycle-20261111/heap-hy3-resume1/`，包括`recorder-absent-red`、`recorder-first-host`、`recorder-null-fields-red`、`recorder-corrected-host`和`recorder-accepted`。这些ignored原件不随普通clone分发。原始调用分别保留于`/private/tmp/odai-hy3-w3-qwen-heap-wrapper-20261111-resume1/`、`odai-hy3-w3-heap-recorder-20261111/`、`odai-hy3-w3-heap-recorder-null-fix-20261111/`。

下一项是独立接线补丁，须核基线摘要、唯一原文和记录器body原字节，再运行整份wrapper故障检查。只有源码/host、实包和双复核全部通过后才能冻结新run；仍仅index25/公开1776、原两阶段与arena-off、1.5GiB/512MiB/120s保持。零设备操作、零模型调用/下载、未改生产或普通App。
