# F02 iOS notice publication and consumer retention

Use this supplement only for the dedicated iOS candidate. It does not change the production
Skiko version or automatically embed materials into a final iOS App.

Apply in order to fixed Skiko `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0`:

1. `Skiko-ios-only.patch`
2. `Skiko-ios-source-notices.patch`
3. `Skiko-ios-notice-publication.patch`

Apply in an independent source export outside another Git worktree (or with explicitly scoped
patch tooling). `git apply` below a parent Git repository can report exit0 while skipping paths;
use verbose output and compare all seven resulting source files against the source receipt.
Frozen `.1/.2` artifacts remain unchanged. `.3` is a new normally generated release candidate.

The publishing supplement reads explicit `-Pskiko.notices.dir=/abs/notice-input`. The directory
must contain `NOTICE-INDEX.json` and all indexed original texts. It registers a reproducible
`thirdPartyNoticesJar` (`notices` classifier) on each publication and puts the same material into
root `sourcesJar` and both target `*SourcesJar` tasks. It does not modify generated Klibs,
POMs, module metadata or published archive contents after Gradle produces them.

Run the same strict iOS-only offline publication command documented in [the base repair](Skiko-ios-only.md),
using `-Pdeploy.version=0.144.6-ondevice-f02.3 -Pdeploy.release=true` and the notices property.
The verified builder uses the retained JDK21 under Gradle's cached `jdks` directory; inspect
`publication.json` for the exact path instead of assuming a Homebrew/SDKMAN installation.

Current candidate material has69files: original component licenses/notices/patent text,
file-specific libjpeg and SkParagraph comments, Unicode review references, attribution,
modification notes and a SHA256 index. Both native targets' Ninja inputs cover3994local files,
including2115headers and10external components. This inventory and the notice delivery proof
are not an independent legal approval. Historical Unicode terms/file exceptions, final App
material retention and production acceptance remain explicit gates.

## Consumer collection

Declare the candidate's local or authorized remote Maven repository. During the verified local
consumer run, exclusiveContent restricted `org.jetbrains.skiko` to the isolated candidate repo,
so Gradle could not silently fall back to unpatched Skiko artifacts.

Apply the [collection script](collect-skiko-notices.gradle.kts) from a consumer Gradle build:

```kotlin
apply(from = "/abs/collect-skiko-notices.gradle.kts")
```

```sh
rtk proxy ./gradlew --offline -PskikoNoticesVersion=0.144.6-ondevice-f02.3 collectSkikoNotices
```

The explicit `org.jetbrains.skiko:skiko:<version>:notices@jar` dependency is non-transitive.
`collectSkikoNotices` writes the original tree under
`build/distribution-materials/META-INF/ondevice-ai-kit/notices/`. Keep that tree with the SDK
or final App distribution and verify its index/hashes. Merely resolving the iOS Klib does not
perform this collection or final-App packaging step.

Existing ignored evidence is required for read-only revalidation:

```sh
rtk proxy python3 artifacts/verification/f02-notice-delivery-20261106/verify.py
```

The check covers exact repository/archive bytes, all six notice/source archives,18 source
comments,69 collected files,6module references,40embedded native archives,linked framework
identities and protected inputs. No upload, remote anonymous consumer, device resource or
release conclusion is inferred from local compilation and collection.
