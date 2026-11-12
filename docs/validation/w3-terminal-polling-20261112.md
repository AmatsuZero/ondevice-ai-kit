# W3 运行中进程轮询验证（2026-11-12）

独立 `terminal_polling.observe_until_terminal()` 已按免费 hy3 候选及精确补丁集成。新增 **23 个方法**（外部 4、父端 6、边界 13）和完整 **194 项 Python 测试**通过；33 个 Python 文件语法检查、旧 120 份冻结文件及失败报告、95 条其他 dirty 路径、81 份已准备 wrapper 源文件和两 APK 摘要均保持。独立 supplied-source 复核 `accept`，父端跨回调故障已修正。**W3/R4/R5 仍 open，源码分支未 push。**

本项从外层启动器已建立首份报告和真实 PID/startTicks 绑定后开始，不启动、停止、安装或恢复进程。它先持久化 new-only `poll-start.json`，再调用时钟、process poll 与实际 sampler/reader；每次有效观察保存原始报告 SHA256 和真实资源样本。严格 JSON、身份、协议、variant、30000-ms 元数据和状态校验后，终态只进入实际 remote coordinator 一次。三次同进程资源观察、两次新鲜报告读取、本地持久化和 before-link journal 完成后发布 ACK；成功后立即返回，不再读取资源或轮询进程。

资源门保持 RSS ≤1610612736 B、available ≥536870912 B；阶段窗口 ≤120s、总窗口 ≤240s，等号可接受。全路径共享 host monotonic 时钟，倒退、回调、传输、资源、身份或持久化错误均停止，保留原异常及发布不确定性，不重试、不覆盖旧证据。失败终态仍仅为观察，`qualityAccepted=false`、`truePeakMeasured=false`。

首整份 hy4free 请求自然 902.693s 终态，CLI0/raw0、`Empty stream`，没有源码交付；按规则停止后，用户明确选择 hy3。两份短片段真实交付，但首隔离 19 方法有 41 FAIL/13 ERROR（含子例）：路径 map 值、协议字段、依赖 API、绑定/runner/hash 接线有误。主代理精简提示漏给完整字段名及 map 语义，已补真实接口；没有改正确协议或测试迎合候选。外部修正后 21 方法尚有 2 FAIL；开始文件/时钟顺序和 poll 类型补测另有 1 FAIL/1 ERROR。精确补丁后 22 方法全 GREEN。

父端随后真实复现三个跨回调漏洞：poll/sample/read 使时间推进到 121s 后，新阶段可重置已过期窗口并错误发布 ACK。三个 RED 保留，外部只补三个回调后的阶段守卫；最终 23 方法、隔离整套 194 和仓库整套 194 均 GREEN。真实本地 fake-ADB subprocess、shell、文件、hardlink/fsync 覆盖成功、旧退出竞态、预算/身份/元数据失败、陈旧输出、回调顺序和发布后异常；**不构成 Android/OEM 证明**。

复核仅静态读取候选及给定依赖，未执行命令。它遗漏了当时待验证的跨回调故障；父端实际 RED→GREEN 补足该项，不能把原 `accept` 当作这些旧字节无缺陷的证明。低优先级意见保留：首阶段从循环开始计时，可能比从首次报告计时更早拒绝一个接近 120s 的阶段；这是当前更保守的行为，暂不放宽。`_PollClock` 负责总窗口与倒退，120s 守卫由轮询循环实现，纠正复核限制中对二者的混称。

原始候选、失败、补丁与验收收据在 `.omx/artifacts/w3-terminal-observation-20261112/poll-*`；机器摘要见[同名 JSON](w3-terminal-polling-20261112.json)。当前 ADB 清单为空，本次新增安装/推理/麦克风/AiAi/模型下载均为零。

下一项是完整外层启动、收取、部分准备失败后的精确恢复与新冻结合同；已备 4 项外层路由检查并取得缺 driver RED，尚无外层实现。此后才可收取新 run 的同进程真机终态证据。完整手机质量/资源、生产通知/再分发和发布余门继续，旧 consumed run 不重放、不追认。
