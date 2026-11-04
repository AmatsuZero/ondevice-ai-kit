# Android LiteRT-LM 普通 UI 验证

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
