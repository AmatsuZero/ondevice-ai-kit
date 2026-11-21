# W3 Sherpa 运行时许可材料准备

已新增便携[运行时材料目录](../../probes/sherpa-runtime-materials/2.0.1-a8d09d1d/RUNTIME-NOTICES.md)：20 份许可/声明全文、26 份构建源文本、固定 AAR 成员/ELF 动态清单和来源 manifest，共 50 个文件。不含运行时或模型二进制，不修改已冻结探针或生产包。**这是材料准备进展，运行时完整组成、生产通知保留与发布复审仍未通过，W3 保持 open。**

| 本次核验 | 结果与范围 |
| --- | --- |
| 固定 AAR | SHA `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；26 ZIP 条目含5目录，21文件，其中16 `.so` 跨4 ABI |
| 实际库清单 | 每ABI有 ORT、Sherpa JNI/C-API/C++-API 四库；逐成员 size/SHA、ELF NEEDED/SONAME/build-id已登记，缺失build-id为null；动态依赖不证明静态组件不存在 |
| 源码与许可 | 46份来源文本逐份 size/SHA及GitBlob核对；其中19份Sherpa固定 `af2d8d81dbfa61c4cea4c568da78f3982edca005` 源文本，另7份传递构建源和20份许可/通知全文。Sherpa LICENSE以相同上游字节分别登记于许可和源码目录 |
| 许可来源 | Sherpa、ORT及其上游ThirdPartyNotices、kaldi-native-fbank、utf8proc、kaldi-decoder、openfst、simple-sentencepiece、nlohmann/json、kissfft、kaldifst、hclust-cpp、Eigen；精确ref/blob/hash见manifest |
| 归属限定 | utf8proc保留MIT与Unicode数据声明；Eigen完整保留来源许可备选，不据GPL/LGPL文本存在推定该AAR包含对应代码或新增产品义务；kissfft与ORT上游通知同样保留适用范围边界 |
| 外部目录文档 | 免费hy3实际写入，定点订正后原字节集成，6240B/SHA `81392f744786a33b396175d49657b503695e3fe3bb0049c061b4183387b56ab9`；39相对链接全部存在 |
| 本机压缩包 | 110665B，SHA及全部50成员逐字节核对见[机器收据](w3-runtime-materials-20261111.json)；未上传压缩包 |
| 设备与生产 | 新设备/模型调用0、新模型/二进制下载0；原108冻结文件和两APK保持，普通App已恢复 |

既有 Sherpa/Whisper/Silero 通知从来不是全部缺失：原通知保留于11月5日tiny资产，Qwen模型材料也已有独立交付。本项补充的是固定运行时的来源材料与实际AAR清单，不将模型许可、运行时许可和最终生产通知混成一个门。

助手首范围报告将13+5误记18+5，并从未供文本填写许可标签、把未证实分支排除。原文未当最终清单。后续目录初稿的计数、成员已知范围和JSON字段位置经定点订正；组件许可段落保持。原三次调用均自然终态，分别245.406s、122.307s、101.319s，实际25Read/1Write、14Read/1Write、6Read/1Write，各有1次工具错误，分别为超长pitfalls、目录Read和缺文件Read；自报零错误/错误工具计数均由父记录订正。没有用CLI exit0代替实际交付、文件摘要或核验。

尚未穷尽递归静态组件，未证明固定预编译AAR与上述源码候选精确版本/构建调用相同；Android脚本有环境覆盖，不是已执行AAR构建的证明。F-Droid参考工作流的非逐字节可复现声明只针对该工作流。最终生产分发通知保留和独立发布复审继续独立。本目录没有放行生产或新设法律/专利审批流程。

完整收据、最初报告及两次目录原文/工具错误位于本机 `.omx/artifacts/w3-runtime-materials-20261111/`，不保证随Git分发。下一项先补固定AAR官方发布来源对应，再追未穷尽组件；资源改进同时参考[成本复算](w3-qwen-cost-options-20261111.md)，不重放设备批次。
