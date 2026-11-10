# AMI 原幅度 no-VAD 文件对照

**三段均返回文本，但没有形成可接受的生产修复。** 既有 scorer 得到总 WER **21/27（77.78%）**、CER **63/114（55.26%）**。原两次 no-speech 被绕过后，D/C 两段各有8/8词错误；不能把“有文本”写成识别质量已解决。

这是与固定增益分开的新诊断：只在隔离副本复用既有 [no-VAD 单行控制](../../probes/android-natural-file-asr/no-vad-control-replacements.json)，将 FILE 路径的 VAD prepare 换为 `samples.copyOf()`。三段原 AMI PCM、幅度、reference、整片切分、en-US、Whisper tiny、四模型文件、session/decoder 和 scorer 全部保持；生产代码及麦克风路径不改。单行逆替换后与生产逐字相同。先前中文 no-VAD 的44/92→53/92退步结论保持，不以本批推翻。

| 片段 | no-VAD hypothesis | WER | CER |
| --- | --- | ---: | ---: |
| D-330 | `I'm going to do it and I'm working.` | 8/8 | 21/32 |
| C-294 | `(multi-e-e-e-e-e-e-e-e-` | 8/8 | 31/37 |
| B-146 | `Okay, I will not need to discuss the project finals.` | 5/11 | 11/45 |

原B输出 `I need to discuss the project finance.` 的 WER 为5/11、CER14/45。事前“3段返回非空文本，且B的WER不退步”的**功能信号满足**，没有把它倒写成失败；该信号从来不是生产准确率通过线。D/C 真实输出与reference不符，整体质量未解决，故不建议生产全局绕过VAD。原D/C typed no-speech不伪造成空hypothesis计入基线总WER；本批3行分数单独报告。

## 唯一批次及恢复

- TECNO KM9/API35，PID29678，run `w3-ami-novad-20261110-01`；3/3真实请求、close及前台退出成功，输入数组保持。
- 构建/lint23.166s通过，签名/无权限/实际PCM与四native库摘要匹配。原三片runner逐字复用，没有新增实现或助手调用。
- 首次提交在安全锁屏gate被阻断，run-started及报告均不存在，**零识别命令**。用户再次解锁后才提交唯一实际批次；原拦截证据保留。
- 终态双读、四模型前后SHA、六个原App路径/普通模型SHA核验及精确别名/远端暂存恢复通过，实际 resumed Activity 回到普通Demo。
- 原始报告SHA-256：`cfb1a95a3c9d635e2c3e085556e375860eeb1f6216e55d8b96b1059ab0a86bf3`。零麦克风、资源下载和生产变更。

[可复算reference/hypothesis与分数](w3-ami-no-vad-observed-20261110.json)。本机完整协议、build/APK、首次锁屏拦截、same-run原始报告/双读/恢复位于 `.omx/artifacts/w3-ami-no-vad-20261110/`。

当前两条前处理尝试已取得判别结果：固定+20dB仍漏检C；移除VAD虽返回文本，D/C词错率仍100%。不能据此宣称“唯一根因已锁定模型”，但证据不支持继续用放大、降阈值或全局移除VAD作为修复。下一阶段应先评审模型/场景匹配及资源取舍，再确定一个有独立验证集的候选；不重复扫当前前处理参数。W3/R4/R5仍open，生产质量与预算未批准。
