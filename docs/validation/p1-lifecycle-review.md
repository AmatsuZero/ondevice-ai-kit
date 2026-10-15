# P1 SDK 生命周期审查与修复

日期：2026-10-15。审查基线 `cb7c13b`；本包只覆盖原生 Core/TaskGate/LatestFrameQueue、Vision/MLKit detector 及其测试，不覆盖安装器、桥接、Demo 相机或新算法。

## 独立审查

由独立 code-reviewer 只读检查 10 个文件，返回 0 Critical、1 High、1 Medium、0 Minor，建议修复后再验收。审查运行了 Swift Core 4 项、Vision 3 项；其 Android wrapper 下载因 TLS 失败，没有 Android 测试结果。本包主执行器复用固定 Gradle 9.4.1 缓存完成 Android 测试。

| 发现 | 影响与处理 | 回归证据 |
| --- | --- | --- |
| High：Android onRelease 在 monitor 内且在状态清理前执行 | 回调重入可重复释放同一帧甚至递归溢出；改为锁内提交新状态、锁外释放，droppedCount 读取同步 | 两个新用例在旧实现均断言失败，修复后通过；覆盖替换 pending、complete/close 重入与恰好释放一次 |
| Medium：两端没有活跃推理期间 close 等待的测试证据 | Android 将原有 gate/worker 逻辑提取为 internal DetectorSession，真实 MLKit 调用使用同一生命周期单元；测试无需构造平台 Bitmap。底层操作结束前不释放资源，资源释放一次 | Android close/cancel 两项测试先因缺少可测试单元编译失败，再实现通过；Swift 新测试直接通过，证明已有 close 行为，无 Swift 生产代码修改 |

Android close 会拒绝后续 admission、取消活跃 worker、等待后端实际完成，再释放 client。底层 ML Kit 无推理取消接口，不把 caller cancel 当作底层已停止。DetectorSession 是内部实现细节，没有新增公共 SDK API 或依赖。

## 审查边界的裁定

- 设备特有的 Vision/MLKit 推理正确性和平台取消细节留到 P3/P4；本包只证明可注入异步工作的生命周期，不能代替设备验证。若平台 API 行为不同，仍需后续修复。
- 安装器、UI、相机和新算法没有在本包审查，分别留 P2/P4 及后续包。它们仍可能存在问题，不能据此声称完整 SDK 已审查。
- 将 Android 生命周期抽为内部 DetectorSession，复用在生产 MLKit 上并以无图像测试验证；成本是一个内部类，避免引入只为测试服务的模拟框架依赖。

## 验证

原始日志保存在本机 `.superpowers/sdd/2026-10-15-ondevice-ai-kit-foundation/p1-*`。最终结果：`swift test --package-path apple` 20/20 通过（ModelStore 8、Face 7、Core 5）；`scripts/verify-android.sh` 通过，15 个 JVM 测试报告无失败，lint 与 Demo/原生消费者 APK 构建通过（290 tasks，57 executed，233 up-to-date）。`git diff --check` 通过。没有进行真机安装或性能采样。

下一包只处理 P2 模型存储/下载审查；不自动串行执行 P3 或新模型接入。
