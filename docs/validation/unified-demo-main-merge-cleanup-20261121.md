# 统一 Demo 合入主干与清理 — 2026-11-21

统一 Demo 实现提交为 `bb5439ee45c6d99d1570108b03b59dafac332f92`；主干 merge 提交为 `14068f9a72238aec6e5c04ec91ef04e2cb7cbdf4`，父提交为原 main `e533472` 与 Demo 提交。GitHub `origin/main` 和公司 GitLab `jihu/main` 均已推送并回读为该 merge SHA。本报告之后的文档提交不改变实现。

主干原来尚无 SDK/Demo 实现，因此合并保留 foundation 的已提交历史，没有只移植两个依赖不完整的 Demo 提交。最终 merge 文件树与 `bb5439e` 完全相同，主干独有设计提交保留在历史中。原生助手给出 `ACCEPT_BOUNDED_INTEGRATION`，仅审核本轮 33 个路径、排除原 dirty 内容后的引用闭包，不代替构建或设备验收。

## 本次验证

- clean main 合并候选：shared `iosSimulatorArm64Test` 105 项、Android 单测 67 项，均零失败、错误及跳过；双 iOS Debug framework 链接、Android Debug APK 构建通过。Gradle 616 项任务执行，51 秒成功。
- Android lint：0 errors、11 warnings。
- 全量工具测试：725 项通过，47 秒。首轮有 3 项 Swift/Clang 用户 ModuleCache 写入被沙箱拒绝；保留失败日志，在正常构建权限下原代码重跑通过，没有修改测试来掩盖失败。
- 本轮没有新增 iOS UI/真机运行；前一阶段四项 iOS 模拟器 UI 结果见[实现报告](unified-demo-implementation-20261121.md)，不能把 framework 链接视为设备验收。
- 原有历史许可、来源证据和少量旧代码有空白诊断；保留原文。真正新增的 Demo 提交及 merge 相对 foundation 的差异检查通过。

公司 GitLab 默认经本机代理时返回 `Recv failure: Connection reset by peer`；同一远端用调用级 `git -c http.proxy= -c https.proxy=` 直连成功读取、推送及回读。未修改全局代理、远端 URL 或凭据，未 force push。

## 清理与保护

清理前只读统计目录占用、构建进程和打开句柄；删除清单精确限定为验证副本中的 Android `build/intermediates` 及 Xcode `Intermediates.noindex`、Module/Index 缓存，没有 tracked 文件或保护路径，没有打开句柄。

- 实际删除 60 个目录，原目录分配量合计 18,110,435,328 B（约 16.9 GiB）。
- 删除窗口实测可用空间增加 13,560,770,560 B（约 12.6 GiB）；不能将目录占用总和等同实际释放，亦不归因其他时间的磁盘波动。
- 删除后约 27 GiB 可用；主干重新构建及工具测试后约 26 GiB。
- 保留模型、媒体素材、trace、xcresult、最终 APK/App、原始报告、源文件与 helper 输出；保留 dirty foundation 和其他工作树、分支，不用 `reset`、`stash`、`git clean` 或强制删除。
- foundation 任务前 1,354 个 dirty/untracked 文件逐项 SHA-256 复核：无缺失、无内容变化；提交造成的 Git 状态变化单独处理，不宣称 index 字节未变。

主工作区另有四个未跟踪文件与 incoming 路径同名且内容不同，merge 首次安全拒绝覆盖。原件已移动并逐字节核验于 foundation `.omx/artifacts/main-merge-cleanup-20261121/original-main-files/`，路径如下，未作为 Demo 改动提交：

- `docs/validation/speech-backends.md`
- `tools/inspect_android_vlm_artifact.py`
- `tools/probes/native_tts_probe.sh`
- `tools/tests/test_android_vlm_artifact.py`

主工作区原 `AGENTS.md` 与 `.xcodebuildmcp/config.yaml` 字节保持，仍未跟踪。

## 播放器入口边界

统一 Demo 已有“播放时增强”入口和状态展示，但双端绑定的 `realtimeSr` 仍为空，未连接选片、实际播放器、增强后端选择及播放生命周期。用户暂时不能从统一入口体验边播边增强；此前独立播放器和 KMP bridge 的实现并未删除。该入口应明确显示未接入，而不能将目录卡片视为功能已整合。冻结的视频理解、身份活体实验没有恢复。

本次日志与精确删除/保护收据位于 `/private/tmp/odai-main-merge-cleanup-20261121/`；原生报告为 `/private/tmp/odai-unified-demo-merge-review-20261121.md`。它们的小型日志与收据副本另存 foundation `.omx/artifacts/main-merge-cleanup-20261121/evidence/`，保留原始首错。
