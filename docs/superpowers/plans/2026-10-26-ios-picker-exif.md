# iOS PHPicker EXIF validation plan

2026-10-27 用户授权更新：已验证改动可分批提交并同步 `origin` + `jihu` 的现有 foundation 分支；覆盖本计划此前的暂不提交约束。受保护文件和未完成线程取证保留本地；不包含合并、tag 或发布。

> Execute inline with superpowers:executing-plans. Existing approved P4 scope and autonomous continuation authorize this bounded validation work; no new product capability or approval gate is introduced.

**Goal:** Exercise real PHPicker selection of synthetic EXIF2/6/7 inputs through the production DemoController and verify upright dimensions plus mirrored/rotated pixels.

**Architecture:** Generate an isolated simulator-only SwiftUI host that compiles the actual DemoController and existing dependencies. Observe its published normalized image after real picker selection. Each run imports one public black/white JPEG into a newly created disposable simulator. The runtime also contains six Apple sample assets; verify the imported original by SHA256 and require its exact unique accessibility label via `ODAI_PICKER_FIXTURE_LABEL`, never a positional fallback. No injected PHPicker callback or duplicate decode implementation.

**Tech Stack:** Swift/ImageIO/UIKit/SwiftUI, XCTest UI, Ruby/xcodeproj, simctl.

**Spec:** docs/ROADMAP.md P4 and docs/validation/p4-camera-stability.md remaining EXIF2/6/7 gate.

## Constraints and approach

- Preserve all dirty files and both protected hashes. No commit, push, signing change, dependency change, or real-device photo access.
- Existing host-only ImageIO checks do not prove picker behavior. Adding product debug pixel exports would unnecessarily expand production code. A separate validation host uses the real controller and exposes only synthetic-image measurements.
- The probe measures controller output before/independently of Vision success. Simulator Vision failure is not a successful inference; a passing simulator result does not close the physical-device gate.
- Photo libraries may normalize encoded representation during import/export. The known source EXIF plus final output proves end-to-end orientation, not preservation of the EXIF tag inside the provider's temporary file.
- Stop and retain raw failure evidence if the UI runner fails before the test body. Do not blindly retry a blocked physical-device runner.

## Review focus

- Wrong asset: require exactly one match for the externally verified synthetic fixture label and use only newly created simulators. Missing label configuration fails before opening the picker.
- Mirror ignored: assert literal four-corner colors, not only width/height.
- Rotation applied twice: assert 80x40 for EXIF2 and40x80 for6/7 plus ordered corner values.
- Direct-path false positive: host button invokes DemoController.selectPhoto; only a real picker tap can publish the measured image.
- Environment cleanup: only shut down/delete simulator IDs created by this run; retain fixture metadata/hash and xcresult evidence.

## Task 1: Isolated picker acceptance harness

- [x] Create probes/ios-photo-picker/MakeOrientationFixtures.swift: generate80x40 left-black/right-white JPEGs with explicit EXIF2/6/7, verify stored metadata, print paths/hashes.
- [x] Create probes/ios-photo-picker/PhotoPickerHost.swift: actual DemoController plus select button and normalized-image width/height/four-corner grayscale observations. No alternate decode path.
- [x] Create probes/ios-photo-picker/PhotoPickerOrientationUITests.swift: real picker selection; literal signatures are2=80x40:white,black,white,black;6=40x80:black,black,white,white;7=40x80:white,white,black,black.
- [x] Create scripts/generate-photo-picker-tests.rb: refuse existing output, create independent host/UI test targets with production controller sources, local native packages, and existing simulator KMP framework.
- [x] Build the host/tests, create isolated simulators, import one fixture each, and execute each filtered test. This validates existing behavior; do not invent a RED unless a product assertion actually fails.
- [x] Save original results/fixture metadata/source hashes and distinguish simulator picker results from physical picker+Vision acceptance. Run git diff --check and verify protected hashes.

Only if a product defect is reproduced: preserve the failing evidence, repair the specific production behavior with regression coverage, and run its relevant build/tests before claiming a fix. Otherwise keep this package validation-only.
