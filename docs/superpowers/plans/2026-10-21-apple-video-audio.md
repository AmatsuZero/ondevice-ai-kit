# AppleVideo Audio Preservation Implementation Plan

**Goal:** 默认preserve在固定视频profile下保留兼容AAC，严格拒绝不兼容输入。
**Spec:** docs/superpowers/specs/2026-10-21-apple-video-audio-design.md
**Execution:** executing-plans inline，TDD与逐Task范围审读，既有foundation工作树；受保护文件不得暂存。

## Review Focus

- 音频无裁剪/重编码，start与duration保留；AAC不兼容与超视频range提前拒绝。
- cancelExport之后等待真实终态；失败只清理自己输出，backend排空完整workspace。
- 默认preserve的能力声明必须匹配实际引擎；discard保持原语义；Core/Video无AVFoundation依赖。

### Task 1: Mux/admission with real media tests

Files: Create apple/Sources/ODAIKitAppleVideo/AppleVideoAudioMuxer.swift；apple/Tests/ODAIKitAppleVideoTests/AppleVideoAudioMuxerTests.swift、Fixtures、fixture生成说明；Modify apple/Package.swift test resources。
Produce internal AppleVideoAudioMuxer.validateInput(source:videoDuration:) async throws、preserve(source:processedVideo:output:videoDuration:) async throws -> Int；复用AppleVideoError。
- [x] RED：真实固定fixture无音轨/AAC passthrough、范围/不兼容拒绝、已有输出保护、取消先行，运行swift test --filter AppleVideoAudioMuxerTests，Expected missing类型。
- [x] 实现AVFoundation admission/composition/passthrough/cleanup，GREEN定向及全套；范围审读/提交。

### Task 2: Default preserve engine integration

Files: Modify AppleVideoToolboxBackend.swift、VideoToolboxSREngine.swift、AppleVideoToolboxBackendTests.swift。
ConsumeTask1 mux；Produce支持preserve/discard的后端描述与真实engine输出metadata。
- [x] RED：client默认preserve可交付/释放、metadata包含preserve，Expected旧实现拒绝。
- [x] 引擎前置audio admission，内部silent文件→mux→删除intermediate，准确audioTrackCount；GREEN定向/全套，device/simulator编译；范围审读/提交。

### Task 3: Distribution/device/docs

Files: regenerate distribution pbxproj；Modify README/ROADMAP/handoff/validation；忽略目录device harness固定公开视频+合成tone。
- [x] fresh九XCFramework、Core+Video-only consumer与signed device/unsigned simulator build。
- [x] 固定fixture成功→取消/drain→同进程重入，ffprobe/完整decode/音频payload与时间线复核；独立审查可用则一次，否则pending。
- [x] Apple/tools门禁、保护hash/逐路径提交，KMP/Demo成为下一入口，其他缺口保持。
