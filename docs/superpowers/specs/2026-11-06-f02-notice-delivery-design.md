# F02 notice delivery design

Continue the repaired fixed iOS-only publication with complete, retrievable notice material.
Consumers need the original license texts when redistributing native libraries; Maven POM
license labels alone do not carry those texts. Keep candidate .2 immutable and use .3.

Use normal MavenPublication classifier artifacts (notices JAR) on all three modules and
include the same material in normally generated source JARs. A documented Gradle consumer
configuration explicitly resolves the root notices classifier and copies it into a distribution
materials directory. Verify that directory by exact file hashes, and retain its responsibility
for final App distribution rather than pretending Maven automatically embeds resources in iOS.

Alternatives: editing generated Klibs/module metadata would break provenance and is excluded;
a sidecar tarball alone is easy for consumers to miss. Classifier + source archives uses the
existing Gradle publishing APIs and adds no dependency. Agent implementation choice under the
standing autonomous/solo instruction; no new approval or reviewer loop.

Include retained Skiko/Skia and10 external-component texts, file-specific libjpeg notices,
Unicode review material, source provenance and required IJG attribution. Cover native input
headers using retained Ninja dependency data if available; uncertain file-level/generated-data
terms remain explicit in the material. A packaging proof is not legal/independent approval.
No new network/download/device/publication/production-switch actions.

Success: normal .3 release publication; modified source comments visible in source JARs;
notices available under all3 coordinates; a real consumer resolves and preserves exact bytes;
source/binary graph stays complete and original native archive hashes match. Preserve every
protected path, production lock and .1/.2 artifact. Roadmap gates only change on scoped evidence.
