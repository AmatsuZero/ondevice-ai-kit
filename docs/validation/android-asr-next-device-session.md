# Android ASR next device session

Updated 2026-11-05. The user returned and explicitly requested immediate start.
The authorized single attempt has now run; the earlier deferred/zero-attempt
statements below describe the November4 checkpoint only.

## Single physical-device attempt — 2026-11-05

Fresh preflight confirmed TECNO KM9/API35 (`15365375C9007986`), the original
ordinary App `405d64e1…082c0`, original test package `7b9e47ce…a621d`, Identity
runner, granted microphone/camera permissions and an awake screen. The frozen
test APK `db759cca…ca12` and source matched `prepared.json`; only the test package
was temporarily replaced. No ordinary App update or permission change occurred.

After the user said “立即开始”, one authorized framework invocation returned
`status=platform-error`, `platformError=13`, `startCount=1`,
`startToDestroyMillis=120`, `withinAuthorizedLimit=true`, `cancelReturned=true`,
and `recognizerDestroyed=true`. No `readyForSpeech`, `speechBegan`, `speechEnded`
or nonempty-result receipt was reported. Code13 is `ERROR_LANGUAGE_UNAVAILABLE`
(also confirmed against the installed Android SDK source constant); the chosen
en-US resource remains unavailable. This is a real attempted start and immediate
platform rejection, not successful transcription or a user speaking failure.
There was host approval latency after the visible start cue, so the cue alone
does not establish synchronized speaking/capture timing. No second attempt ran.

The probe reports `audioPersisted=false` and `transcriptPersisted=false`; no
raw audio, transcript or unrestricted speech logcat was collected. Instrumentation
exit/code values are not transcription evidence. The on-device factory restriction
does not independently measure system-service network traffic.

The original test APK/Identity runner and both App/test hashes were restored
and read back; permissions match preflight. Ordinary MainActivity launch succeeded.
Ignored evidence is in `artifacts/verification/android-asr-single-20261105/`:
`before.json`, `installed.json`, `ready.json`, `attempt-started.json`, `result.log`,
`command.json`, `restored.json` and the bounded session driver. No SDK or ordinary
speech-UI acceptance is claimed. Resource provisioning, SDK/UI transcription and
lifecycle validation remain open; do not silently repeat capture or resource
download requests. F05, P4/P11 and release remain open/not-ready.

## Fresh network/resource result

After the user connected TECNO KM9/API35 (`15365375C9007986`), read-only checks
confirmed default network159 and validated Wi-Fi. One already-authorized en-US
download request was retried on that network. The service returned
`requested-without-events-not-ready / unverified`; independent before/after
queries still reported `NOT_REPORTED`, with all three language lists empty.
This is neither installation proof nor a fresh transcription failure. There is
no trackable live download handle, so do not repeatedly issue or poll that request.

The original ordinary Demo APK (`405d64e1…082c0`), test APK (`7b9e47ce…a621d`),
Identity instrumentation runner and existing permissions were restored and
verified. Evidence: ignored `artifacts/verification/android-asr-network-20261104/`,
including `restored.json`. The on-device service remains Google Android System
Intelligence, not the Google Search package's separately displayed language pack.

## Prepared single-attempt diagnostic

`AsrSingleAttemptInstrumentation` is a separate operator-only framework probe.
It rejects missing `authorizedSingleAttempt=true` before Activity or microphone
startup. On the authorized branch it launches ordinary MainActivity for foreground
microphone policy, creates only `createOnDeviceSpeechRecognizer`, and calls
`startListening` once for en-US. Launch alone does not start the camera.

The main-queue deadline cancels/destroys at9.5 seconds. An independent thread
kills its own instrumented target process at9.8 seconds if cleanup has not
completed, closing the recognizer Binder client even if the main queue stalls.
This fallback yields no successful receipt and must be reported as inconclusive.
Actual `startToDestroyMillis`, `withinAuthorizedLimit`, readiness, platform error
and cleanup results are recorded. This is a practical bounded diagnostic, not a
hard real-time scheduling guarantee. Sanitized exception classes omit payloads.

No raw audio or transcript is written or logged by the probe. The result reports
only whether recognition was nonempty and whether it matched the suggested
sentence, “This is an on device speech test.” Network traffic by the system
service is not independently measured. The factory restriction does not claim
a measured airplane-mode/offline quality result.

Offline test-APK build and AndroidTest lint passed; a bounded independent review
approved after adding the separate watchdog and exception categories. Immutable
APK/source and logs are retained in ignored
`artifacts/verification/android-asr-single-20261104/`. No physical-device install
or microphone attempt was made after the user deferred the session.

## No-capture emulator preflight

On2026-11-04 the exact prepared test APK (`db759cca…ca12`) was temporarily
installed only on the existing `ODAI_MainFlow_20261104` / emulator-5580. Its
source hash matches `prepared.json` and the current committed probe. The ordinary
App stayed at the frozen layout APK `c098fc41…cf1a6`; no App rebuild/update,
permission grant or physical-phone operation was involved.

Running the operator runner **without any authorization argument** returned
`status=precondition-rejected`, `startCount=0`, no `activityStarted`,
`recognizerDestroyed=false`, and no audio/transcript persistence. This validates
the rejection branch only: microphone permission was also denied, so it is not
an authorized-branch, timeout/watchdog, real-service or transcription test.
The unchanged `RECORD_AUDIO: ignore` appops and denied capture permissions
support the no-capture boundary; no system-service network/offline claim is made.

Instrumentation terminated its target process as expected. The temporary test
package was removed to restore its original absent state, and ordinary Demo
was relaunched (PID8481 before →9996 after) and returned to the speech page.
The exact App/model hashes, denied permissions/appops and two-file export cache
inventory matched before/after. Those cache checks establish names only, not
export-content hash equality. Initial `pm list instrumentation` observations
used the test package as a target filter and were empty; the post-run query
uses the actual target package and identifies the installed runner correctly.

Ignored evidence: `artifacts/verification/android-asr-single-no-capture-20261104/`.
Fresh `verify.py` passes7 evidence hashes, actual result-bundle contents,
restoration and existing252-source/layout/APK/protected-file/production-lock
checks. No new production code, SDK tests or independent review was performed.
Physical microphone attempts remain zero and tomorrow's authorized single
attempt remains unconsumed.

## November4 planned order (historical; single attempt now consumed)

1. Recheck device, APK/runner hashes and permissions; preserve original test APK.
2. Give the user the start cue before consuming the one authorized microphone
   attempt. Do not trigger automatically based only on time or an agent resume.
3. Run the bounded probe once. Inspect its result bundle rather than treating
   instrumentation exit0 as transcription success. Missing result, watchdog
   termination, cleanup failure or elapsed time above10 seconds is inconclusive.
4. Restore and verify the original test APK/runner and ordinary Demo; preserve
   permissions. No cloud/default recognizer fallback or second capture.
5. If the service produces text, that closes only this framework resource
   diagnostic. SDK/ordinary speech-UI acceptance, cancellation/reentry and
   natural/offline quality remain separately unverified.

F05, P4/P11 and release stay open/not-ready. ASR preparation is not a complete
Demo or release acceptance claim.
