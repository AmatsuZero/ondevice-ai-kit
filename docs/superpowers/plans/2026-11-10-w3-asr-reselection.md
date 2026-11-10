# W3 ASR Reselection Implementation Plan

**Goal:** 对一个新候选给出可复核的Mac筛查结果，只有通过才进入独立确认和手机方案。
**Architecture:** 独立Python环境与runner，不修改Android后端。主代理运行/验收，免费CBC交runner候选。
**Tech Stack:** Python3.14、sherpa-onnx2.0.1、numpy2.4.2、现有speech_error_metrics.py。
**Spec:** `docs/superpowers/specs/2026-11-10-w3-asr-reselection-design.md`。

## 约束与复核重点

- 只读原fixture，模型SHA与所有PCM SHA必须在native前验证；非法输入不允许进入解码。
- 实际runtime必须2.0.1；输出包含协议/runner摘要、逐行状态/错误、耗时及资源观测边界。
- 重复run/output禁止覆盖；部分失败保留，不能以CLI退出0代表全部通过。
- 不切片/增益/VAD/重写reference；中文和英文语言选项要逐片真实设置。
- 不将静音empty结果计为语音成功，不把typed failure计为空hypothesis。

### Task 1: 固定输入和接受runner

- [x] 完成官方资产size/SHA验证、包内路径/文件清单检查与本机依赖记录。
- [x] 免费CBC交`host_qwen_asr.py`完整候选，主代理核原始result/error/tool事件。
- [x] 用fake runtime做篡改模型/PCM、重复输出、异常报告和语言设置的验收；不得调用真实native。
- [x] 冻结9语音+2静音协议及源码SHA，再进行一次真实顺序批次。

### Task 2: 实际筛查和裁定

- [x] 每次调用的120s/4GiB保护由父监测；保留原始日志、同次输出和失败。
- [x] 现有scorer独立复算9条，逐组按spec规则裁定；完整验证实际配置和输入/模型绑定。
- [x] 通过才冻结独立确认；失败不扫模型/参数，报告具体结果与下一项有依据的决策。
- [ ] 精确提交本任务文档/候选/报告，保留其他dirty；同步已授权issue/wiki时逐端回读。

### 后续诊断

- [x] 固定 Silero v5 准入协议及源码，通过 5/5 fake-runtime 检查后真实运行一次。
- [x] 8/9 语音准入、4/4 非语音拒绝，按原条件拒绝此准入器，不扫参。
- [x] 核实际 wheel 构建 af2d8d81 与固定 tag 的原生源码相同，已含静音修复 #3907。
- [x] 单变量自动语言诊断：非语音4/4空，语音远场17/27且C段100%词错，拒绝纯auto配置。
- [x] 两阶段候选：14/14新故障检查及原15组通过；13输入22decode，开发筛查通过。
- [x] 独立52语音+13挑战：65/65、117decode，中文7.12%/近场英文8.53%/远场英文27.43%，13挑战均空，工程确认通过。
- [ ] Android新候选的实际质量/完整两阶段资源、再分发通知链及独立审查；Mac证据不替代。
