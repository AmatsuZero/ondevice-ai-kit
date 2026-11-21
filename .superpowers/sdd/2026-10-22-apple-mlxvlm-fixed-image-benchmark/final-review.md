# Fixed-image benchmark — author self-review

Scope: `626b27b..f9512a0` plus Task3 documentation/evidence checkpoint. This is a separate author read-only review pass, not an independent reviewer verdict or APPROVE.

## Evidence checked

- Task1 fixed-schema validator, Task2 observer/runtime/client instrumentation, generated consumer and actual helper execution tests.
- Observer mutation RED: missing forwarding yields only update instead of load then update; restored host12/12 GREEN. Failure/source validation regression RED→GREEN; focused10/10 and full tool98/98, zero failures.
- Cached fixed-lock Xcode27.1 signed Debug iOS build succeeded. Generated Swift sources and Package.resolved compare byte-for-byte with committed source/lock. Source trees apple/apple-mlx/examples/tools have no diff from f9512a0 at device launch; unrelated protected dirty files excluded.
- Model14/14 bytes/SHA256 plus fixture verified; device install/acquire and consumer hash guard succeeded. New PID20276/raw runId6A376940-9A43-4654-BCA2-33EDD3D2C453 matches launch/source metadata.
- Device raw strictly validates; summary recomputation is byte-identical. Load/first update/total ordered in all5; one warm-up excluded; RSS baseline/peak/delta arithmetically consistent.

## Scope findings resolved

1. Nil RSS was zero-filled and state was read outside locks. Dedicated helper now locks reads/writes, propagates any failure as unverified, runs sampling detached, and joins before snapshot.
2. Consumer's error cleanup previously launched an unawaited close Task. Both success and failure now await close.
3. Timing helper now rejects first-update-before-load and duplicate/late load events instead of inferring order from duration alone.
4. Validator rejected neither unknown sdkCommit nor error-bearing runs. Regression tests now prove rejection; explicit unverified RSS is allowed without invented numeric fields.
5. Removed the new source-text-only benchmark test; compiled helper behavior and actual device JSON supply evidence.
6. Design wording corrected: RSS is one suite-level observation, so no RSS p50/p95 is claimed. The requested10ms interval is not a continuous hardware peak.

## Interpretation checks and retained gates

- The output's extra “flag” semantic claim is preserved as an observed limitation. Red+square5/5 is only a narrow concept rubric, not accurate complete description or natural-image quality acceptance.
- Debug, cache-warm, n5 nearest-rank p95=max; not a cold-start, pure-first-token, Release SLA or P11-size result. Total excludes setup/acquire/close; loadContainer lazy costs may occur later. RSS includes process/install effects, not model-only/GPU memory.
- GPU/Metal, energy, natural quality, video, KMP/Demo, Android real backend, minimum-system execution, enhanced binary distribution and independent review remain open. Prior Video UI/P6/ASR gaps remain open.
- No base deployment/dependency changes, downloads, recordings, private-media access, signing-asset changes, remote pushes or publication. Protected hash/staging checks run before local checkpoint commit; ignored logs/artifacts retained.

## Assessment

No outstanding blocking issue found within this bounded author-review scope. Commit the local evidence checkpoint after fresh final verification. Independent review remains **pending**; no independent APPROVE or release recommendation is issued. Next independent package selected: single-image KMP bridge contract/adapters, separate from Demo/video/new Android model work.
