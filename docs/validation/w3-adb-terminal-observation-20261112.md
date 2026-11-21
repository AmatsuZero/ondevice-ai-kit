# W3 三适配器终态协调器验证（2026-11-12）

`publish_remote_terminal_observation()` 已按外部候选集成，实际组合 ADB sampler、fresh report reader 和 ACK publisher。完整 Python **171/171**、25 文件 AST、旧 120 冻结文件、原 heap 报告及 false verdict 摘要、96 条其他 dirty 路径保持。源码复核经事实复现裁定 **accept，无剩余发现**。这只证明主机组合行为；W3/R4/R5 仍 open，源码分支未 push。

配置、binding 和输出预检完成后，以同一 host monotonic 时钟包围 sample1/read1/read2/sample2；两份终态报告必须逐字节相同，PID/startTicks、资源预算与时序均有效。实际报告 SHA 参与 ACK。协调器先持久化本地 observed report、receipt、ACK，publisher 再写远端 stage、逐字节回读及文件 fsync。before-link 回调取得第三次同进程资源样本并持久化独立 journal，完成后才允许 hardlink 与目录 fsync。错误即停，保留先前证据及 typed publication uncertainty，不重试或回滚。

新增 14 方法由外部 4 和父端 10 组成。父端 6 方法通过真实本地 fake-ADB 子进程、`/bin/sh`、`os.link/os.fsync` 运行全部实际适配器，正常路径共 19 次 transport，覆盖旧进程退出竞态、journal 失败阻止 link、目录 fsync 失败保留完整 final。父端 4 方法覆盖 callback 前配置/binding 拒绝、binding snapshot、三次采样资源拒绝及 before-link 时钟倒退。它是一次终态 handshake，尚非完整 polling/launch/restore driver，也非 OEM/Android 文件系统证据。

外部与父端 direct missing-module RED 早于集成。初始父 10 检查 3fail 中，2 条为真实 fingerprint 格式预检晚于 callback，外部精确补丁修正；另 1 条是父测试错误限定既有 writer 的 cause 类型，按真实 ValueError/OSError 合同纠正。外部测试先后有无效 config、占位路径、错误嵌套 argv、遗漏 nonlocal 等缺陷，均保留原件与真实失败，经独立外部窄修后验收；未改实现迎合假传输。一次“缺模块 RED”误放在已有源码目录，已明确作废，另在真正无实现的目录取得 RED。

原源码复核两条意见源于供给依赖说明含糊：`_probe_path` 实际定义于 `terminal_observation_coordinator.py:62`；validator 实际要求 `terminalObservationGap` 存在且恰为 None。父端实际 import/validator 复现反证后，限定事实裁定撤回两条、accept。该裁定不是第二轮全量复核；复核者未执行命令，父供给上下文限制与原 revise 均保留。

机器收据见[同名 JSON](w3-adb-terminal-observation-20261112.json)。证据位于 `.omx/artifacts/w3-terminal-observation-20261112/remote-*`、`continuation-remote-coordinator/`。实际 Android wrapper/driver、build/lint/APK、新冻结合同、同 run 末端证据和完整真机质量/资源，以及递归最终二进制/source 对应、生产通知和发布复审仍待完成。本组件零设备操作、推理、麦克风、AiAi、模型/runtime 下载；旧 consumed run 不重放。
