# W3 首轮阶段窗口修正（2026-11-12）

运行中轮询在尚未命名首个phase时也须执行120秒预算。父端新用例真实复现：第一轮poll、sample或read将host时钟推进121秒，旧代码均未拒绝并发布ACK，三个子例RED；恰好120秒的三个子例通过。原194GREEN和先前静态复核未覆盖此边界，不能据它们声明旧字节没有该缺口。

免费hy3仅返回一组exactcount=5补丁，删除五处既有预算守卫的anchor_phase非空前提，保留起点、严格大于120秒的比较、后续phase切换、240秒总时钟及全部API/协议。候选2方法GREEN后原样集成；主代理核对精确小diff和真实RED→GREEN，没有另做一次独立全源码复核。

仓库终态观察 **196方法/24.208s**、设备合同守卫 **13方法/1.648s** 全通过，共209；36个Python文件AST、旧120冻结/原报告和false verdict、95其他dirty路径、81准备源及两APK摘要保持。机器摘要见[同名JSON](w3-terminal-first-window-20261112.json)；原RED、提示、CLI流、精确补丁、候选GREEN及仓库收据保留于 `.omx/artifacts/w3-terminal-observation-20261112/poll-first-window-*`。

这仍是实际本地fake-ADB/subprocess/文件测试，不是Android/OEM或真实阶段峰值证明。下一步须显式衔接startup与polling的首阶段起点和同一总时钟，再完成完整启动/收取/部分准备恢复、新冻结和同run真机；不允许startup将120/240秒窗口重置。当前该接口扩展只已写入下一brief，未实现。零新设备/推理/麦克风/AiAi/模型下载，旧consumedrun不重放，源码未push，W3/R4/R5仍open。
