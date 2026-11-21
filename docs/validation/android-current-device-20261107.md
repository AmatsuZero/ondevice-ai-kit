# 当前 Android 候选真机 ASR/SR — 2026-11-07

精确 `636a6eb8854db5dff74244c42c3ce0e82bd2be9f` 候选在 TECNO KM9/API35 完成
一次固定输入 ASR native 生命周期和一次真实 SR 生命周期验证，私有终态均通过。
原主包、原 test/Identity runner、25 项私有摘要、四份 SR 文件、权限与 TTS 设置已恢复核验，
本轮临时文件精确清理并返回原 MainActivity。没有新增录音、拍照、模型下载或上传。
当前修复的这两项真机缺口关闭，完整 F12、P4/P11 与发布仍保留原退出标准。

## 精确候选与暂存

普通 APK 沿用[上一轮原始构建产物](android-current-candidate-20261107.md)，456350170 B，
SHA-256 `dfccaa95a809b4f37b8d384cd32f92c37c7422178803dc1ff5d44a88654879a7`，没有重建或重新签名。
另取同一源码归档，只增加两个绑定当前完整提交号的持久报告测试 overlay；1046 原文件前后不变，
固定构建输入、既有 PCM、两个 runner 的 manifest/DEX/签名和输出 APK 摘要均已核验。

| 测试包 | 字节数 | SHA-256 |
| --- | ---: | --- |
| ASR 生命周期 | 213242 | `aed34dd28d1075db0351905f0be3dd9276e6ab4e64aa30bcd507f51e14e2dd53` |
| SR | 214249 | `e3405c13604c70a21c7d5b0cb573e5c1e3a495aef18c4c6e94b4871af94f2652` |

手机端保留原 APK 和原测试包作独立回滚，上传 9849005 B literal、两份小测试包及脚本。
135 段按原始字节拼出完整新 APK，远端完整摘要与主机构建产物相同；没有重新打包或签名。
安装前全部五份候选／回滚／原 test／ASR test／SR test 的独立本地和远端全量摘要均匹配。
本轮验证了约 97.84% 主包 USB 传输削减，实际主 APK 和回滚主 APK 均未经 USB 整包重传。

首次设备重建的最终 `ln` 被拒绝，原持久退出码 1 和日志保留。
此时 `candidate.partial` 已全量匹配新 APK；核对后以禁止覆盖的本机复制完成发布，再验全量摘要，
记录 `publication-recovery.json`，没有重新执行那 135 段重建。
另有设备帮助列出 `conv=excl`、实际运行却拒绝的情况，改用 shell noclobber 复制；
字节偏移参数通过固定 16 字符 stdin/stdout 实测。两类失败均不算安装或推理失败。

## 当前包的实际结果

- ASR：`current-asr-native-20261107-636a6eb`，私有报告 SHA-256
  `3a35808329fbf79a62ea89211cec39a08091ece0a47e67d9a2a7ccf544b1023e`。
  13 项 native 取消、drain、同 backend 重入、活动 close 和关闭拒绝检查通过；
  `microphoneStarts=0`，未持久化音频、转录或线程堆栈。
- SR：`current-sr-native-20261107-636a6eb`，私有报告 SHA-256
  `d76748efad54320ee9560da7181607ea020e9947e589602a27db9ca90182eba3`。
  独立 native runId `e4c1c7e1-37e8-4ae7-a91b-89ee92fe64ef` 与原 SR 报告不同；
  六项检查通过，包括取消/drain 后重入、close 后输出保留和 release 删除输出。
- 主机对同次回读的输入、保留音频输出和丢弃音频输出全部完整解码：
  两帧 64×48→256×192，视频 PTS 误差不超过 1µs，保留输出 AAC packet 摘要/PTS 与输入相同，
  丢弃输出无音轨。自然质量和资源预算不由这些固定输入结果证明。

两次报告均核对唯一 runId、完整 sdkCommit、精确主包/测试包身份及回读前后原始字节摘要。
USB 曾短暂 not-found/closed/unauthorized 后自行在线；只读观察允许等待同一设备后复读一次，
安装、删除、重建和推理不会自动重放。实际 ASR/SR 各运行一次，没有从断连重发推理。
安装前的 ASR 只读检查曾失败，当时没有启动意图，不能把这些检查失败算作已执行探针。

## 恢复、辅助审查与边界

恢复不依赖推理通过。原普通主包恢复到 `afed156b…926c461`，原测试包为 `7b9e47ce…3a621d`，
原 Identity runner、25 私有摘要、四份 SR 原文件、权限和 TTS 设置前后相同。
按精确摘要删除本轮两份私有报告、五份暂存 APK、两块 literal、合并 blob、脚本和 PID/退出码文件；
唯一临时目录已为空后删除，原目录保留，最终返回原 MainActivity 并再次核验恢复状态。
九个保护路径与生产 Apple lock 保持。

27 项离线协议检查通过：16 恢复、6 传输解析/生成、5 USB 观察边界。
这是验证脚本的检查数，与上一轮 599 项实际 JVM 测试分列。
cbc `hy3` 的限定只读审查在 120.147 秒自然完成；四条意见经实际输入和调用者核对后否决，
包括把正确 `SHA_OLD_TEST` 错引成主包摘要，以及把未提供的 run 模块推断为不存在。
原文/父处置在 `.omx/artifacts/cbc-current-device-protocol-review-20261107/`；
后续父修改和真实设备结果另列，不把这份意见当完整分支或发布批准。
未重启已失败的原生审查器、Qoder403、dsh 或 OpenCode 额度线路。

本轮是当前精确候选的固定输入回归，不增加 P11 benchmark 样本，分组仍以现有 manifest 的 10 组为准。
Apple 当前统一候选、其余能力/普通交互、自然质量、长期资源、授权校准数据、最低系统设备、
完整独立复审、许可和生产交付原门槛继续；10/12、P4/P11 open、release not-ready 保持。
旧构建收据 `deviceAcceptance=not-run` 只描述当时构建阶段，原字节保留，由本轮独立证据补充。

本地 ignored 证据：`artifacts/verification/android-current-device-20261107/`。
`device-summary.json` 固定两份终态报告、媒体、源码/overlay、暂存/恢复收据与脚本摘要。
只读复核，无 ADB、推理、模型请求或构建：

```sh
rtk proxy python3 artifacts/verification/android-current-device-20261107/freeze.py --verify
```
