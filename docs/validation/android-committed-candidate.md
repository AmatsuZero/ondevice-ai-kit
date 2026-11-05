# 精确提交 Android 组合候选 — 2026-11-05

状态：Android 构建、固定单图 LM、固定英文 ASR/原生生命周期、SR、Identity 与普通人脸检测已通过限定验证；本 AVD 的 TTS 因无引擎初始化失败。全能力候选验收仍未完成，P4/P11 与发布保持 `open/not-ready`。

本次从提交 `e0438c030a987ceeff9f9207c2475684749e4d8b` 生成独立源码归档，tree 为
`1a79aabdc997ca99fe441a02b9cb54982ce2c34c`，归档 SHA-256 为
`c3b5966e09fdd44a957e3da1f3a2674a289857f99e02211a18f1f772155373b4`。
953 份 committed 文件的路径、Git blob、执行位与提取后 SHA-256 均匹配；原9个保护路径未混入构建。
既有 Sherpa 资源与 LiteRT-LM/Sherpa/OpenCV Maven 文件共14份复制到隔离目录，构建后再次核对。
生产 `apple-mlx/Package.resolved` 保持 `e67e9859…f858`，未增加下载或变更签名资产。

四个后端同时启用，使用已有 JDK21、Android36、固定 Gradle9.4.1 与依赖缓存，执行
offline/no-build-cache/rerun-tasks 的 Android 单测、lint、普通与测试 APK 构建：
**997 个任务全部执行、构建成功**。17 个模块报告共 **586 项 JVM 测试，0失败/错误/跳过**；
20 个模块 lint 无 Fatal/Error，有52项既有 Warning。586 包含 examples/probes 额外11项；
不宣称此次执行了 shared/KMP 的其他 host 测试。
外部 artifact 收据记录运行类路径126份、单测类路径131份及逐文件 SHA-256；
项目依赖以完整源码/构建绑定，未用外部 artifact 清单冒充项目源码覆盖。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| 普通 APK | 456350170 | `b90e783062548368f12e5262a1dc8437d511119c6dddf1c1ffb66aabafa64d8d` |
| 测试 APK | 139454 | `4f3e07ae32b6dca65e16aad2244d888ec82902b50a0f6610f2544280348de6a4` |

包内 applicationId/minSdk/targetSdk/debuggable 为 `io.ondeviceai.demo/26/36/true`，
DEX 含完整 sdkCommit；46份原生库条目覆盖 LM/SR/OpenCV/Sherpa/ONNX，7份 ASR 资源逐字节匹配。
上表原始测试 APK 未带额外 PCM fixture，也未安装；后续另建的测试包与运行见下节，不回写此构建收据。

## 同一 APK 的普通 LM 验证

专用 `emulator-5582` / `ODAI_LM_UI_20261104` / API36 升级上述普通 APK。
原 `afed156b…926c461` APK 保留回退；Gemma 权重及附属4文件摘要保持，麦克风/相机权限始终拒绝，原本没有 test APK。
TECNO 上可用的普通 Demo 与模型未操作。

普通系统 DocumentsUI 选既有 `ODAI-solid-red-256.png`，私有输入 SHA=`991b54f0…d3aa`；
默认 prompt `Describe this image.`、budget32。PID **10705** 在 SwiftShader 软件 Vulkan 初始化后普通取消，
`RUNNING → CANCELLING → CANCELLED`；drain 期间生成/取消禁用，终态恢复生成。
同图同PID重入 `RUNNING → SUCCEEDED`，输出描述纯红色矩形，实际后端、模型 revision 和固定 runtime0.18.1 一致。
两轮各有1448/1448视觉delegate与CPU/GPU销毁对，前轮销毁先于后轮初始化。
普通清除后本轮输入和native cache为空，模型、拒绝权限、test APK空值及PID保持。
**44项运行证据检查通过**。这是软件GPU验证，旧afed真机硬件证据不改标成此新APK。

构建与运行证据位于 ignored `artifacts/verification/android-committed-candidate-20261105/`。
`construction-summary.json` 冻结构建阶段（`deviceAcceptance=not-run` 是当时状态）；
后续 `summary.json` 绑定构建摘要与实际运行，SHA-256 为
`73f2adce4f500143b8e17a029f9f244b212aa63ae6f1cabd1803583c50d5728c`。
只读复核：

```sh
rtk proxy python3 artifacts/verification/android-committed-candidate-20261105/verify.py
```

首次依赖收集的 legacy artifact 视图歧义、sandbox缓存锁拒绝、审批服务429/超时未执行，
以及 host selector/缺席test APK收集错误均保留。改为外部组件 artifact 视图后离线收集成功；
普通审批重试后执行，无绕过；未重建已通过候选、未重复运行超时观察中的推理。
产品源码没有为这些收集错误修改。

本构建阶段当时的余项是此精确候选的 ASR/SR/Identity/face/TTS 运行、shared host与Apple候选验证、
当前候选真机与人工触摸/其他provider、异步drain、质量/持续资源/minOS、完整独立复审和许可/生产交付裁定。
没有新增benchmark样本、采音、拍照、私人媒体或发布；本次只收窄 F12 的 Android构建和LM运行缺口。

## 同一 APK 补充运行：ASR、SR、Identity、人脸与 TTS

普通 APK 始终为 `b90e7830…a64d8d`。复用上述953份未修改源码与首次构建已编译的输出，
通过已有 `odaiTestRunner` 和 `sherpaAsrFixtureDir` 属性离线生成6份单独测试 APK；
仅补入已有合成 `english.pcm`（SHA=`fc0f3f5b…39df46`），每次构建前后重新核对全部源码与普通 APK。
这些是补充测试产物，不是强制重跑全部构建，也不是替换普通候选；逐份命令/manifest/哈希已保留。
专用 API36 AVD 依次执行，设备操作前核对上一运行已终结。

| 检查 | 实际结果与范围 |
| --- | --- |
| 离线 ASR | `sherpa.onnx.whisper` 固定合成英文归一化全文匹配；不采音或保存转写 |
| ASR 生命周期 | 实际JNI decode后取消、busy/drain、同backend重入全文匹配、活动close等待/拒绝/幂等、调用方输入未变，共13项通过 |
| 页面布局 | 语音/视频/图像理解页不保留人脸预览空间，返回人脸页恢复；没有点击采集控件 |
| ESRGAN | 既有32×24双帧AAC输入生成128×96；保留/丢弃音轨、首帧解码、非零进度取消/drain/重入、close后输出lease保留/release删除通过；两输出主机完整解码、视频PTS≤1µs、4个AAC packet数据与PTS匹配 |
| Identity | 固定YuNet/SFace、已授权NASA图，检测/对齐及128维embedding、两候选注册/单独删除/整会话purge与撤销、模型lease释放通过；未校准1:1与图库仍拒绝，`qualityVerdict=unverified` |
| 普通人脸页 | 普通系统照片选择器的Browse→DocumentsUI选同一NASA文件，bundled MLKit显示1张人脸/512×512与检测框；不是人脸准确率benchmark |
| TTS | **真实失败**：`TTS initialization failed: -1`。该AVD只读查询为`No services found`、默认引擎`null`；未下载或安装引擎，不算通过 |

SR/Identity输入暂存前，4个精确测试目录均不存在；运行后已记录清单、核对原模型/图片未变并精确清理。
临时test包已卸载，恢复原无instrumentation状态；NASA公共副本已精确删除，普通App重开以释放内存图片。
原LM导入/ModelStore与4份ASR模型、相机/麦克风拒绝权限均保持，最终no_backup文件哈希集合与运行前完全相同。
不声称清除了App管理的测试AndroidKeyStore密钥；没有变更项目签名资产。
TECNO上的可用Demo未操作，没有下载、采音、拍照、新benchmark或阈值校准。

ignored `artifacts/verification/android-candidate-runtime-20261105/summary.json` SHA-256 为
`de593ced802708bfda4880493de0360205a706df246ca60370face8e14edc1e1`，**68项证据复核通过**。
它验证通过项及真实TTS失败的记录一致性，不把68项复核称为全部产品功能通过。
复核命令：

```sh
rtk proxy python3 artifacts/verification/android-candidate-runtime-20261105/verify.py
```

首次SR收集器后端ID拼写断言错误，按固定源码修正后只读取原输出，没有重跑模型；只读host展示语法错误也已保留说明。
TTS需在已有可用引擎的适当隔离设备上验同一候选；当前候选真机/人工触摸/更多provider、真实采集生命周期、
shared host/Apple精确候选、PAD与Identity校准、异步LM、质量资源/minOS及独立复审/许可/生产交付仍缺。
