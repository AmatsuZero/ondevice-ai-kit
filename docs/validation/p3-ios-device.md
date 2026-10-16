# P3 iOS 真机最小闭环

日期：2026-10-16。P3 完成；这是合成空白输入的真实 Vision 推理验收，不是性能 benchmark 或完整 SDK 发布验收。

## 设备与签名

- USB 连接的 iPhone 16 Pro Max（iPhone17,2），iOS 27.0.1（24A446）。已配对、开启开发者模式且解锁。
- 复用本机已有 Apple Development 身份及 Xcode 管理的通配描述文件，Automatic 签名；未使用 `-allowProvisioningUpdates`，未创建或变更签名账户资产。
- 本地构建覆盖 `ODAI_APP_BUNDLE_ID`，避免将 App Bundle ID 误用于 UI 测试 runner。设备标识、签名身份和描述文件标识不入库。
- KMP `iosArm64` Debug framework 构建、App 签名构建、安装与启动均成功。缓存首次缺少三个已固定版本的 arm64 klib，补齐后设备 framework 可离线构建。

## 输入与结果

Debug 启动参数 `--smoke-blank` 创建 64×64 白色正向图像。只有显式标记为合成 fixture 的输入允许写入 `Documents/face-smoke.json`；正常相机帧不导出。取回后逐字段断言通过，原始合成报告见 [p3-ios-device-smoke.json](p3-ios-device-smoke.json)。

| 字段 | 实际结果 |
| --- | --- |
| runId | `p3-usb-20261016-02` |
| validationStatus / status | `device` / `success` |
| OS | `27.0.1` |
| sdkCommit | `d19ed4141caa96beab19553ab8ada39c89bffc54` |
| fixture | `synthetic-white-64x64-upright-v1` |
| backendId | `apple.vision.face` |
| orientedWidth × orientedHeight | `64 × 64` |
| faces | `[]` |

`sdkCommit` 指向此次构建使用的已提交 SDK 基线；Demo 修复及诊断导出当时在工作区内，随本记录一起提交。报告的单次 `elapsedMs` 只证明调用完成，不作为延迟性能结论。

## 验证边界

此前 iOS27 模拟器 Vision Code9 不再阻塞 P3，但仍保留模拟器缺口。该真机结果不能替代 iOS15 真机、Android 真机、离线完整场景、识别质量、持续相机热量/内存或分发验收。P4 相机修复与 UI 验收见 [记录](p4-camera-stability.md)。
