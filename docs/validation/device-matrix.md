# Device validation matrix

Inventory 2026-10-17: USB-connected iPhone 16 Pro Max (iPhone17,2), iOS 27.0.1 (24A446), and TECNO KM9, Android 15/API 35. The user configured signing for the Demo and UI runner; the existing configuration successfully ran the sustained camera test. Signing changes remain local. No iOS15 runtime/device found.

| Gate | Status | Evidence |
| --- | --- | --- |
| iOS15 deployment compilation | pass | NativeConsumer unsigned simulator build, no KMP/MLX dependency |
| Swift lifecycle/model/geometry | pass | 33 XCTest cases on macOS host (P5 rerun); not mobile evidence |
| Vision blank image | pass on macOS | Real Vision request against synthetic blank CGImage |
| KMP iOS export + Swift implementation | pass compile | Swift FaceBridge conformance + KMP injected backend test |
| Android native unit tests | pass JVM/lint/build | 20 JVM tests (SDK18 + Android photo-helper2); shared ViewModel4 on iOS simulator; lint and Demo/native APK build pass; published Maven consumer build pass; bundled detector runtime remains device gate |
| iOS15 device launch/camera/offline | missing-device | Cannot substitute modern simulator |
| Android API26 device launch/camera/offline | missing-device | No API26 device; current ADB device is API35 |
| Connected modern iPhone launch + Vision blank | pass | Signed build/install/launch; real 64x64 synthetic input returns empty faces; [P3 evidence](p3-ios-device.md) |
| Connected modern iPhone current face benchmark | pass bounded | SDK commit `927b7749`; iPhone 16 Pro Max / iOS 27.0.1, 5 warmup + 30 inference, `apple.vision.face`, p50/p95 `2.432/3.065 ms`; raw/report JSON retained under ignored `artifacts/P5/current` |
| Connected modern Android current face benchmark | pass bounded | SDK commit `927b7749`; TECNO KM9 / Android 15/API 35, 5 warmup + 30 inference, `android.mlkit.face.bundled`, p50/p95 `10.853538/18.088231 ms`; raw/report JSON retained under ignored `artifacts/P5/current` |
| Modern iPhone camera controls + foreground resume | pass bounded | 2 real-device UI tests; explicit stop survives activation; [P4 evidence](p4-camera-stability.md) |
| Modern iPhone photo selection/cancellation + Settings entry | pass bounded | iPhone 16 Pro Max / iOS 27.0.1: five routine interaction tests passed after handling the Photo Picker onboarding card; includes selected image reaching Vision, but excludes EXIF 2/6/7 and actual permission changes in Settings; [P4 evidence](p4-camera-stability.md) |
| Modern iPhone actual camera permission round trip | pass manual + post-recovery automated check | User confirmed closing camera permission in App Settings, observing the recovery entry, granting permission again and restarting the camera; subsequent real Vision camera test 1/1 passed (10.156 s). The automated Settings-switch draft failed and was removed; [P4 evidence](p4-camera-stability.md) |
| Modern Android launch | pass bounded | TECNO KM9 API35 Demo installed and launched; [P4 evidence](p4-camera-stability.md) |
| Speech distribution consumers | pass compile/lint | P6 Apple speech/face binary consumers and Android contract/TTS/ASR published flavors compile independently; no device runtime claim; [speech evidence](speech-backends.md) |
| System TTS on modern devices | pass bounded | iPhone 16 Pro Max / TECNO KM9: success, cancel, close probes; offline behavior and quality unmeasured; [speech evidence](speech-backends.md) |
| Android on-device ASR | unavailable assets | TECNO KM9/API35 service available, en-US error 13 -> MISSING_ASSETS; transcription/cancel/close runtime gates remain unverified |
| iOS ASR fixed-file probe | bounded device pass | iPhone 16 Pro Max/iOS27.0.1: forced on-device fixed-file recognition, immediate cancellation (`platformCancelled=true`, task completed, resource release confirmed) and reentry passed; delayed cancellation is separately recorded as incomplete. Microphone, audio session and SDK integration remain unverified; [probe evidence](ios-asr-file-probe.md) |
| iOS natural-video SR probe | partial device pass | iPhone 16 Pro Max/iOS27.0.1: VideoToolbox SR and Core Image bicubic both processed the fixed 48-frame natural fixture; paired output metadata/quality JSON, cancellation during SR with no new MOV, and successful processing after app relaunch are recorded in [P7 evidence](video-sr-backends.md). Model transfer function, subjective quality, Android and SDK integration remain unverified |
| Sustained camera/thermal/memory | Android API35 bounded pass; iOS bounded pass | TECNO KM9 前置相机约两分钟，4 个 30 秒样本持续显示人脸结果；iPhone 16 Pro Max 两分钟 UI 用例 4 个 30 秒窗口持续产生成功回调，Activity Monitor 覆盖 96.378 秒、physical footprint 约 79.6–145.3 MB，thermal state 为 Nominal；两端均不覆盖峰值、GPU、能耗、长期背压或完整资源释放，见 [P4 evidence](p4-camera-stability.md) |

No stored device UDIDs or input photos in this report. Update rows only with fresh commands and actual results.

用户已授权直接使用本地真机做 device-only 验证。P3 与 P4 部分实测不改变最低系统和持续热量/内存的缺口。

2026-10-16：用户确认 iOS15 与 Android API26 真机暂不可用；当前 Android API35 设备仅验证现代平台。最低系统验收挂起，待对应设备可用后继续。已登记 [DEVICE-IOS15 / DEVICE-ANDROID TODO](../ROADMAP.md#等待设备的-todo)，不以当前构建、模拟器或较新设备关闭这些项。
