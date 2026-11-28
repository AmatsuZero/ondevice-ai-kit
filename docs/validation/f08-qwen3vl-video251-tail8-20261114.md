# F08：补齐video251末段第三个物体后，答案仍错误

2026-11-14。`video_251/q0`保持八帧，只将最后一帧26.393s替换为29.897s、补入第三个黄色三角物体后，**自然时间与仅像素两个控制仍答A=No，期望C=Yes**。同进程原八帧也答A，与原手机一致。已确认的漏采不是本题的充分解释；当前模型/配置的形状识别、物体计数、跨帧比较或视觉编码仍有未定位限制，不能据本实验确认是哪一个内部环节，更不能推出所有模型都无法视频理解。

## 固定合同

预冻结[计划](../superpowers/plans/2026-11-14-qwen3vl-video251-tail8.md)和`selection-before-inference.json`，单次串行Mac Swift MLX Metal进程，三个输入各一次fresh28KV。使用既有Qwen3-VL-2B-Instruct-4bit revision`9c4f5209e57b31f4b9dfba735de3fb983739c9cc`及已验Swift运行时，八帧、512预算、balanced512、temperature0、maxTokens32、实际A/B/C→EOS约束全部保持。没有Torch、CPU模型、Ollama推理、下载或手机操作。

这是已知答案后的后验事件覆盖诊断，不是独立benchmark、通用采样修复或新产品成绩；原valid36严格16/36和连续热保护失败均保持。

唯一干预为末帧：原26.39305972639306s替换为已离线解码的29.896563229896564s，前七帧逐字节相同。新像素在[实际packed末帧对照](../../artifacts/verification/f08-qwen3vl-video251-tail8-20261114/actual-old-new-final-frame.png)右侧可见三件物体，左侧原输入只有两件；该可见性判断属于作者后验审读，非盲评或独立人工标注。

| 输入 | 时间合同 | 实际答案 | A/B/C首步logits |
| --- | --- | --- | --- |
| 原八帧，同进程校准 | 原时间 | A=No，错 | 26.75 / 23.5 / 26.375 |
| 替换末帧，自然输入 | 新帧真实时间 | A=No，错 | 26.875 / 23.375 / 26.25 |
| 替换末帧，人工像素控制 | 原时间/文本/位置 | A=No，错 | 26.875 / 23.5 / 26.375 |

全部实际ID32→EOS151645。分数不是置信概率。人工控制刻意使用旧时间表示新画面，不作为自然视频协议接受；其像素与自然控制精确相等，tokens/positions/rope delta与原输入精确相同。三臂均681tokens、THW`[4,18,32]`、delta-512；实际packed前三个temporal组精确保持，只有第四组变化。未按结果继续选择别的帧。

## 验证、资源与限制

证据根`artifacts/verification/f08-qwen3vl-video251-tail8-20261114/`：

- `natural/parent-prepared-verification.json`与`parent-official-positions.json`：官方text/packing AST、checkpoint BPE、官方RoPE AST+NumPy验证shim；Swift实际processor三臂tokens/positions/THW通过。沿用已记录Pillow/Swift像素残差，未冒称完整官方像素数值等价。
- `input-negative-checks.json`：非末帧、原时间、自然末时间、人工时间、问题、标签、原tokens七类篡改均拒绝。基线Swift像素SHA精确同原手机；新两臂像素相同。
- `build-source-freeze.json`、`build-process.json`：只派生runID、要求case数3和claim文本；没有改模型推理/协议/资源保护。Release实际构建81.739s成功，缓存依赖关闭自动解析与更新。
- `execution-plan.json`、`model-process.json`：冻结源码/实际二进制/输入/权重后唯一实际推理，网络拒绝sandbox，GPU两队列均空，exit0。首包装器误用系统Python导入NumPy失败，在任何freeze/launch前退出，改用既有隔离Python后唯一实际launch；原错误保留`launch-tooling-first-failure.json`，没有新依赖或模型重试。
- `parent-results.json`：六个完整151936维有限向量、真实允许集argmax、解码/EOS、28层KV offsets/rope、实际输入SHA及1053个旧文件复核通过。

本次host整臂4.400s、热状态0→0、采样物理内存峰3,627,699,224B。三题各准备/生成2.845s、0.270s、0.263s；首臂冷路径与后续热路径不同，不能当iPhone性能、均摊加载预算或持续热验收。没有新手机运行，原手机serious保护仍未解决。外部helper失败/native429规则下没有新独立审稿，本轮为作者单独验收。

## 裁定

本分支已完成并停止同题搜索，**W6/F08保持open/not-ready**。现在有两个补齐可见事件后仍错误的独立计数个例（旧9676及本251），可以排除“只要补漏帧就能解决这两题”的简单解释，不能估计普遍失败率或定位内部机制。下一质量工作应审读其余独立视频错误的实际输入覆盖与任务类型，再决定有依据的通用修复；不能以继续调这两个已知答案案例代替独立质量验收。正式采用/集成、产品质量门和持续资源预算仍缺。
