# 类型化视频 KMP Bridge 验证

2026-10-24，foundation `feat/sdk-foundation`。在已完成的Swift固定合成视频native链路及Android契约之上，新增可选typed video bridge和双平台adapter。本包没有Demo视频状态/UI/host stager或KMP→权重真机视频证据。仍5/12、P9 active、独立review pending。

## 实现与兼容性

[设计](../superpowers/specs/2026-10-24-multimodal-video-bridge-design.md)、[逐Task计划](../superpowers/plans/2026-10-24-multimodal-video-bridge.md)。设计`e5a91a7`；common`447a7d5`；Androidadapter`0a84af6`；Appleadapter/generator`2a1757d`。

- `MultimodalBridgeVideoRequest(inputReference,prompt,maxOutputTokens=128)`保留opaque token和原始prompt，只做非空/正预算校验。可选`MultimodalVideoBridge : MultimodalBridge`新增`generateMultimodalVideo`，旧图片bridge conformer无需新增方法；Unavailable实现视频仍明确backendNotLinked，closed优先。
- 两端native bridge新增单独可选`videoInputs`（复用既有provider/lease接口），默认nil/null。视频不会通过图片provider解析；缺视频provider时INVALID_INPUT、不acquire图片lease。Android保留三参构造descriptor，Swift旧init源调用可继续。既有Demo仍image-only host，不自动接视频。
- image/video复用同一bridge active任务、native client和query/close屏障。native drain→input release→一次UI terminal，slot保持到清理完成；支持video→image同步terminal重入。any-thread cancel、立即取消、取消胜过晚错误、update过滤、close join、稳定错误隐私沿用原语义。native legacy拒绝视频、不fallback，bridge已acquire的lease在失败前释放；availability不承诺video profile。

## 新鲜证据

所有Gradle offline、无新依赖；Xcode独立生成project只用本地基础apple package和选定KMP framework，`-disableAutomaticPackageResolution`，现有iOS27.1 simulator。原SDK固定8帧真机结果只作前包证据，本包未读模型/媒体或运行设备推理。

| Gate | 实际结果 |
| --- | --- |
| Common RED→GREEN | 缺video request/interface真实RED exit1；最终 **24/24**（新增3），两framework link成功、headers导出新接口 |
| Android adapter RED→GREEN | 缺entrypoint/videoInputs真实RED；最终模块 **48/48**（新adapter13+旧35），lint/Android binding compile成功 |
| 完整Android回归 | 既有完整verify列表 **725 tasks、465项JUnit/46 suites、0failure/error/skip**，11 test modules，任务数与测试数分开 |
| Swift adapter RED→GREEN | freshBinding测试工程缺API/videoInputs真实exit65；最终Binding ABI **23/23**，Demo ABI **55/55**，每ABI含新video13，xcresult均0fail/skip |
| 旧Video ABI | 新Binding framework下原Video Swift adapter **6/6、0skip/fail**，无selector collision |
| 工具/静态检查 | fulltools **133/133**（显式Ruby3.4.11）；changed Swift strict swift-format、Python compile、diff-check exit0 |

两端均使用真实native MultimodalGenerationClient与受控外部backend：原始video request/预算/identity/selection、ordered UI updates、missing/unlinked/legacy/unknown backend、未知Swiftselection、双向image/video busy、立即/跨线程cancel、drain/release前busy、late error取消优先、video→image重入、query/close、host Job取消后清理、private error固定映射。不是mock client，也不是模型权重执行。Swift测试工程沿用Swift5桥接语言模式，基础SDK包Swift6；不扩大语言/最低OS兼容性结论。

保留真实中间失败：common测试多余always-false instance check触发编译诊断，移除该反射断言并保留旧conformer行为测试；Android两项新测试推断String返回导致JUnit initializationError，显式Unit后运行；tools首次133项中2项失败，生成器fake仓库漏新Swift测试源，修正fixture后实际PBXSourcesBuildPhase与全工具通过。没有产品逻辑规避。全部raw/argv/review/checks/source+framework hash inventory保留在`.superpowers/sdd/2026-10-24-multimodal-video-bridge/`；xcresults分别在`artifacts/verification/multimodal-video-{binding,demo}-20261024/build/Logs/Test/`，旧Video回归在`old-video-abi-after-multimodal-video-20261024/`。

## 同步、审查与下一步

用户本次授权的既有检查点 `05c67d2a85c050d70ad51e255d0ed8deabc1d833` 在极狐网络恢复后已非强推同步至GitHub origin与GitLab jihu，两个独立fresh ls-remote均相同。此前TLS/reset预检失败记录保留；没有修改代理/路由/aTrust/凭据，没有合入main/发布。新Android与KMP包仍仅本地提交。同步raw为本ledger `remote-sync-20261024.json`、`sync-*-frozen-push.log`及前包transport失败logs。

本包仅作者范围复审/最终自审，独立review仍pending、无APPROVE。两个原始保护文件hash/state/index不变，所有artifact/失败logs保留。下一独立包先设计shared typed video input/state/UI及双端host stager，之后另验KMP→固定模型设备视频链路。保留Android真backend、原上游8→7失败/短于8实际样本拒绝、binary archive exit65、自然视频/时序质量、Metal/GPU/energy、最低系统、物理触摸/真实picker、旧Video UI、P6实录等全部门槛；不提升5/12或关闭P9。
