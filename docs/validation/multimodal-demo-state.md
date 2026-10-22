# 单图 Demo 共享状态机验证

更新：2026-10-22。只交付 shared state，不含 UI、导航、host/picker 或设备推理。设计/计划 `5866196`；实现 `5db0083`；最终验证/交接同次提交，最终 SHA 以 git log 为准。

[设计](../superpowers/specs/2026-10-22-multimodal-demo-state-design.md)、[两 Task 计划](../superpowers/plans/2026-10-22-multimodal-demo-state.md)。前置 [typed bridge 包](multimodal-bridge.md) 已完成，不重做。ledger、RED/GREEN、final-review 在 `.superpowers/sdd/2026-10-22-multimodal-demo-state/`。

## 行为

`MultimodalViewModel` 消费 typed bridge，默认 `UnavailableMultimodalBridge`。UI-serialized admission/callbacks；输入仅 opaque reference/displayName，不读取图片、不管理 native lease。默认 prompt 为 `Describe this image.`、预算文本 `32`；空/非数字/非正/Int溢出预算在调用 bridge 前拒绝，原 prompt/reference/显式后端字节不变。

IDLE/RUNNING/CANCELLING/SUCCEEDED/FAILED/CANCELLED 状态、backend query/selection、累计 update、最终输出身份及 typed failure 可供未来 UI 使用。同步 terminal 先于 handle 返回不会保留旧任务；取消保持 pending 直到 bridge terminal，晚到成功/错误均按取消处理。编辑 input/prompt/budget/selection 会取消一次并清空旧展示，旧任务终态前不能重入。更新 ordinal 必须严格递增，旧/重复 callbacks 不改变后续任务。

离页/后台取消 generation、使 query 失效，不自动开始新任务。close 立即封闭 admission，只调用 bridge close 一次，closing 保持到 close callback；close 失败仍关闭，独立 closeFailure 可展示。close 完成后旧生成 callback 不能复活状态。原生 lease/drain 的正确性来自前置 adapter 包，不由 shared fake 测试重复证明。

## 新鲜验证

| 门禁 | 结果 / 日志 |
| --- | --- |
| RED→GREEN | 缺 VM/Phase 符号→新 Multimodal12/12；`task-1-red.log`、`task-1-green.log` |
| 全 shared simulator | 60/60（Multimodal12、Speech15、Video8、Face5、Identity20），无失败/跳过；Task1 实际执行，Task2 同源码缓存命中 |
| binding tests/links | binding21/21 既有结果缓存复用；Binding/Demo simulator framework 链接成功，Android shared compile 成功；`task-1-green.log` |
| Android Demo APK/lint | `task-2-android-gate.log`，560tasks（60 executed、500 up-to-date），assembleDebug/lintDebug成功；0 error、9条既有 warning，无依赖升级 |
| 更新后 Demo ABI | 前包独立 MultimodalBridgeTests 用新 Demo framework 实际重链/执行10/10，`task-2-demo-abi.log`；不是新 VM UI 测试 |
| 工具全套 | 101/101，`task-2-tools.log`，现有Ruby3.4环境，无下载 |

Tests 控制平台 bridge 边界并断言真实 VM state：无效输入不发请求、严格选择和原值、同步 callbacks、取消等待与晚到成功/错误、旧/倒序/重复更新、每类编辑分别取消、query tickets、离页/后台不重启、一次 close/close失败/旧callback及真实默认 unavailable。没有用 fake 输出冒称模型成功。

Android warning 为既有 platform ExifInterface、target版本、Compose/协程升级 advisory、allowBackup、icon与toUri提示；未修改对应模块。Swift no-cast与前包XCTest/ICU版本警告仍保留。编译target下限未改，不等于 iOS15/API26 设备执行。

复跑：

```sh
rtk proxy python3 scripts/gradle-local.py :demo:shared:iosSimulatorArm64Test :kmp-binding:iosSimulatorArm64Test :demo:shared:linkDebugFrameworkIosSimulatorArm64 :demo:shared:compileAndroidMain :demo:androidApp:assembleDebug :demo:androidApp:lintDebug --offline --console=plain
rtk proxy env RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby python3 -m unittest discover -s tools/tests -v
```

要强制重新执行已缓存测试可单独追加 `--rerun-tasks`；不得把 UP-TO-DATE 算成新一轮实际测试执行。

## 审查与下一入口

Task1 范围作者自审与 Task2 最终单独作者自审已记录，未发现本包阻断项；没有派子代理，独立 review pending，不能称 APPROVE/可发布。

下一独立包：**共享单图 Screen/UI 与 opt-in 入口**，消费本状态机，默认不可用、显示严格选择/预算/状态/update/结果身份。先独立设计/逐 Task 计划。随后另包接两端 host/picker、Apple 固定本地模型/ModelStore 与现代设备闭环，Android 仍无生产后端则明确 unavailable。不把 UI/host/视频/Android新推理一次扩入。

未验：页面渲染/交互、媒体导入、KMP→MLXVLM 真机、自然图片质量、Metal/GPU/energy、最低系统设备与独立审查。旧 Video UI 初始化超时、P6 实录、Android ASR 缺资产及其余 roadmap 门槛保留。基础iOS15/macOS12/API26、apple-mlx隔离不变；无模型/依赖下载、录音、私人媒体、签名修改、push/发布。

两保护文件 SHA256 精确不变且未暂存：Demo project `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`；未追踪 face-identity.md `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。原工作树、artifacts、日志完整保留。
