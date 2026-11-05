# Android speech consumer

The library example has `contract`, `systemAsr` and `systemTts` flavors.
`-PenableSherpaAsr=true` adds the optional `sherpaAsr` flavor. Its
`SherpaAsrConsumer.recognize` uses explicit backend selection and closes the
client even when its caller is cancelled. The host supplies the model directory
and grants microphone permission; building this example does not record audio.

From the repository root, prepare the pinned runtime/models as described in the
[backend README](../../android/backend-sherpa-asr/README.md), then publish SDK
AARs into a worktree-local repository:

```sh
./gradlew :android:sdk-core:publishReleasePublicationToMavenLocal \
  :android:speech:publishReleasePublicationToMavenLocal \
  :android:backend-sherpa-asr:publishReleasePublicationToMavenLocal \
  :android:backend-system-asr:publishReleasePublicationToMavenLocal \
  :android:backend-system-tts:publishReleasePublicationToMavenLocal \
  -Dmaven.repo.local="$PWD/artifacts/speech-consumer-m2" \
  -PsherpaMavenRepo="$PWD/artifacts/sherpa-asr/m2"

./gradlew -p examples/android-speech \
  assembleSherpaAsrDebug assembleSherpaAsrRelease lintSherpaAsrDebug \
  -PusePublishedSdk=true -PenableSherpaAsr=true -Pandroid.useAndroidX=true \
  -Dmaven.repo.local="$PWD/artifacts/speech-consumer-m2" \
  -PsherpaMavenRepo="$PWD/artifacts/sherpa-asr/m2"
```

Set `ANDROID_HOME` to the installed Android SDK for the standalone build. Its
settings include no SDK projects and require an explicit local Maven directory.
The pinned Kotlin plugin is loaded to align AGP's built-in compiler with SDK
Kotlin metadata; no metadata-version checks are disabled.

Without `enableSherpaAsr` or `sherpaMavenRepo`, standalone
`assembleContractDebug assembleSystemAsrDebug` builds the existing flavors
without sherpa. No model files are bundled in any consumer AAR; the host owns
model storage, verification and resource lifetime. The repository-root build
also supports project dependencies by omitting `usePublishedSdk`.

This proves compile/link consumption, not execution of a consumer application.
See [validation](../../docs/validation/android-sherpa-asr.md) for device evidence.
