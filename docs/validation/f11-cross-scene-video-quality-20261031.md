# F11 自然视频跨场景自由描述核对（2026-10-31）

本报告只比较两段公开视频各三个 2 秒 motion window 的原始自由描述。六次生成均在 iPhone17,2 / iOS 27.0.1 上使用 `mlx.smolvlm2-500m`、相同模型 revision `fa57db46815177fbdfd65cc85a2b3416a8332268`、runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a` 和 prompt `Describe what happens in this video in one brief sentence.`。素材与 SDK source commit 不同，因此下表是跨场景失败模式核对，不是配对性能 benchmark。

| 场景 / source commit | 原始报告及 SHA-256 | 素材来源 |
| --- | --- | --- |
| Common swifts / `b000458ec8f27a5ff9fb3ddd7056bf421238077d` | `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/device-report-v2.json` / `620ce4d2d3a90e83e9385ad5fbb92fe7eb60be728d7be5aa2015863735413ace` | [固定公开素材](multimodal-video-demo.md#2026-10-29-静止帧场景语义真机探针)，fixture manifest `666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694` |
| Kawaida waterfall / `f0ff4ed72c061394f0c04357da771a50c0fb4d2b` | `artifacts/verification/f08-cross-scene-waterfall-20261031/device-report.json` / `bcf785bc9637965378f22ac67204e15d68fa63eb6544d35fa976946cfdd68b7b` | [Wikimedia Commons，Lebu Ayiga，CC BY 4.0](multimodal-video-demo.md#2026-10-31-f08-第二自然场景kawaida-瀑布)，fixture manifest `69ecac7378a1a76e9acbbc135ac3e8bbe2f5c7942e95c0e46f4540b21bd7e92b` |

两份原始报告均为完成状态、9/9 请求成功。以下只取各报告的三条 `semantic-motion-*`；输出仅去除首尾空白，保留原文。哈希绑定的六行派生记录位于 ignored `artifacts/verification/f11-cross-scene-video-quality-20261031/summary.json`，SHA-256=`530c4676f9e81f9a68a76e27aa806f872a4713bfc793361edd8a84267bebe474`。

| 场景 | window | 原始输出 | 窄场景词 | 质量边界 |
| --- | --- | --- | --- | --- |
| 飞鸟 | 0–2 s | A group of birds fly in formation against a clear blue sky. | birds/sky 命中 | 旧固定 rubric 的 subject/action/scene 通过 |
| 飞鸟 | 2–4 s | A flock of birds is seen flying in the sky, with a small bird flying in the foreground. | birds/sky 命中 | 旧 rubric 的 scene 未通过 |
| 飞鸟 | 4–6 s | A flock of birds is seen flying in the sky, with a small bird flying in the foreground. | birds/sky 命中 | 旧 rubric 的 scene 未通过 |
| 瀑布 | 0–2 s | A group of people are gathered around a waterfall, with one person holding a camera. | waterfall/water 命中 | 抽查画面未支持人和相机细节 |
| 瀑布 | 2–4 s | A small animal is seen near a waterfall, possibly a frog, and then disappears into the water. | waterfall/water 命中 | 抽查画面未支持动物或青蛙细节 |
| 瀑布 | 4–6 s | A person is seen walking along a path next to a waterfall, with a bird flying in the air. | waterfall/water 命中 | 抽查画面未支持人和鸟细节 |

场景词在两组中均为 **3/3**，只说明输出提到了大类画面。飞鸟组现有固定 rubric 的完整语义通过为 **1/3**；瀑布组目前只抽查了原片 0、2、4 秒画面，不能给出逐帧人工标注后的完整正确率。两组 motion/frozen 的正式严格标签 pair 均为 **0/3**，所以自然 temporal quality 仍是 `observed-failure`，全量语义质量为 `unverified`。

本报告没有新增设备请求，也不把六条自由描述加入原有合成单图的三个 grouped benchmark 组。未完成项包括完整逐帧人工标注、更多独立场景、主观质量、跨场景持续资源、GPU/能耗和发布预算；P11 继续 `open/not-ready`。
