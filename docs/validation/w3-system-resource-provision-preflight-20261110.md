# W3 系统资源请求准备 — 2026-11-10

**后续状态：获批的一次请求已执行，返回实际错误15（服务不支持下载事件监听），资源ready未证实，已停止并恢复。** 首次reservation失败SDK0；只读诊断后单行修正版11路径/31故障断言及fresh构建通过，run02唯一SDK调用/零麦克风，30父证据检查通过。见[当前观察与修正](w3-system-resource-provision-observed-20261110.md)。下文保留原准备状态，不作为重复询问或再请求下载的依据。

**隔离探针已验收至构建/打包，尚未安装或请求下载。** 实际 on-device 服务是 AiAi，正常设置页
未暴露语言资源操作；详情见[AMI 补测与资源入口调查](w3-ami-unattempted-observed-20261110.md)。

下一项只调用现有生产 `AndroidAsrResources.download("en-US")` 一次，可能联网或显示系统确认。
它会改变系统管理的语音资源，等待用户对此动作的单独明确授权；解锁和文件补测许可不代替该授权。
运行时 token 只防误启动，不代表用户已经批准。

证据：`artifacts/verification/w3-resource-provision-20261110/`。

- 候选由免费 hy3 交付；初稿忽略下载前写入失败、遗漏 teardown 无确认的失败判定，已拒绝装机。
  两次独立小修正均原样保留，最终候选 SHA-256：
  `8ece747cf1a25101e168bf90bd0d645f91ed2839b91dad126985eed1686edd90`。
- 用完整候选及 Android/SDK host fault doubles 执行：原稿在“写入失败不能调用 SDK”真实 RED，
  修正版 **31 项**断言通过，包括错误15不回退、PENDING不ready、缺前台/释放确认失败、最终
  写入失败不声称已保存、非法参数/已有报告/软链目录阻断。host 未调用真实 SDK、下载或麦克风；
  唯一源码适配是故障测试将 teardown timer 10s 降为1ms，实际装机源码未改 timer。
- 只集成隔离候选；六份当前生产 Kotlin 源逐字节相同，复用已验 ForegroundProbeActivity。
  offline build+lint 24.709s 成功，lint零error、10warnings保留。APK签名核验通过；main/test
  均没有 RECORD_AUDIO 权限，没有 native libraries 或模型文件。
- main APK：1,456,178 B，SHA
  `68f59768e5c563cafcd6faf025a8f170c1947924cfa128ac7c896b5a5dd5d4d3`；test APK：18,516 B，SHA
  `11e49113c6d8c9d87b32d27464776bcb0a0561e0a4fced60c36994165a3a9e1d`。
- 首次 apksigner 使用系统 Java shim 报 Unable to locate a Java Runtime；原错保留，后续显式
  沿用构建已有 JDK21 后验证通过。未下载 JDK、修改全局默认或签名资产。

执行合同在 `execution-contract.json`，固定 runId `w3-asr-resource-enUS-20261110-01`：
最多一次 SDK download，无 query/recognize/recording/自动重试/无listener备用调用。
SDK timeout是10分钟；PENDING仅表示排队，错误/超时原样保留。停止监测或卸载探针**不会取消**
系统已排队的下载。只有 INSTALLED 且前台/调用/teardown均成功才记资源ready；仍不能把资源回调
当 R1 麦克风成功。后续任何采集都要新的开始提示。

获批后仍须重核实际服务、设备awake/解锁、无活动instrumentation、源码/APK摘要、原App/模型/
服务默认值，以及新别名/stage/report不存在；同run终态双读，移除仅本次别名/暂存并回普通App。
不改服务默认值、组件enabled/exported、普通App、模型/依赖/锁或权限。当前下载请求与新采集均为0。
