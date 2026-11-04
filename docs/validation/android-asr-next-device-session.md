# Android ASR next device session

Updated 2026-11-04. Device-assisted work is deferred at the user's request to
**2026-11-05 09:00 Asia/Shanghai**. No microphone attempt has run in this checkpoint.

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

## Tomorrow's order

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
