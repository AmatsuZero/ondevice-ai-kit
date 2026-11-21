# Android Identity 固定输入资源测量

2026-11-07：TECNO KM9/API35，当前冻结636a6eb/dfccaa95主APK，
新test-only包212190B/SHA `95c2694167c9d6a6edd99d4e197806fc8c86c8b0fa9203acb884489989fe52de`。
一次前台受控运行 `identity-foreground-resource-20261107-01`，PID4454，5预热+30测量，
完整私有终态两次留存一致；72个RESUMED检查点、关闭、模型租约释放、工作目录清理及输入/模型保持通过。
这测量固定图片的检测/几何质量/对齐/128维特征管线，不是身份准确率、注册或PAD校准。

## 实测与范围

| 项目 | 结果 |
| --- | --- |
| SDK evaluateQuality + embed 墙钟时间 | p50 133.174ms；p95 148.906ms；范围124.708–155.106ms |
| 30轮采样进程RSS峰值范围 | 437751808–472952832B |
| 后端打开前／打开后、调用前RSS | 315252736／377446400B |
| close后／再等1000ms RSS | 451489792／451489792B |
| 实际RSS采样间隔 | p50 21.972ms；p95 26.063ms；最大42.631ms；目标20ms |
| 原始报告SHA256 | `8ed478ba928f060722a87928655777e591fd7a40f20d99cf579b29d306111b61` |

计时不含App启动、模型安装/后端打开，包含实际探针采样开销。RSS包含普通首页、其他已初始化
运行时及SDK，是进程采样值；不能归因为SDK独占内存、真实峰值或即时释放。没有强制GC。
RESUMED只在72个检查点观察，不能证明两点之间连续可见。无camera/mic/注册/匹配调用，
不保存embedding/身份标签；沿用既有公开NASA图和固定模型，无下载/新媒体采集/阈值修改。

## 冻结恢复与工程校验

原Schema1的同次PID31731在7行处停于 `do_freezer_trap`；ActivityManager仍报告活动测试，
未证明OpenCV死锁或具体OEM冻结机制。保留输入/私有状态并确认普通模型staging为空后，
仅将同PID MainActivity前台化一次，该原请求自然完成35行/释放/清理，未重复签发。
该运行含人工干预，明确不入干净基线；原包与精确临时路径随后完整恢复/清理。
原始目录 `artifacts/verification/android-identity-resources-20261107/` 保留原Schema1 reducer。

新Schema2将前台启动放在所有测量前，启动前再次拒绝非空/链接staging；普通Identity会话始终不开启。
主线程读取生命周期，断言留在instrumentation线程以便写失败终态；5+30调用前后及启动/关闭后共72点。
新增打开前RSS，保留旧beforeRss语义，并在reducer/grouped输出固定进程范围，未改生产SDK。
Copilot42.21秒给出限定方案审查，0工具/错误，父采纳存储门禁、精确schema和进程范围意见；
原文/父处置 `.omx/artifacts/copilot-identity-foreground-recovery-review-20261107/`。
这是原源码+提议的审查，不冒作最终完整分支或发布批准。

Schema/输出范围真实RED后51聚焦GREEN；四个离线协议脚本34检查通过；完整tools **531/531**，
48.933秒，源码/9保护/Apple lock前后摘要保持。新测试包offline397任务/42秒完成，
原1046源码、完整commit、Manifest/minSDK26、DEX和签名兼容核验通过；构建warnings保留。
第一次stage因父漏prepare而在APK复制前停止，只创建已登记目录；补本地prepare后同目录恢复，
没有重跑推理。USB短读中断只等待原设备；host255由同run持久终态核实，没有人工运行中恢复。

原afed主包、原test/Identity runner、25私有摘要、相机/麦克风权限、TTS与四SR文件恢复核验通过；
报告/四暂存APK/分块/重建元数据按哈希精确清理、返回原MainActivity，无清App数据。
最终source-manifest绑定原始报告、输入、测试源码/APK/构建/协议/恢复收据及冻结源码归档。

## P11接入与保留门槛

当前manifest新增 `identity-android-resource` 的30次测量；覆盖从7能力/10组增至8能力/11组，
原10组逐项一致。Identity行仍为 `unverified-calibration-pad`，9行均保留gap，
基础10/12、P4/P11 open、release not-ready。识别/PAD授权数据、阈值/准确率、最低系统、
长期资源预算、GPU/能耗和实际发布门槛不变。473MB观测值和close后保留不等于资源预算或泄漏裁定。

本地ignored证据：`artifacts/verification/android-identity-foreground-resources-20261107/`；
readiness/grouped位于各自 `p11-*-20261107-android-identity-resources/` 目录。
原始证据不随普通Git clone；源码/测试/协议说明与本分支一起提交，不上传制品或push。
