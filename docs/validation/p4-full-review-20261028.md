# P4 全范围审查与上游依赖结论

日期：2026-10-28。基线为 `feat/sdk-foundation` 的
`c9736ffaf54fcab0913cec51abd56acd37461e48`。本记录关闭当前 P4 代码审查
子门槛；它不关闭原生资源残留、真机 PHPicker→Vision EXIF 或最低系统验收。

## 审查范围与结论

fresh code-reviewer 只读检查了：

- `e7c1484` 的 CameraSource/DemoController 隔离、terminal close、FIFO 与回归；
- `33dd8c4` 的 simulator-only synthetic PHPicker EXIF 2/6/7 harness；
- 当前未提交的 resource/page/thread probe、生成器、README，以及两个受保护文件的范围边界；
- P4 计划、SDD ledger 和 Task 12 的冻结 trace/runtime/线程校准证据。

复核结果为 **0 Critical、2 Important、0 Minor**：

1. `CameraResourceValidation` 在失败报告再次写入失败时使用 `try?`，会静默丢失当前 run 的诊断。
2. 受保护 `project.pbxproj` 的 signing diff 不属于 P4，不能进入 P4 的暂存、提交或合并选择。

第 1 项按 TDD 修复：先加入 `CameraResourceFailureReportChecks.swift`，旧源码下
compile RED 仅因 `CameraResourceFailureReport` 不存在；随后加入有界 reporter，要求原子
报告写入失败时唯一输出 run ID、原 probe 错误和 report 错误，三段分别限制长度。主机
检查 GREEN；从当前生产 App 新生成的默认 resource probe 以 iOS 15 deployment target
完成 generic iOS unsigned build，最终 `BUILD SUCCEEDED`。首次 build 仅因受限环境无权写
SwiftPM/Xcode cache 失败；同一生成项目在获批环境复跑成功，没有安装或启动设备。

第 2 项按用户保护边界处理：不 restore、不覆盖、不 stage、不 commit。该文件只保留在
独立 signing workstream；P4 不评价 automatic signing/team 设置本身是否应交付。
`face-identity.md` 同样属于 P10 草稿，不纳入 P4。

## 审查快照

排除本轮待提交的验证文档后，审查涉及的受保护/未完成本地子集为 1 项 tracked
dirty + 8 项 untracked；其中 untracked 子集包含本轮新增的 fallback 回归。下列文件继续保留
本地，不属于远端 SDK candidate；这不是完整 `git status` 的计数：

| 文件 | SHA-256 |
| --- | --- |
| `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` | `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5` |
| `docs/validation/face-identity.md` | `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822` |
| `probes/ios-camera-lifecycle/CameraPageResourceValidation.swift` | `1ea2f42453121c0fc8c9065089d3b01e1726b5508ff7f574485bb4bb836fa60e` |
| `probes/ios-camera-lifecycle/CameraResourceFailureReportChecks.swift` | `e8fca1071b5fc34a5c0294965ce20ba86ad0475178cc48d88cb91ca0cef0f53c` |
| `probes/ios-camera-lifecycle/CameraResourceValidation.swift` | `1bd13d018f435c71e6a3bcf406dbd3fe1731850134b09cb23e10885b12d3a8bb` |
| `probes/ios-camera-lifecycle/README.md` | `9b600b07d936ff1fece485d1d495758cb2df0c6a038eead50b9d5e0cc42ecf34` |
| `probes/ios-camera-lifecycle/ThreadInventory.swift` | `d7ab11ca123fd2c68656eb2ed79b2150d48e99d6322be1512d7593a998eeec18` |
| `probes/ios-camera-lifecycle/ThreadInventoryChecks.swift` | `c2fa73a5f2534e96ee0c61725513baf378f28788e9fa9ff8a380609ec07f197f` |
| `scripts/generate-camera-resource-app.rb` | `f58d43e6754708062bb9c6df376380bd5e02613daa8319ca2595728690fb889f` |

这些哈希只标识本次审查/验证快照；受保护 signing 工程不是 P4 deliverable。

## AtlasTextOp 依赖评估

当前链路为 Compose Multiplatform `1.11.1` → Skiko `0.144.6` /
`9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0` → Skia
`m144-22f58c9fd4` / `22f58c9fd43d55bde818821c04b48fda5d7ec939`。

只读 dependency review 的结论为 **`no-supported-upgrade-conclusion`**：

- [Compose 1.12.2](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2)
  已发布，但 release notes 没有 AtlasTextOp/GrOp 生命周期修复声明；
- [Skiko 0.150.2](https://github.com/JetBrains/skiko/releases/tag/v0.150.2)
  更新到 Skia `m150-a3f509bf52`；
- m150 与 m153 的 `AtlasTextOp.cpp` 仍保留相同 `static thread_local void*`
  单槽、cache-or-free 和调用线程 `ClearCache()` 机制；`GrRecordingContext` 析构仍只调用
  该线程的清理。更高版本本身不能证明命中本次 18 个 residual。

因此不强制覆盖受 Compose 管理的 Skiko、不调用私有 `ClearCache()`、不把 Leaks 重标为
false positive。若继续根因研究，应先设计非生产、仪器化的 Skiko/Skia research build，
只记录 opaque allocation ID 与构造/析构/cache/线程事件；它是新的依赖研究工作包，不能
用另一次同构 301 秒 trace 或已失败的 LLDB attach 变体替代。

## 当前停止条件

P4 仍未完成：

- 同 PID trace 仍有 `18×GrOp / 2304B`，未证明 destructor/free/TLS/thread-exit；
- 物理 PHPicker→Vision EXIF 2/6/7 仍无安全、可复核的 synthetic-only 设备注入路径；
- iOS 15 runtime（以及统一矩阵中的 Android API26）仍缺少设备签收。

基础里程碑保持 **10/12**，P4/P11 与完整 roadmap goal 继续 active。
