# Android OpenCV Identity resource characterization

Measure the existing production `OpenCvIdentityBackend.evaluateQuality` plus `embed`
on the already-authorized astronaut PNG. This is a representation-pipeline resource
measurement, not enrollment, matching, PAD, calibration or recognition accuracy.
No production behavior, dependencies, model versions or thresholds change.

Use the frozen `636a6eb8854db5dff74244c42c3ce0e82bd2be9f` source archive and exact
`dfccaa95…879a7` main APK. Add only this test instrumentation to an isolated archive
copy and build its AndroidTest APK. Do not execute the ordinary Identity runner:
that runner accesses the ordinary App's journal namespace.

The runner reads `files/identity-validation/authorized-astronaut.png`, SHA-256
`88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5`.
Read existing models from `files/identity-validation/models`; install copies into
a newly created, run-specific cache ModelStore. The probe never opens an ordinary
IdentityStore/session. Schema2 starts the ordinary MainActivity before measuring;
its existing Identity ModelStore constructor is allowed only after proving its
staging directory exists, is not a symlink and is empty. Verify all ordinary private
hashes/settings/SR paths afterward. Never enable enrollment or import any model. Close the backend and await
its model lease, remove the temporary model, then delete only the newly owned work
directory. Preserve the original image, models, journals, packages and settings.

One backend open precedes 5 warmups and 30 measurements in the same PID. Each
measured call evaluates geometric quality then embeds the same upright encoded
image (two detections); it includes decoding, resizing, detection, alignment and
feature normalization. Backend construction/model installation are outside that
window. RSS instrumentation overhead is included; sampler threads are joined
before their arrays are read. No forced GC. OpenCV's Java wrappers are finalized
by GC, so close/lease release does not establish immediate native memory freeing.

Reports use private `AtomicFile`, one validated/reserved runId, incremental samples
and a durable terminal written before finish. ADB failure only permits observation
of that same run. Failed/nonterminal reports remain evidence, not success.

## Controlled foreground scope

The first physical Schema1 run stalled with both main/instrumentation threads in
`do_freezer_trap`. The same PID finished35rows only after a recorded manual
foreground launch. Preserve that run as interrupted runtime evidence and exclude it
from clean resource admission. The OEM freezer mechanism is not proven.

A distinct Schema2 run launches one MainActivity with Instrumentation before any
backend open or measured call. Main-thread lifecycle reads must observe RESUMED at
startup, before and after all35calls, and after close/wait:72checkpoints. Assertions
run on the instrumentation thread so failures reach the durable recorder. Checkpoints
do not prove continuous visibility. UI startup latency is outside measured call
durations, but its resident footprint is included in process RSS; foreground hooks
only mark their hosts foreground and do not start capture or a user request.

## Schema 2

The report kind is `android-identity-resource`; mode `inference`; backend
`opencv.identity.android`; runtime `4.12.0`; model revision `zoo-47534e27-v1`;
preprocess `opencv-4.12-yunet640-sface112-rgb-l2-v1`; full sdkCommit as above.
`modelSHA256` contains exactly the two pinned filenames and digests from
`OpenCvIdentityModel.manifest()`. `deviceModel`, `sdkInt` and positive `pid` bind
the physical process. `status=completed`, `terminal=true` require no error fields.

`enrollmentStarts`, `cameraStarts` and `imageWrites` are integer zero;
`embeddingPersisted=false`. Counts are `warmupCount=5`, `measurementCount=30`,
`samplingIntervalMs=20`. `measurementMethod=proc-self-status-VmRSS-sampled-process`;
`timingMethod=sdk-quality-and-embed-wall-after-one-backend-open`.

Each of 35 ordered samples has index/phase/backendId, `faceCount=1`,
`embeddingDimension=128`, finite `embeddingNormSquared` within 0.00001 of one,
`callerInputUnchanged=true`, positive started/ended/duration nanoseconds with
duration equal to end-start, timestamped RSS bytes, exact sampled peak and
`afterIdleRssBytes` after 200ms. First/last RSS readings bracket the call, at least
one reading is inside it; actual sampling intervals are summarized, not assumed.

Final fields: `finalCloseSucceeded`, `modelLeaseReleased`, `workDirectoryRemoved`,
`sourceInputUnchanged`, `callerInputUnchanged` all true; nonnegative closeDurationNs;
positive beforeBackendOpenRssBytes/beforeRssBytes/afterCloseRssBytes/afterCloseWaitRssBytes;
afterCloseWaitMs=1000. `beforeRssBytes` retains its original meaning after backend
open and before the35calls; the new field captures RSS immediately before backend
open. Exact control fields: foregroundActivity=io.ondeviceai.demo.MainActivity,
foregroundStartedBeforeTiming=true, foregroundCheckCount=72,
foregroundPolicy=RESUMED-at-72-checkpoints, processScope=process-with-idle-main-activity.
No image, feature vector, identity label, template, grant, similarity or PAD decision
is persisted in reports. Geometric faceCount/norm are probe sanity checks only.

The reducer reports nearest-rank p50/p95 over the 30 measurements, sampled process
RSS peaks/idle/close observations and explicit unverified quality, true peak,
attribution, GPU, energy, calibration, PAD and sustained-budget fields. Integrating
this source into P11 never changes its release stop contract.
