# W3 native 通知集成与独立 APK 保留

2026-11-15：已知 native 通知子集及上游修改说明已进入 Sherpa SDK，新 Debug/Release AAR、lint 和独立 Maven 最终 APK 验证通过。本次没有关闭完整 P1–P4、R4/R5 或 W3。

材料入口为 [README](../../probes/sherpa-runtime-materials/native-notice-integration-20261115/README.md)，其目录保留 19 文件，其中 manifest 校验其余 18 文件。完整调用、原始输出和构建日志保存在 `.omx/artifacts/w3-native-notice-integration-20261115T115026Z/`。

## 实际改动

`android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/native/` 新增 23 份原文本、`index.json` 和 `UPSTREAM-MODIFICATIONS.md`，共 25 项、477865 B；原文本合计 452769 B。加上既有 JVM 11 项，目前共 36 项通知资产、550583 B。模块 README 采用 hy3 候选及其限定复核提供的段落。Kotlin、Gradle、依赖、版本、锁、模型和固定 runtime AAR 未改变。

选择来自 58 项已固定参照，选中 ID 为 `1..10,12,13,20..29,57`。覆盖 Sherpa/ORT 根许可、完整上游 ORT ThirdPartyNotices、已知直接依赖、Sherpa 实际 Eigen 5.0.1、ARM 观察的 websocketpp/asio 以及 simple-sentencepiece 内 Darts 原 BSD2 归属。所有未选项均保留处置依据；历史 Eigen 3.4 不混成当前版本，KissFFT Unlicense 不被断言为法律不适用，Android 被 stub 替换的原 ThreadPool 不被误记 MIT 或必然嵌入。ORT 嵌套 27 项仍待递归最终组成选材，完整上游 ThirdPartyNotices 不能替代该验收。

修改说明记录 Sherpa `version.cc` 的日期/短 SHA 修改、simple-sentencepiece 三文件线程池移除与顺序处理修改，以及 ORT 两条实际 sed 命令和四 ABI 所示 diff。旧 P1 patchSummary 把命令概括为删除 SOVERSION 与 target VERSION；本次逐一核对发现，第二条命令的匹配模式不等于实际移除了所有 VERSION，多行 `VERSION ${ORT_VERSION}` 仍在。新解释保存在 `ort-patch-interpretation.json`，旧历史材料保持原字节。所示 diff 仍不代表全部生成输入或补丁已穷尽。

## 候选与父端验收

免费 hy3 的选择、修改说明、独立供应文本复核分别耗时 49.151、34.180、49.827 秒，均为 CLI 0、完整 success result，无已观察实际工具事件或 quota。复核返回 `accept_bounded_candidate`、无 findings；它明确没有执行来源摘要、构建、最终组成或法律符合性验收，不能作为 P4 完整发布接受。三个原始请求和收据均保留；没有重试旧 native/hy4free 429、自动 fallback 或付费请求。

父端首次机械绑定假定 Darts 也有可选 `sourceManifest` 字段，触发 `KeyError: 'sourceManifest'`。在索引及修改说明未完成时错误提前启动的构建自然结束 12.285 秒、exit 0，已明确拒作完整候选验收。失败原件、首 AAR 和旧 m2 目录均保留。后续依据 Darts 实际材料 manifest 补绑定，原文本不改，完整候选使用新 `m2-complete` 离线重建。

| 验证对象 | 实际结果 |
| --- | --- |
| 完整 SDK 构建 | 11.135 秒；435 任务，29 执行、406 up-to-date；Debug/Release AAR、lint、隔离本地 Maven 发布通过。backend Kotlin 仍 up-to-date，不称全量重编 |
| Release AAR | 161636 B；SHA-256 `5ae065d23647898eed3cbc258395f1c9c98804b002e4f9b4934546ae55bb7dd5` |
| Debug AAR | 163500 B；SHA-256 `291d74a98aef7b27be1669e2abc43472967519f6b260b02ca2bae643799227e1` |
| AAR 内容 | 两者各 40 非目录成员、36 通知资产；Python/Info-ZIP 原字节核对一致。Release 原 15 成员含 JVM 11 资产全部保持，只增 25 native 资产 |
| 独立 Maven APK | 复用上一轮 hy3 三文件 application 夹具，7.709 秒、35 任务全执行；不含 project dependency、模型或普通 Demo。128014927 B、71 非目录成员；SHA-256 `20be0aa3e9c8a3a3ef40952ca7029c2f5a171761187ef98cfffa68329debef2a` |
| APK 内容 | 实际解析到本轮新 SDK；36 通知资产经 Python/Info-ZIP 全字节相同，四 ABI 的 16 SO 与固定 runtime 相同。固定 AAR SHA 仍为 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7` |
| 六项负例 | 在 APK 复制件分别删除或同长改单字节 Darts NOTICE、修改说明、native 索引，全部拒收，原 APK 保持 |

新 `verify-delivery.py` 检查完整 `assets/ondeviceai/sherpa-asr/notices/` 前缀，不误用旧 JVM-only 检查。收尾时重新核对 23 份来源/目标、18 项材料 manifest、AAR/APK 摘要和 36 项资产，并重新验证现有六负例均拒收；没有重复构建或修改原负例。SDK XML 及原 SO 不可 strip 警告保留，不称无警告构建。

## 范围与后续

173 个原保护路径在新增文档前全部保持，161 文件冻结合同通过；共享脏文档只追加本轮前缀，提交时以 `prefix + HEAD blob` 构造暂存内容，避免夹带原编辑。唯一 fresh ADB 清单为空；本轮未安装或执行设备，未 prepare/解码、开启麦克风、请求 AiAi 或下载模型。

本次通过的是已知 JVM/native 子集在实际 SDK 与独立最终 APK 的技术保留。完整递归组成、全部适用通知与修改说明、最终生产 App 采用及独立发布接受仍待补齐。继续条件与关闭要求以 [W3 当前验收范围](w3-closure-scope-20261115.md) 为准；R4 完整目标真机质量和 R5/W8 资源/预算仍 open。
