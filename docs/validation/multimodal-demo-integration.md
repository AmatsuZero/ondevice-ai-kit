# Enhanced Multimodal Demo integration validation

2026-10-23（设计文件日期2026-10-22，实施跨日）。接续6136a62。设计558b057，Task1 App-local AX驱动d498783，Task2接线10d65b8、诊断7e6250e、异步控件等待修复51d3b72；本验证检查点随文档提交。独立spec/plan `2026-10-22-multimodal-demo-integration`，ledger/final-review与所有日志 `.superpowers/sdd/2026-10-22-multimodal-demo-integration/`。

## Actual App integration

增强生成Demo `io.ondeviceai.multimodalmlxdemo` 实际运行既有DemoController/MultimodalHostPreparation/ModelStore/MLXVLM工厂/host/VM/bridge；新增验证源仅在 `ODAI_MULTIMODAL_DEMO_VALIDATION=1` 且全部增强flag成立时编入。运行必须同时提供 `--multimodal-mlx-local-model --multimodal-demo-validation`。once-only App.onAppear验证hook、weakcontroller闭包；默认与普通增强构建不含验证源/元数据。KMP源码/header/旧Swift导出标签不变，无新生成协调器。

App-local UIKit公开AX精确ID查找/激活、歧义拒绝/对象去重/有限节点和深度，不改变VoiceOver或全局automation。真实multimodal-tab→共享页面→multimodal-select-input→真实MultimodalFilePicker后，仅向实际delegate注入本App Documents/fixture.png并dismiss；不操作/读取私人媒体或枚举文件。随后等待实际生成按钮notEnabled trait解除、AX激活真实shared回调→VM/bridge/client/MLXVLM，核对真实输出与共享页面AX输出/phase/identity一致，再激活旧Speech/Video/返回Multimodal页签（仅动作被接受，未单独验其页面内容）。最后host.close等待drain完成。

## Fresh evidence and exact boundary

授权iPhone16ProMax/iOS27.0.1，UDID00008140-0008703436D3001C。最终sourceCommit `51d3b7213cbdf41cdac5747409ac63b75b3a5615`，built resource validation-metadata.json精确匹配，runId `6CEF2880-772D-4175-88BD-3CF919E68429`，PID21217。原始 `artifacts/verification/multimodal-demo-integration-final-20261023/demo-integration-wait.json`，SHA256 `5420aa1ce7c66305e516d6a37f7376205cead3440d73cdb5c304074852dab06e`；summary.json、install/launch及全部中间失败报告保留同目录。

9 gates全true：bootstrap、available、pageOpened、inputAction、pickerReply、generated、outputRendered、legacyTabs、closed。生成实际backend mlx.smolvlm2-500m/model fa57db46815177fbdfd65cc85a2b3416a8332268/runtime6e338e5df49424fc0db7c6d58184d622f92ed18a，原始文本非空。phase IDLE→RUNNING→SUCCEEDED；generateNodeInitiallyDisabled=true，等待解除后activationAccepted=true。`outputRendered`门禁名称仅代表AX文本/phase/identity反映，**不证明视觉像素/可见区域/物理触摸命中或屏幕阅读器完整体验**。

先前source10d65b8/PID21192与诊断7e6250e/PID21211均statusunverified，bootstrap/选择接线已通过，但generate activation返回false、canGenerate=true、phaseIDLE。最小诊断确认没有推理启动；最终报告的initiallyDisabled=true证实尚未重组到可用AX节点。修复为等待真实notEnabled解除、终态等待只接受SUCCEEDED/FAILED/CANCELLED（不把IDLE当终态），修复后真实device GREEN。保留全部RED/中间报告，不提升旧运行的等级。

Task1实际generator source-set RED→3/3，干净缺helper符号XCTest RED→新增5/5/全Demo38/38；fixture UIKit声明错误/错误project路径日志保留，不算有效RED。Task2 generator两项RED→Speech10/10，缺验证入口实际App编译RED→unsigned增强device编译GREEN；freshDemo38/38、全tools114/114、default完整simulator与enhanced signedphysicaldevice builds成功。初次占位sourceCommit长度不对被拒绝，修正后clean RED另留。strict新Swift早期一个NoBlockComments被合并shell掩盖，最终修正后task-2-corrected-format.log实际exit0；不声称早期lint已通过。

模型14/14文件bytes/SHA和fixture60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606在复制前复核；固定lock SHA256e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858。只既有SourcePackages，build参数 `-disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile`，既有team4X996UV8LC/profile，无allowProvisioningUpdates/依赖或模型下载/签名资产变更。最终生成工程最初fresh，之后只在忽略目录精确刷新validation-metadata.json到每次已提交snapshot并重新build/检查产物，未覆盖原保护工程。

## Remaining gates / next independent package

本包证明增强Demo真实bootstrap与共享控件→真实SDK成功、AX输出接线及idle旧页签动作；不等于物理触摸、真实system picker选文件、OS前后台或最低系统通过。成功文本仍把合成图描述为flag，与旧benchmark语义局限一致，不能算自然图片质量通过。不报告性能/RSS/MetalGPU/energy数值。

程序化host/VM取消/drain/重入/后台/close/lease已在前包通过；本包未通过shared取消控件或页签离页触发实际活动取消。下一独立包可复用App-local AX进行**共享页面首update后取消/离页与重入**有界验收；实际OS后台与触摸/systempicker仍另列，已确认外部门槛不反复重试。Android继续明确unavailable；短视频/生产backend/质量/资源/分发及独立review另包。

每Task/最终仅作者自审，独立review pending，无APPROVE。旧Video UI runner初始化超时、P6实录、Android ASR MISSING_ASSETS与最低系统等缺口保留；roadmap仍5/12完成、7未收口，P9active。所有日志/artifacts/.superpowers保留；保护文件hash/state/index不变。只本地提交，无push/merge/发布/录音/私人媒体读取。
