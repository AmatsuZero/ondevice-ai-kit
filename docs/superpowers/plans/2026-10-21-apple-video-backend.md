# Apple VideoToolbox Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 注册可选AppleVideo后端，把固定SDR4×探针路径变为真实Video SDK调用。

**Architecture:** 独立ODAIKitAppleVideo，Core/Video依赖；workspace/lease拥有临时输出；actor协调真实VT/AVFoundation引擎。

**Tech Stack:** Swift5.9、VideoToolbox iOS26、AVFoundation、CoreImage、XCTest、既有Ruby分发生成器。

**Spec:** `docs/superpowers/specs/2026-10-21-apple-video-backend-design.md`

## Global Constraints

- 基础iOS15/macOS12不变；真实SR仅非模拟器iOS26+，不下载资源或添加第三方依赖。
- 首版320×180、24fps、1...48帧、identity transform、BT.709 SDR、≤32MiB；4×且显式discard；preserve拒绝。
- 输出由调用方release，close不删除已交付lease；所有失败cleanup错误显式抛出。
- 原Demo pbxproj/face-identity.md受保护；不push、修改签名或记录私人内容。

## Review Focus

1. 多次/并发release只能移除拥有的UUID目录，不能删除兄弟目录与输入；Task1测试。
2. 取消和close必须等待硬件操作以及晚到lease清理；Task2 barrier测试。
3. 模型未ready/低系统/模拟器不启动session，不自动下载；Task2/3 availability与slice验证。
4. 非零/倒退/非24fps PTS、HDR、旋转、超48帧必须失败无残留；Task3 profile及实际fixture验证。
5. Core+Video不链接新后端，新模块在低系统可装载；Task4分发依赖/weak symbol证据。

### Task 1: Owned workspace/output lease

**Files:** Create `apple/Sources/ODAIKitAppleVideo/AppleVideoOutputLease.swift`、`apple/Tests/ODAIKitAppleVideoTests/AppleVideoOutputLeaseTests.swift`；Modify `apple/Package.swift`。

**Interfaces:** Produce internal AppleVideoOutputWorkspace.init(root: URL) throws、file、remove() throws；actor AppleVideoOutputLease.init(workspace:info:)，VideoOutputLease release async throws。

- [x] RED：真实临时目录，两workspace不相同、并发release幂等、兄弟文件/输入保留、已消失目录可释放。运行 `rtk proxy swift test --disable-sandbox --package-path apple --filter AppleVideoOutputLeaseTests`，Expected missing类型。
- [x] 实现仅拥有UUID目录的workspace及actor lease，失败不标记released。
- [x] GREEN：定向与Swift全套0失败，提交 `feat: own Apple video output leases`。

### Task 2: Backend coordination

**Files:** Create `apple/Sources/ODAIKitAppleVideo/AppleVideoToolboxBackend.swift`、`apple/Tests/ODAIKitAppleVideoTests/AppleVideoToolboxBackendTests.swift`。

**Interfaces:** Consume Task1/Video contracts；Produce AppleVideoToolboxBackend actor、internal AppleVideoProcessingEngine.availability() -> CapabilityAvailability / process(request:output:progress:) async throws -> VideoOutputInfo。

- [x] RED：真实client/backend与可控引擎，unsupported preserve/scale不启动；不可用保留结构元数据；late success取消清理、busy直到drain、close等待和只关闭一次、已交付lease保留。Expected missing backend。
- [x] 实现backend单槽worker、取消/join、失败workspace清理、交付所有权；不可用平台公共init先返回真实unavailable配置，不假装处理。
- [x] GREEN：定向及Swift全套0失败，范围审读/提交 `feat: coordinate optional Apple video backend`。

### Task 3: Actual fixed-profile VT engine

**Files:** Create `apple/Sources/ODAIKitAppleVideo/VideoToolboxSREngine.swift`、`AppleVideoProfile.swift`、`apple/Tests/ODAIKitAppleVideoTests/AppleVideoProfileTests.swift`；Modify Task2公共factory。

**Interfaces:** Produce profile validate(frameIndex:timestampSeconds:width:height:colorTags:) throws；production engine配置ready及process。稳定AppleVideoError类型，内置引擎不暴露VT类型。

- [x] RED：profile拒绝超48/错误尺寸、时序和color；运行filter Expected missingprofile。
- [x] 实现asset admission、逐帧安全await、相同实验色彩与HEVC链路、writer deadline/endSession/失败清理；无下载/hash/报告。按官方SDK定义合并buffer attributes。
- [x] GREEN：macOS全套、iOS device/simulator build及固定fixture成功/取消/重入（设备条件不满足则明确unavailable，不替代通过）；提交 `feat: process bounded SDR video with VideoToolbox`。

### Task 4: Optional distribution/docs

**Files:** Modify distribution generator/package/products测试；Create `docs/validation/apple-video-backend.md`；Update README/ROADMAP/handoff。

**Interfaces:** Produce第9个ODAIKitAppleVideo XCFramework，仅Core/Video SDK依赖。

- [x] RED：真实generator检查9target、AppleVideo全源及仅Core/Video，Video仍仅Core；Expected缺target。
- [x] 加产品/target依赖，fresh9XCFramework；实际独立消费者与低系统optional链接验证；Apple/tools总门禁，Android未改则引用本轮既有证据。
- [x] 独立审查容量失败、pending（无APPROVE）；逐路径提交，保留真机/质量/最低系统/音轨/KMP/Demo门禁。
