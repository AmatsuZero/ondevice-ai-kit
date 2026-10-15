# Device validation matrix

Inventory 2026-10-15 (read-only discovery): one iPhone16 Pro Max connected; OS version not yet queried. Previously paired iPhone14 Pro Max and iPhone17 Pro Max unavailable. No adb device attached. iOS26.5/27 simulators available; no iOS15 runtime/device found.

| Gate | Status | Evidence |
| --- | --- | --- |
| iOS15 deployment compilation | pass | NativeConsumer unsigned simulator build, no KMP/MLX dependency |
| Swift lifecycle/model/geometry | pass | 19 XCTest cases on macOS host; not mobile evidence |
| Vision blank image | pass on macOS | Real Vision request against synthetic blank CGImage |
| KMP iOS export + Swift implementation | pass compile | Swift FaceBridge conformance + KMP injected backend test |
| Android native unit tests | pass JVM/lint/build | 11 JVM tests; lint and Demo/native APK build pass; published Maven consumer build pass; bundled detector runtime remains device gate |
| iOS15 device launch/camera/offline | missing-device | Cannot substitute modern simulator |
| Android API26 device launch/camera/offline | missing-device | adb devices empty |
| Connected modern iPhone launch | not-run | Signing/build/install evidence required |
| Modern Android launch | missing-device | adb devices empty |
| Sustained camera/thermal/memory | not-run | Requires device run and recorded input conditions |

No stored device UDIDs or input photos in this report. Update rows only with fresh commands and actual results.

用户已授权直接使用本地真机做 device-only 验证；P3 将发现当前设备与现有签名后执行。授权不改变上表 not-run 状态。
