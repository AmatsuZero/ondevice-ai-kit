# Device validation matrix

Inventory 2026-10-16: USB-connected iPhone 16 Pro Max (iPhone17,2), iOS 27.0.1 (24A446). Existing signing reused for this project. No adb device attached. iOS26.5/27 simulators available; no iOS15 runtime/device found.

| Gate | Status | Evidence |
| --- | --- | --- |
| iOS15 deployment compilation | pass | NativeConsumer unsigned simulator build, no KMP/MLX dependency |
| Swift lifecycle/model/geometry | pass | 25 XCTest cases on macOS host; not mobile evidence |
| Vision blank image | pass on macOS | Real Vision request against synthetic blank CGImage |
| KMP iOS export + Swift implementation | pass compile | Swift FaceBridge conformance + KMP injected backend test |
| Android native unit tests | pass JVM/lint/build | 20 JVM tests (SDK18 + Android photo-helper2); shared ViewModel4 on iOS simulator; lint and Demo/native APK build pass; published Maven consumer build pass; bundled detector runtime remains device gate |
| iOS15 device launch/camera/offline | missing-device | Cannot substitute modern simulator |
| Android API26 device launch/camera/offline | missing-device | adb devices empty |
| Connected modern iPhone launch + Vision blank | pass | Signed build/install/launch; real 64x64 synthetic input returns empty faces; [P3 evidence](p3-ios-device.md) |
| Modern iPhone camera controls + foreground resume | pass bounded | 2 real-device UI tests; explicit stop survives activation; [P4 evidence](p4-camera-stability.md) |
| Modern Android launch | missing-device | adb devices empty |
| Sustained camera/thermal/memory | not-run | Requires device run and recorded input conditions |

No stored device UDIDs or input photos in this report. Update rows only with fresh commands and actual results.

用户已授权直接使用本地真机做 device-only 验证。P3 与 P4 部分实测不改变最低系统、Android 设备、持续热量/内存的缺口。
