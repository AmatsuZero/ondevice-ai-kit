# Android 当前源码候选与字节复用 — 2026-11-07

`636a6eb8854db5dff74244c42c3ce0e82bd2be9f` 的全后端候选已完成离线构建和本地包核验。
新语音与 SR 清理修复已编入此包，**尚未装机验收**。手机仍运行原 `afed156b…926c461` APK。
本轮关闭当前源码构建子项，并证明本地字节复用可行；基础路线图仍为 10/12，P4/P11 open、release not-ready。

## 构建与包身份

从精确提交归档核验 1046 份源码文件，复制并核验 14 份既有固定输入，未下载模型或依赖。
九个保护路径的本地修改没有进入源码副本；生产 Apple lock 保持。
LiteRT-LM、LiteRT SR、OpenCV Identity、Sherpa ASR 四个可选后端同时启用，使用既有 JDK21/Android36，
离线、禁用 build cache 并显式 rerun，997 个任务全部实际执行，构建耗时约 2 分 3 秒。

599 项 JVM 测试实际通过，无 failure/error/skip；20 模块 lint 为 0 error、52 warning。
原 `build-checks.json` 覆盖主模块的 73 份 XML／588 项测试；最终冻结清单另纳入示例和探针的
两份 XML／11 项测试，共 75 份 XML／599 项，没有重跑构建或改写原收据。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| 普通候选 APK | 456350170 | `dfccaa95a809b4f37b8d384cd32f92c37c7422178803dc1ff5d44a88654879a7` |
| 候选测试 APK | 139454 | `2848cc8f723d8149e7ce47875fb428c02dab42d93e97e1dcce4b3c8ed54d0b0a` |

包名 `io.ondeviceai.demo`，minSdk26、targetSdk36、debuggable，MainActivity 与 DEX 内完整提交号均已核验。
主包、测试包及原回滚包通过 apksigner，证书摘要相同；46 份原生库和 7 份 Sherpa 资源与原包逐字节一致，
ASR 资源亦与固定构建输入一致。这里只证明签名和包内容，不替代运行或发布签名批准。
测试包 manifest 为 TtsProbeInstrumentation，尚未运行；不得直接运行原 Identity runner 改动既有 journal。

首次父代理包检查把 instrumentation 的 targetPackage 误设为 `aapt2 dump badging` 的输出字段，
断言失败已保留。改读同一 APK 的 XML manifest 后核验通过，没有修改产品或重建。

## cbc 候选与本地精确重建

cbc `hy3` 的既有请求在 415.712 秒自然完成，进程 exit0、完整 success 正文、无工具事件。
未因耗时或静默取消，也没有重复发起。父代理补充负向用例，复现四个候选问题：
源文件追加和路径替换未拒绝、失败清理会删除替换后的输出、第二输入打开失败泄漏第一文件描述符。
父修正使用固定文件描述符读取 ZIP 元数据，核对源身份／长度／元数据和前后全量摘要，
并修正描述符关闭、清理身份检查及压缩长度匹配。四项原候选 RED 后，修正版 10 项离线检查通过。
首轮另有一项异常类型预期过窄的检查错误，单独保留，不当作产品缺陷 RED。

在本机用已保留的原 `afed156b…926c461` APK 与新候选生成 135 段计划：

- 复用原始压缩数据字节 446501165 B；新 literal 数据 9849005 B，约为整包的 2.16%。
- ZIP 头、签名块、目录和其他间隙均按原始字节保留，没有重新压缩、打包或签名。
- 独立流式拼接后的 456350170 B 与新候选完整 SHA-256 相同，重建结果再次通过 apksigner。

这是约 97.84% 主包传输量削减的本地可行性证据，不是设备端传输或重建成功。
辅助函数只在 ignored 验证目录内，不是 SDK 产品接口；输出 inode 清理检查也不构成对任意并发替换的原子保证。
原提示、调用、响应、候选、父处置在 `.omx/artifacts/cbc-apk-byte-plan-20261107/`，
摘要入口 `.omx/artifacts/ask-cbc-apk-byte-plan-20261107.md`。

## 设备与后续门槛

一次只读观察确认 TECNO KM9/API35 在线；原主包、原 test/Identity runner、25 项私有摘要、四份 SR 文件、
权限和 TTS 默认值与保留基线一致。没有安装、推送、采集、推理或上传；无需为本项重连设备。
此连接状态仅是记录时观察，不保证后续连接状态。

下一步先验证设备端精确拼接和暂存恢复协议，重新核验候选、原主包、原测试包及所需新测试包的
本地／远端全量摘要，再安装和验证当前修复。旧 `b90e…64d8d` 或 `afed…` 的运行结果不转标成新包结果。
USB 断连只观察同一 runId，不自动重复推理；恢复不依赖推理成功。自然质量、长期资源、校准、
最低系统真机、完整独立复审和生产交付的原门槛仍保留。

本地证据在 ignored `artifacts/verification/android-current-candidate-20261107/`。
只读复核，无设备操作、无模型请求、无构建重跑：

```sh
rtk proxy python3 artifacts/verification/android-current-candidate-20261107/freeze.py --verify
```

Qoder 调用方式已在根本地 AGENTS.md，先前两项成功记录保留；最近的 HTTP403/error_code112 线路继续停止，
不重试、不据此推断具体余额。本轮未启动失败的原生审查器、dsh 或 OpenCode 额度线路。
