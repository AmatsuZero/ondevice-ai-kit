# F11 SDK Video SR Grouped Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Include five retained public SDK Video SR requests in a separate, hash-bound P11 group.

**Architecture:** Add `video-sr-sdk-resource` to the existing manifest and reporter. Verify the raw device report, bounded summary, input, reference, five distinct MOV outputs, and first/last quality JSON before recomputing nearest-rank request wall time and sampled RSS deltas. Keep this source separate from `video-sr-resource` frame processing and leave P11 open.

**Tech Stack:** Python standard library, existing unittest and grouped reporter.

**Spec:** `docs/superpowers/specs/2026-10-27-p11-grouped-benchmark-design.md` and `docs/validation/video-sr-backends.md`.

## Global Constraints

- Preserve the nine existing dirty/untracked paths and ignored raw artifacts.
- Report one public clip, one device, five sequential same-PID requests, first including warm-up.
- Quality remains `unverified`; sampled process RSS is not true/GPU peak or energy.
- Do not combine full SDK request latency with the system probe's per-frame latency.

## Review Focus

- Reject missing, changed, aliased, or cross-linked evidence before writing output.
- Reject duplicate/failed run IDs and invalid wall/RSS numbers.
- Reject wrong input/output/quality identity even if manifest hashes are updated.
- Reject a wrong P11 capability or duplicate run ID across sources.
- Preserve quality and release gaps in the generated report.

---

### Task 1: SDK source adapter

**Files:** `tools/tests/test_p11_grouped_report.py`, `tools/p11_grouped_report.py`.

**Interfaces:** A `video-sr-sdk-resource` manifest entry has `path`, `sha256`, `summary`, `input`, `reference`, `outputs` (five entries in run order), `quality` (`first`, `last`), and `p11Capability: video-sr`. One output group carries five request run IDs, `requestWall` p50/p95, sampled process RSS delta p50/p95, and bounded first/last quality metrics.

- [x] Write and observe RED tests for recomputation, identity/number/hash rejection, and output alias protection.
- [x] Implement the source validator and dispatch without changing existing Video SR group semantics.
- [x] Run the focused reporter suite.

### Task 2: Real evidence and reporting

**Files:** `benchmark/p11/grouped-report-manifest.json`, `docs/validation/p11-grouped-benchmark.md`, `docs/validation/foundation-followups.md`, `docs/HANDOFF-2026-10-23-next-session.md`.

- [x] Pin retained evidence hashes and regenerate a new ignored grouped snapshot.
- [x] Inspect exact group count, statistics, coverage, and P11 gate; refresh readiness only if documentation hashes require it.
- [x] Update report and handoff with the new group and explicit limits.
- [x] Run focused/readiness/full tools tests and `git diff --check`; stage only this package.

Independent review found that two differently named MOVs could be aliases. A hardlink regression test failed, then passed after cross-evidence alias rejection. The final focused reporter/readiness suite passed 33/33, and the full tools suite passed 237/237 in the allowed Ruby 3.4/SwiftPM environment. The restricted run's sole SwiftPM manifest error was `sandbox_apply: Operation not permitted`.
