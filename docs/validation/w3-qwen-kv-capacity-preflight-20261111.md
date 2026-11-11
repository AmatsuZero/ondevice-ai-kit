# W3 Qwen KV 容量预检

固定语料的静态预检支持开展构造器 `maxTotalLen=288` 的独立实验。当前仅完成容量论证、实际 tokenizer 执行和材料限定复核；尚未修改 Kotlin、构建新 APK 或执行新 ASR。W3/R4/R5 保持 open。

## 已验证的容量边界

范围固定为原 65 个公开文件、16 kHz 单声道、最长 160000 samples（10 秒）、空 hotwords、自动语言准入与 Chinese/English 请求语言阶段，`maxNewTokens=128` 不变。

固定 Sherpa 源码 commit `af2d8d81dbfa61c4cea4c568da78f3982edca005` 的 tokenizer 已原样编译并执行。前缀 9 tokens、后缀 6 tokens、audio pad 1 token；`language Chinese` 和 `language English` 均为 2 tokens，另有 1 个 `<asr_text>`。自动阶段 scaffold 为 15，请求语言阶段为 18。

源码中心 STFT 路径将帧数限制在 `floor(samples/160)`；最长输入至多 1000 帧，原 `FeatToAudioTokensLen(1000,100)=130`。因此最大 context 为 `130+18=148`。Prefill 已预测首 token，后续最多再写 127 个 KV 位置，容纳原 128 token 上限所需容量为 `148+127=275`。预先选择满足该边界的最小 32 倍数 **288**，没有根据新识别结果扫参。

**256 不满足上述边界**：最坏 context 下只能保留 109 个生成 token，比原 128 上限少 19。275 刚好容纳 128；274 只能容纳 127。原 C++ 函数的 0–1000 全部 1001 个结果与独立 Python 算术一致，65 文件两个阶段的静态上界均不超过 288。

## 分配位置与静态数据量

已核当前 decoder 图有 56 个 float32 KV 输入，形状为 `[batch,max_total_len,8,128]`，长度维为动态符号，28 层。固定源码在模型构造时以 config 决定动态长度；`CreateEmptyKVCache()` 随后以该长度分配。stream 的 `max_total_len` 在分配后读取，只限制解码长度，**单改 stream option 不会缩小基础 KV 分配**。

| 构造器容量 | 基础 KV tensor 数据量 |
| --- | --- |
| 512 | 117440512 B = 112 MiB |
| 288 | 66060288 B = 63 MiB |
| 差额 | 51380224 B = 49 MiB |

公式为 `28 × 2 × capacity × 8 × 128 × 4`。这里只计算一套基础 KV 数据；不等于 RSS 差额、峰值、allocator 保留量或手机预算通过。若换成固定长度导出图，config 不再决定这一维，不能沿用当前结论。

## 验证与复核

父端重新编译原 tokenizer（C++17、Wall/Wextra/Werror/O2），实际输出与首轮逐项一致；2 份来源 size/SHA256/GitBlob、1001 个帧数结果及 65 文件容量上界重核通过。只有 ReadFile/logging 使用父端 shim；没有链接 ORT、加载 ONNX 或新增 ASR 推理。更正父端注释中的“21”为实际计算的“19”，原机器结果不变。

免费 hy3 独立复核用时 66.935 秒，成功结果、零工具请求、无 findings。它仅审阅 supplied context，没有直接观察设备或重新执行程序；[复核收据](w3-qwen-kv-capacity-review-20261111.json)保留此范围。复核原文对固定维图使用了“constructor change unnecessary”，本报告准确解释为“config 不能缩小固定图维度”，不把这句话当作另一图的容量保证。

## 后续独立实验边界

下一实现候选继续交免费 CBC；使用独立类、alias、报告目录及新 run。唯一推理参数变化为 constructor 512→288，报告须如实覆盖共享 serializer 的旧 512 值。保留原模型、四 arm64 库、65 波形顺序、两阶段、arena-off、128 生成上限、评分器和原资源停止线。先验源码、host、Android 构建/lint、实包、独立复核与新冻结后，再执行一次独立模拟器对照，逐条比较原文、指标与采样 RSS。

本预检没有证明输出一致、实测省内存、TECNO 过预算或任意时长/热词的生产安全性。精确 source-to-AAR 对应仍缺。原物理 arena 已消耗 run 不重放；原物理 heap 合同仍待真机连接，不由模拟器替代。不开麦克风/AiAi，不下载模型、镜像或运行时二进制，不改变生产默认。

## 证据入口

[机器摘要](w3-qwen-kv-capacity-preflight-20261111.json)。原件在本机忽略目录 `artifacts/verification/w3-qwen-kv-capacity-preflight-20261111/`：来源收据、原 tokenizer 源码、编译 harness、`verify_capacity.py`、实际 tokenizer 输出、含 65 行的 `capacity-preflight.json`、`fresh-verification/` 和 `hy3-review/`。前置图事实见[内存生命周期](w3-qwen-memory-lifetimes-20261111.md)，已完成的原容量结果见[模拟器全量对照](w3-qwen-arena-emulator-observed-20261111.md)。
