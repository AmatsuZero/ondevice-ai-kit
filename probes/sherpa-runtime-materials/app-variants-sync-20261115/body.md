<!-- w3-app-variants-delivery-20261115 -->
# W3 App-variants 交付更新（2026-11-15）

结论：接受 bounded increment。仅接受 `sourceHEAD fbf059f41527769129ba635e9511d297d3e5ede7` 下 Demo debug/release 与独立 Maven 消费者 debug/release 的打包/归档证据组合；

## 提交与 hy3 交付

- 本地证据提交：`05a04320d4146d5d0c3f355f74bd0fa2c760d40f`（45 files，source branch 未推送）。该提交是被接受的本地证据快照；sourceHEAD 是证据报告中的 source 侧 HEAD，两者不混同。
- 此前 3 次 hy3 请求均为 complete success / no observed tool events：fixture 19.671s、review 65.420s、correction 25.844s。hy3 仅创作 4 处精确 variant option 替换，父侧机械集成。
- 路线仍为 Whisper-tiny：`enableSherpaAsr=true`，其他可选后端 false，tiny model 资产沿用既有受保护输入；非 Qwen 生产集成或质量修复。

## 四个 APK identities

| APK | bytes | sha256 | notice / model | native |
|---|---:|---|---|---|
| Demo debug | 244934281 | `85aff749d85257b4e9368b618b4fa22ab0b0d47ff2d429a5aa7e2aa087ba6688` | 68 / 770479B；7 assets | 32 = 16 ASR + 16 historical extras |
| Demo release-unsigned | 236322599 | `43b2c5b9e4beebe1712747bf0943dbbcdcfb742f34d18800ea413b265f9010e1` | 68 / 770479B；7 assets | 32 = 16 ASR + 16 historical extras |
| Consumer debug | 128102798 | `33c3fdab3ac862f7b7645c5ff7c1820c7a5da806718a4317ff7b25ff43145edc` | 68 / 770479B；0 model assets | 16 ASR |
| Consumer release-unsigned | 127673445 | `affc7579eec2d2e61b7b4b7ec8e798f91f5183aed8549dabf0d7c4f09582608e` | 68 / 770479B；0 model assets | 16 ASR |

- 4 个 identity 的 `fullOrderedArchiveListingEqual` 与 `selectedASRAssetAndNativePayloadByteEqual` 均为 true；按 archive order 比较完整有序成员列表及相关 payload 字节，不是排序后的参数顺序，不扩展到无关 Demo payload。
- Real Demo 的 32 SO 为 16 fixed ASR + 16 historical extras；独立消费者为 16 ASR。Demo 额外 16 个为 4 个历史 SO x 4 ABI，既存于原始 Demo 且两个新 Demo 字节一致。notice payload byte equality 不是最终 native-code composition proof；接受组合证据，不要求唯一密码学证明，但不等于 full contract 已关闭。
- DEX：4 APK 均见 124 runtime + 18 backend 类名，DemoFactory 仅在 Demo；defined class-name presence 不代表语义或字节码等价。SDK commit literal 匹配 BuildConfig 且出现在 DEX，但非完整源真实性证明。
- 独立消费者：隔离 artifact root；debug 与 release 各 8 个精确 SDK/runtime/JVM artifact path 与 bytes，release 显式解析 `releaseRuntimeClasspath`；固定 backend AAR `ca0065f23e7c65fe0d30fc0d2035d97543a0b8f8470f5c6e194723fdf535478c` 来自隔离 Maven publication；无 SDK project components。非法 variant configure-time exit 1，debug/release exit 0。
- apksigner 只读：debug verify 0、release-unsigned verify 1，均符合 expected；unsigned Release build 不是 signing/distribution，也不是 device inference、quality 或 budget acceptance。

## 证据路径与边界

- 报告：`docs/validation/w3-app-variants-delivery-20261115.md`。证据目录：`.omx/artifacts/w3-app-variants-20261115T145000Z/`，含 `fresh-precommit-verification.json` 与 `fresh-protection-insertion-diagnosis.json`。
- 边界：真实 Demo debug+release 构建 exit 0（37.920s，739 tasks：261 executed、478 up-to-date），无生产源码编辑；无设备/AiAi/mic 推理或下载；未重试 source-host429、native/hy4free429；无模型/运行时二进制新下载，模型为既有受保护输入；APK binaries 仍是 ignored local artifacts，非 Git material directory。
- source 侧 304 selected inputs / 68 notices / 7 model-license inputs 未改，但不是完整 build-input closure；protectedFiles 175 receipts 保留，frozen 161 guard 通过，new terminal run 未准备/未消费，旧 Demo APK 哈希保留。

## 保护检查、Blockers 与剩余 gates

- Fresh strict protection check 初次失败：无关的 929B F08 GPU-preflight section 插入到 `docs/agent-pitfalls.md` 两个既有 section 之间；在内存中移除该确切 section 可重现原始 268388B hash `8d06e26d76ef4eaf5c7ff5f0ffb26880b9fc34f163907929c1a129c69f42f0a6`。工作文件未改且该新 section 保留，staged blob 排除并发变更；另两个 F08 文件 additive prefixes 保留。初始失败与诊断保留，无 product changes/rebuild/device action。175 baselines preserved，但不能表述为 all175 equal/suffix-equal：其中一个是原始顺序完整保留并含一处 non-prefix insertion。
- 4 rawGradle whitespace diagnostics 有意保留；all other text passed。
- Blockers/scope changes：source branch 未推送；source-host429 lane 已停止，不重试/镜像；hy4free/native429 不视为 hy3 unavailable，也未从 catalog listing 宣称 quota recovery；无 signing/distribution、device、quality、budget 证据。本更新只记录 bounded increment。
- 未关闭：P1 complete input trust/final production adoption，P2 composition/full applicable notices，P3 complete coverage，P4，R4 quality，R5/W8 budget，W8，W3；`W3Complete=false`。不承诺 release。
