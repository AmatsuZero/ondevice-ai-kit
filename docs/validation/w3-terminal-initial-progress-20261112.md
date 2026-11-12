# W3 启动阶段进度衔接验证（2026-11-12）

`observe_until_terminal(..., initial_progress=None)` 已按免费 hy3 候选集成。`None` 保持旧行为；非空值要求精确普通 dict，字段为非空 phase、None 或精确整数0的 activeCaseIndex、非负有界 startedNs。参数在任何 callback、ADB transport 或输出写入前验证；通过后把不可变快照写入新 `poll-start.json`。

首个时钟取值沿用同一个 `_PollClock`。若 startedNs 晚于首时钟立即失败；首阶段锚点使用 startedNs，121 秒以及 poll/sample/read 回调越界均拒绝，恰好120秒的终态允许，phase/index变化仍从新鲜 report read_ns重新锚定。终态发布后保持原有立即返回和无额外 process/resource/clock 读取。

独立父端5方法先在无参数实现上取得缺 keyword RED；修正测试中的合法 `None` 默认和合法完整 dict 误列后，实际 fake-ADB 子进程 5/5 GREEN（1.067s）。hy3 候选源码精确集成，完整轮询 **201 tests / 24.876s / OK**；冻结守卫 **13 / 1.707s / OK**；37个Python文件AST通过。独立 supplied-source 复核 `accept`，保留静态审查限制。

这只证明 host fake-ADB、文件和时钟链路，不证明 Android/OEM。下一步 outer driver 必须在 Popen 前建立同一240秒总时钟，并把首份真实 report 的 phase/index/startedNs 传入该 API；不能在 startup 后重置窗口。完整启动、ACK结果字段、收取、部分 prepare 失败恢复、新真实冻结和同run真机仍未完成。当前ADB清单为空，零新安装/推理/麦克风/AiAi/模型下载，旧consumed run不重放，源码未push，W3/R4/R5仍open。

机器摘要见[同名JSON](w3-terminal-initial-progress-20261112.json)。原 RED、测试纠正、候选、精确源码、完整回归和复核保留在 `.omx/artifacts/w3-terminal-observation-20261112/initial-progress-parent/`。
