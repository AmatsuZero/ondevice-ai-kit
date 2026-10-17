# Device validation matrix

Inventory 2026-10-16: USB-connected iPhone 16 Pro Max (iPhone17,2), iOS 27.0.1 (24A446), and TECNO KM9, Android 15/API 35. Existing signing reused for the earlier iOS device build; no matching profile is currently available for the UI runner. iOS26.5/27 simulators available; no iOS15 runtime/device found.

| Gate | Status | Evidence |
| --- | --- | --- |
| iOS15 deployment compilation | pass | NativeConsumer unsigned simulator build, no KMP/MLX dependency |
| Swift lifecycle/model/geometry | pass | 25 XCTest cases on macOS host; not mobile evidence |
| Vision blank image | pass on macOS | Real Vision request against synthetic blank CGImage |
| KMP iOS export + Swift implementation | pass compile | Swift FaceBridge conformance + KMP injected backend test |
| Android native unit tests | pass JVM/lint/build | 20 JVM tests (SDK18 + Android photo-helper2); shared ViewModel4 on iOS simulator; lint and Demo/native APK build pass; published Maven consumer build pass; bundled detector runtime remains device gate |
| iOS15 device launch/camera/offline | missing-device | Cannot substitute modern simulator |
| Android API26 device launch/camera/offline | missing-device | No API26 device; current ADB device is API35 |
| Connected modern iPhone launch + Vision blank | pass | Signed build/install/launch; real 64x64 synthetic input returns empty faces; [P3 evidence](p3-ios-device.md) |
| Connected modern iPhone frozen face benchmark | pass bounded | SDK commit `acf3573`; iPhone 16 Pro Max / iOS 27.0.1, 5 warmup + 30 inference, `apple.vision.face`; raw/report JSON retained under ignored `artifacts/P5` |
| Modern iPhone camera controls + foreground resume | pass bounded | 2 real-device UI tests; explicit stop survives activation; [P4 evidence](p4-camera-stability.md) |
| Modern Android launch | pass bounded | TECNO KM9 API35 Demo installed and launched; [P4 evidence](p4-camera-stability.md) |
| Sustained camera/thermal/memory | Android API35 bounded pass; iOS unverified | TECNO KM9 前置相机约两分钟，4 个 30 秒样本持续显示人脸结果；PSS 260815→231722 KiB，热状态均为 0；不覆盖峰值、能耗或长期背压，见 [P4 evidence](p4-camera-stability.md) |

No stored device UDIDs or input photos in this report. Update rows only with fresh commands and actual results.

用户已授权直接使用本地真机做 device-only 验证。P3 与 P4 部分实测不改变最低系统和持续热量/内存的缺口。

2026-10-16：用户确认 iOS15 与 Android API26 真机暂不可用；当前 Android API35 设备仅验证现代平台。最低系统验收挂起，待对应设备可用后继续。已登记 [DEVICE-IOS15 / DEVICE-ANDROID TODO](../ROADMAP.md#等待设备的-todo)，不以当前构建、模拟器或较新设备关闭这些项。
