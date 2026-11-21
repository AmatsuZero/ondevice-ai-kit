# P11 本地输入冻结 — 2026-11-05

状态：`partial-input-freeze`，发布仍为 `not-ready`。本轮统一登记既有源码、外部模型、固定样本和9项能力的历史证据；没有从同一候选重建并重测全部能力。功能优先、corner case 与长期资源专项后置的决定保持，延期不计通过。

源码为提交 `5a65f51aa5c5d0765724b48bb7de1ce011b83c3a` 的 committed archive，tree=`efebe04634dc0ab77798662517d88ea06a5cd04b`，951个文件逐一匹配 Git blob、路径及执行位，内嵌 commit 一致。归档 SHA-256 为 `79835bba9fb7a71af2d7ee7741bbf9a958ed86a61e3b357925daa5aa2660e7a2`；原9个 dirty/untracked 保护路径未纳入归档、未改变。生产 `Package.resolved` 保持 `e67e9859…f858`。它与先前 F09 二进制所用源码快照、各历史运行报告的 SDK commit 分开记录，不宣称归档全部文件已在当前设备运行。

| 输入 | 冻结覆盖 | 边界 |
| --- | --- | --- |
| 外部模型 | SmolVLM2、Gemma3n、Whisper tiny＋Silero VAD、ESRGAN、YuNet/SFace、PAD，共6组27份模型及附属文件；完整路径、大小、SHA-256和既有revision/runtime绑定 | 仅核对现有本地字节，未复制/分发或扩展模型许可；PAD仍只是原始分数smoke |
| 固定媒体 | 图片、视频及参考、Apple文件ASR和7份sherpa合成PCM，共14份 | PCM为16kHz单声道PCM16LE；不包含用户麦克风音频或转写，不是准确性语料集 |
| TTS | 既有固定文本请求、probe源码及设备报告；历史en-US/Samantha身份 | 系统voice/model字节无法导出冻结；未新增运行 |
| 人脸历史benchmark | 原报告及其fixture hash | 原输入图字节未定位，标为report-only；不得用另一个NASA图片或重建图片冒充原图 |
| 设备/系统模型 | 最新F09实际iPhone17,2/iOS27.2/24B119，以及各报告自己的历史iOS27.0.1、TECNO/API35和模拟器/API36身份 | 未将历史报告重标成当前设备；系统Vision/Speech/VideoToolbox/voice模型不属于byte-frozen，iOS15/API26仍未验 |

本地证据目录为 `artifacts/verification/p11-input-freeze-20261105/`（ignored，不随Git提交搬移）。`summary.json` 是完整输入清单，`verify.py` 只读复核归档/Git对象、模型/样本、316项历史readiness来源、能力剩余gap、生产lock及9保护路径；发布状态提升和fixture哈希篡改的内存副本均被拒绝。历史跟踪文档与benchmark入口按归档版本校验，新文档和入口另由最新readiness绑定；原始ignored报告与媒体仍核对当前字节。复核命令：

```sh
rtk proxy python3 artifacts/verification/p11-input-freeze-20261105/verify.py
```

初次归档检查脚本误用了不存在的Git子命令；首个tar和失败记录保留，修正为 `get-tar-commit-id` 后成功。初版清单另存 `summary-initial.json`，补齐样本不重建归档。此脚本错误不属于产品缺陷。

新packager审查尝试仍返回429、无结论，未重试失败代理；不能代替独立审查批准。余项是完整分支复审、SDK源码再分发许可/生产交付策略、精确候选全能力重建验收、系统模型身份、缺失原fixture、Identity/PAD授权校准及P4/P11质量/资源/最低系统门槛。本轮没有新增benchmark样本、下载、采音、私人媒体或设备操作，也没有发布。
