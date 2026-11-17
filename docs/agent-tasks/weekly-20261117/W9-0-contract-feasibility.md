# W9-0 派工模板：合同与双端文件变声可行性

Task：为本周离线效果变声交付合同与最小实际渲染候选。不是实时/通话/克隆，不处理 W3/W6。

Owned files：仅隔离候选目录中的 `docs/voice-effects/contract-v1.md`、`probes/voice-effects-feasibility/apple/*`、`probes/voice-effects-feasibility/android/*`；不得改工作树现有源、依赖锁、全局配置、签名、模型或设备。候选文件集须小到可完整交付；不能完整返回时报告拆分边界。

Inputs：主代理提供的本周 spec、Swift/Android Speech/Video合同及依赖清单、相关官方文档。不能根据没提供的文件编造接口。平台/库语法先查 Context7/官方文档；没有工具时请求缺失原文，不编造调用。

Acceptance：输出合同明确 input/output/lease、busy、cancel/drain/close、错误与线程语义；交合成音频的 bypass 与非 bypass 真正 WAV 渲染候选、验证器和明确预期。主代理运行实际检查；你未跑的测试写 unverified。工程提议单声道PCM16 WAV、16/44.1/48kHz、≤30s、±4半音/保持时长先验证再冻结，不能只改播放速度冒称等价。Android 无现成已接入引擎的事实必须处理；新依赖先报告，不擅自添加。

You are not alone in the codebase. Preserve other edits. Do not recursively delegate. Report blockers and scope changes.

Before work and on errors, consult docs/agent-pitfalls.md. For a no-tools task, the primary must supply applicable excerpts below. Preserve other edits, do not retry a known quota failure, and report concrete blockers upward.

Required supplied excerpts：§1“进程成功不等于任务成功”；§11 保护原dirty/普通App/模型与真实状态；当前 CBC 429 条目；音频/异步编译和候选真实输出边界。未注入这些原文前本模板不能直接当无工具prompt发送。

Requested output：完整小文件内容或结构化候选、文件名/字节数/SHA、依赖/API依据、检查是否实际执行、失败/权限事件、缺测和scope blockers。禁止自称设备/发布通过；主代理拥有集成与验收。
