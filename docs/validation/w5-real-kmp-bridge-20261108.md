# W5 正式 Demo 与真实 KMP bridge 生命周期验收

2026-11-08，API 36 / arm64 专用模拟器 `emulator-5584`。本次关闭的是实际公开 process 后端、生产 DemoBackend 和 KMP bridge 的生命周期切片；整体 W5 的其他 provider、广泛质量与物理 GPU/资源验收继续开放。

- 正式 MainActivity / 共享 Compose 页面通过系统 Files 选择公开红图，默认 prompt / 32 tokens 实际 `SUCCEEDED`；早期请求经过 `RUNNING → CANCELLING → CANCELLED`，控件恢复。该早期取消未观察到首文本，不用它证明活动回调后取消。
- BACK 后以 NEW_TASK | CLEAR_TASK 正常重建 Activity，重新选择同图并生成，再次 `SUCCEEDED`。没有 force-stop 或主代理杀原生进程。
- 新测试 runner 调用真实 `LiteRtLmDemoBackend → MultimodalGenerationClient → AndroidMultimodalBridge → LiteRtLm.createProcessBackend → Service/native`，单次运行 186.140 秒，最终 `status=passed`、持久收据 `completed`。
- 三轮实际结果为成功、首个非空文本更新后取消、关闭后同 ModelStore 的新 backend/client/bridge 重开成功；终态计数 `result=2 / failure=0 / cancelled=1`，更新数 `9 / 1 / 9`。两次完整输出均为 `The dominant color in this image is red.`。
- bridge 关闭后查询返回 CLOSED；全部 observer 经 close barrier 复核严格序列、累计前缀、重复/迟到回调和恰一终态；最终聚合计数通过。ModelStore.remove 后 ABSENT，输入/cache/归属 store 不再有文件，run 目录只剩 receipt.json；原生服务 PID 已无。
- 主 APK、源模型/图片/manifest/card 摘要、9 份保护文件与 Apple 生产 lock 保持。新测试包 manifest 的 target/runner、DEX 类与安装 SHA 均核对；不修改普通实体设备 App。

公开输入是 564 字节合成图。模型 SHA `2ed7bc3a…ba4010d6`，模型 revision `c03b6f60b8da6c5400b6838a2cf26420f80c0a01`，runtime `litertlm-android:0.18.1`，policy 为明确的 CPU_TEXT_GPU_VISION。模拟器视觉使用软件 GPU；成功不代表物理设备性能或通用质量。

测试实现由免费 CBC hy3 直接写候选，主代理原样集成和验收。原生独立审查提出的取消排队/实际调用混淆及 close 后复核缺失已订正；其中 File.startsWith 编译质疑由实际构建证伪。最终源码 SHA `68ece9a4b5aa78e6c84af3b6a808608f1c9d67e211e4ec552c01d1f057351644`，测试 APK SHA `c3b23c0fec988e2a1392c76406e688f4fa1149bb957414c93c9284e9ad93591f`。最终构建 18.728 秒通过；没有把前稿编译或助手声称当作设备通过。

W8 随真实负载复用现有观察器：正式 UI 的主/服务各 30 秒、27 样本完整窗口已有；本次 bridge 主进程 26 样本完整，采样 RSS 最大 184,893,440 B / 20 threads；服务 18 样本后 TARGET_EXIT，部分采样 RSS 最大 2,822,004,736 B / 22 threads。保留部分失败收据，不声称真实峰值、GPU 回收、能耗或生产预算通过。

本地证据：

- `artifacts/verification/w5-factory-demo-real-20261108-1710/{first-ui,ui-cancel,ui-reopen}-parent-acceptance.json`
- `artifacts/verification/w5-bridge-runner-20261108-1906/parent-acceptance.json`、`device-receipt.json`、`postflight.json`
- `.omx/artifacts/cbc-hy3-w5-bridge-runner-finalfix-20261108-1908/`

运行时使用构建属性 `-PenableLiteRtLm=true -PodaiTestRunner=io.ondeviceai.demo.LiteRtLmProcessBridgeInstrumentation`，并显式提供私有源 rootPath 和新的 runId。runner 拒绝复用 run 目录；不自动下载、请求模型许可、绕过 ModelStore 或重试推理。
