# 精确提交 Apple/shared 候选 — 2026-11-05

状态：同一源码候选的 Apple host、KMP/shared iOS 单测、基础 Demo/消费者构建和注入后端桥接测试通过。
本次未重建增强 MLX/OpenCV 整 App，也未做当前候选真机、模型推理或普通 UI 验收；发布仍 `open/not-ready`。

与 [Android 候选](android-committed-candidate.md) 使用同一份已核验归档：
提交 `e0438c030a987ceeff9f9207c2475684749e4d8b`，tree `1a79aabdc997ca99fe441a02b9cb54982ce2c34c`，
归档 SHA-256 `c3b5966e09fdd44a957e3da1f3a2674a289857f99e02211a18f1f772155373b4`。
重新独立提取953份 committed 文件，逐文件 SHA/执行位与既有 Git blob 清单一致；
没有复制受保护的 dirty iOS project。所有阶段结束后再次核对953源文件、9保护路径和生产 lock。

使用现有 Xcode27.2/Swift6.4.2、JDK21、Gradle9.4.1、Kotlin/Native2.4.0 与依赖缓存。
Swift package 无外部依赖；Gradle 使用 offline/no-build-cache/rerun-tasks 和显式本地 Native 工具链，
35个构建任务全部执行。没有下载、采音、拍照、签名资产变更或发布，也没有操作 TECNO 的可用 Demo。

| 验证 | 实际结果 |
| --- | --- |
| `apple` macOS host XCTest | 8目标、481项，0失败/跳过；按唯一 test case 和各目标日志核对 |
| `kmp-binding` iOS simulator | 24项，0失败/错误/跳过；6份XML报告 |
| `demo/shared` iOS simulator | 80项，0失败/错误/跳过；6份XML报告 |
| KMP frameworks | Binding simulator、shared simulator/device Debug静态framework链接通过 |
| 基础 Demo | 既有generator开启speech/video/multimodal UI，unsigned generic iOS与simulator构建均成功；Info.plist内完整sdkCommit匹配 |
| 原生消费者 | 同归档 `NativeConsumer` generic simulator构建成功 |
| Swift→shared Demo ABI | Speech39、Video9、Identity10、Multimodal75，共133项；实际iOS27.2/24B8117 simulator、0失败/跳过/预期失败，逐组xcresult与日志一致 |
| 既有辅助检查 | Swift bridge、camera lifecycle7场景、photo submission、合成ImageIO方向1/2/6/7、ASR状态机、Swift5/6严格并发均通过 |

桥接测试使用注入后端、合成输入和临时store，验证取消/drain、权限晚到不启动、locale选择、
输入/输出lease、host关闭及未校准Identity策略拒绝等契约；不是麦克风、相机、PAD模型或自然质量验收。
macOS host通过也不代表仅iOS可用分支全部运行。基础Demo含多模态UI，但没有启用MLX模型factory或OpenCV Identity，
不能将它表述为这些真实后端的整App候选。

六组iOS编译/测试配置的外部artifact共120条引用、80个实际文件，逐文件记录坐标/大小/SHA；
Binding无外部Klib，shared每组40条。两平台Skiko均仍为生产 `0.144.6`，没有替换为F02补丁版本。
收据不声称冻结了整个系统SDK/Kotlin工具链内容。F02生产分发与资源门槛继续开放。
六个framework/App产品完整文件树已冻结；unsigned构建不是签名、安装或设备运行证据。

原日志保留Kotlin Native系统cache archive路径警告、既有无用cast和AppIntents提示，
多模态注入semaphore用例仍有XCTest QoS priority-inversion警告；没有为这些提示修改产品或放宽检查。
首次process inventory与xcresulttool报告缓存读取被sandbox限制；普通权限提升后收集成功，未重跑测试。

原始证据位于 ignored `artifacts/verification/apple-committed-candidate-20261105/`。
`summary.json` SHA-256 为 `79c0bca1bb9c9c1829ac68bf7dd50f6a75c2c0eaa919b577b58950d6d3d3f5ff`。
`run.py`/各阶段started-result-log、12份KMP XML、四份xcresult统计、resolution与完整产品树均保留；
所有请求已terminal。只读复核：

```sh
rtk proxy python3 artifacts/verification/apple-committed-candidate-20261105/verify.py
```

本轮新增718项不同层级测试执行（481+24+80+133），不将它们合成为功能覆盖率或benchmark样本。
下一为同源码增强Apple整App及适当设备运行、当前Android候选TTS支持引擎设备/真机、
独立完整审查与生产交付/许可决策；真实采集、质量/资源、最低系统、F02/PAD校准及P4/P11缺口保持。
