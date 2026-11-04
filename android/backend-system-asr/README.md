# Android system ASR

`AndroidSpeechRecognizerBackend` provides the existing API31+ microphone route.
It creates only the system on-device recognizer and never falls back to a cloud
recognizer. The module retains minSdk26 and reports unsupported OS versions.

## Language resources

`AndroidAsrResources` manages resources separately from recognition and permissions.
Creating it and calling `check` never request recording or initiate a download.

```kotlin
val resources = AndroidAsrResources(context)
val status = resources.check("en-US") // API33+, suspends until the metadata reply
// Only after an explicit user download action, on API34+:
val outcome = resources.download("en-US") { percent -> showProgress(percent) }
```

`INSTALLED`, `PENDING`, `DOWNLOADABLE` and `NOT_REPORTED` refer to the exact
requested language tag, normalizing underscores/case without guessing a parent
language. Empty language lists yield `NOT_REPORTED`. Query/download failures throw
`AsrResourceException(code)`; an unsupported OS/service throws
`SpeechBackendUnavailableException`. Missing callbacks time out after30seconds
for query or10minutes for download monitoring.

API34 download callbacks distinguish progress, scheduled and installed. API33
supports query only here: the void download overload cannot prove when its queued
request finishes. `canObserveDownload` reports the OS API level, not a promise
that this device's service implements download callbacks.

Each operation creates/destroys its own recognizer on Main, including cancellation
and failure. Cancelling a download coroutine **detaches monitoring**; it does not
promise to stop the system's download. Serialize resource operations with active
microphone recognition in the host. The Demo does this and asks for confirmation
before requesting a download; no page entry/query initiates one.

An installed result is system metadata. Real transcription, recording permission,
offline behavior and quality still require separate validation. See
[validation](../../docs/validation/speech-backends.md#2026-11-04-android-sdkdemo-语言资源恢复入口).
