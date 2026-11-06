# Nexus plan/archive consistency preflight

## Decision and goal

The user requested more parallel helper work, specifically Nexus and Copilot.
Copilot's supplied-context review identified that the standalone .6 preparation
script checks archive and local graph separately. Although earlier publication
evidence exists, that script does not itself prove plan rows equal archive members.
Add a separate read-only cross-check, preserving all frozen plans and archives.

Alternatives are rewriting historical preparation evidence (would change its
identity), or treating separate digests as equivalence (does not prove it). The
primary agent chooses one standard-library Python checker and synthetic tests.
Copilot supplies a candidate; the primary agent applies tests first and verifies it.

## Interface and exact target

`tools.nexus_plan_archive.validate_archive(plan: dict, archive: Path) -> dict`.
CLI: `python3 -m tools.nexus_plan_archive PLAN_JSON ARCHIVE_TAR_GZ`.
Read-only, no network, credentials, downloads, writes or tar extraction to disk.
The current .6 archive has exactly 115 regular members and no directory members.

Plan is extensible, but requires exact integer schemaVersion1, nonblank version
token (letters/digits/dot/dash), positive exact-integer fileCount/archiveBytes,
lowercase64-hex archiveSha256, HTTPS hostedCandidate ending slash/no userinfo,
and files list with length=fileCount. The base URL rejects whitespace and all
Unicode Cc control characters. Each file entry has exactly path/url/bytes/
sha256; bytes is a positive exact integer, digest lowercase64-hex, URL exactly
base+path. Path has six POSIX components: org/jetbrains/skiko/module/version/name;
module is skiko, skiko-iosarm64 or skiko-iossimulatorarm64. Filename starts with
module+'-'+version and matches `[A-Za-z0-9][A-Za-z0-9._-]*`; URL-reserved characters,
whitespace and encoded separators are refused. Refuse absolute/empty/dot/dotdot/backslash/duplicate paths.
This excludes mutable module metadata.

Open a regular archive without following symlinks or blocking on a FIFO, verify
the platform supports the required nofollow/nonblock flags (otherwise fail), verify
its byte size/digest first, then seek to start and stream every tar member without
extracting to disk. All members must be regular with safe unique names, exactly
the plan's path set, member sizes and SHA-256. Hash in bounded chunks. Use one
opened descriptor and reject changes in size/mtime_ns/ctime_ns during the check.
No claim of defense against arbitrary hostile concurrent mutation.

Output exact keys: schemaVersion1, kind=nexus-plan-archive-consistency, version,
validatedFileCount, archiveSha256, archiveBytes, validationStatus=pass-bounded,
uploaded=false, remoteConsumerValidated=false. No URLs/private paths in output.
Errors are ValueError; CLI emits a generic nonzero diagnostic. CLI rejects
duplicate JSON keys and nonfinite numbers including unknown extension values.

This check does not establish source/license applicability, current remote absence,
upload approval, actual remote retrieval or consumer compile. Do not query Nexus
management APIs or repeat already pending destination/upload questions.

## Validation

Synthetic tar.gz fixtures prove correct binding; rehash an archive after modifying
one member to prove member checks are independent; reject missing/extra/duplicate/
linked/special/unsafe entries, schema aliases/path/URL/hash errors and bad archive
digests/sizes. Then read the actual .6 archive once and compare all 115 plan rows.
Preserve the old archive and plan hashes, production dependency, protected paths
and Apple lock. No publication or remote success can be inferred from this result.
