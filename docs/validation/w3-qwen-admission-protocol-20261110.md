# Qwen 后续：Silero v5 仅作准入诊断

Qwen host screen01 的原判定仍为failed：语音质量筛查通过，2/2纯静音生成文本。
不修改原通过线、不重放那11次Qwen解码，不直接进入独立质量确认或手机。

新问题是固定v5检测器能否同时准入9个原语音文件、拒绝4个非语音输入。
这是新前端候选的分类诊断；不对Whisper/tiny再扫参数，不是W3质量通过。
原643854B Silero结果、固定增益拒收均保持；新模型仅作Mac隔离诊断。

- 模型：Sherpa官方release `silero_vad_v5.onnx`，2313101B，实取SHA256
  `6b99cbfd39246b6706f98ec13c7c50c6b299181f2474fa05cbc8046acc274396`。
  release该资产未给digest；这里是首次实取摘要，不冒充供应方发布SHA。上游Silero为MIT，
  发布链/通知仍在实际SDK采用前复核。TEN VAD因额外限制不纳入SDK候选。
- 固定Sherpa2.0.1、CPU1thread、16kHz、window512、threshold0.5、
  min_speech_duration0.25s、min_silence_duration0.5s、max_speech_duration30s。
- 13个fresh VAD：原9语音+zh/en两个全零5秒输入，再加两个固定seed高斯噪声，
  nominal sigma -40/-20dBFS、5秒。合成噪声不是实际环境无语音验收。
- 全文件无增益、不提早终止；最后不足512的分类窗口补零，明确报告补零数，最后flush。
  只记录真实segment起点/长度/计数；有>=1 segment才准入。不改阈值/最短语音长度。
- 每次VAD前核模型/PCM摘要；原始浮点波形前后摘要相同。即使准入成功，未来送给ASR
  的仍是完整原幅度PCM，不使用VAD crop。此处不调用ASR、不输出裁剪音频。

成功条件事前固定：9/9语音准入、4/4非语音拒绝、0错误、输入完整性及释放报告无伪称。
任何失败就结束该候选诊断，不对本13片扫参。通过也只是准入功能信号，必须新独立集
验证整条候选管线；当前Mac Qwen采样RSS约2.57GB，不能声称满足手机1.5GiB目标。
