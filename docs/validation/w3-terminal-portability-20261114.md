# W3 TECNO 准备失败与主机可移植性修正

TECNO 已恢复 ADB，首轮新鲜前置检查通过；随后一次准备在暂存目录身份检查失败，零安装、零模型传输、零 instrumentation、零 native decode。原失败没有改写为成功。当前修正面向独立合同 `w3-qwen-terminal-20261114-01`，不重放已消费的 FILE/heap，也不向原失败目录重新执行准备。

## 现场证据与恢复

现场证据根为 `.omx/artifacts/w3-terminal-physical-20261114-0036/`。TECNO KM9/API35/arm64、用户0、awake/unlocked 均通过，初始 MemAvailable 为 2,856,394,752 B。一次 prepare 在 18.925 秒后因 `stat -c '%u %d %i'` 的远端参数被拆开而终止：stdout 为 `2000\n`，stderr 把 `%d` 和 `%i` 当成文件。此时只有本次暂存目录和33字节所有权 marker，没有安装或传模型。

自动恢复同样遇到未引用的 `stat`，另把 TECNO 对不存在包的 `pm path` 退出码1、双通道空输出判成错误。原 `artifacts/verification/w3-qwen-terminal-20261112/restoration.json` 的 `allRestored:false` 保留。

主代理重新只读核验所有权两次：UID2000/dev65100/inode34334、唯一 child `.odai-owner`、33字节 nonce 与 durable claim 相同且无链接。随后仅删该 marker、`rmdir` 空目录；没有 `rm -rf`、没有卸载普通 App、没有重跑原 restore。补充 `manual-recovery-result.json` 确认精确暂存和两别名均不存在，六个普通 package 路径/六APK摘要/四个 Whisper-Silero 文件摘要保持，普通 Demo 用户0前台恢复。该补充收据不替换原失败。

原140冻结文件全部实际字节另存 `frozen-before-correction/`。历史源码校验器已在该快照独立验证全部140项，冻结摘要仍为 `f778ea664bd407007845e4035428e06262fe0531033735d9fefe961fd4afc4c0`。原失败输出的摘要另有保存。

## 修正与验收

免费 CBC hy3 产出候选，主代理审查、精确集成和运行验收；实现未由主代理或 native 子代理代写。prepare 一处和 restore 两处 `stat` 均改为 `shlex.join` 合成一个远端命令。恢复新增有界包状态判断：只有精确整数0/1、stdout/stderr 均为精确空 bytes 才算不存在；存在必须为退出码0、无 stderr、逐行有效的 `package:/…apk`。未知退出、畸形行、空白、CR/NUL、非UTF8或诊断文本都会报错并保留别名；卸载后仍要求精确 `Success` 和已证明不存在。原 APK 摘要、nonce、inode、known children、durable claim、一次性恢复边界保持。

父端新增真实 argv 重组夹具。旧源码5方法中4失败，现场 `stat` 错误逐字复现。hy3 首稿又实测错误接受4类畸形包路径，原稿/失败保留；独立定点修正后原5方法全部通过，另有精确空字节/多split路径/畸形路径解析检查。新增 OEM 六action链路两方法通过，覆盖成功和资源超限后的单次恢复。

新合同使用输出 `artifacts/verification/w3-qwen-terminal-20261114`、独立 runId 和两份新日期复审记录。原81准备源及两APK逐字节复制核验，不重新构建、不改 Kotlin/sourceFingerprint。原失败目录、原冻结、旧复审均保留为历史；当前源码校验针对新合同。

新合同最终 **384 方法、64 文件 AST** 通过，103个原有dirty/untracked文件摘要无漂移。首合同夹具18ERROR来自主代理先启动测试、后填充新输出本地输入；保留原输出，填充相同输入后独立18GREEN，没有改正确源码或测试迁就该失败。首失败aggregate保留，最终汇总明确绑定该补核及OEM两项。

两份16模块复审均接受。hy3首审依据未提供的 `call/text` 行为推测非零退出可能被吞，给出medium/low；主代理补实际不可变FILE绑定和5项可执行复现后，hy3撤回两意见，无需修改正确源码。父首次复现夹具未支持 `test ! -L`、随后错误消息表示断言过严，均保留并明确订正；最终复现真正到达缺失目录/文件谓词和模拟rm拒绝，全部抛错、零变更。新冻结 **161文件** 已由现行守卫回读，摘要 `6387847c79904c7f67d03dfec7c92c79e69467b408064f5a7fb96bbe3494d7fa`。

新冻结后的真机前置检查在3.806秒时报告 `adb: device … not found`，发生于普通package只读快照；尚未调用新prepare、未写 `device-before.json` 或 `run-started.json`。随后USB仍枚举TECNO、ADB清单为空；一次有界ADB服务重启后仍为空，当前日志 `usb_read failed with status: e00002ed`，只证明IOKit未响应，不定位物理原因。不把短暂前序读取成功推断为当前连接、锁屏或授权情况。原stdout/stderr完整保存在 `physical/`。

完整机器收据见 [w3-terminal-portability-20261114.json](w3-terminal-portability-20261114.json)。本轮没有重新运行 Android build/lint，因为集成只改主机探针及新合同标识、两APK/Kotlin准备源摘要完全相同；该事实不替代新合同真机验收。

## 范围

以上主机证据不证明 TECNO 新合同已运行、完整 Android 质量/资源通过、原生 allocator policy effect 或 true peak。门限仍为初始2GiB可用、采样RSS1.5GiB、可用512MiB、阶段120秒/总240秒，固定原index25、最多一构造/两流/两解码。不开麦克风、不请求 AiAi、不下载模型、不修改普通 App/models。W3/R4/R5、完整真机质量/预算和生产最终组成/source-to-AAR/实际通知保留/发布复审保持未完成。
