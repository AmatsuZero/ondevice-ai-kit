# Android ASR resource recovery

Continue F05 within the existing system on-device ASR route: let SDK consumers
inspect resources for one requested locale and explicitly request their download;
let Demo users recover from `MISSING_ASSETS` without recording just to discover it.
This does not close F05's real-transcription, offline or quality gates.

Use `checkRecognitionSupport` on API33+, exact normalized locale membership in
installed/pending/supported on-device lists, and a separate `NOT_REPORTED` state.
An unsupported query is an error, never evidence of installed/missing assets.
Use the observable API34+ `triggerModelDownload` overload: progress is provisional,
`onScheduled` is pending, `onSuccess` is installed metadata, and errors retain the
platform code. API31–32 recognition remains unchanged; API33 has query only.
All engine operations run on Main and each operation destroys its own recognizer.
Cancellation detaches monitoring; it cannot promise cancellation of a system download.

Approaches considered: launching generic settings cannot identify a locale or
confirm installation; the API33 void download call has no terminal receipt. Use
API34 callbacks to avoid reporting an enqueued request as a completed install.
No cloud recognizer, new runtime, model, lockfile or permission request is added.

Demo offers explicit check and, only after a matching downloadable result, a
confirmation describing the system/network side effect. Neither page entry nor
query initiates download. Resource operations serialize with microphone work;
background, page exit and close detach the operation. Changing locale invalidates
download eligibility. Apple has no resource controls unless its host supplies them.

Implementation choice made under the user's autonomous continuation instruction;
no system download or recording will be executed as part of this implementation.
