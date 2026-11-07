# Roadmap 全任务状态复核 — 2026-11-07

按用户要求核对其他任务，修正已完成项仍列待办的情况。本次为现有证据/来源复核，没有重做
设备推理、构建SDK、上传或下载。原P0–P11停止点仍是 **10/12**，P4/P11开放；
它是历史阶段计数，不能当作完整产品完成比例。最低系统iOS15/API26依用户决定deferred/unverified。

当前F01–F12均有尚未闭合的扩展验收条件，不能据此说“12项都没做”；已完成的功能和子项如下。

| 专项 | 已完成且本次核实的范围 | 尚未闭合的范围 |
| --- | --- | --- |
| F01 / 照片与相机交互 | 真实相机/前后台、sandbox EXIF2/6/7、模拟器PHPicker；P4限定审查已完成 | 真实Photos provider的EXIF路径；不重做sandbox，也不擅自写Photos |
| F02 / Skia资源与分发 | 原TLS真实回归与修复候选、限定补丁审查、候选十轮native trace；当前`.6`115版本文件、双iOS SDK链接、74材料与App保留；用户本人维护已明确 | Nexus实际上传/匿名远端消费、来源条款适用、生产依赖接入及长期资源预算；有界trace不证明零泄漏 |
| F03 / 最低系统 | 编译下限仍保留 | iOS15/API26缺物理设备，已延期，不算通过 |
| F04 / Apple语音 | TTS、文件ASR；已授权中文麦克风、取消/重入、Home和显式close/drain有界通过 | 自然CER/WER、完整离线矩阵、持续预算 |
| F05 / Android语音 | 普通离线麦克风基本流程、独立AAR、当前636候选ASR13检查及TTS终态；旧afed包ASR30测量 | Google系统语言资源、强噪声/中文准确性、完整capture/后台和长期预算；不可把旧包资源数据当新包测量 |
| F06 / Video SR | Apple输出/取消/音轨与部分文件清理；Android普通UI48帧、完整动态重播/返回清理，当前636候选SR6检查 | 自然/时域质量、传递函数、帧节奏/峰值/能耗/持续预算及延期角落 |
| F07 / Android多模态 | 固定授权模型同步CPU文本/GPU图像、普通系统导入、Mali取消重入和清理；旧afed包5测量 | 异步native drain/streaming、其他provider、广泛质量/预算；当前supportsVideo=false |
| F08 / Apple多模态 | 既有Files/短视频/取消重入；普通人工模型导入→生成→清除→冷启动；当前636非UI真实图片/视频及释放 | 两来源temporal motion/control均0/3的observed-failure；广泛自然质量、当前候选普通交互统一验收、GPU/能耗/预算 |
| F09 / Apple二进制交付 | 20模块双slice/4C/资源、App24材料；25,288,674B GitLab包HTTP201，认证回下载SHA一致；本人维护已明确 | 从远端取得制品后的独立环境编译/运行、完整分支/来源许可/生产验收；未创建Release或tag |
| F10 / Identity/PAD | 双端真实特征/注册删除/未校准拒绝和普通控件流程；当前Android30资源测量；独立PAD固定模型iPhone9/9原始分数；manifest/score工具 | 获授权身份/攻击数据、1:1/1:N/PAD校准与生产阈值、PAD capture/crop及SDK/Demo判定；原始分数不作LIVE/SPOOF |
| F11 / 统一质量资源 | 9状态行中8能力已有11组兼容报告；Identity新增30测量，旧10组保持 | 9行均有gap；统一自然质量、真实峰值、GPU/energy和持续预算；相机资源行无兼容benchmark |
| F12 / 当前候选与最终复审 | Android636归档、599JVM/四后端构建、ASR13/SR6/TTS/Identity资源与恢复；同归档Apple构建/bridge82、真机2个XCTest方法/86原生检查 | 当前候选全能力统一交互/质量资源和完整分支独立审查、最终生产验收；旧局部审查不升级为整分支批准 |

## 本次证据与修正

本机证据在`artifacts/verification/roadmap-status-audit-20261107/`，以下结论各自保持范围：

- 原阶段表12个停止点核对在`baseline-stages.json`：P0–P3/P5–P10完成的是各原始工作包，
  P4/P11未完成。P0历史同步不表示本轮新增提交已push；本次没有查询远端分支。
- 当前readiness复核通过 **3356次证据路径检查、2741来源摘要**，9行/9gap。
  `grouped-before.json`重建11组与冻结组逐项一致；manifest是10份原始报告入口产生11组，
  8行能力reported、相机行unverified，三种计数含义不同。
- 当前Android ASR/SR、TTS、Identity资源三个冻结核验通过；普通Android Identity记录、
  Apple人工导入14模型/冷启动/恢复证据核验通过。详见`device-evidence-recheck.json`。
  这些是留存记录/摘要复核，没有再次操作设备；人工报告不包装成自动UI回放。
- Apple原`freeze.py --verify`当前exit1，原因是最后一条全局benchmark manifest摘要断言过期。
  原生440冻结文件、2个XCTest方法/86检查、18输入与44安装URL恢复条件另行通过，
  见`apple-native-supplement.json`及可复验的`verify_apple_native.py`。保留原失败、旧summary/脚本，
  没有更改原冻结证据或把该旧全量命令写成通过。
- F09重新核对留存的实际回下载ZIP及历史单次HTTP201/下载收据：25,288,674B，
  SHA256=`639205a78911289a604bc542a850d6d1d4772dc6a8b11e4d1b98635d41a152ed`。
  `f09-transport-recheck.json`不代表本次重查服务可达，也不证明远端环境编译。
- cbc hy3供应上下文交叉审计119.69秒自然完成，完整响应/零工具；两条8/11过期意见采纳。
  F02版本建议经父更正为`.6`，否决把本地publication等同远端上传；额外发现维护人、F10、
  F12和恢复任务总目标的旧文字，逐项更新当前表。原受限启动EPERM/本地端口失败独立保留，
  正常主机启动没有改auth/模型。助手结果不是完整分支/发布审查。

已更新当前F表、维护归属、ROADMAP恢复口径、一页矩阵和分组入口。历史日期日志及冻结包
保持原始身份，不能用补充记录倒写过去。当前readiness将以新快照记录这些明确文档变更，
保留11组原始数据和所有未验门槛，不把“核对完成”写成“roadmap完成”。

## 重新派发的边界

先完成本次核对/测试与当前记录，再按独立性派发。F10经验曲线hy4free任务已在1647.306秒自然结束，344064B输出JSON截断，无可用候选；
不是父超时，不修补JSON/重发，主代理接手原实现。Copilot材料核对候选已验收。下一批只选择剩余门槛中的独立小项，
不再派发已完成的上传、本地构建、普通导入/注册或相同设备推理。实际运行句柄与状态写入
[助手任务台账](agent-task-ledger-20261107.md)，额度失败线路不自动重试。


核对后两项独立派发已自然完成：Copilot最终材料checker源码复核25.75秒、0工具/0finding，
输入摘要保持；cbc hy3 variant核查84.803秒、0工具，12个variant对原metadata核实。
notices/property两条推断由既有计划/实际command否决，期望清单保留为未来远端执行输入。
材料checker32聚焦及完整tools545/545通过；首轮Swift缓存权限失败仍留存。
