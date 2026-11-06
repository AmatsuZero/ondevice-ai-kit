# Android 候选评估报告工具 (`probes/android-candidate-report`)

本工具为测试专属（test-only）诊断工具，基于 SDK commit `e0438c030a987ceeff9f9207c2475684749e4d8b` 构建。仅包含 2 个测试覆盖层（test-only overlays），全量 953 个源码文件保持不变。

### 1. 代码生成器 (Generator CLI)
通过 SHA 门禁的 Python 脚本生成测试 Runner，要求目标路径 `OUTPUT` 必须不存在：
```bash
rtk proxy python3 probes/android-candidate-report/generate.py BASE OUTPUT --kind asr-lifecycle
rtk proxy python3 probes/android-candidate-report/generate.py BASE OUTPUT --kind sr
```
生成对应的 Runner：`PersistentSherpaAsrLifecycleInstrumentation` 与 `PersistentVideoSrInstrumentation`。

### 2. 运行参数与文件管理
`runId` 仅允许 1~80 位字母、数字或连字符（`^[a-zA-Z0-9-]{1,80}$`），并自动拒绝已存在的 `base`、`.new` 与 `.bak` 文件。
```bash
rtk proxy adb -s SERIAL shell am instrument -w -e runId test-run-01 -e reportMode inference <RUNNER_COMPONENT>
```
- **参数规格**：`-e runId ID -e reportMode inference|recorder-pass|recorder-fail`。
- **状态保证**：使用 `AtomicFile` 在启动前写入 receipt，在结束前写入 terminal 状态；持久化输出至私有目录 `files/odai-candidate-reports/ID.json`。
- **安全自检**：自检模式绝不判定为 inference pass。

### 3. 测试用例与数据脱敏规则
- **ASR**：强制复用既有固定合成英文 PCM，严禁调用麦克风；由测试 Runner 产生并核验 Bundle 的 13 项生命周期标志与隐私标志。
- **SR**：复用现有 2 帧 32×24 AAC 测试夹具，目标输出 128×96 并保留音频；内置 discard / cancel / drain / reentry / native decode 断言；校验器所需的底层原生细节独立输出至 `files/video-sr-validation/report.json`。
- **数据脱敏**：报告仅包含白名单原始类型诊断；严禁包含音视频媒体、转录文本或异常堆栈；SR 失败诊断严格缩减为异常类名。

### 4. 设备操作、合规与验证状态
双 APK 均已离线编译，并通过 405 项工具测试；当前切片尚未执行真机物理推理。
- **设备运维**：安装前须备份并记录常规 App、测试包、模型及私有数据哈希，暂存并验证回滚链路；USB 中断后需重检同一次运行状态，严禁仅因观测断开而重启；报告回读后必须完整还原初始文件与安装包。
- **约束声明**：不下载或捕获新媒体，不引入任何签名及生产配置变更；`qualityVerdict` 保持 unverified，不代表正式发布、校准、自然画质或最低系统版本审批。
