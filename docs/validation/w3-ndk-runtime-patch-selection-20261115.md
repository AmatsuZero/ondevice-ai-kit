# W3 NDK C++ runtime patch selection - bounded validation report

结论先行：父级获取并校验了官方源码文本，hy3 编写了可集成的报告、README 与分类修正候选文本；本轮并非只做候选文字工作。官方源码文本下载确实发生，并与运行时二进制下载分开记录。本轮没有新增构建/CMake 求值、ELF 扫描、设备操作、模型或运行时二进制下载，也没有编辑生产 63 notice 资产；其它现有资产保持不变。

## Receipt 与范围

receipt 记录：source-host HTTP 429 后停止新请求；该 429 来自 source host，与 hy3 配额无关。134 条官方声明对应取得 76 份可保留源码文本 body（r27d 59，r30 17）；58 份 body 缺失（56 次 HTTP 429、2 个 bad URL（HTTP 404））。独立的 r27d BUILD.md 404 是额外来源 404，不计入这 58/134。独立 r30 builder 另丢失 5 份成功 body，并记录 3 次 429；这 8 份不作为文件证据，receipt 也表明并非 fail-fast。未取得正文与生成器缺失仍是覆盖限制。

## 来源结果

- 两份选定 metadata 集合（r27d 75、r30 59）与 134 条官方 patch 声明按 basename 精确匹配，零额外、零缺失；这是 official declaration 与 metadata agreement，不证明 final retention。
- r27d get_source_info 可解析 `The following patches applied successfully:`，区分 applied/failed/inapplicable；但 write_source_info 只序列化 pi.applied_patches；r27d source info 因而是官方 applied-success 声明，强于泛泛依赖列表，但不证明历史执行真实性、最终归档成员关系，也不覆盖 r30。
- r30 generator code 未保留；不能称已审阅或已证明 r30 与 r27d 生成语义等价。
- 固定后的 r27d DeviceLibcxxBuilder 源码文本区分 platform 与 NDK：NDK 配置使用 __ndk1、shared ON、static ABI link OFF、LLVM_ENABLE_RUNTIMES=libcxx;libcxxabi，并生成 `INPUT(-lc++_static -lc++abi)` 作为 libc++.a 输入；platform 配置另有不同 flags 与可选 demangle libs。这只是 source recipe/default flag，不证明最终产物保留。
- 旧 formatter 正则只识别 fullhash 加可选 _vN；两个 r27d dash 后缀 URL 落入 root 分支，metadata 指向 cherry 真实路径。父级最终额外核验 6 个 Git tree objects 与 40 条 retained child edges；未核验全部 76 个 patch blob 与预期 cherry-tree object 的对应关系。cherry subtree 未保留时，cherry body 以固定官方 URL + local hash 绑定，不声明存在 retained cherry-tree ID。

## 分类

17 条分类只按 source-path scope only；按给定列表复核为 runtime-source 3、runtime-build-config 4、runtime-tests 5、compiler-with-runtime-tests 1、other-runtime-family 4，且全部 finalPresenceVerified=false。58 份缺失 body 全部保持未分类。compiler-rt builtins/sanitizer 归 other-runtime-family；libcxx/libcxxabi 生产头与 CMake 分别归 runtime-source/runtime-build-config；纯测试与 lit 支持归 runtime-tests；clang Sema 加 libcxx verify 测试归 compiler-with-runtime-tests。ndk-r30:53 的实际路径为 libcxx/include/__memory/compressed_pair.h，并涉及 libcxx/include/string 等。分类不推出最终链接、STL/NDK 选择、ABI、sanitizer 或架构包含；Sherpa default legacy 与 ORT explicit legacy=false 仍是独立条件，不是 final archive retention。

## 许可与开放项

四个组件根 LICENSE.TXT 的 future index 仍为 FUTURE CANDIDATE ONLY；本轮无集成验收，生产 63 notice 资产不变。候选索引只保存原样副本到四个唯一目的地，保留 Apache2 LLVM exception 与原始 legacy notices，不重生成、不摘要、不替换为通用 SPDX；标记 incomplete=true、finalBinaryPresenceVerified=false，并绑定 r27d/r30 修订与 source-retention-map 的 original/canonical identities。不含 whole-toolchain NOTICE，不推断所有工具链组件存在，不宣称完整法律条件或已集成。

R4/R5/P1-P4/W3 与完整用户目标仍开放，目标不收窄。闭环接受组合证据，但须通过 coverage/trust review；不以唯一 binary/cryptographic proof 作为门槛或已达成事实。后续闸门包括 r30 生成语义、缺失正文、历史 apply 真实性、最终归档/APK 组成、链接/设备验证和通知集成。即使后续 ELF NEEDED 缺项，也不能排除静态或内联代码。在现有输入下，后续离线 notice 集成与真实 APK 留存核验可继续推进；source-host 限额不取消这些后续任务。

相关链接：[前置来源](w3-ndk-runtime-provenance-20261115.md)、[链接规则](w3-cxx-linkage-rules-20261115.md)、[验收范围](w3-closure-scope-20261115.md)、[材料](../../probes/sherpa-runtime-materials/ndk-runtime-patch-selection-20261115/README.md)
