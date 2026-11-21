# Android LiteRT-LM 普通 UI 验证

## 2026-11-05 最终组合包真机导入、取消与重入

普通最终 APK `afed156b…926c461` 已安装在 TECNO KM9/API35，无需重装。前置 LM 导入目录缺席、
ModelStore 为空；普通 App/test APK/Identity runner、五份既有 ASR/SR 模型摘要和权限先核对。
USB 传入既有授权 ZIP `772d5bb5…ea734e`，经真实 DocumentsUI 普通导入，观察安装期间按钮禁用，
随后“模型已安装，可以选择图片并生成”及 `litertlm.android · 可用`。导入/ModelStore 七份文件
在运行前后摘要相同，权重为 `2ed7bc3a…4010d6`，与固定 ZIP 一致。

同一普通 Activity 通过系统 picker 选择原564字节红图，私有输入 SHA=`991b54f0…d3aa`，
prompt `Describe this image.` / budget32。全程 PID **19257**；原生初始化日志确认
**Mali-G57 MC2 / Vulkan Integrated GPU** 后普通取消，`RUNNING → CANCELLING → CANCELLED`，
等待期间生成/取消 disabled，终态恢复生成。同输入普通重入后 `RUNNING → SUCCEEDED`，
可见后端/model/revision/runtime均与固定清单一致，输出描述纯红色矩形。两轮各有1448/1448
WebGPU视觉delegate和CPU/GPU销毁对，第一轮销毁早于第二轮GPU初始化；仅覆盖同步执行合同。

普通清除后本轮私有输入/native cache已删除；原三字节历史fixture摘要及原cache文件名清单保持，
不声称所有历史视频cache内容均重新做过摘要。精确删除本轮临时公共ZIP，**保留已安装授权模型**
供Demo继续使用；普通包/test/runner、五ASR/SR摘要、权限和PID均不变。没有新build、下载、采音、
相机或私人图片，也没有新增benchmark样本；32tokens输出有界，不作自然质量/速度验收。

ignored `artifacts/verification/android-final-physical-lm-20261105/` 保存阶段/UI/XML、可见结果截图、
同PID时间窗口native日志、输入绑定和失败记录。冻结summary及只读 `verify.py` **52项检查通过**。
USB曾在第一图片点击前短暂断开，未执行点击，恢复同序列/同PID后继续；第二轮RUNNING后UI dump
exit255使主机观察脚本退出，另行观察同一请求直至成功，未重发生成。审批服务数次429/一次超时
均未执行对应命令，后续正常审批恢复；收尾helper初次仅主机import入口错误，修正后继续，未重启App。
分层264匹配源码/2历史测试差异仍按上一节边界，非全能力统一候选重建；人工触摸/其他provider、
异步native drain/streaming、质量/持续资源/minOS及完整审查许可仍缺，P4/P11/发布open/not-ready。

## 2026-11-05 最终组合包普通 UI 取消与重入

复用保留的最终普通 APK `afed156b…926c461`，在既有专用 AVD
`ODAI_LM_UI_20261104` / API36 / `emulator-5582` install-r 后运行；相机和麦克风权限继续拒绝，
不改测试 runner、不操作物理设备、不下载或复制权重。旧布局 APK `c098fc41…cf1a6`
保留作为精确回退，最终 APK 与已装模型留在专用 AVD。

系统 DocumentsUI 选择原564字节红图后，picker-owned 输入 SHA256=`991b54f0…d3aa`。
默认 prompt `Describe this image.`、32 tokens。PID **9200** 的 native 日志出现
`Selected adapter` 后普通点击取消，实际观察 `RUNNING → CANCELLING → CANCELLED`；
等待期间生成与取消按钮均 disabled，终态重新允许生成。同输入/同PID普通再次生成
`RUNNING → SUCCEEDED`，可见实际后端 `litertlm.android`、模型 `gemma-3n-e2b-it`、
revision `c03b6f60b8da6c5400b6838a2cf26420f80c0a01`、runtime `litertlm-android:0.18.1`，
输出描述纯红矩形。普通清除输入后，本轮 cache/input 文件为空，生成 disabled；四份模型/附属
文件、拒绝权限和PID均保持。两轮各有1448/1448 WebGPU delegate及CPU/GPU accelerator销毁记录。

这是 SwiftShader/Vulkan **软件 GPU** 模拟器证据，不能替代真机硬件GPU、人工触摸、其他provider、
自然质量/资源验收或异步drain合同。旧组合快照与最终sherpa相关来源分层核对264份相同hash，
另2处历史差异仅在测试；arm64 LM native库与旧回退包字节相同。这不是完整重新构建或穷尽的
APK与源码证明，各历史build身份继续保留。

本地 `artifacts/verification/android-final-combined-lm-20261105/` 保留输入/阶段/UI/XML、
同PID窗口日志、失败记录、summary和只读 `verify.py`，**38检查通过**。首个UI快照因自动审批服务
429未执行；后续更安全的只读命令和普通快照分别走正常审批后通过，没有绕过。首次picker脚本
因节点是focusable而非clickable，在点击前拒绝；按实际属性修正后选中，产品代码未改。
九保护文件与生产lock保持，无新增benchmark样本/采音/私人媒体，P4/P11/发布仍open/not-ready。

## 2026-11-04 TECNO 系统 ZIP 导入与真实生成

复用已获授权的固定 Gemma manifest-first ZIP，无新下载/许可操作。TECNO
KM9/API35 新鲜检查 Awake/unlocked；普通405d64e1…082c0 APK与原空LM存储先核对保留，
临时 install-r 冻结组合候选 **644bbde4…df30**。候选255份源码输入中的254份仍匹配当前树，
唯一差异是后续 headless ASR instrumentation 的一行顺序调整，不是 APK 主源码；
不宣称从当前HEAD重新构建过候选。ZIP SHA256仍为下方772d5bb5…ea734e、3,655,853,789 B，
USB推送后设备摘要匹配，未预置App私有模型。

普通 Demo 实际点击“导入本地模型包（ZIP）”→系统 DocumentsUI/下载→选择精确ZIP。
UI先显示“正在校验并安装本地模型…”且导入按钮disabled，再显示“模型已安装，可以选择图片并生成”；
后端显示 `litertlm.android · 可用 · minOS 26`。导入副本与ModelStore中的权重、model card
及导入manifest/descriptor分别匹配原ZIP身份，权重SHA256为2ed7bc3a…4010d6；
incoming/previous暂存目录已清理，仅剩import目录。

同一普通Activity随后经系统图片picker选既有 `ODAI-solid-red-256.png`，32-token
请求到达 **SUCCEEDED**，输出 `The image is a solid red rectangle.` 等描述；页面显示
`litertlm.android`、`gemma-3n-e2b-it`、revision
`c03b6f60b8da6c5400b6838a2cf26420f80c0a01`、runtime `litertlm-android:0.18.1`。
picker私有副本与native input.image均为991b54f0…d3aa。PID32723保持；原生日志确认
Mali-G57 MC2/Vulkan Integrated GPU、视觉1448/1448 WebGPU与CPU XNNPACK，
终态有且仅有一对CPU/GPU DestroyAccelerator。32tokens仍截断，不称完整自然质量。

普通UI清除输入后“尚未选择输入”、生成disabled；本轮私有输入与native cache已无文件，
原三字节fixture摘要039058c6…1cfb81不变。随后精确恢复原APK与空LM目录，权限前后原已granted，
未请求相机/麦克风或录音；原普通Demo无验证参数启动。本轮新建的导入/安装模型副本、
公开测试ZIP与恢复用临时APK已删除，其他获授权模型/fixture未清理。

恢复阶段保留两项失败：首次只读检查遇 `adb: device ... not found`，确认同序列重新在线后
继续；流式原APK恢复随后120秒未返回。只读核对确认候选包仍在、主机原安装进程已结束、
无active/finalized安装会话，历史会话2051014383为destroyed、finalStatus=-100及不完整APK解析失败。
未把观察超时当完成或并发重装；改将原APK传到本轮专用路径、校验SHA后 `pm install -r` 返回
Success，最终设备原包SHA/目录/权限与前置状态匹配，临时APK删除。具体传输断连原因未确定，
不是模型或推理失败，未重启ADB/设备或修改系统网络。

ignored `artifacts/verification/android-litertlm-physical-zip-20261104/` 保留UI快照/截图、
源码与包绑定、模型/输入摘要、同PID原生日志、失败与精确恢复回执；`verify.py` **317/317证据检查通过**，
含254份匹配源码、九项保护及生产MLX lock。本次没有新build/SDK单测或独立复审；此前429
不视为批准。本节关闭真机系统ZIP导入并串联一次真实生成的子项；当前候选取消重入、
人工触摸/任意provider、自然质量/资源与异步drain仍开放，P4/P11/发布保持open/not-ready。

## 2026-11-04 最新三后端组合包普通生成

固定组合 APK `bd89bdbf…16bd7`（源码提交7132208；LM/SR/Identity三opt-in）已在
既有6GiB `ODAI_LM_UI_20261104` 通过普通系统选红图→32-token真实生成→清除输入。
PID3723保持；实际输出描述 `solid, vibrant red rectangle`，页面报告固定模型revision
与runtime0.18.1。私有输入完整SHA与原合成图一致，既有授权Gemma模型四文件摘要前后
不变；CPU文本XNNPACK、视觉1448/1448 WebGPU实际为SwiftShader软件Vulkan，原生日志
有一对CPU/GPU销毁。普通UI清除后cache文件为空，相机/麦克风权限仍未授予。
未重建/重新下载或导入模型；原APK已核对可回退，当前组合包与模型保留在专用AVD。

联合LM/Identity证据核验 **297/297**，含249份源码/9保护/MLX lock；独立复审429无结论。
这补上最新组合包实际LM执行证据；旧包的取消/重入、ZIP导入不能转算当前包覆盖。
32tokens输出仍截断，不提升完整自然质量/硬件性能/异步drain/发布结论。
见[精确身份与联合证据](android-mainflow-emulator.md#current-candidate-lm-and-identity-execution)。

## 2026-11-04 TECNO 普通 UI 与自然图像重入

已在获授权 TECNO KM9/API35 的普通 Demo Activity 完成系统 DocumentsUI
图片选择取消/重入、红图真实生成、原生初始化后的取消与同 PID 自然图像再次生成。
设备预检为 Awake、keyguard showing=false/inputRestricted=false；本轮没有执行解锁、
相机或麦克风操作。生产代码未改，未重新构建或下载模型/runtime。

候选 APK 沿用下方模拟器的 `4e8a7c7d…15e868` 完整摘要，安装后设备回读匹配。
Android/backend 与 Demo 生产源码自 `c5be267` 无差异，固定生产源文件摘要与原构建绑定一致。
本轮通过已文档化的 debug 私有目录预置，复用设备上的已授权模型包；ModelStore
按正常生产路径校验安装。设备安装后模型完整 SHA-256 为
`2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`。
这次没有在真机重做 3.66 GB ZIP 系统导入；该 UI 子项仍只有下方模拟器证据。

普通 UI 的实际顺序如下：

1. 从图像理解页打开系统选择器，取消返回后仍无输入；再次进入 Downloads，选择本轮
   固定 `ODAI-solid-red-256.png`（SHA-256 `991b54f0…d3aa`）。未打开私人媒体。
   `Describe this image.`、32-token 请求 `RUNNING → SUCCEEDED`，最终输出描述
   `solid red rectangle`，并显示实际 backend/model/revision/runtime。
2. 对同一输入再次生成。原生日志已出现第二次 Mali adapter 初始化后点击取消，UI 显示
   `CANCELLING / 等待原生任务释放…`，生成按钮 disabled；随后 `CANCELLED`、恢复操作，
   无结果交付。12:41:09.679 的 CPU/GPU accelerator destroy 与此次释放对应。
3. 从系统 Downloads 选择既有获批 Common swifts 自然视频的首帧 PNG，
   保留同一 prompt，仅将输出预算改为64。再次 `RUNNING → SUCCEEDED`，最终输出提到
   `bright blue sky`、`white clouds`、`birds`，实际模型身份与第一轮相同。
   初始化前、取消后及最后清除后 PID 均为 **9562**。

自然图片由原 `artifacts/P7/natural/prepared/input-180p-4x.mp4`（SHA-256
`97682e3f…f2f1f`）直接解码第0帧，320×180 PNG，23,605字节，SHA-256
`f49c63e73297294b2252aa0610c248cedb0ba8b51e143e3dd6a3f5d02a76fb29`。
系统 picker 私有副本与 native `input.image` 的设备摘要均匹配。输出64 tokens仍在
`peaceful and airy` 处截断；单帧可见内容检查不等于完整自然图像质量，更不验证视频/时域能力。

PID9562过滤日志记录三轮实际 Mali-G57 MC2、Vulkan、`adapterType=Integrated GPU`，
视觉1448/1448 nodes为 `LITERT_WEBGPU`，CPU文本为XNNPACK。三轮均有CPU/GPU销毁事件。
这确认该硬件 delegate 路径可运行，不是性能、峰值内存、能耗或长期资源验收。
缺OpenCL库、GPU准备与UI掉帧等warning保留，不称无warning。

清除输入后本轮 `multimodal-input` 文件和 `cache/litertlm` 文件均为空，生成disabled；
原有另一个目录中的3字节测试provider fixture按原时间/大小/摘要保留，不声称整个cache为空。
原Demo APK在测试前备份，原空LM存储目录单独rename保留；测试结束恢复原APK
（完整SHA-256 `405d64e1bf67d1879ff96db386282066e7cda612a2b77134377e070feec082c0`）
与原目录，并移除本轮新增私有模型/导入副本。原普通Demo已无验证参数重新启动。
设备上的本轮公开输入文件保留复现，没有修改其他App或设备设置。

ignored 证据：`artifacts/verification/android-litertlm-physical-ui-20261104/`，含
准备/恢复脚本及逐步回执、APK备份、设备完整模型/APK身份、系统picker/取消/两次成功/UI清除
snapshots与截图、自然帧与picker/native绑定、过滤日志、恢复后状态及 `verify.py`。
新鲜结果 **67 checks passed**，并核对九项保护文件和生产MLX lock。
初次准备guard停止后核查发现旧空LM目录，改为精确保留；首次verifier误把注册日志计入销毁数，
失败报告保留，修正为只统计 `DestroyAccelerator` 后通过。二者均不是模型推理失败。

复核命令：
`rtk proxy python3 artifacts/verification/android-litertlm-physical-ui-20261104/verify.py`。
它复核已保存证据，不重跑UI/推理。独立有界证据/文档复核重新运行同一脚本，结论
**PASS（仅本轮TECNO普通UI范围）**，未修改文件或操作设备；下方模拟器PASS仍属原范围。
真机ZIP导入、人工触摸/任意provider、跨任务自然质量、异步native drain/streaming与资源仍开放。
P4/P11/发布保持 `open/not-ready`，goal active，未push/merge/release。

## 此前的隔离模拟器 UI

2026-11-04，源码 `c5be267740d904a63f6a634bcd11a45eff2b1a18`。本轮没有修改生产源码或重建 runtime；普通 Demo 在隔离模拟器完成系统 ZIP/图片选择、真实生成、取消和同进程重入。独立有界 verifier 补审结论为 **PASS（仅此 UI 范围）**，不是全分支或发布批准。

## 环境与身份

- API36 arm64 AVD `ODAI_LM_UI_20261104` / `emulator-5582`，6 GiB RAM、32 GiB data；复用已有本机系统镜像。禁用音频输入和相机，未授予 App 相机/麦克风权限。
- 安装此前已验证 APK，SHA-256 `4e8a7c7de8ea57ac04b75d8b2cd92a680947f6dddf70c31eb990b2eeab15e868`；安装后读取 base.apk 的完整摘要相同，生产源文件与构建快照匹配。无 validation 启动参数。
- 官方已授权 Gemma3n E2B revision `c03b6f60b8da6c5400b6838a2cf26420f80c0a01`，权重3,655,827,456 B，SHA-256 `2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`。manifest-first ZIP 为3,655,853,789 B，SHA-256 `772d5bb570fd1d1905b0421d35249759f2556baa66c2d2bfff74030433ea734e`；descriptor 显式 `CPU_TEXT_GPU_VISION`，runtime `0.18.1`，默认 CPU_ONLY 保留。
- 输入为固定合成红色256×256 PNG；prompt `Describe this image.`，输出预算32 tokens，无私人媒体或 Photos 写入，权重未分发。

## 实际观察

| 流程 | 证据与结果 |
| --- | --- |
| 模型导入 | 系统 DocumentsUI 打开→取消→重入→选择真实ZIP；校验安装期间导入按钮 disabled；终态模型已安装、`litertlm.android · 可用`。 |
| 图片选择 | 系统 Downloads 图片 picker 打开→取消→重入→选择 `ODAI-solid-red-256.png`，输入栏更新。 |
| 第一轮生成 | `RUNNING → SUCCEEDED`，实际 backend/model/runtime identity 可见，输出描述纯红矩形。 |
| 第二轮取消 | 实际 native engine 已开始初始化；点击取消后 `CANCELLING` 显示等待原生任务释放，生成 disabled；随后 `CANCELLED` 并恢复操作。 |
| 第三轮重入 | 同一 PID2552 再次 `RUNNING → SUCCEEDED`；11:44:13原生初始化、11:44:42.921 CPU/GPU accelerator destroy，11:45:56采集终态UI；11:46及11:49独立回读 PID2552。 |
| 清除与清理 | 点击清除输入后“尚未选择输入”、生成 disabled；App cache 无文件、picker/incoming staging 无残留。空 cache/import 父目录仍存在，不声称目录全部删除。 |

两次成功输出均为：

> The image is a solid, vibrant red rectangle. It's a simple, bold shape with no discernible details or patterns. The red is a pure, intense

32-token 输出截断，不能作为自然图像质量或完整文本质量验收。

## 证据与边界

ignored `artifacts/verification/android-litertlm-ui-20261104/` 保留实际 UI snapshots/screenshots、`app-identity.json`、`avd-identity.json`、`bundle-identity.json`、`staging.json`、PID2552过滤的 `ui-generation-logcat.log` 及补录 `ui-generation-logcat-tail.log`、`post-state-raw.json`、`post-state.json`、`post-observation.json`、`verify.py` 和 `verification.json`。本轮 verifier **57 checks passed**，并冻结小型证据文件摘要；APK/权重/AVD均留在ignored本机目录。

原 `preflight.json` 是旧 `ODAI_MainFlow_20261104` 容量不足的调查；新AVD身份取自 `avd-identity.json` 和实际 `emu avd name`，不把旧容量数据套到新AVD。

日志实际选择 SwiftShader Vulkan/WebGPU，`adapterType=CPU / Software`，vision1448/1448 nodes由 `LITERT_WEBGPU` 执行。它证明软件GPU模拟器可运行该真实UI链路，不能证明硬件GPU性能、真机UI或自然质量；日志中的缺OpenCL/渲染丢帧warning保留。真机shell生成另见[同步接入验证](android-litertlm-sync-demo.md)，不与此UI结果混同。

后置物理设备只读观察为 **Awake，keyguard showing=false/inputRestricted=false**；此前Asleep是历史状态。最初脚本沿用Asleep断言而失败，已改为显式记录实际变化，未对设备执行锁屏、解锁或UI输入。本轮UI目标始终为隔离模拟器；不把物理设备状态变化推断为本轮导致。

九项受保护dirty/untracked文件与生产MLX lock保持。异步 native drain/streaming、真机UI、自然质量、长期资源仍开放，基础里程碑10/12，P4/P11/发布 `open/not-ready`；未push/merge/release。
