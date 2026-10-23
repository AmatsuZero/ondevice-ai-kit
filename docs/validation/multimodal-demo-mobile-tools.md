# Mobile tools boundary validation

2026-10-23。active shared AX 包提交 `a2538f6` 后，对本地已授权 iPhone `00008140-0008703436D3001C` 做只读 mobile MCP 能力检查，未改变设备 App、资料或签名资产。

`mobile_list_available_devices` 成功报告 iPhone16ProMax/iOS27.0.1 online；同一 device id 的 `mobile_get_foreground_app` 与 `mobile_list_elements_on_screen` 均失败：

`failed to start agent on device ...: agent is not installed, use 'mobilecli agent install --device ... --provisioning-profile <path>' to install it`

因此 mobile MCP 当前不能提供该设备的真实元素/触摸/前后台观测。安装 agent 需要 provisioning profile，属于额外签名/授权输入，本包不执行。此前 `xcrun devicectl` 的增强 Demo 安装、三参数启动和报告取回已完成，但仅能验证进程/文件证据，不能替代 mobile agent 的物理触摸或 OS 前后台观察。

状态：`unverified`（真实触摸、元素命中、OS 前后台）。不重复重试已确认门槛；待有授权 provisioning profile 或其他已安装 agent 的本项目设备工具后再开独立包。旧 Video runner 初始化超时、P6 实录、Android ASR MISSING_ASSETS、最低系统、质量/资源与独立 review 缺口继续保留。
