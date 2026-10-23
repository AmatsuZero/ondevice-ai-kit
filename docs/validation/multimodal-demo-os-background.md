# Multimodal Demo real OS background validation

2026-10-23。接续 active AX/CLI 检查点 `39660fc`；本包运行代码 `d50eff1`；设计/计划 `76b6807`，Task1 报告门禁 `b521952`。实际增强 Demo 与项目-owned inert companion 均使用既有签名配置，未安装 mobile agent、未读取其他 App/媒体。

## Physical device evidence

授权 iPhone16ProMax/iOS27.0.1，UDID `00008140-0008703436D3001C`。源提交 `d50eff18ed53db61bc46f050f411ab076d118ede`，增强 bundle `io.ondeviceai.multimodalmlxdemo`，companion `io.ondeviceai.multimodalbackground`。增强 Demo PID `21398`，runId `16D64305-AD03-404E-ACB1-A509F9A79DA3`。原始报告 `artifacts/verification/multimodal-background-20261023/demo-background-integration.json`，SHA256 `bc5ec5fbcb17f93ff3a3f034a8bab27a82fe0f48646236dd0a944bc7755b96e5`；summary、安装/启动/ready/copy 日志和 build 日志同目录保留。

严格 `python3 tools/multimodal_background_report.py ...` 通过，14 gates 全 true：bootstrap、available、pageOpened、inputAction、pickerReply、backgroundReady、四个 OS 通知、activeCancel、noAutoRestart、reentry、closed。

- ready 文件在 PID21398、sourceCommit 精确匹配、`RUNNING`、update sequence0 时写出。
- 由 `devicectl` 前台启动项目-owned companion 触发系统切换，再前台恢复增强 Demo；恢复后 PID 仍为21398。
- 通知按 `willResignActive` → `didEnterBackground` → `willEnterForeground` → `didBecomeActive` 顺序。第一通知为 application state active、`RUNNING`/sequence23；进入后台为 state background、`CANCELLED`/sequence23。
- 取消终态无 output/failure；恢复后 300ms phase/update/output 不变；显式 token budget32 共享重入成功，文本非空且 backend `mlx.smolvlm2-500m`、model `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`、model revision `fa57db46815177fbdfd65cc85a2b3416a8332268`、runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a` 精确匹配。

## Local validation and boundaries

Task1 missing validator RED→4 tests/两种 CLI GREEN；Task2 observer missing symbol RED→2/2 XCTest GREEN；bridge/Demo simulator regression 42/42，tools123/123（Ruby `/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`），Swift-format strict0，默认 iOS15 generator build与增强 signed device build均 `BUILD SUCCEEDED`。validation-only source、第三 runtime arg `--multimodal-demo-os-background` 和 companion 不改变默认/Android/KMP ABI/production lifecycle；所有 logs/artifacts 保留。

本证据证明项目 App 的真实 UIKit application lifecycle 通知与已有 DemoController→MultimodalHost background cancellation 接线，以及同进程恢复/重入。`touchAcceptance`、`systemFileSelection` 仍 `unverified`；AX 激活/注入 fixture 不等于物理触摸或系统 picker。真实 OS background 已 verified，但不提升最低系统、自然质量、Metal/GPU、能耗、资源或独立 review 等旧门禁。mobile MCP agent 缺失仍记录在 [工具边界](multimodal-demo-mobile-tools.md)。roadmap 仍 5/12、P9 active；本地提交，无 push/merge/publish。
