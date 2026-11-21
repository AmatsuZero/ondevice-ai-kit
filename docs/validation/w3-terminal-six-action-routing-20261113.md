# W3 six-action source routing (2026-11-13)

The pure source adapter now routes `preflight`, `prepare`, `run`, `collect`,
`collect-policy` and `restore` to the future terminal dispatcher. The inherited
preflight body is removed alongside the four old device-action bodies; the
immutable FILE source itself and its pinned SHA are unchanged.

A new parent test first reproduced one error from the inherited preflight branch
and one failure from the action assertion rejecting `collect-policy`. Free hy3
delivered the minimal adapter candidate in 149.565 seconds. Its exact returned
source was integrated after parent review. The eight existing purity/helper-AST/
guard tests, the new actual fake-ADB six-action routing method and eleven prepare
integration methods all passed. The old 120 frozen files, failed heap evidence,
95 protected files, 81 prepared sources and both APKs retain their hashes.

Only test-root lookup changed on test integration. Raw prompt/output, the RED,
candidate diff, integration hashes and fresh results are retained under
`.omx/artifacts/w3-terminal-observation-20261112/adapter-routing-parent/`.
The preceding 348-method policy checkpoint remains historical evidence; this
extension claims twenty fresh targeted methods, not a fresh full 349-method run.

This completes routing preparation. The actual `device_acceptance.py` dispatcher,
launch validation and recovery closure still need implementation and integrated
acceptance. Current complete dual reviews, a new freeze and same-run TECNO
evidence remain pending. No device, inference, microphone, AiAi or download
occurred. W3/R4/R5 remain open; the source branch is not pushed.
