# 后端选择契约验证

更新时间：2026-10-17。

本阶段把现有人脸检测后端接入统一的 `Default` / `Explicit(backendId)` 选择入口。能力 Client 只分派到一个已注册后端，不会因显式后端不可用而静默回退到默认后端。注册、可用性查询和请求选择都保留稳定的能力 ID 与后端 ID。

## 已实现边界

- Apple：`ODAIKitFace.FaceDetectionClient`，基础后端可注册 `apple.vision.face`；`FaceDetectionClientError` 区分未注册、不可用和不支持选项。
- Android：`io.ondeviceai.face.FaceDetectionClient`，基础后端可注册 `android.mlkit.face.bundled`；`FaceDetectionClientError` 使用同名错误类别。
- 两端都拒绝重复后端、错误能力 ID 和未注册默认后端；关闭 Client 后不再接受选择，并幂等关闭已注册后端。
- `fixtures/contracts/backend-selection.json` 固定默认、显式、未注册、不可用和不支持选项的共享场景。

## 验证

- `swift test --package-path apple`：Apple 全套 **27/27** 通过，其中新增选择契约 **2/2**。
- `./gradlew :android:face:testDebugUnitTest --tests io.ondeviceai.face.FaceDetectionClientTest --offline`：Android 选择契约 **2/2** 通过。

该包验证的是注册和选择语义，不宣称新增第三方后端、云端调用或身份识别能力已交付。Demo 的 KMP 桥接迁移和每项能力的独立后端分发仍按后续工作包推进。

## KMP 与 Demo 桥接补充

KMP 的 `FaceBridge.detect` 和 `FaceViewModel.detect`/`detectFrame` 已接收 `BridgeBackendSelection`。`backendId=null` 映射固定平台默认后端；非空 ID 严格映射原生 Client 的显式选择。Android 桥接使用 `FaceDetectionClient`，iOS 桥接使用 Swift `FaceDetectionClient`；未注册 ID 由 Client 返回错误，不在桥接层选择其他后端。Demo 当前仍只注册各自的基础后端，尚无可供最终用户选择的第二个生产后端或 UI 控件。

验证：新增 KMP ViewModel 传参测试先因缺少选择 DTO/接口失败，接入后 `:demo:shared:iosSimulatorArm64Test` 与 `:kmp-binding:iosSimulatorArm64Test` 通过；Android Demo 单元测试及 APK 构建通过；`scripts/check-swift-bridge.sh` 通过；重新链接 KMP iOS framework 后，iOS Demo unsigned simulator build 通过。上述构建验证不替代真机行为或依赖裁剪验收。
