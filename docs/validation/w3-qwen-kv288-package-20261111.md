# W3 KV288 隔离探针源码与实包

免费 hy3 交付的六处最小编辑已原样集成到 `probes/android-qwen-kv288/QwenKv288Instrumentation.kt`。原 arena512 探针未改；新类/alias/report/variant 独立，constructor288、128生成上限不变，每次 snapshot 显式覆盖共享 serializer 的旧512值。仅限已预检的固定≤10秒、空hotwords文件实验，不是生产容量默认值。

真实 Kotlin host 基线（只改身份，仍512）编译通过后16项失败；候选32组/6003断言通过，覆盖实际构造参数及初始/运行/失败/终态报告的288和原生命周期故障。离线 Android75任务22.361秒通过，lint0错误7既有警告。实际APK16类、65PCM、零权限、共同debug证书和四个原arm64成员摘要通过；DEX构造参数`v50=0x120`/`v51=0x80`分别为288/128，snapshot同为288，`v56=0x200`为默认参数mask。

源码fingerprint `ed42cdce172883052e0c56c102f258eddb667495d8fb29475fc9aaa7dfdbc3f0`。免费hy3材料限定独立复核60.787秒、零工具、accept/无findings；未直接操作构建或设备。父端current delivery检查及完整tools691通过，原arena108/heap120冻结重核完好，物理heaprun未消耗。

首次父端prepare跳过唯一旧test-src文件后漏建目标目录，真实FileNotFoundError在Gradle前停止，后续build亦被缺候选收据阻断。原半成品与错误保留；补父目录mkdir后重新准备79文件并完成构建/实包核验。不是模型或provider故障。

此检查点零安装/模型推理，新的模拟器合同尚未冻结或执行，不证明质量一致、实测省内存、nativepolicy有效、TECNO预算或W3完成。运行前仍需父合同检查/独立复核/冻结。入口：[实验计划](../superpowers/plans/2026-11-11-w3-qwen-kv288-emulator.md)、[容量预检](w3-qwen-kv-capacity-preflight-20261111.md)。原始证据本机 `artifacts/verification/w3-qwen-kv288-emulator-20261111/`，含helper-wrapper、host-red/green、build/package、DEX、source-review、tools-suite及失败半成品。
