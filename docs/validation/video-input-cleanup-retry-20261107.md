## 2026-11-07 真机补充验收

主代理已用隔离包名/测试authority完成本次当前源码装机；两修复共四真实场景4/4通过，私有持久终态及摘要独立回读。临时双包及精确暂存清除、原App/test/runner/25私有/4SR/权限/TTS保持，返回普通App。证据`artifacts/verification/video-cleanup-device-20261107/summary.json`。仅同进程失败清理重试，不证明进程死亡恢复、SR质量或生产预算；下方未安装描述是历史检查点。

# VideoInputStore 清理重试验证

## 缺陷与修复

删除失败时，旧逻辑忽略 `delete=false`，仍丢弃路径所有权；`close` 已关闭时的提前返回又阻止后续清理重试。最小修复在删除成功前保留登记项，`remove` 可重试已退役条目，并允许重复 `close` 重试。源码：`demo/androidApp/src/main/kotlin/io/ondeviceai/demo/VideoInputStore.kt`。

## 验证与边界

父代理提供的记录为 RED 2 failures/0 errors → GREEN 4 tests → 完整 defaultDemo 58 tests；`lintDebug` 有 10 warnings、0 errors，`assembleDebug` 成功。所提供的 Copilot 独立聚焦审查无 findings。未安装 APK，未运行设备；仅验证公开入口的同进程重试。

测试夹具是精确归属路径上的非空目录：只移除 sentinel，再要求 store 删除空目录；这不是普通设备复现。failed-import 清理、进程死亡恢复、F06 质量/资源/预算及 roadmap 均未关闭。

## 证据

以下路径相对于 `docs/validation/`：

- `../../artifacts/verification/video-input-cleanup-retry-20261107/summary.json`
- `../../artifacts/verification/video-input-cleanup-retry-20261107/red-corrected-result.json`
- `../../artifacts/verification/video-input-cleanup-retry-20261107/green-targeted-result.json`
- `../../artifacts/verification/video-input-cleanup-retry-20261107/full-demo-result.json`
- `../../artifacts/verification/video-input-cleanup-retry-20261107/build-and-lint.json`
