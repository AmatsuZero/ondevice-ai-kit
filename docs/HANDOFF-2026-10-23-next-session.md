# Next session — foundation continuation

2026-10-23。建议在新会话开始下一独立包，本文件是恢复入口，不是整个项目完成结论。

## 基线与远端

- 工作树 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`，分支 `feat/sdk-foundation`。
- 本轮实现/验证基线 `9b6c8880244df10550edcee20b33ac18f1199037`；用户明确授权后，GitHub origin 与 GitLab jihu 的功能分支已通过仓库 sync-remotes 脚本快进同步到该提交（此前两端626b27b，本地领先49，无分叉）。本恢复文档随后本地提交并再次同步；最终精确HEAD以git/两端新鲜ls-remote核对为准。没有合入main、强推或发布。
- 下一会话恢复默认**仅本地提交**；本次同步授权不作为以后自动push/merge/publish授权。
- 工作树只保留以下原始脏状态；暂存区应为空。恢复前复核HEAD/branch/worktree/index与保护哈希；有并发变化只调查，不恢复覆盖。

| 文件 | 状态 | SHA256 |
| --- | --- | --- |
| demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj | tracked dirty，禁止修改/暂存 | b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5 |
| docs/validation/face-identity.md | untracked，禁止修改/暂存 | 6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822 |

## 已完成，不重做

SDK Foundation、独立apple-mlx后端/真实SDK固定单图生命周期与benchmark、KMP双平台bridge、sharedVM/UI/opt-in、两端input lease/host/picker接线、Apple可选ModelStore/MLX host均已本地实现。默认/Android无生产多模态后端时明确unavailable。

- 共享App-local AX成功/取消/活动离页/同PID重入：真实device source2915725、PID21348、11 gates；见 `docs/validation/multimodal-demo-active-ui.md`。
- 真实OS前后台：source `d50eff18ed53db61bc46f050f411ab076d118ede`、iPhone16ProMax/iOS27.0.1、PID21398/runId16D64305-AD03-404E-ACB1-A509F9A79DA3；项目-owned inert companion触发真实UIKit四通知，ready update0、willResignActive时RUNNING/update23、后台CANCELLED无output/failure、同PID恢复300ms不自动重启、显式重入成功与close；14 gates严格通过。
- raw `artifacts/verification/multimodal-background-20261023/demo-background-integration.json`，SHA256 `bc5ec5fbcb17f93ff3a3f034a8bab27a82fe0f48646236dd0a944bc7755b96e5`；详细见 `docs/validation/multimodal-demo-os-background.md`。
- 最新工具123/123、Apple Demo42/42、strict新Swift0、default simulator/enhanced signed device/companion构建成功；之后仅交接文档变化，没有新App行为。CLI修复39660fc支持direct/module。
- 当前设备通过devicectl可用；mobile MCP只执行了foreground检查，报agent未安装。未执行元素读取、未安装agent/profile。agent缺失继续限制触摸工具，**不阻断已通过的OS前后台证据**。

## 下一独立包建议

优先检查 **可选Apple MLXVLM增强包的分发与独立consumer** 是否可用既有依赖缓存独立推进：先读 `docs/validation/apple-mlxvlm-backend.md` 及对应backend/benchmark设计计划、现有apple-mlx package和分发脚本；明确source消费与binary分发各自证据后设计/逐Task实施，不能把根基础十XCFramework或增强Demo source build当新增增强包binary分发通过。若现有缓存/平台限制构成外部门槛，准确记录并选择其他独立包，不下载新依赖/模型。此处仅是建议包，不预先声称产物一定可分发。

物理触摸/命中、真实system picker选文件、自然图片质量、可靠Metal/GPU/能耗/最低系统、短视频、Android真后端和独立review各自仍待验；不以AX/合成fixture/processRSS替代。短视频或Android新后端不与分发包混做。作者自审不称独立APPROVE。

## 执行约束与必读

先读生效AGENTS.md（无磁盘文件则会话注入版）和 `/Users/daubert/.codex/RTK.md`，再读本文件、`docs/HANDOFF-2026-10-20-speech-demo-bridge.md`顶部最新检查点、`docs/ROADMAP.md`、本包validation/spec/plan及`.superpowers/sdd/2026-10-23-multimodal-demo-os-background/{progress.md,final-review.md}`。保留全部本地`.superpowers/`/artifacts/日志；不clean/reset/stash，不覆盖用户改动。

单人当前会话执行，不派子代理；按适用技能设计/逐Task计划、行为修改RED→GREEN、范围作者自审、精确暂存与本地提交；清晰可逆下一步不例行询问。所有shell命令以rtk开头，普通命令rtk proxy；Ruby测试显式RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby。只既有固定模型/依赖缓存/公开合成fixture，不下载、录音、读取私人媒体或变更签名资产。允许使用已授权本项目真机和既有签名，先核对连接，只操作本项目App。

保留旧Video UI runner初始化超时、P6实录、Android ASR MISSING_ASSETS、iOS15/API26最低系统和独立review缺口；全roadmap仍5/12完成、7阶段未收口，P9active。只因真实破坏性操作/并发冲突/必要权限缺失或无安全恢复路径停下。
