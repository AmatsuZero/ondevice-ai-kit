# W3 Qwen TECNO 真机 load-only 已验收

用户确认已解锁后，冻结探针在 TECNO KM9/API35/arm64-v8a 上完成唯一一次模型构造和显式释放。
本项通过真机加载与采样资源门，可进入 Android 两阶段文件识别候选设计；不表示推理质量或完整两阶段预算通过。

| 项目 | 本次实际证据 |
| --- | --- |
| 日期/批次 | 2026-11-11 执行；沿用事前冻结 runId `w3-qwen-load-20261110-01`，没有补造前一日运行 |
| 前置 | Awake/已解锁，可用内存2818752512B，新alias不存在，无活动instrumentation；源码/APK/保护文件摘要一致 |
| 模型/配置 | 原六文件987015347B，手机JNI前逐size/SHA验证；Sherpa2.0.1、CPU2threads/128mel/greedy/seed42/temp1e-6/topP0.8/512/128，tokenizer子目录不变 |
| 实际动作 | 构造尝试1/成功返回；release尝试1/成功。streamsCreated/nativeDecodeCalls/microphoneStarts/networkRequests均0 |
| 耗时 | 构造9.381619386s、释放0.272898154s；父采样窗口14.206945750s，不能当作推理RTF |
| 同进程采样 | PID25102/startTicks221524649，49个RSS样本；最大1407234048B（1.310589GiB），实体系统MemAvailable最小1964892160B |
| 停止门 | 未触发1.5GiB RSS/512MiB可用/120s窗口阈值；无采样错误、终态观测gap或自动重试。采样RSS不是true peak |
| 实际间隔 | min0.232025s、median0.272112s、max0.384164s；如实保留真实间隔，不称精确250ms连续采样 |
| 报告 | terminal/completed、teardown/destroyed ack、主/释放/持久化错误均空；两次原始回读逐字一致并与手机SHA相符：`91d27c572d8bf205807a3df4377fc878068f1906466d36b8c60d96e37f46ce8c` |
| 恢复 | 六Qwen文件SHA后核一致；只卸本次main/test alias、清manifest匹配staging；六普通App路径和四普通模型SHA保持，实际普通Demo已回前台 |

父验收33项通过，sourceFingerprint保持
`605181e50ea9357c292be93e2bf634742c40566a13984a0ff0b83fdb8b9c0ecd`。
普通App安装路径一致性不等于重新hash全部普通APK；本次没有作该额外声明。
原普通模型、生产后端/依赖/默认服务、此前AiAi一次请求和已归档识别批次均未改变。

本项复用已有构建/公开模型，没有新下载或主机构建缓存；临时传输文件已删，设备临时包/暂存已恢复。
源码、APK、模型原件及全部transfer/原始报告/采样/恢复收据保留，无可安全新增清理的大缓存。

对照此前[模拟器加载](w3-qwen-emulator-load-accepted-20261110.md)：同一冻结探针，模拟器构造2.419s、
采样max1.204823GiB，真机构造9.382s、采样max1.310589GiB。两次均仅加载，不能由此推算推理速度或完整两阶段内存。

依据：[冻结合同](w3-qwen-android-load-protocol-20261110.md)、
[原始设备报告](w3-qwen-physical-load-report-20261111.json)、
[完整父收据](w3-qwen-physical-load-accepted-20261111.json)。
本机证据：`artifacts/verification/w3-qwen-load-20261110/`。

下一项为 Android 两阶段 FILE ASR 的候选、完整同输入质量对照与实际成本测量。
本项没有创建任何stream，warm RTF、完整两阶段内存、持续资源、true peak、GPU/能耗及生产通知/发布复审仍另验。
W3保持open，不将load-only通过改写为整包完成。

本次结果已本地提交`cb3ff40`；GitHub/GitLab的W3 #15及专题Wiki均已发布并逐端回读，
[同步收据](w3-qwen-physical-load-sync-20261111.json)记录评论链接、内容摘要和Wiki commit。
没有推送源码分支或上传模型/音频。
