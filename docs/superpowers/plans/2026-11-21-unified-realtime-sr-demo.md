# Unified Realtime SR Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Android 与 iOS 的真实边播边视频超分能力接入同一个 CMP Demo，统一控制、状态、回退和页面生命周期。

**Architecture:** CMP 只负责共享控制和状态展示；像素链路留在平台侧。Android 复用现有 MediaCodec/GLES realtime-SR renderer，通过嵌入式 host 挂入 CMP 的渲染 slot；iOS 使用 AVPlayer/AVPlayerItemVideoOutput 的 NV12 帧和 Metal SR renderer，复用已验证的 CuNNy/Spatial/System 路由与 deadline/presented 语义。两端都把用户选择、实际上屏后端和原画回退分开记录。

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android MediaCodec/SurfaceView/GLES, Swift/AVFoundation/Metal/VideoToolbox, Xcode project generator.

**Spec:** 用户已批准的统一 CMP Demo 与真实边播边超分接入方向，当前页面和既有 realtime-SR 实现为事实来源。

**Execution record:** [最终验证记录](../../validation/unified-realtime-sr-demo-20261121.md)。原生只读复审已给出 ACCEPT，无新增 correctness blocker；验收边界与原始失败记录保持。

**Rulings:** 共享 JVM/KMP snapshot 测试只证明 Host 状态读取，CMP 重组另以真实双端 UI 验收证明。Android 生命周期用真实 session instrumentation，避免引入 mock 框架。iOS deadline/epoch 用纯 Swift gate 与 UI 模式/EOS 验证；无法稳定制造的真实 GPU/纹理/提交失败保留为明确验证缺口，不宣称硬件故障测试通过。生成器改为打开既有工程并复用引用，避免新源集成重写其他 target/UUID。

## Global Constraints

- 保留 CMP 统一页面；平台原生代码只提供渲染面和会话，不另起控制页。
- 不引入整套 UHPlayerSDK；iOS 只提取独立 Demo 所需的 Metal SR renderer/engine 代码。
- Android 支持 ORIGINAL/AUTO/CUNNY/SPATIAL；不把请求后端、swap 或 GPU completion 当作实际上屏证据。
- iOS 优先 SYSTEM → SPATIAL → CUNNY → ORIGINAL；系统后端不可用时必须清楚回退原因。
- 不修改用户现有 dirty 的 SDKDistribution scheme、IDE 文件、`.xcodebuildmcp` 和 `AGENTS.md`。
- 保留现有独立 Android realtime-SR Activity 行为与测试。

## Review Focus

- 用户切换后端时迟到回调是否会覆盖新代次：用 generation/epoch 丢弃旧帧。
- 超分计算赶不上显示 deadline 时是否原画播放且状态显示 fallback：用实际 display/presented 事件验证。
- iOS 模拟器和旧系统没有 System SR 时是否仍能播放原画或 Metal 后端。
- Android 默认 Demo 是否真正打入 `libsrtiming.so`，而不是永远等待确认上屏。
- 离开 capability、后台/前台和销毁 surface 后是否停止播放器、display link、worker 与回调。

### Task 1: Shared CMP realtime playback contract

**Files:**
- Modify: `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/UnifiedDemoScreen.kt`
- Modify: shared tests adjacent to `demo/shared/src/commonTest`

**Interfaces:**
- `PlaybackEnhancementDemoActions` exposes immutable state getters for `hasVideo`, `isPlaying`, `status`, and `inputName`, with default values for existing hosts.
- CMP polls a compact snapshot and renders selected mode, presented backend, fallback/original state, input and playback status.

- [x] Add snapshot regression tests and actual UI verification that the page follows Host state.
- [x] Extend the interface with backward-compatible defaults and a bounded polling/state refresh.
- [x] Remove copy that calls iOS enhancement a Core Image sharpening preview.
- [x] Run shared tests and debug framework link.

### Task 2: Android embedded host and native build wiring

**Files:**
- Create/modify: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/PlaybackEnhancementHost.kt`
- Modify: `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/MainActivity.kt`
- Modify: `demo/androidApp/build.gradle.kts`
- Add/modify: Android host tests and CMake wiring under `demo/androidApp/src/main/cpp`

**Interfaces:**
- Host implements the shared playback actions, owns a `SurfaceView`/session, and exposes a CMP render slot.
- Existing `PlayerActivity` remains available and its source/tests are unchanged except for reusable lifecycle hooks.

- [x] Write state regressions, real session lifecycle instrumentation and CMP UI checks for mode/no-source/leave/background.
- [x] Extract or wrap the validated realtime session without duplicating decoder/renderer logic.
- [x] Move picker/play/pause/mode operations behind the CMP actions while retaining the native surface only.
- [x] Add the existing `egl_timing.cpp`/CMake target and verify each packaged ABI contains `libsrtiming.so`.
- [x] Run Android unit tests, lint, and debug APK build.

### Task 3: iOS standalone Metal SR renderer and host integration

**Files:**
- Create: `demo/iosApp/PlaybackEnhancementMetalRenderer.swift`
- Create: `demo/iosApp/PlaybackEnhancementShaders.metal`
- Modify: `demo/iosApp/PlaybackEnhancementHost.swift`
- Modify: `demo/iosApp/OnDeviceAIDemoApp.swift`
- Modify: `scripts/generate-xcode-projects.rb` and generated Demo PBX entries as required

**Interfaces:**
- Renderer accepts full-range NV12 frames with strict BT.709 metadata and returns a rendered drawable plus actual presented backend callback.
- Host maps renderer state to the shared playback actions and remains responsible for AVPlayer/audio/lifecycle only.

- [x] Add focused frame-gate deadline/epoch tests and backend/EOS/UI checks; document unsupported input and hardware fault-injection coverage limits.
- [x] Port only the minimal CuNNy/Spatial/System engine and shader pieces from fixed upstream commit `7d40dfe94f007c808c30cdb3b8004444f0405cdc`; preserve required license/provenance.
- [x] Replace BGRA + `CISharpenLuminance` with NV12 Metal rendering and presented-backend callbacks.
- [x] Wire leave/background/foreground cleanup and update the status copy.
- [x] Regenerate the Xcode project and verify simulator compilation.

### Task 4: Integration verification

- [x] Run shared tests/framework link, Android unit/lint/build, and iOS simulator build/tests.
- [x] On the dedicated iOS simulator, import a synthetic BT.709 clip, play original/auto/Metal modes, and capture actual fallback/presented state.
- [x] On Android emulator or available device, verify CMP controls, embedded surface, picker, JNI timing library and leave cleanup.
- [x] Review the complete diff and confirm protected files are unchanged; native final review ACCEPT, with device/performance gaps recorded.
- [x] Commit the feature branch and record the resulting Git state in the local acceptance receipt.
