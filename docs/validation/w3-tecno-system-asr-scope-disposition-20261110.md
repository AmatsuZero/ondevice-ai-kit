# W3 TECNO 系统 ASR 已知限制裁定 — 2026-11-10

**用户已明确选择“将TECNO限制纳入验收范围裁定”。本次 W3 对当前 TECNO KM9/API35 配置的 R1–R3 系统 ASR 余项，按用户接受的已知限制完成范围裁定；功能实测没有改记为通过。**

该选择来自当前会话对“更换支持系统ASR的Android继续实测 / 将TECNO限制纳入验收范围裁定”的回答。仅解决这台设备及当前系统服务配置的验收范围，不代表所有 Android 不支持系统 ASR，也不证明这台设备的全部资源供应途径均不可行。

| 项目 | 保留的实际结果 | 本次裁定 |
| --- | --- | --- |
| R1：系统 on-device en-US 麦克风识别 | 普通 App 显式系统 backend 单次请求 FAILED / BACKEND_UNAVAILABLE / MISSING_ASSETS；没有成功识别证据 | 当前 TECNO 配置按已知限制接受，不再作为本次 W3 的待重试任务；功能未通过 |
| R2：系统麦克风背景语音处理 | 资源前置未满足，未执行 | 当前 TECNO 配置按已知限制接受；没有背景语音处理通过证据 |
| R3：Android 系统麦克风中断/取消/重入余项 | 活动识别前置未满足，未执行；已验 iPhone 生命周期证据保留原范围 | 仅 Android/TECNO 余项按已知限制接受；不补造活动生命周期通过证据，不重复 iPhone 已验场景 |

资源调查及唯一获批 SDK 请求的实际结果见[资源观察](w3-system-resource-provision-observed-20261110.md)：

- 实际 on-device 服务为 `com.google.android.as/com.google.android.apps.miphone.aiai.app.AiAiSpeechRecognitionService`，与 Google TTS 的 voice 默认服务不同。
- 首次探针 reservation 失败，SDK0；独立只读路径诊断及修正版验收后，run02 唯一调用返回 `AsrResourceException / resourceErrorCode=15`，`readyClaim=false`。
- 错误15表示不支持下载事件监听，不是 VPN/网络失败或所有 provisioning 途径不可能的证明。没有资源 ready 或成功麦克风识别证据。
- 跨全部尝试一次 SDK download；未调用备用 API、未重试。资源探针零麦克风，报告双读/30项父证据检查及设备恢复通过。

本次裁定不产生新的下载或麦克风授权，也不要求换机。原下载授权次数已用完；不再为这三项等待解锁、发起资源请求或启动录音。未来若用户重新扩大设备/功能范围，再按当时资源状态和新的采集开始提示验收。

**不受本次范围裁定影响的项：**

- Android Sherpa 文件识别及其已验资源测量仍适用；不把整个 TECNO/Android 语音能力标记为不适用。
- iPhone 已有文件识别、生命周期证据保留原范围，不改为完整 Android 兼容证明。
- R4：固定前缀诊断完成；FLEURS 中文 CER Apple16/92、Android44/92，AMI Android1/3、Apple2/3 recognized，其余真实 no-speech/1110。质量仍未通过，没有批准的质量门槛；不重放、换样本或扫参。
- R5：仍引用 W8 的来源/设备测量及预算裁定，既有 Sherpa20轮/40次测量不重跑；缺测和预算不因此豁免。

因此，R1–R3 的**当前 TECNO 范围裁定完成**，W3 整包仍 open，剩余为 R4 质量裁定和 R5/W8 资源预算依赖。该记录不能作为产品质量或发布就绪批准。

机器收据与原始证据摘要：`artifacts/verification/w3-tecno-scope-disposition-20261110/scope-disposition.json`。本次只更新验收记录；没有实现、构建、设备、助手、下载或采集操作。
