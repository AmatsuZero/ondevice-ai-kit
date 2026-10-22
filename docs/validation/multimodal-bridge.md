# 单图 Multimodal KMP Bridge 验证

更新：2026-10-22。工作树 `.worktrees/foundation`，分支 `feat/sdk-foundation`。只交付单图 typed bridge 和原生 adapter；没有 Demo UI、视频或新 Android 推理后端。

## 交付与契约

- 设计/计划：`88c2fa9`，[设计](../superpowers/specs/2026-10-22-multimodal-bridge-design.md)、[三 Task 计划](../superpowers/plans/2026-10-22-multimodal-bridge.md)。
- Task 1：`2e96a26`，common typed request/selection/backend/update/identity/output/failure、callbacks、UnavailableMultimodalBridge。
- Task 2：`b1e6873`，AndroidMultimodalBridge、host input provider/lease、既有 native client 注入。
- Task 3：Apple adapter、MainActor URL lease、独立工程生成器、双 ABI fixture 与本检查点同次提交；最终 SHA 以 git log 为准。

请求使用 opaque inputReference，不把路径送入 shared；原始 prompt 不改写，预算为正数。Default/Explicit 不回退，Swift 未知 selection conformer 返回 INVALID_INPUT。结果保留实际 backend 与 model/runtime revision，已交付文本在 close 后有效。

除 any-thread cancel 外，admission/provider/callback 均在 UI dispatcher/MainActor。单 generation 槽保持至 native drain 和 input release 结束，清理先于一次 terminal，可同步重入。立即取消仍执行 cleanup；取消胜过晚到成功/普通错误。updates 是累计文本、递增 sequence，不是 token 数；取消/关闭/完成后的排队更新被抑制，终态可先于尚未交付的更新。

close 立即关闭 admission、取消并 join 已接纳 query/generation，再关闭 client；重复 close 共享完成。Android cleanup scope 不依附 host Job，host Job 取消后仍需调用 close，不能把 host Job 取消当作资源关闭。未知错误只暴露固定 `ios.multimodal` / `android.multimodal` code 1，不含原始错误、prompt 或媒体路径。无 client 时明确 `BACKEND_UNAVAILABLE/backendNotLinked`，不 acquire 输入、不伪造结果。

## 本包证据

日志与 ledger 保留在 `.superpowers/sdd/2026-10-22-multimodal-bridge/`；所有 Gradle 离线，Xcode 使用本地无外部依赖的 `apple` package 与 `-disableAutomaticPackageResolution`。

| 验证 | 新鲜结果 / 记录 |
| --- | --- |
| Common RED→GREEN | 缺符号→4/4；`task-1-red.log`、`task-1-abi-final.log`；最终强制重跑全 common 21/21，`task-3-common-rerun.log` |
| 旧 Video ABI 回归 | generic entry points 引入 selector collision RED；前缀化后旧 Swift adapter 不改代码、6/6，`task-1-old-video-abi-fixed.log` |
| Binding/Demo framework | common test 与两 framework link 成功；`task-3-regression-final.log` |
| Android bridge RED→GREEN | 缺 adapter/provider/lease→10/10；`task-2-red.log`、`task-2-green-final.log` |
| Android native 与回归 | Multimodal 12/12、Video bridge/native 10+7、Speech 25；`task-2-green-final.log`、`task-3-regression-final.log` |
| Android lint/compile | lintDebug、binding compile 成功；仅既有 coroutines1.10.2 新版本 advisory，未升级 |
| Apple adapter RED→GREEN | 空实现占位下实际独立工程报缺 adapter/provider/lease；`task-3-apple-red.log`；最终 Binding 10/10 与 Demo 10/10，`task-3-binding-checkpoint.log`、`task-3-demo-checkpoint.log` |
| Swift 格式静态检查 | 三新增 Swift 文件 `swift-format lint --strict` exit0；首次 forEach 风格报告改为等价 for-in 后重新运行双 ABI 测试 |
| 工程生成器 | 3/3；检查实际产品、framework、source、部署下限，拒绝已有目录和悬空 symlink；`task-3-generator-green-final.log` |
| Python 工具全套 | 101/101；`task-3-tools-final.log` |

Android/Apple 均使用真实 MultimodalGenerationClient 与受控 barrier backend，不 mock client：严格选择、原始请求/预算、结果身份、递增/重复/晚到更新、UI delivery、立即/跨线程取消、晚到普通错误、busy/lease 直到 drain、重入、query/close join、重复 close 与错误隐私。Android 另测 host Job 取消和 release 期间 busy/取消胜过 release 普通错误。

Apple 两种 ABI 的 XCTest/xcresult 分别在：

- `artifacts/verification/multimodal-binding-20261022/build/Logs/Test/`
- `artifacts/verification/multimodal-demo-20261022/build/Logs/Test/`

生成器仅链接 `ODAIKitCore`、`ODAIKitMultimodal` 和选定 KMP framework（系统 Foundation/XCTest 除外），不链接 MLX 或修改默认 Demo 工程。生成的 target 保持 iOS15；运行环境为现有 iOS27.1 simulator。Xcode XCTest 17.0 与 Demo 既有 ICU 对象 18.5 的版本警告保留，不能据此宣称最低系统设备可执行。

复跑到一个**未存在**的输出目录；Ruby 与 xcodeproj/json 应使用同一既有环境（本机默认 `/usr/bin/ruby` 2.6 混用 Ruby3.4 gems 会失败）：

```sh
rtk proxy env RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby python3 -m unittest tools.tests.test_multimodal_bridge_project -v
rtk proxy env ODAI_MULTIMODAL_BRIDGE_TEST_DIR=artifacts/verification/multimodal-fresh ODAI_MULTIMODAL_BRIDGE_KMP_MODULE=ODAIKitBinding /Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby scripts/generate-multimodal-bridge-tests.rb
rtk proxy xcodebuild -project artifacts/verification/multimodal-fresh/MultimodalBridgeTests.xcodeproj -scheme MultimodalBridgeTests -destination 'id=DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F' -derivedDataPath artifacts/verification/multimodal-fresh/build -disableAutomaticPackageResolution test
```

选择 `ODAIKitDemo` 可验证另一 ABI，不代表 Demo Host/UI 接入。基础 Swift461/461、MLXVLM host12/12 与固定单图设备 benchmark 是前包证据；本包未修改这些 SDK 源码，不冒称重新执行。

## 审查、未验与下一包

每 Task 范围作者自审，最终单独自审见 `.superpowers/sdd/2026-10-22-multimodal-bridge/final-review.md`。用户要求不派子代理，独立审查仍 pending，无独立 APPROVE、合并或发布结论。

下一独立包选择：**单图 Demo 共享状态机与 UI**，只消费 typed bridge，平台默认 unavailable；随后另包接 host/固定模型、picker 与真机生命周期。先独立设计/逐 Task 计划，不把 UI 状态测试当真机推理。短视频与 Android 真后端另包，不扩入当前工作。

仍 unverified：KMP→真实 MLXVLM 设备端到端、Demo UI/host、自然图片质量、Metal/GPU 峰值/energy、Android 真实推理、最低系统设备与增强包二进制分发。前包合成 red+square rubric 与进程 RSS 不补足这些门槛；P9/roadmap 未完成。旧 Video UI 自动化初始化超时、P6 实录、Android ASR 缺资产及旧独立审查缺口继续保留。

无 push/模型或依赖下载/录音/私人媒体/签名资产变更。两保护文件未修改或暂存：`demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` SHA256 `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`；`docs/validation/face-identity.md` SHA256 `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。
