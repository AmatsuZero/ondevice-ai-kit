# W3 Qwen arena policy implementation plan

> **For agentic workers:** Follow the existing user-authorized external-candidate workflow. Free CBC owns implementation; the primary agent plans, reviews, integrates and validates. No new native child; the prior native429 is not retried.

**Goal:** Prepare and verify one exact arena-off configuration module, without model/device execution.

**Architecture:** A pure Kotlin policy module reserves a new private child, writes/syncs/rechecks one fixed config, and produces an immutable receipt/provider suffix. A parent test driver verifies real filesystem failure behavior. Future wrapper integration is a separate prerequisite-gated task.

**Tech Stack:** Existing Kotlin2.2.20/JDK21, Java file APIs, fixed Sherpa2.0.1 source/AAR. No dependencies added.

**Spec:** `docs/superpowers/specs/2026-11-11-w3-qwen-arena-policy-design.md`.

## Global constraints

- No production/old-probe/model/AAR/source-branch-push changes, JNI/model load, microphone, AiAi or device operations.
- Only config bytes `EnableCpuMemArena=0\n`; no memory-pattern, graph, thread, token, audio or quality changes.
- Shared pitfalls apply; preserve other edits and exact failed-run evidence.

## Review focus

1. Existing directory/file collisions fail without overwrite (task1).
2. Symlink leaf/ancestor cannot redirect policy writes (task1).
3. Config tampering or extra keys fail before use (task1).
4. Receipt binds the exact canonical path/bytes/SHA/provider (task1).
5. Passing module tests cannot be reported as native memory/quality success (task2).

## Task1: Exact policy module and parent checks

**Files:** create external `probes/android-qwen-arena/QwenArenaPolicy.kt`; parent `probes/android-qwen-arena/check_policy.py` and `PolicyChecks.kt`.

**Interface:** package `io.ondeviceai.acceptance.qwenarena`; `data class ArenaPolicyReceipt(val provider: String, val configPath: String, val bytes: Long, val sha256: String)`; `object QwenArenaPolicy { fun prepare(privateRoot: java.io.File): ArenaPolicyReceipt; fun verify(privateRoot: java.io.File, receipt: ArenaPolicyReceipt) }`. `prepare` reserves child `cpu-arena-off` and file `cpu.config`; repeated prepare fails. `verify` binds to that exact child/file and checks all receipt fields and bytes, without writing.

- [x] Parent writes/runs absent-candidate RED and real Kotlin filesystem assertions.
- [x] Separate free hy3 candidate, exact single-file ownership and tested OS sandbox; inspect actual file/tool/result events.
- [x] Compile/run the candidate against real parent checks. Preserve any failure; correction is a distinct bounded task.
- [x] Integrate external bytes unchanged and commit source/validation.

## Task2: Report precise preparation and next gate

**Files:** validation report and `probes/android-qwen-arena/README.md`.

- [x] Archive source feasibility report verbatim with parent corrections/context-read limits and source/AAR/JVM evidence.
- [x] Record local policy evidence and scope; mark native consumption/savings/quality unverified.
- [x] Preserve consumed default FILE run and fixed budget. Wrapper integration/build/review/new contract are pending; this task starts no new native run.

Self-review: all five failure classes map to task1 real filesystem checks or task2 reporting. No new quality thresholds or authority claims. Implementation remains with external CBC under the user's explicit execution method; no additional approval handoff.

Execution: first external candidate naturally completed326.954s,7901B/SHA699bb859…, source only. Actual Kotlin -Werror compiled;12groups39assertions passed. Parent supplemental symlink-before-dot-dot test passed; the oversized2GiB sparse-file test failed with `OutOfMemoryError: Required array size too large` at verify's unbounded `Files.readAllBytes`, before any large allocation succeeded. Original source/raw/RED retained; no integration/device run. Distinct freehy3 correction owns one source, bounded at most21byte reads in both reread paths and fixes misleading absoluteFile comment. No test relaxation or original-request mutation.

Final correction113.973s/9380B/SHAc86b96b6… passed13groups41assertions, integrated byte-for-byte and checked again. Existing AndroidAPI34jar with-no-jdk/-Werror compiled3classes; absentAPI35jar prerequisite retained, no download. No native/device execution. Policy preparation plan complete; wrapper/new diagnostic work remains separate and W3open.
