# Apple Exact Identity Index Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. User authorized autonomous continuation; implement inline with one bounded independent review.

**Goal:** Enable Apple's encoded-image 1:N query through an exact cosine index.

**Architecture:** Public builder creates bounded versioned Data. Internal search binds it to retained authorized embeddings; FaceIdentification keeps all existing gates and fixture compatibility.

**Tech Stack:** Swift/Foundation, existing Identity/ModelStore, no dependencies.

**Spec:** ../specs/2026-11-04-apple-exact-identity-index-design.md

## Global Constraints

Protect nine unrelated dirty files. No runtime/model/data acquisition, fabricated VERIFIED policy or plaintext persistence. No push/merge/release. Bounds 4096 templates/dimensions and 65536 canonical identity bytes; float32/little/cosine/higher-is-better.

## Review Focus

Malformed/stale/reordered vectors must fail closed against leases. Foreign full embedding spaces must fail. Extreme finite Float values must remain finite after Double scoring. Cancelled operations must not publish. Encoded probes must not accept fixture payloads.

### Task 1: Exact codec and query integration

Files: create `apple/Sources/ODAIKitIdentity/ExactCosineIdentityIndex.swift`, `apple/Tests/ODAIKitIdentityTests/ExactCosineIdentityIndexTests.swift`; modify `FaceIdentification.swift` and `FaceIdentificationTests.swift` under their existing source/test directories, and register the new source in `apple/OnDeviceAIKit.xcodeproj/project.pbxproj`.

Interfaces: `public static func build(_ embeddings: [IdentityEmbedding], stopSignal: IdentityStopSignal) throws -> Data`; internal `scores(_ bytes: Data, probe: IdentityEmbedding, retained: [IdentityEmbedding], stopSignal: IdentityStopSignal) throws -> [Double]`.

- [x] Write codec and public-engine tests: cosine order/subject-max/topK, zero norm, malformed/truncated/foreign/stale payload, maximum/minimum finite Float and cancellation; encoded probe rejects fixture payload.
- [x] Run `rtk proxy swift test --package-path apple --filter ExactCosineIdentityIndexTests` and targeted engine tests; observe absent builder/query support RED.
- [x] Implement codec and switch exact/fixture scoring based on internal probe provenance. Keep all final gates intact.
- [x] Run targeted tests then `rtk proxy swift test --package-path apple`; read and record actual counts/failures. Build release Identity target and diff check.
- [x] Request bounded independent review; handle material findings in one RED/GREEN pass. Update validation/handoff/ledger, verify protected hashes and local commit only.
