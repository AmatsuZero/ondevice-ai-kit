# W3 heap 父端构建与设备路由准备

2026-11-11。本项只完成不依赖外部 Kotlin 候选的父验收部分。W3 保持 **open**；实际 wrapper、Kotlin host 验收、Android 构建/实包、独立复核和新设备合同均未完成。

新增 `probes/android-qwen-heap/prepare_build.py`、`build_acceptance.py`、`device_acceptance.py` 与 `check_routing.py`，更新同目录 host adapter 和报告校验。外部拥有的 `QwenHeapInstrumentation.kt` 仍不存在，主代理未代写。

## 已验证的行为

- 源码准备先核真实 host 退出码、core/Android/native doubles、源码及测试摘要，再复制旧 FILE 已绑定源码到新目录；新 alias 为 `io.ondeviceai.acceptance.qwenheap[.test]`，runner 为 `io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation`。拒绝覆盖已有准备，不生成 run-plan 或冻结合同。
- 构建和实包入口重新核 host 收据、当前测试与源码，逐个绑定仓库源码与准备副本。复用既有离线构建/manifest/DEX/65 PCM/零权限/签名检查，要求四个 arm64 runtime 成员与固定 AAR 集合一致。这里只验证适配后的代码可编译及入口拒收，未运行 Gradle、aapt 或签名工具。
- 设备入口在任何 ADB 前核完整冻结摘要、原报告 index25 仍 planned、两份独立复核、实际包/源码/计划绑定。固定一片 `fleurs-en_us-1776`，最多构造1/stream2/decode2，保留 1.5GiB RSS、512MiB available、每片/加载120s，整诊断240s守卫；没有合法合同就不能调用设备。
- RSS 读取前后保存 `/proc/uptime`，保留观察区间和原一秒容差。报告校验拒收缺失/反向区间及伪称精确对齐。`preciseClockAlignmentVerified=false`；这不证明两个时钟精确映射，也不测 native 峰值或 allocator 根因。
- 政策续取复用同 UID/device/inode 身份比较 framework/shell 路径别名，拒绝文件 symlink；最多21B双读，要求精确20B。`collect-policy` 只续取同 run，不重放识别。

## 失败与验证

构建入口最初只核 host 收据字节，遗漏重新验证收据指向的当前测试。修改测试后，原测试意外在后续其他断言处通过；加 renderer sentinel 后真实复现 `RuntimeError: stale host reached build renderer`。修复是在渲染/启动工具前重新调用 `verify_host`，同时核当前源码与副本相同；原两次记录均保留，不能把首次意外通过当有效拒收证据。

定向检查：路由19、heap报告22、arena父22、FILE父8、metrics31，合计 **102 项通过**。另完整 `tools/tests/test_*.py` **691 项通过**（含上述 metrics，不重复累计）；仅测试子进程 PATH 对齐已安装 Ruby3.4.11。七份 Python 源码语法通过。原108冻结文件保持；78保护基线中77匹配，唯一此前其他任务漂移 `docs/HANDOFF-F08-BIRDS-EXPERT-2026-11-08.md` 保留。

原 RED、GREEN、完整输出和本地完整性收据位于 `.omx/artifacts/w3-qwen-ort-lifecycle-20261111/heap-routing-preparation/`。可分发机器收据见 [JSON](w3-qwen-heap-routing-preparation-20261111.json)。本轮0新助手、0构建、0设备、0模型调用；只自审，独立复核未通过。旧 FILE/arena/load-only 批次不重放，预算不放宽。

## 下一步与阻塞

外部免费 hy4free 原调用已终态429/code6004、零文件。服务端提示的15:12:49+08重置已过，不构成额度恢复证明或自动重试授权；按项目既定额度规则，仍待此前提出的接续线路选择。候选交付后按 [原计划](../superpowers/plans/2026-11-11-w3-qwen-heap-diagnostic.md) 顺序做真实 host、构建/实包、独立复核、冻结、fresh设备门、唯一诊断与恢复；本项不替代这些门，也不关闭R4/R5。
