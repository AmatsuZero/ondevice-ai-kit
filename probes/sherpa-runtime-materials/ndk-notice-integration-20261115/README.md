# NDK C++ notice 补充验证材料

材料路径：`probes/sherpa-runtime-materials/ndk-notice-integration-20261115/README.md`。报告见 [W3 NDK notice 增量集成复核](../../../docs/validation/w3-ndk-notice-integration-20261115.md)。

本材料记录 `compiler-rt`、`libcxx`、`libcxxabi`、`libunwind` 的 4 份原始 `LICENSE.TXT` 与 1 个独立 child `index.json`：新增原文 66823B，旧 63 个 notice 资产不变，总计 68 assets/770479B。原文未改写；r30 原始身份按固定 source map 指向既有规范 r27d 副本，因为正文逐字节相同，未虚构本地 r30 副本。

父级证据显示 Debug/Release AAR、isolated Maven 发布及真实 standalone Maven application fixture 的实际 APK 均保留 68/770479B notice；Python/InfoZIP 清单一致；4ABI16SO 与 fixed runtime AAR 相同；10 个 notice-only ZIP 负例被拒。

边界：child index 故意不完整；`finalBinaryPresenceVerified=false` 仅指 native-code 存在性未证，不代表 notice 字节验证失败。无 whole-toolchain NOTICE、完整性/法律结论、P4 发布验收、设备安装、生产 app 采用或资源预算验收；R4/R5/W8、P1-P4 和 W3 仍开放。
