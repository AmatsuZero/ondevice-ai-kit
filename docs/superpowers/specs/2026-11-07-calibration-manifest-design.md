# F10 calibration dataset preflight

## Intent and decision

Make the first executable step from the F10 exploration: validate a local dataset
manifest before any calibration or evaluation. The current autonomous roadmap scope
authorizes this reversible tooling. The primary agent chooses a standard-library
Python validator, consistent with `tools/`, without a new approval handoff.

Alternatives: add platform-specific importers (duplicates protocol checks and touches
device state), or only document a future checklist (does not enforce it). A local,
read-only manifest validator supplies a reusable boundary for later score/metrics
tools. This is one deliverable; metrics and policy generation remain separate tasks.

## Contract

`tools.identity_calibration_manifest.validate_manifest(document: dict, root: Path)
-> dict` validates Schema 1 and referenced local files. The CLI is
`python3 -m tools.identity_calibration_manifest MANIFEST`; references are relative
to the manifest's directory. JSON on stdout only; errors exit nonzero without
echoing sample identifiers or private paths. No downloads, inference or file writes.

Exact top-level keys:

- `schemaVersion`: integer 1 (not bool/float).
- `kind`: `identity-calibration-manifest`.
- `dataClass`: `synthetic` or `real`.
- `task`: `verification`, `identification`, or `passive-pad`.
- `dataset`: exact `id`, `revision` (nonblank tokens), `sourceURL` (HTTPS, no userinfo).
- `purpose`: `local-calibration` or `local-evaluation`.
- `redistributeDataset`: bool.
- `licenseEvidence`: file reference.
- `authorization`: exact `allowedPurposes` (nonempty unique subset of the two
  purposes), `redistributionAllowed` (bool), `evidence` (file reference).
- `binding`: exact nonblank-token `backendId`, `modelId`, `modelRevision`,
  `preprocessRevision`, `scoreMetric`; lowercase 64-hex `modelSHA256`;
  `scoreDirection`: identity `higher-is-match`/`lower-is-match`, PAD
  `higher-is-bona-fide`/`lower-is-bona-fide`.
- `samples`: nonempty list, each exact `id`, `subjectId`, `sessionId`,
  `mediaGroupId` (tokens), `split` (`calibration`/`test`), `role`, `label`,
  `pai`, `file` (file reference).

All token fields are single strings matching `[A-Za-z0-9][A-Za-z0-9._:-]{0,127}`;
`pai` is JSON null for bona-fide/identity or a single token for an attack.
Source URLs reject whitespace/control characters and malformed/out-of-range ports.
A file reference has exactly `path` (nonempty relative POSIX path, no dot/dotdot,
backslash or empty components) and lowercase 64-hex `sha256`. Resolve only regular
files beneath the root, refusing symlinks and special files; stream hashes without
loading media into memory. Detect changes to the opened file while hashing. This
is a local preflight, not a claim of immutability against an ongoing hostile writer.

The selected purpose must appear in the authorization declaration. Redistribution
permission is required only when `redistributeDataset=true`. Evidence must exist
and match its digest; the tool does not interpret legal prose, authenticate a
signatory, establish consent or independently approve the declared use.

Sample IDs and media digests are unique; a media group cannot cross calibration
and test (declared grouping catches known recodes/crops, not undetected derivatives).
Subjects cannot cross these two splits. Subjects may overlap enrollment and probe
within the same split, as genuine matching requires, but the same subject/session
cannot be used for both roles. Session IDs are scoped to subjects, not globally.
An identity media group also cannot appear in both enrollment and probe: different
file hashes or re-labelled sessions do not establish independence of derivatives.
Both splits must exist. Identity samples use enrollment/probe, bona-fide, null pai;
both roles must exist in each split. PAD uses presentation, bona-fide/null pai or
attack/nonblank pai; each split has both labels. No invented universal device split.

Return only aggregate counts per split/role/label/PAI, binding, dataset revision,
evidence digests, `dataClass`, canonical manifest SHA-256, and explicit:
`validationStatus=pass-bounded`, `authorizationStatus=declared-scope-only`,
`calibrationStatus=unverified`. Never output sample IDs, paths, images or embeddings.
Counts and PAI metadata remain potentially sensitive. Canonical SHA uses sorted,
compact ASCII JSON with NaN/Infinity forbidden. CLI additionally rejects duplicate
JSON keys. Do not mutate the input document or change SDK policies.

## Verification and boundaries

Synthetic byte files only: identity/PAD success with hand-checked counts; genuine
subject overlap within a split; same session token for different subjects; explicit
purpose/redistribution denial; digest tampering; subject/media/session leakage;
roles/classes/PAI; missing splits, keys/types/direction, non-finite/duplicate JSON;
symlink/path escapes/FIFO/directory; no emitted identifiers or writes. A tool pass
does not establish trial completeness, representative data, label correctness,
privacy approval, real accuracy, production thresholds, PAD readiness or release.
