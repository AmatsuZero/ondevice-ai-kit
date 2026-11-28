# Model-free reporting lifetime replay

2026-11-14. Solo diagnostic under established primary implementation authority; no new helper, native, provider, model or device invocation. Context7 is not exposed; use the exact accepted Foundation reporting source and compiler evidence. Consult `docs/agent-pitfalls.md` before work/on errors.

## Hypothesis and fixed comparison

The growing resource sample array is re-encoded, bridged through Foundation and written after each case. Autoreleased reporting temporaries might contribute to residual footprint despite released image slots. This is a hypothesis, not attribution of the phone's196.16MiB growth.

Freeze a unique `f08-qwen3vl-report-replay-20261114` root. Reuse the accepted phone36 result, all36 row values and typed resource samples. Select each sample prefix ending at the corresponding `after-perform-return` phase; these are deterministic approximations to original snapshot boundaries, not a reproduction of concurrent sampler timing.

Compile one Foundation/Darwin/CryptoKit-only executable. Run raw then scoped, exactly one new serial process per arm. Both use the same synchronous reporting body: parse row Data, append rows, replace report rows/count, JSONEncoder(sample prefix) -> JSONSerialization object, sorted-key atomic write. The sole intervention is a synchronous autoreleasepool around that body. No await/pool across suspension, no model/MLX import/weights/GPU/phone/network/new dependencies. The latest report remains retained during all36 boundary samples.

An explicit outer pool lasting the replay fixes the drain boundary for both arms. This tests accumulation under a non-draining synchronous loop; it cannot establish pool behavior across the real async model task or device-specific bytes. End-of-outer-pool readings are descriptive only. No artificial repetition/amplification beyond original36 prefixes.

## Predeclared validation and decision

- Validate fixture semantic equality to all original rows/samples, exact36 ordered case IDs/prefix phase matches and seven typed sample fields. Freeze original input/reporting source, fixture, plan, executable and scripts before first arm. Protect previous terminal snapshots.
- Require both exit0/completed36, per-step serialized SHA equality and final JSON semantic equality to the expected36 rows and final prefix. Input corruption/row loss/changed sample must be rejected offline.
- Record phys_footprint and RSS before/after every body; evaluate second→last after-body growth. A diagnostic-positive result requires raw growth at least64MiB and scoped growth at least64MiB and50% lower than raw. Do not change thresholds after seeing data. This author experiment is not independent acceptance.
- One arm stops at boundary footprint1GiB or elapsed60s (parent120s fail-stop); preserve partial/failure, no retry. These fixture-only limits do not change model/device guards.
- If positive, recommend a separately frozen actual model reporting-boundary comparison; do not integrate an unverified fix. If negative, stop this reporting-only branch and retain uncertainty about async/device/model interaction.
- Capture processes terminally before writing/freezing launcher logs. Preserve all failures. Update living handoff/W6/pitfalls and terminal file freeze; no production/dependency/default/model changes. W6 staysopen/not-ready and overall goalactive.
