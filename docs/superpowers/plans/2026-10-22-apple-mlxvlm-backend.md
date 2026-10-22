# Optional Apple MLXVLM Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an isolated iOS 18+ MLXVLM backend for the existing multimodal client without changing the base package's deployment floors or dependency graph.

**Architecture:** A new `apple-mlx` Swift package owns the MLX imports and exposes `ODAIKitAppleMLXVLM`. The backend adapts an acquired `ModelLease` to the existing `MultimodalGenerationBackend`, joins cancelled generation before clearing the session and releasing the lease, and uses only the fixed local SmolVLM2/MLX revisions.

**Tech Stack:** Swift 6, Swift Package Manager, iOS 18, MLXVLM/MLXLMCommon/MLXHuggingFace, Tokenizers, local ODAIKitMultimodal and ODAIKitModelStore.

**Spec:** `docs/superpowers/specs/2026-10-22-apple-mlxvlm-backend-design.md`

## Global Constraints

- Root `apple/Package.swift` remains free of MLX dependencies and keeps iOS 15/macOS 12 products unchanged.
- The enhancement package targets iOS 18 and uses exact revisions `6e338e5df49424fc0db7c6d58184d622f92ed18a` and `b38443e44d93eca770f2eb68e2a4d0fa100f9aa2`.
- Only the verified SmolVLM2 500M manifest and `mlx.smolvlm2-500m` backend are accepted.
- Availability never loads, installs, or reads model/image data.
- Cancellation and close wait for the MLX generation task before lease release; no fallback or download.

## Review Focus

- Consumer cancellation after a stream update must not release the lease before the MLX task exits.
- A backend error that arrives after cancellation must not replace the cancelled terminal state.
- A manifest with the right files but the wrong runtime/model identity must remain unavailable.
- Repeated concurrent `close()` calls must share one lease release and one drain.
- The separate package must not make a Core/Multimodal-only consumer resolve MLX.

---

### Task 1: Isolated package and public backend contract

**Files:**
- Create: `apple-mlx/Package.swift`
- Create: `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMBackend.swift`
- Create: `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMGenerationSession.swift`
- Create: `apple-mlx/Sources/ODAIKitAppleMLXVLM/SmolVLM2Model.swift` and canonical manifest resource
- Create: `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/MLXVLMBackendTests.swift`

**Interfaces:**
- Consumes: `MultimodalGenerationBackend`, `ModelLease`, `ModelManifest`.
- Produces: `MLXVLMBackend(lease:)`, stable backend id, availability and lifecycle surface.

- [ ] **Step 1: Write failing tests** for fixed identity admission, unavailable availability without invoking a loader, and repeated close.
- [ ] **Step 2: Run** `rtk proxy swift test --package-path apple-mlx --filter MLXVLMBackendTests`; Expected: compile failure because package/backend are absent.
- [ ] **Step 3: Implement** the separate manifest-gated backend shell with an injected loader protocol and one-shot close state.
- [ ] **Step 4: Run** the focused tests; Expected: PASS.
- [ ] **Step 5: Commit** `feat: add isolated Apple MLXVLM backend shell`.

### Task 2: Fixed MLXVLM runtime adapter

**Files:**
- Modify: `apple-mlx/Package.swift`
- Modify: `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMBackend.swift`
- Modify: `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/MLXVLMBackendTests.swift`

**Interfaces:**
- Consumes: Task 1 internal runtime protocol `generate(directory:request:update:) async throws -> String`, real ModelLease and fixed local MLX checkout API.
- Produces: request-budgeted owned `generateTask` streaming, cumulative ordered updates, cancellation drain, and lease-safe close.

- [ ] **Step 1: Add failing adapter tests** for `maxOutputTokens`, update ordering, cancellation drain before lease close, and ordinary error preservation.
- [ ] **Step 2: Run focused tests**; Expected: FAIL on missing runtime adapter behavior.
- [ ] **Step 3: Implement** `VLMModelFactory` local loading, tokenizer loader, `GenerateParameters(maxTokens:)`, bounded image snapshot, explicit generation-task join, and close serialization.
- [ ] **Step 4: Run focused and package tests**; Expected: PASS, no skipped tests.
- [ ] **Step 5: Commit** `feat: adapt fixed MLXVLM generation lifecycle`.

### Task 3: Consumer and evidence gates

**Files:**
- Create: `examples/ios-mlxvlm-native/MLXVLMConsumerApp.swift`
- Create: `docs/validation/apple-mlxvlm-backend.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/HANDOFF-2026-10-20-speech-demo-bridge.md`

**Interfaces:**
- Consumes: Task 2 public product and the verified local fixture/model directory.
- Produces: compile-only separate consumer, local verification record, and next-stage handoff.

- [ ] **Step 1: Write consumer/gate assertions** that importing `ODAIKitAppleMLXVLM` does not alter the base package's products.
- [ ] **Step 2: Run the gate before implementation**; Expected: consumer and validation links fail because files/product are absent.
- [ ] **Step 3: Add the minimal consumer, validation record, and roadmap/handoff checkpoint.**
- [ ] **Step 4: Run `swift test --package-path apple`, `swift test --package-path apple-mlx`, and the compile-only consumer; Expected: base package remains green and enhancement package compiles on the fixed local checkout.
- [ ] **Step 5: Commit** `docs: checkpoint Apple MLXVLM backend evidence`.
