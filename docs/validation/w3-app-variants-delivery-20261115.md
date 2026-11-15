# W3 App Variants Delivery 证据报告（2026-11-15）

结论：接受 bounded increment。仅接受当前 sourceHEAD `fbf059f41527769129ba635e9511d297d3e5ede7` 下 Demo debug/release 与独立 Maven 消费者 debug/release 的打包/归档证据组合；不关闭 P1/P2/P3/P4/R4/R5/W8/W3。

## 范围与边界
- 当前为 Whisper-tiny 路线：`enableSherpaAsr=true`，其他可选后端 false，tiny model 资产沿用既有受保护输入；不是 Qwen 生产集成或质量修复。
- 真实仓库 Demo debug+release 构建 exit 0，37.920s，739 tasks（261 executed，478 up-to-date）；无生产源码编辑。
- 无设备/AiAi/mic 推理或下载；未重试 source-host429、native/hy4free429；无模型/运行时二进制新下载，模型为既有受保护输入。
- InfoZIP 证据按 archive order 比较；父侧比较完整有序成员列表与相关 notice/ASR native/model payload 字节，不是排序后的参数顺序，不扩展到无关 Demo payload。

## 核心证据

| APK | bytes | sha256 | notice / model | native |
|---|---:|---|---|---|
| Demo debug | 244934281 | `85aff749d85257b4e9368b618b4fa22ab0b0d47ff2d429a5aa7e2aa087ba6688` | 68 / 770479B；7 assets | 32 = 16 ASR + 16 extras |
| Demo release-unsigned | 236322599 | `43b2c5b9e4beebe1712747bf0943dbbcdcfb742f34d18800ea413b265f9010e1` | 68 / 770479B；7 assets | 32 = 16 ASR + 16 extras |
| Consumer debug | 128102798 | `33c3fdab3ac862f7b7645c5ff7c1820c7a5da806718a4317ff7b25ff43145edc` | 68 / 770479B；0 model assets | 16 ASR |
| Consumer release-unsigned | 127673445 | `affc7579eec2d2e61b7b4b7ec8e798f91f5183aed8549dabf0d7c4f09582608e` | 68 / 770479B；0 model assets | 16 ASR |

- 4 artifact 的 `fullOrderedArchiveListingEqual` 与 `selectedASRAssetAndNativePayloadByteEqual` 均为 true。Demo 额外 16 个为 `libandroidx.graphics.path.so`、`libface_detector_v2_jni.so`、`libimage_processing_util_jni.so`、`libsurface_util_jni.so` 各 4 ABI；历史原始 Demo 已存在且两个新 Demo 字节一致。原失败/checker 保留，修正只显式验证历史 16 extras，不放宽 16 ASR gate，也未重建产品。
- DEX：4 APK 均见 124 runtime + 18 backend 类名；DemoFactory 仅在 Demo。方法为 defined class-name presence，不代表语义或字节码等价。SDK commit literal 匹配生成 BuildConfig 且实际出现在 DEX，但非完整源真实性证明。
- 独立消费者：fixture 复制到新隔离 artifact root；hy3 仅创作 4 处精确 variant option 替换，父侧机械集成。默认 debug 保留 8 artifact graph；release 显式解析 `releaseRuntimeClasspath`，同样 8 个精确 SDK/runtime/JVM artifact path 与 bytes；固定 backend AAR `ca0065f23e7c65fe0d30fc0d2035d97543a0b8f8470f5c6e194723fdf535478c` 来自隔离 Maven publication；无 SDK project components。非法 variant configure-time exit 1 并给出 `-PconsumerVariant must be either debug or release, found: unexpected`、无报告；debug/release exit 0。
- apksigner 只读：debug verify 0、release-unsigned verify 1，均匹配 expected；签名资产未改，无 production release/distribution。
- 保护与冻结：source 侧 304 selected inputs / 68 notices / 7 model-license inputs 未改，非完整 build-input closure；protectedFiles 175 receipts 保留，两个范围外前缀按 before/after 哈希保留；frozen 161 guard 通过，new terminal run 未准备/未消费；旧 Demo APK 哈希保留。

## 未关闭项
R4 quality、R5/W8 budget、P1 complete input trust/final production adoption、P2 composition/full applicable notices、P3 complete coverage、P4 均开放。接受组合证据，不要求唯一密码学证明。

## 状态文档
仅做三处 exact snippet corrections；复选框与 gate 不变，其他旧快照不变。

## 关联
- [NDK/notice 集成](w3-ndk-notice-integration-20261115.md)
- [closure 范围](w3-closure-scope-20261115.md)
- [artifact README](../../probes/sherpa-runtime-materials/app-variants-delivery-20261115/README.md)
