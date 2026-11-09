# Apple TTS sustained resource observation — 2026-11-09

The bounded physical TTS resource experiment is accepted and restored. **W8 stays open**: GPU, energy, true peak and prospective production budgets are not approved by this run. W1/W2/W4/W5 remain closed; W3/W6/W7/W8 remain open.

On the user-confirmed available iPhone17,2/iOS27.2, the parent triggered one new isolated alias once at10:23. Twenty fresh real AppleAVSpeechBackend/SpeechSynthesisClient create/play/close cycles completed, each at least30s, with one initial warm-up excluded and40 successful measured fixed-text en-US requests. The selected voice was `com.apple.voice.compact.en-US.Samantha`. PID6917 and UUID54AFA617-52CF-48C5-A9C6-5B4BC13BD039 matched the launched process and progressive atomic reports; no observed resign-active/background event occurred.

| Observation | Measured value |
| --- | --- |
| Total monotonic time | 620144.815ms |
| Actual paced idle | 472614ms |
| Sum of40 measured speak durations | 121153.614ms |
| Speak duration p50/p95, nearest-rank | 2770.328/3320.940ms |
| Request RSS sampler observations | 10952 |
| Maximum sampled request RSS | 113131520B, about107.89MiB |
| First/last cycle close+1s RSS | 112476160/113033216B |
| Final process RSS, immediate and+1s | 112771072B /112771072B |

Total elapsed includes pacing and close waits; this is not620s of continuous speech. Close+1s difference557056B is descriptive and does not establish a leak or zero-leak result. Sampling approximately every10ms is not a true or GPU peak. Source restores the exact previous app idle-timer value with defer on all exits; the report has no separate runtime idle-timer restoration field.

Free CBC hy3 delivered the final three measurement guards in297.199s. Parent verified all actual file hashes and the exact scope. The final correct-bundle unsigned build passed4.543s; existing authorized signing preserved all compiled Mach-O sections. An initial sandbox cache-permission failure and earlier actual observer-token compiler failure are retained separately. No production Speech source or Apple MLX lock changed. The source SHA is `7ff5ab37dc1ff213e18b3185d48d30169c73df8cb2d2078efba8ed7356814af3`.

The parent independently checked78 report/provenance/protocol assertions, read the terminal report twice with identical SHA `4b9d631b88f16bc51c14a96343fc070a62dab6f609101a82d1e74dfa0fc4ca62`, removed only `io.ondeviceai.probe.w8tts20261109`, verified all44 original app records unchanged, and returned to the original Combined app. Nine protected files and the production lock match. This is parent acceptance; no fresh independent native review is claimed after the prior429.

After the run, three exact inactive task-local Xcode caches were removed:127563479 nominal bytes. All2607 retained source/product/raw/failure/signing-input file hashes remained identical. No models, global caches, photos, unique evidence or other task directories were deleted. Host free-space movement may include concurrent writers and is not attributed to this cleanup.

Source and protocol: [probe](../../probes/ios-tts-sustained/W8TTSSustainedApp.swift), [runbook](../../probes/ios-tts-sustained/RUNBOOK.md), [build specification](../../probes/ios-tts-sustained/project.yml). Reproduce with XcodeGen from the probe directory; device signing, a free device slot and one deliberate run remain separate prerequisites.

Local evidence: `artifacts/verification/w8-tts-sustained-build-20261109-0940/`: `physical-protocol-freeze.json`, `final-source-review.json`, `build-result.json`, `signed-receipt.json`, `one-batch-trigger.json`, `checkpoint-01.json` through `checkpoint-06.json`, `w8-tts-sustained-report.json`, `accept-report.py`, `parent-acceptance.json`, `apps-before.json`, `apps-restored.json`, `uninstall.json`, `return-combined.json`, `task-cleanup-plan.json`, `task-cleanup.json`. Raw signing material stays local.
