# Android FILE wrapper/package bounded review

The independent free hy3 review completed naturally in 329.023 seconds and found no Critical/Important source blocker for the single fixed physical-file run. The primary agent checked its findings against actual source, fresh package receipts and the official Android API reference before accepting that bounded conclusion. It is not a production or release review.

The immutable supplied context contained 24 files. Its hashes remained unchanged. The reviewer inspected source and parent-recorded receipts; it did not execute tests/build/device checks or rehash actual APK/model/PCM files. Seven missing-path Read errors are retained in the raw events; the subsequently successful declared-context reads are recorded separately.

| Finding / boundary | Parent disposition |
| --- | --- |
| M1: report variant check and base reservation are separate operations | Deferred minor for this exact exclusive run. Atomic base creation and parent alias/run reservation prevent normal concurrent use; an adversarial same-UID writer is outside this isolated acceptance scope. No general multi-writer guarantee is claimed. |
| M2: five-second resume/destroy acknowledgement waits | Existing fail-closed operating boundary. A timeout fails the batch; no wait extension or automatic retry. Resume occurs before inference. |
| Reviewer says `onStart` runs on the App main thread and `runOnMainSync` can run inline there | Incorrect. The official [Instrumentation reference](https://developer.android.com/reference/android/app/Instrumentation) states that `start()` creates a new thread which calls `onStart()`, separate from the application thread. `runOnMainSync` posts work to the App main thread and waits. Local SDK source also calls `validateNotAppThread`; it does not support the reviewer's inline claim. |
| Suggested extra device smoke | Declined. It was based on that incorrect thread premise; the existing physical load-only used the same accepted lifecycle pattern. Preserve the frozen one-shot 65-file contract and do not replay load-only or add trial decodes. Actual new device execution is still independently checked. |
| Manifest hash consistency | The reviewer received source-file digests, not `cases.json` itself. This is a transitive receipt comparison. The primary agent separately verified actual packaged bytes for all 65 PCM files and manifest. |

The [original review](w3-qwen-android-file-wrapper-review-original-20261111.md) is retained unmodified, including the inaccurate thread explanation. The [machine disposition](w3-qwen-android-file-wrapper-review-20261111.json) records its hash and bounded acceptance. Official-reference retrieval is retained in `.omx/artifacts/w3-qwen-file-20261111/instrumentation-official-docs.json`; local SDK source is Android37 and is only supporting source evidence, not a claim that API35 device execution was rechecked by reading it.

Frozen source fingerprint remains `ef4665190423630afa548d4c6b4ea1a4706f98ab29761437887742a7872f8696`; [package checks](w3-qwen-android-file-package-20261111.md) retain the two APK hashes. No source/default-service/model changes or extra trial run result from this review.
