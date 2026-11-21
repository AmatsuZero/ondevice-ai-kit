# Android speech cleanup corrections

2026-11-07, foundation source base `5e466183b8208f06b922ae990912a8478fdb56a5`.

Two independent lifecycle failures were reproduced before changing production code:

- `SpeechClient.close()` stopped iterating when the first backend threw. The regression expected three backends to close but observed only the first. Cleanup now attempts every registered backend, retains the first failure, and attaches later distinct failures as suppressed exceptions. Concurrent/later callers still join one completion; teardown remains noncancellable. Repeated failure identity cannot cause self-suppression to interrupt cleanup.
- System ASR resource progress callbacks invoked after `engine.download()` returned could throw directly from the listener. The regression observed the caller's exception escaping that callback. The listener now completes the active session exceptionally, allowing the existing `finally` block to destroy its recognizer. Late callbacks remain ignored. This detaches monitoring; it does not claim to cancel a system model download.

The primary agent implemented the registry correction; a native `executor` added the ASR failing test, waited for the parent's RED result, then made the six-line ASR correction. The parent inspected both changes and executed validation. The existing independent reviewer supplied the initial ASR finding. A new four-file correction review subsequently failed with `429 Too Many Requests`; it returned no review and was not retried. This is not an independent approval or whole-branch review.

## Validation

Evidence: `artifacts/verification/speech-cleanup-20261107/` (local ignored material).

| Check | Result |
| --- | --- |
| Registry RED, original production hash | 1 test, 1 expected failure: `[first, second, third]` versus `[first]` |
| ASR RED, original production hash | 7 tests, 1 expected failure: progress exception escaped listener |
| Speech JVM module | 29 passed, including all-backend cleanup, repeated Throwable and cancelled-owner/concurrent-joiner cases |
| System ASR JVM module | 14 passed, including asynchronous progress failure, late callbacks and one destroy |
| Related TTS / Demo JVM modules | 3 + 56 passed |
| Speech / system ASR lint | 0 errors, 6 warnings (`NewerVersionAvailable`, `AnnotateVersionCheck`, `UseRequiresApi`) |

The 102-test total combines actual speech execution in `green-final.json`, ASR execution in `green-verified.json`, and related modules in `consumer-green.json`. Speech was up-to-date in the final combined check; it had already executed successfully with the same source/test hashes. No new dependencies, devices, capture, model downloads, artifacts uploads or production lock changes.

The first GREEN attempt failed three test assertions that incorrectly required exception identity across coroutine suspension. Kotlin coroutine stacktrace recovery can create a copy with the original as its cause, retaining suppressed exceptions on the original. Tests now check error semantics and cleanup effects; the ASR test also requires the original directly or as the cause. No production workaround or global debug setting was introduced. Original failures and upstream documentation are retained. The first GREEN stopped before the ASR task; its old XML was stale and is explicitly excluded by `first-green-assessment.json`. The launcher now checks actual task execution before using XML.

Read-only verification: `rtk proxy python3 artifacts/verification/speech-cleanup-20261107/freeze.py --verify`.

## Helper and delivery boundaries

Qoder `Qwen3.8-Flash` received a separate generic, supplied-context-only test-matrix task, without project source, credentials or device data. It returned exit 1, HTTP `403` with `pricingUrl`, `error_code: 112`, zero output tokens and no candidate. The lane was stopped without retry, login changes or a model switch. This response does not establish exact remaining quota or payment state. Invocation, prompt, raw response and parent assessment are retained at `.omx/artifacts/qoder-close-cases-20261107/`; the user was asked to select cbc hy3 or parent-only completion for that optional matrix. No helper result was fabricated or counted as approval. Root local `AGENTS.md` and the shared pitfalls record the latest failure.

Roadmap remains **10/12**, P4/P11 open and release not-ready. Four regression tests and two corrected paths do not replace system-service/device acceptance, authorized identity calibration data, production resource evidence, remote `.6` consumption, or independent release review. The hosted `.6` upload scope and TTS external-review provider choices remain pending; neither was repeated or inferred from silence.
