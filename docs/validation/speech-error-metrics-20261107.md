
## 2026-11-08T00:38:54.806056+08:00：已关闭当前9份ASR源码的有限模拟器转写验收

9份当前生产Kotlin源码逐字节冻结并实际编译/DEX核对后，独立API36 ARM64模拟器新run `d7f47dfab306673670f7cc26df0aa5027545e390fafcf9b183aeae6c891acc92`真实completed：3公开英文clip、完整23.155秒、固定4request、输入不变、finalclose成功；6合成负向均拒绝。casefold/保留标点且不人工改转写：WER10/59=16.9492%、CER25/268=9.3284%。rawSHA `2d935c29fec181cf0517a274b39c364acbc4891a71b3401f2708a39f461048e5`，与旧afed快照run/旧失败分开保留。当前源码验收子项不再待办，证据`artifacts/verification/asr-current-source-emulator-20261108-0023/summary.json`。

验收壳初次NO-SOURCE、模型即时hash校验失败和validator缺冗余字段的原错均保留：分别仅修隔离Gradle source registration、核验实际字节后仅补缺模型、直接绑定事前源码manifest；不改生产、不重放推理/不挑转写。两APK签名/9源/3PCM/4模型hash均已核对。范围仅9份ASR源码与1speaker/3English，不代表全SDK、广泛语音质量、系统离线ASR/capture后台或真机性能/能耗。W3这些余项仍在，F03低系统实体设备唯一延期。hy3 Binder生命周期4文件32947继续，付费/新原生0。

# 离线语音 CER/WER

此工具离线比较人工参考文本与识别假设文本；不读取音频或生成语料，也不判断识别质量是否达标。

输入为 UTF-8 JSON，且只能包含 `schemaVersion` 与 `pairs`。版本必须是整数 `1`；每项必须恰有 `id`、`reference`、`hypothesis` 三个字段：

```json
{"schemaVersion":1,"pairs":[{"id":"sample-1","reference":"你好 世界","hypothesis":"你好 世"}]}
```

Python API：

```python
from tools.speech_error_metrics import score_pair, score_corpus

pair = score_pair("Hello, world!", "hello world", casefold=True)
report = score_corpus(
    [{"id": "sample-1", "reference": "你好 世界", "hypothesis": "你好 世"}]
)
```

命令行输出完整 JSON 报告，输入摘要绑定文件原始字节（包括空白）：

```sh
python3 -m tools.speech_error_metrics input.json --casefold
```

每种指标含参考/假设单位数、替换、删除、插入、错误总数、错误率与是否有定义。错误率是最小编辑距离除以参考单位数，允许大于 1；参考为空时（即使假设也为空）错误率为 `null`、`defined` 为 `false`，插入错误仍计入。语料 aggregate 是按参考单位数加权的微平均，而非逐项错误率的平均。

文本先 NFC 规范化，可选 casefold 后再 NFC；首尾 Unicode 空白移除、内部空白折叠。标点、重音和数字保留。WER 按 Unicode 空白分词，不做中文分词；CER 按非空白 Unicode 码点计数，不按字素簇分段。最优编辑路径平局时依次优先精确匹配、对角替换、删除、插入。

报告不输出转录文本；转录相关字段仅为计数和原始文本 SHA-256。ID 由调用方提供，并非匿名性证明。哈希不能证明文本来源或识别质量。报告固定标为 `qualityVerdict: "unverified"`、`productionReady: false`，没有通过阈值或生产批准。

## 2026-11-07实际自然语音基线

独立API36 ARM64模拟器、Sherpa Whisper tiny既有API快照，3段公开LibriSpeech/23.155秒/1speaker，原始音频和reference不变。第3段因12.485秒超单次10秒合同，原失败保留；新固定中点分段后3段共4请求完成，close成功。复用本工具casefold、保留标点、无缩写展开：**WER10/59=16.9492%，CER25/268=9.3284%**。这只是小样本实测，不设生产阈值，不证明多语料质量或真机性能。原report/segment/hypothesis拼接/输入hash及6负向见`../../artifacts/verification/asr-natural-emulator-chunk-20261107-2350/summary.json`，完整评分同目录`speech-scores.json`。
