# W3 固定 Qwen/ORT 的内存生命周期核查

当前证据不能确定 Android 越过 1.5 GiB 的内存根因，暂不直接翻转 `EnableMemPattern`。本项核查固定源码与实际模型图结构，新增模型加载、设备运行、推理和运行时二进制下载均为零。[机器收据](w3-qwen-memory-lifetimes-20261111.json)保留来源摘要、实际图输入及外部报告裁定。

| 已核实内容 | 对下一步的影响 |
| --- | --- |
| 固定 ORT `33ca962…` 的 `SessionState::mem_patterns_` 缓存 `MemoryPatternGroup`；其中是位置、偏移和大小 | 不能把每个形状键误当成留存一整块激活数据；映射实际体积及增长仍未测 |
| `ExecutionFrame` 命中 pattern 时按 `PeakSize()` 分配大块，由 `BufferUniquePtr` 持有，随 frame 析构归还 allocator | `Free` 不保证进程 RSS 同时下降，底层分配器保留和活跃张量峰值需分别测量 |
| Sherpa 的 CPU arena 与 memory pattern 是独立配置分支 | 关闭 arena 不等于关闭 pattern；目前未证明 Android native 消费配置或下一开关能节省内存 |
| 一个 recognizer 持有三个 ORT session；`GenerateText` 内的 KV 为调用局部变量 | 源码生命周期不足以证明实际中断批次已析构，也不能排除 runtime 其他内部状态或分配器保留 |
| 实际 decoder SHA `4f6885be…`：60 个输入，其中56个 KV，float32，形状均为 `[batch,max_total_len,8,128]` | 当前模型长度维是动态的。固定源码会回退到 config 的512，不适用“正数固定导出维覆盖 config”的分支 |
| 28层 × K/V2 × batch1 × 长度512 × heads8 × headDim128 × float32 4B = 117,440,512B（112MiB） | 仅为基础 KV 数据量的静态推导，不含权重、激活、输出、分配开销，也不是实测峰值或可节省量 |

源码依据为 Sherpa `af2d8d81…` 的 `offline-qwen3-asr-model.cc:311,533`、`offline-recognizer-qwen3-asr-impl.cc:839,841`，以及 ORT `v1.28.2` 的 `mem_pattern.h:10,61`、`session_state.h:527`、`session_state.cc:952`、`execution_frame.h:254`、`execution_frame.cc:437,509`、`allocator.cc:175`。相应官方 URL、完整来源大小/SHA/GitBlob在收据内。实际模型按[官方 ONNX protobuf schema](https://github.com/onnx/onnx/blob/v1.21.0/onnx/onnx.proto3)只读解析 metadata/graph input，跳过张量载荷，不创建 ORT session；7项解析器检查通过。初次误按 `past_key` 名称筛选、手工合成消息长度错误和 JSONL 读取错误均保留，修正后实际输入为 `cache_key_*`/`cache_value_*`。

免费 hy3 138.804秒自然终态，18 Read/1 Write，1次目录 Read 错误，17份context摘要保持。原报告9113B超出9000B要求，且把当前模型误称固定512、部分行号错误、未测即称缓存体量小；原文作为部分研究证据保留，没有无条件验收。以上结论使用父端实际源码与模型检查订正，不采用这些扩大结论。

下一步先建立阶段归属明确的堆/RSS观测，区分 native heap 已分配量、底层保留与进程驻留页。既有413个RSS样本只含滞后的case索引，不能补造原批次缺失的堆或阶段数据。公开 Android 堆计数同样不能直接读出 ORT 每个 session 的 pattern 键数/活跃缓冲；若需要 allocator 内部追踪，须先确认固定AAR实际可观测能力及开销，不能默认为已有。

本项没有授权新 run、重放已消耗输入或修改算法。新的诊断仍须独立输入范围、外部候选、host/实包/复核、固定摘要与停止合同；保留1.5GiB/512MiB/120s门、两阶段和强制语言。Android剩余质量、完整成本与发布门仍未过，W3保持open。
