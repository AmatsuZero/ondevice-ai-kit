# LiteRT-LM availability consumer

This optional Kotlin app queries the public `LiteRtLm.availability` API against a real app-private `ModelStore`. It displays `REQUIRES_ASSETS/MISSING_ASSETS` on a supported device with an empty store. The model reference and relative filenames are placeholders; no model is bundled or downloaded.

Installed assets still return `UNAVAILABLE/UNSUPPORTED_DEVICE`: the pinned LiteRT-LM 0.18.1 native drain contract is unproven, so production backend construction and generation remain unavailable. This app verifies dependency wiring and availability UI only.

Build from project dependencies:

```sh
rtk proxy python3 scripts/gradle-local.py :examples:android-litertlm-native:assembleDebug
```

Verify the published artifact locally by publishing the four SDK modules first, then building with `-PusePublishedSdk=true`:

```sh
rtk proxy python3 scripts/gradle-local.py :android:sdk-core:publishToMavenLocal :android:model-store:publishToMavenLocal :android:multimodal:publishToMavenLocal :android:backend-litertlm:publishToMavenLocal
rtk proxy python3 scripts/gradle-local.py -PusePublishedSdk=true :examples:android-litertlm-native:assembleDebug :examples:android-litertlm-native:dependencies --configuration debugRuntimeClasspath
```

The app explicitly opts in to `backend-litertlm`; the default Demo and `android:multimodal` do not depend on the vendor runtime. Instrumentation exercises the empty-store Activity without model weights or personal media. It expects a device supporting `arm64-v8a` or `x86_64` and Android API 26 or later; minimum-system runtime acceptance remains a separate gate.
