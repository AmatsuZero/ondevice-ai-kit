# W3 固定运行时的官方发布出处

本地 Sherpa 2.0.1 AAR 已与官方 Release 的发布摘要、大小核对一致；官方 tag 指向材料目录使用的固定源码 commit。**官方预编译包身份这一项已确认**，没有重新下载运行时二进制。此项不证明全部构建选项、递归静态组件或逐字节可复现，W3 仍 open。

| 核验对象 | 当前证据 |
| --- | --- |
| 官方发布 | [k2-fsa/sherpa-onnx v2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1)，release ID `424419758`，发布于 `2026-10-26T15:24:29Z` |
| 精确 asset | `sherpa-onnx-2.0.1.aar`，ID `678742021`；49,441,341 B；官方 `digest=sha256:a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`，与现有本地 AAR 实际字节相同 |
| tag 对应 | `refs/tags/v2.0.1` 为 commit `af2d8d81dbfa61c4cea4c568da78f3982edca005`，与[材料 manifest](../../probes/sherpa-runtime-materials/2.0.1-a8d09d1d/material-manifest.json)一致；release 的 `target_commitish=master` 不能代替 tag 解析 |
| 上游构建线索 | 同一 commit 的 [android workflow run 40348432396](https://github.com/k2-fsa/sherpa-onnx/actions/runs/40348432396)成功；libs job `121661567095`、AAR job `121678359263` 均成功。AAR job `16:23:06Z–16:25:52Z`，asset 上传 `16:25:45Z–16:25:48Z`；时间相符仅作旁证 |
| 实际公开构建日志 | 已取得两 job 文本日志，checkout为 `af2d8d81`，libs产物ZIP上传/下游下载摘要同为 `97188739…65fe68`；AAR job明确记录向v2.0.1上传精确文件名。NDK `30.0.16248370`、Release、JNI/C-API/TTS/diarization均启用；不能因本次只用ASR而排除TTS/diarization依赖分支 |
| 嵌套预编译依赖 | 日志从 `csukuangfj/onnxruntime-libs` 的v1.28.2取得 `onnxruntime-android-1.28.2.zip`，本次未下载此归档；ZIP artifact摘要不能当成AAR文件或嵌套ORT成员摘要 |
| 实际 arm64 ORT | AAR 内 `libonnxruntime.so` SHA256 `33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1`；其字符串自报 `VERS_1.28.2`、`git-commit-id=33ca962`、`fp8-kv-cache=1`、`build type=Release` |
| ORT 官方 tag | `v1.28.2` 指向 commit `33ca9628233dc8f002435e868d4c2e9f82766ca1`，与二进制自报的版本及短 commit 相符；不能据字符串排除补丁或恢复全部编译选项 |

原始 Release/tag/jobs JSON、公开job文本日志和摘要见本机 `.omx/artifacts/w3-runtime-materials-20261111/official-provenance/`；[机器收据](w3-runtime-official-provenance-20261111.json)保留官方标识、时间、50条精确行号摘录及原始证据摘要。首次gh输出过滤器拒绝日志ANSI转义而stdout为空，原错保留；后在独立目录启用CLI输出转义选项，仅捕获到文件，两日志414518B/117868B核验，未将转义发往终端。该收据记录的是当前观察，后续发布元数据变化需重新核对。

未把成功 workflow、版本名或时间相符当成此 AAR 与每份来源候选的一一构建证明。尚缺精确构建组成/选项的充分证据、递归静态组件核对、最终生产通知保留及独立发布复审；[运行时材料准备](w3-runtime-materials-20261111.md)中的这些边界继续有效。不开启新发布流程，不修改生产依赖、模型或冻结探针。
