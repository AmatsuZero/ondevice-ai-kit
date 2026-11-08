## 2026-11-07 真机补充验收

主代理已用隔离包名/测试authority完成本次当前源码装机；两修复共四真实场景4/4通过，私有持久终态及摘要独立回读。临时双包及精确暂存清除、原App/test/runner/25私有/4SR/权限/TTS保持，返回普通App。证据`artifacts/verification/video-cleanup-device-20261107/summary.json`。仅同进程失败清理重试，不证明进程死亡恢复、SR质量或生产预算；下方未安装描述是历史检查点。

# 视频导入失败清理重试（2026-11-07）

**缺陷：** 最终清理忽略 `delete=false`，未发布输出又不在注册表中，失败导入后无法重试清理。**修复：** 在锁内将失败的未发布输出登记为 retired，复用现有 collector 做精确、非递归删除；`IOException` 时保留所有权以便重试。源码：`demo/androidApp/src/main/kotlin/io/ondeviceai/demo/VideoInputStore.kt`。

**验证：** 2 个定向测试先红（2 failure、0 error），再绿（6/6）；全量默认 Demo 60/60、0 failure/0 error。`lintDebug` 0 error、10 warning；`assembleDebug` 通过（40.306s）。Copilot 独立定向审查无已确认发现；父级已修正审查证据措辞，审查者未运行测试。证据：`../../artifacts/verification/video-import-cleanup-retry-20261107/summary.json`、`../../artifacts/verification/video-import-cleanup-retry-20261107/red-failed-import-result.json`、`../../artifacts/verification/video-import-cleanup-retry-20261107/green-targeted-result.json`、`../../artifacts/verification/video-import-cleanup-retry-20261107/full-demo-result.json`、`../../artifacts/verification/video-import-cleanup-retry-20261107/build-and-lint.json`。

**边界：** 未安装 APK、未做设备验证；本修复不改变导入准入行为；进程死亡恢复、F06 质量/资源/预算及路线图仍未闭合。
