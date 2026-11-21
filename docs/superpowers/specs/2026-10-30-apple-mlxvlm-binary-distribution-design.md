# Apple MLXVLM binary distribution design

Date: 2026-10-30. Status: design for F09 implementation. Current branch: feat/sdk-foundation.

## Intent

Deliver a relocatable, versioned binary distribution for the optional Apple MLXVLM product, with an independent consumer that does not compile the enhanced package or its transitive dependencies from source. This is separate from the already verified portable source consumer and from the ten base Apple XCFrameworks. F09 remains open until the artifact and consumer gates below pass.

## Current evidence and constraints

- The optional package is apple-mlx/Package.swift: ODAIKitAppleMLXVLM targets iOS 18+ and macOS 15+ and depends on the base Core, Multimodal and ModelStore products plus MLXVLM, MLXLMCommon, MLX and Tokenizers.
- The current image/video source consumer archive succeeds on generic iOS in Release/library-evolution mode only with isolated diagnostic edits: a target-scoped OrderedCollections compiler flag and five mlx-swift-lm unknown-enum switch additions. Those edits are in ignored dependency copies, not the production lock or upstream package.
- Its archive contains an App and object files, not an enhanced framework. The current interface inventory reports 20 local Swift modules. The closure also includes C module maps and generated resource bundles; model JSON and default.metallib were observed inside the App archive.
- The production lock remains SHA-256 e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858. Do not change it as a shortcut. Preserve the current dirty signing project, identity draft, camera probes and existing artifacts.
- Base products continue to target iOS 15/macOS 12. The enhanced binary remains optional and has iOS 18/macOS 15 floors. An iOS Simulator build proves link/interface availability, not MLXVLM inference.

## Artifact contract

The binary distribution consists of an immutable manifest plus XCFrameworks for the full exported module closure required by an independent consumer. The manifest records product/module identity, source revisions, compiler/Xcode version, iOS device and simulator slices, Mach-O architecture, public interface SHA-256, C module maps, resource bundle paths and SHA-256, artifact SHA-256 and license/source attribution. Every local Swift module in the public interface closure must either be shipped as a binary module or be shown by the actual binary consumer to be supplied by an allowed system module. Do not assume a facade framework hides transitive Swift imports.

The first acceptance target is an unsigned local artifact set. Remote ZIP/checksum publication is a separate final gate. Each XCFramework must contain a real framework or library slice for generic iOS device and iOS Simulator, with no source checkout on the consumer dependency path. Build all separately distributed Swift modules with library evolution enabled and verify their emitted interfaces. The independent consumer must import ODAIKitAppleMLXVLM and exercise image and video request typecheck, link, and runtime resource lookup. A physical device smoke may use the existing public fixture and fixed local model, but must not read private media or download a model.

Required resources are explicit: apple-mlx model JSON with canonical SHA-256 c074e693cc0fb9cfb19d6e7a393ffb113108e88d1489a387e8cb06d309602d54, the MLX Metal library with observed SHA-256 953544ddd003eaef4f0f03c5dce821c2911704288a2ffebd231de7dd60383ee2, and every other bundle demanded by the actual runtime. The consumer tests their runtime lookup after packaging, not only file presence.

## Approach choice

1. **Chosen: explicit binary closure with a manifest and independent consumer.** It makes module, slice and resource obligations inspectable. A small vertical probe starts with ODAIKitAppleMLXVLM plus its immediate base dependencies, then extends to the complete closure. A partial probe is diagnostic only; it is never published or marked F09 complete.
2. A single opaque aggregate framework could reduce the number of artifacts, but current public textual interfaces import other Swift modules and the project has no proven aggregate build path. Reconsider only if a consumer demonstrates those imports are fully encapsulated without exposing new source dependencies.
3. Source distribution remains available and verified, but it is not binary distribution and does not close F09.

## Execution phases and gates

1. Freeze a source/lock/module/resource inventory from an exact committed tree. Read committed bytes rather than the working tree, so unrelated protected dirty files remain untouched. Reject mismatched lock or source inventory before producing a release candidate. Keep diagnostic dependency patches in an isolated work directory with their hashes, never in the production checkout.
2. Prove a supported build configuration for OrderedCollections and EventSource on the actual compiler. Confirm the five MLXLM enum switch fixes have a durable upstream or vendored policy with explicit source revision and license review. The current ignored patches are only an experiment.
3. Generate real device and simulator framework slices and create XCFrameworks. Verify Mach-O architecture, minimum OS, library-evolution interfaces, complete module imports, C module maps and resource bundle locations. Stop on the first missing closure member; do not create a misleading partial release ZIP.
4. Generate a clean consumer with binary-only dependencies. Build for generic iOS and iOS Simulator, import image/video APIs and test resource lookup. The consumer build graph and compiled source file list must exclude apple-mlx and third-party source checkouts. A device runtime smoke is separately recorded.
5. After every local gate passes, create a root-layout ZIP, compute its SwiftPM checksum and test a second consumer that resolves the ZIP via a binaryTarget manifest. Publishing a URL, release, tag or package registry entry is a separate authorized action.

## Failure and evidence policy

Any missing slice, unresolved import, interface verification error, C module map, model JSON, Metal library, runtime lookup, independent consumer build or source-free dependency proof keeps F09 open/not-ready. The result report distinguishes source App archive, framework slice, XCFramework, local binary consumer, remote package resolution and device runtime. Each claim cites original commands, exit codes, file hashes and toolchain. No simulator or base-product result substitutes for enhanced device inference or binary distribution.

## References

- Apple: https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages
- Apple: https://developer.apple.com/documentation/xcode/creating-a-multi-platform-binary-framework-bundle
- SwiftPM binaryTarget and resources: https://docs.swift.org/package-manager/PackageDescription/PackageDescription.html
- Swift library evolution: https://www.swift.org/blog/library-evolution/
- Local evidence: docs/validation/apple-mlxvlm-distribution.md and artifacts/verification/f09-current-source-20261030/summary.json
