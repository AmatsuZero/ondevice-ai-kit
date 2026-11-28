# F08 fixed14 detail diagnostic completed: higher detail does not clear quality blocker

2026-11-14. **All14 paired cases now completed in one host Metal process after applying the verified direct graph boundary.** Baseline3/14→detail4/14, exactly one wrong→right (`video1255`), no right→wrong. The five cases whose actual512 inputs had visible relevant cues remain0/5 in both arms; the six fine-detail-uncertain cases improve0/6→1/6. Three original-correct controls remain3/3. This closes the previously interrupted fixed diagnostic, not W6 or independent quality acceptance. Original phone16/36 stays unchanged.

Evidence root: `artifacts/verification/f08-qwen3vl-graph-detail14-20261114/`; [plan](../superpowers/plans/2026-11-14-qwen3vl-graph-detail14.md). Prior [iPhone graph36 verification](f08-qwen3vl-iphone-graph36-20261114.md) and original failed [detail14](f08-qwen3vl-detail14-20261114.md)/[cache256](f08-qwen3vl-cache256-detail14-20261114.md) are preserved. New complete results do not rewrite oldrunning/1/139 orrunning/21/70.

## Identity and invariants

New run`f08-qwen3-graph-detail14-20261114-host01`, actual sole PID3356, exit0. Reused exact original28 paired inputs,14 selected cases/strata/order/labels/questions/eight actual timestamps and PNG bytes. No new media/model downloads, helper/provider/native, phone operation, dependency/signing/default/production change. Model`mlx-community/Qwen3-VL-2B-Instruct-4bit`, revision`9c4f5209e57b31f4b9dfba735de3fb983739c9cc`, same15 model-file hashes and pinned runtime/objects.

Derived from the compiled direct20 source: only runID/claim/planned count andprefix20→all28 changed. Direct normalized graph pool, source loading, tracking, math/protocol, cache256MiB, balanced512, greedy32, A/B/C→EOS,10ms sampler and3.5GiB/serious/180s sampled guards remain unchanged. All28 direct processor inputs already passed in the preceding branch, so there was no new preparation/model-free GPU run. Fresh original-boundary checks and10 time/question/label/frame/order/missing/token/position/pixel/THW mutations all passed/rejected before launch.

The actual processing difference remains decoded cap512→1280, with processor cap262144 unchanged. Actual THW[4,18,32]→[4,24,42] and visual tokens576→1008; do not describe the model as consuming1280px full frames. No timing/frame/prompt tuning or selected-case replacement.

## Results

| Stratum | Cases | Baseline correct | Detail correct |
| --- | --- | --- | --- |
| fine-detail-uncertain | 6 | 0 | 1 |
| visible-sufficient-cue | 5 | 0 | 0 |
| original-correct-control | 3 | 3 | 3 |
| Total | 14 | **3** | **4** |

| Case | Expected | Baseline | Detail |
| --- | --- | --- | --- |
| 4852/q9 | C | B | A |
| 3377/q0 | C | A | A |
| 6500/q1 | A | C | C |
| 4096/q2 | B | A | A |
| 7350/q0 control | A | A | A |
| 2881/q0 control | B | B | B |
| 1255/q1 | B | A | **B** |
| 8473/q0 | C | B | B |
| 11259/q11 | A | C | C |
| 875/q5 | C | A | A |
| 6111/q1 | A | B | B |
| 7277/q4 control | B | B | B |
| 7904/q2 | B | C | C |
| 10179/q0 | A | B | B |

`1255` concerns why pouring fails; its selected answer improves with detail. `6111` paper texture remainsplain rather thanlined despite increased actual detail. `6500` blue package removal remainsNothing changed, and door/mug/room/letter-sequence errors persist. `4852` changes wrong→wrong; original phone baselineA differs from this host baselineB, as already documented before the graph fix. Both are wrong; do not infer phone/host equivalence from equal pixels.

Offline acceptance checks56 actual complete finite151936-dimensional vectors, true constrained argmax/raw BPE/EOS/28KV/delta, input pixels/protocol and strict scores. First20 rows/40 vector hashes are byte-identical to the preceding direct20 control, confirming execution-range change did not alter that prefix. All280 phase/weak receipts pass;448 registration slots are all nil after each perform-return/clear (not deduplicated object counts). The checker has no expected accuracy hardcode.

## Resources and limits

Sampled physical peak3,461,090,232B≈3.223GiB, no guard stop, thermalnominal→nominal, instrumented total11,989.91ms. Within-run per-arm median260.57ms baseline/481.04ms detail; these are Mac timings including evidence work, not iPhone latency or cross-run speed claims. After-clear MLX active always1,781,949,568B andcache0; same-arm second-to-last physical growth is44.06MiB baseline/63.91MiB detail. Residual process growth remains and is not assigned to an allocation owner. Diagnostic protection is not approved product memory/latency budget or hard peak guarantee.

The changed lifetime boundary enables this full run, but the experiment is not randomized proof of every earlier memory/thermal cause. The original old stop receipts stay valid. No high-detail phone run occurred; the prior phone continuous36 used original512 inputs and original phone cache policy. Host cache256/high-detail success cannot substitute for a phone resource gate.

## Current diagnosis and decision

1. **This candidate configuration still fails general short-video quality.** Independent phone36 remains16/36; the current14 are selected known cases, not new independent data. A one-case gain does not justify raising the default resolution or adopting this candidate.
2. **Output grammar and graph retention are addressed separately, with actual evidence.** All valid36 answers are legal letters; graph resource replay preserved every answer/vector. Neither explains away the remaining wrong choices.
3. **Loss of fine detail is a contributing factor in at least this one within-run case, not a sufficient explanation for the diagnostic's failures.** The unchanged cue-visible errors and unsuccessful paper-texture case do not localize the model's internal fault. Existing event-coverage controls for251/9676 also failed after supplementation, so sampling omission alone was not sufficient there. This does not prove all models/video understanding intrinsically impossible.
4. **Stop same-case detail/prompt/frame sweeping.** The fixed14 hypothesis has a complete result. Further quality work needs a materially different, preregistered hypothesis and eventual fresh representative evaluation; do not silently narrow W6 into motion detection or choose a default model based on these known cases. Remaining independent work includes physical-memory ownership/steady state, product resource-budget decisions and formal adoption/integration only if a candidate meets quality needs.

W6/F08 remainsopen/not-ready; overall goalactive. Final author acceptance is not fresh external review under the current no-new-invocation constraint. Protected sources/models/evidence/ordinary Apps are audited separately; only exact own inactive module cache may be removed after no-open-file validation. See`final-audit.json` and`final-file-freeze.json` after finalization.
