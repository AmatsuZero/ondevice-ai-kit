# Sherpa Qwen3 ASR / ORT CPU 内存生命周期：只读源证据审查

范围：仅依据 `../context` 下提供的摘录与报告。不执行任何设备/构建/下载。源级事实与“native 配置已消费/节省量/泄漏根因”严格分离。既有 1.5GiB 门、两阶段与强制语言算法契约保持不变。

## 一、已证源事实（按问题）

**1) session-state 缓存存的是 pattern 与 offset，不是实际激活 buffer；实际 buffer 归 ExecutionFrame，Run 结束随帧析构释放。**
- `session-state.h.excerpts.txt:522-536`：`mem_patterns_` 为 `NodeHashMap<int64_t, MemoryPatternGroup>`，`shape_patterns_` 为形状映射；均按输入形状作键，存储的是 `MemoryPatternGroup`（偏移/大小，`mem-pattern.h.excerpts.txt:64-75`）而非张量数据。
- `session-state.cc.excerpts.txt:950-982`：`GetMemoryPatternGroup` 仅返回指向缓存 `MemoryPatternGroup` 的指针，“pointer is cached, inserted upon creation”；不持有激活张量。`sequential-executor.cc.excerpts.txt:754-767`：Run 后 `GeneratePatterns`+`UpdateMemoryPatternGroupCache` 仅写入 pattern，确认缓存层只存 pattern。
- `execution-frame.h.excerpts.txt:243-260`：`mem_patterns_` 为 `const MemoryPatternGroup*`，实际大块存于 `buffers_`（`InlinedHashMap<OrtDevice, BufferUniquePtr>`）。`execution-frame.cc.excerpts.txt:442-511`：命中 pattern 时按 `PeakSize()` 用 `alloc->Alloc` 预分配大块到 `buffers_`，并注明“the memory pattern buffer will leave in the whole execution”（整个 Run 内存在）；`579-611` 子张量按 `block->offset_` 落在大块上。大块随 `ExecutionFrame` 析构（其 `BufferUniquePtr`+`BufferDeleter(alloc)`）释放，即每 Run 一次。
- `allocator.cc.excerpts.txt:74-78`：`CPUAllocator::Free` 调 `AllocatorDefaultFreeAligned`→`mi_free`（`USE_MIMALLOC`）或 `::operator delete`。**C++ Free 仅归还给底层分配器/arena；是否使进程 RSS 下降取决于 arena 是否把内存退回 OS，供应源未含 BFCArena/Shrink 代码，故“Free 即 RSS 降”不能从所供源证。**

**2) 关闭 CPU arena 不（按所供源）连带关闭 memory pattern；二者是独立开关。**
- `session.cc.excerpts.txt:257-273`：`EnableMemPattern=0`→`DisableMemPattern()`，`EnableCpuMemArena=0`→`DisableCpuMemArena()`，两条独立分支。
- `session-state.cc.excerpts.txt:984-1028`：`ResolveMemoryPatternFlag` 仅在输入非张量或存在多流时关闭 pattern，未读取 arena 标志。故二者正交。
- pattern 对重复自回归 decoder Run 的作用（`execution-frame.cc.excerpts.txt:442-511, 76-158`）：同形状输入命中缓存 pattern 后，每次 Run 复用单块大分配、免去逐张量规划；但 `602-607` 注释指出块大小随序列长度/NonZero 变化，可能使内存停留于高水位。所供源未量化该权衡。

**3) 持久/局部状态与跨片保留。**
- 持久（跨片）：识别器 `config_/model_/tokenizer_/audio_pad_ids_/prompt_ids_after_/asr_text_token_id_/rng_`（`offline-recognizer-qwen3-asr-impl.h:70-82`）；`OfflineQwen3ASRModel::Impl` 持有三个 ORT 会话 `conv_sess_/encoder_sess_/decoder_sess_`、默认 `allocator_`、`max_total_len_`、`past_key_shape_tpl_`（`offline-qwen3-asr-model.cc:121-151, 751-794`）；`observed-scope.md` 亦确认“一个识别器、三个 ORT 会话”，全程存在。
- 局部：`GenerateText`（`offline-recognizer-qwen3-asr-impl.cc:765-1147`）内 `audio_features`、`cache_kv=CreateEmptyKVCache(1)`（:839-840，每次新建）、`logits`、`generated_ids`、`result` 均为调用内局部量，`ApplyKvDeltaInplace` 原地写入该局部 `cache_kv`，函数返回即析构。
- 跨片保留结论：供应代码中**KV/音频/logits/输出均无跨片保留**；唯一跨片留存是模型权重+三会话+会话级 `mem_patterns_` 映射（仅 pattern，体积小）+ 固定 prompt 模板 + rng 状态。是否“会话缓存增长”为泄漏根因，证据不足（pattern 非 buffer）。

**4) 运行时降低 max_total_len 不必然缩小预分配 KV（模型 past_key 维固定正数时）。**
- `offline-qwen3-asr-model.cc:311-324`：`max_total_len_` 在 `past_key_shape_tpl_.size()>=2 && past_key_shape_tpl_[1]>0` 时取自**导出模型固定 past_key 维**，否则才回退 config。`CreateEmptyKVCache`（:533-581）用成员 `max_total_len_` 作为序列维分配 key/value（`{batch, max_total_len_, kv_h, hd}`，:540），与运行时选项无关。
- `offline-recognizer-qwen3-asr-impl.cc:841-847`：`model_max_len=GetMaxTotalLen()`（=模型固定维），`max_seq_len=min(model_max_len, max_total_len_opt)`，仅用于解码长度/CachePosition 边界，不改分配形状。两处须分开追踪：分配由模型维决定，选项只定解码上界。导出模型维固定 512 时，降低选项不缩 KV 分配。

## 二、假设（非已证）
- 关闭 CPU arena 后 `Free` 更可能退回 OS、RSS 更易回落；开启 arena 时内存可能滞留 arena 池——成立与否取决于 `inference-session.cc:4109-4145` 的 `ShrinkMemoryArenas` 是否被调用，供应源未证。
- 单独 `EnableMemPattern=0` 对重复 decoder Run：可能以“多次小分配/失去单块复用”换取避免高水位滞留，但也可能增加分配抖动——方向未测，禁止声称“节省”。
- `mem_patterns_` 随不同 `audio_token_len` 累积不同键而增长，但仅存 pattern，体量有限，是否主导 RSS 未知。

## 三、未验证的 Android 二进制行为
- 固定 AAR 字符串/SHA 与 ORT `33ca962` 仅为自报元数据；native 是否真正消费 `EnableMemPattern`/`EnableCpuMemArena`、编译选项是否一致均**未验证**（`w3-qwen-cost-options-20261111.md:20`，`w3-qwen-arena-observed-20261111.md:21` 均 `nativePolicyEffectVerified=false`）。
- 默认批与 arena-off 批在不同进度分别停止（>1.5GiB vs 第25项 1637896192B），其最大值**不构成受控节省对比**（`observed-scope.md`，`w3-qwen-arena-observed-20261111.md:21`）。
- RSS 采样带 `activeCaseIndex` 滞后，无阶段级 native 峰值/无 detected-language 字段，不能据此证泄漏、跨片缓存或某 allocator 根因（`w3-qwen-cost-options-20261111.md:18`）。

## 四、Q5 最小缺失证据（区分三类，保持预算与契约）
需在同授权设备运行中（本审查不授权新 run）补三类、且按阶段打标的最小迹：
1. **每 Run 激活峰值**：`ExecutionFrame::buffers_` 大块峰值 + 回退逐张量分配，按 encoder/decoder 两阶段与每片归属（现仅进程 RSS+滞后 case-index，无 native 分配迹/无阶段峰值）。
2. **allocator RSS 滞留**：arena 预留 vs 在用、是否调 `ShrinkMemoryArenas`（`inference-session.cc:4131-4145`）的每分配器差额，分离“arena 滞留”与“OS 退回”。
3. **会话缓存增长**：`mem_patterns_` 全程不同 `CalculateMemoryPatternsKey` 键数及各 `MemoryPatternGroup` 体量。
最小判别实验=一次带阶段标记、分配器级 reserved/in-use 与每 Run `ExecutionFrame` 峰值的授权运行/重分析。**不**建议自动重试、重放已耗批次、session-per-clip 重建、删强制语言、质量挑拣或未验证 detected-language 快路径（保持既有预算与两阶段契约）。

## 五、建议（仅诊断，不含根因/节省声明）
- 推迟 `EnableMemPattern=0` 变更。下一合理诊断：在现状默认配置下，用上述带阶段标记的分配迹区分“激活峰值/allocator 滞留/会话缓存增长”三者后再决定单变量假设是否值得冻结（`w3-qwen-cost-options-20261111.md:20`）。
- 停止条件：在取得受控、同进度对比与阶段级峰值前，不翻转 `EnableMemPattern`/`EnableCpuMemArena`，也不据较低采样值宣称 native 设置已消费或产生节省。

## 六、证据/局限表
| 主题 | 已证源依据 | 局限/未验证 |
| --- | --- | --- |
| 缓存存 pattern 非 buffer | session-state.*:522-536,950-982；execution-frame.*:243-260,442-511 | 实际 RSS 行为取决于 arena，未含 BFCArena 代码 |
| arena 与 pattern 独立 | session.cc:257-273；session-state.cc:984-1028 | 二者在 ORT 内是否有其他交互未验证 |
| 跨片仅模型/会话持久，KV 局部 | *-impl.h:70-82；*-model.cc:121-151,839-840 | 会话缓存“增长”是否主导未知 |
| 选项不缩固定维 KV | *-model.cc:311-324,533-581；*-impl.cc:841-847 | 导出模型维动态时回退 config，本次固定 512 |
| native 消费/节省 | w3-qwen-cost-options/arena-observed:20,21 | `nativePolicyEffectVerified=false`，未验证 |

## 七、实际工具错误
- `Read("../context")` 返回 `tool_use_error: Path is a directory, not a file`，但同时回显目录清单（17 个条目），据此取得全部摘录文件名后逐个读取；未使用 shell/其他越权读取。
- 环境不含 Bash/Glob，无法对 `../context` 之外的路径取列；严格只读所供文件，未触及凭据或其他路径。
- 未触发任何构建/设备/网络动作；无新 run 授权，故 RSS/arena/Shrink 行为保持“未验证”。
