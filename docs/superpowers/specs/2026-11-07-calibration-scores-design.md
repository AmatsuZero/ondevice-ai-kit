# F10 bound offline scores and descriptive metrics

## Decision and purpose

Continue the existing F10 plan after dataset preflight. Validate caller-supplied
scores against a checked manifest, then calculate descriptive fixed-threshold
verification, rank-1 identification and passive PAD metrics. This advances the
authorized local roadmap without downloading data or enabling a policy.

Chosen: one score/protocol module plus one small counting module, standard Python.
Alternatives: put metrics into mobile SDKs (unnecessary production changes), or
accept arbitrary class-labelled arrays (loses media/model/split binding). No new
dependencies. Primary owns protocol integration/tests; cbc hy3 owns a short counting
candidate only. The independent hy4free Identity resource lane keeps its handle.

## Inputs and interface

`tools.identity_calibration_scores.summarize(manifest: dict, scores: dict, root: Path)
-> dict`, using existing `validate_manifest(manifest, root)` before score processing.
CLI `python3 -m tools.identity_calibration_scores MANIFEST SCORES` emits JSON only.
References remain relative to MANIFEST's directory. No writes/network/inference.
Reject duplicate/non-finite JSON, symlink/special-file input documents and invalid
schemas with a generic CLI diagnostic, without identifiers/private paths.

Scores Schema1 has exactly schemaVersion(integer1), kind(identity-calibration-scores),
manifestCanonicalSHA256, binding, task, split(calibration/test), threshold and rows.
Binding/task must equal the checked manifest. Split calibration requires declared
purpose local-calibration; split test requires local-evaluation. Threshold and every
non-null score are finite Python int/float, excluding bool and unrepresentably large
integers. No threshold fitting, optimization, ROC sweep or policy export occurs.

Rows are nonempty and task-specific; unknown/duplicate/cross-split/wrong-role sample
references fail. Labels and known/unknown identities derive from manifest metadata,
never score-file labels. A score of JSON null is a recorded nonresponse, not an
automatic correct decision. Comparisons use >= for higher-is-match/bona-fide, <=
for lower direction, including threshold equality.

- verification row exact enrollmentId/probeId/score. Each pair is unique. A subset
  of possible pairs is permitted and reported as supplied-pairs, not a proof of a
  complete independent trial design. Genuine iff subject IDs equal.
- passive-pad row exact sampleId/score. Every selected-split presentation occurs
  exactly once. Bona-fide/attack and PAI come from the manifest.
- identification row exact enrollmentId/probeId/score. Require exactly one cell
  for every selected-split enrollment x probe pair. Gallery template count and
  distinct identity count are reported separately. Any null cell makes that probe
  a nonresponse. Otherwise choose the best score; multiple equal top scores from
  the same identity are one candidate. Ties across distinct identities reject the
  search, independent of true identity. This is an explicit rank-1 reject-ties
  protocol, not evidence of a mobile index implementation. A probe is known iff its
  subject appears in the gallery. An unknown search is one FPIR trial, not N pairs.

## Counting module contract

`binary_counts(records: list[dict]) -> dict`: each record exact positive(bool) and
accepted(bool or None). Empty list allowed. Return exact positive/negative groups,
each exact trials, responses, nonResponses, errors, errorRateOnResponse,
nonResponseRate. Positive error is rejected; negative error is accepted. Missing
denominator returns JSON null, never 0%. Nonresponse rates use total trials;
error rates use responses. Inputs are not mutated, malformed types/fields fail.

`search_counts(records: list[dict]) -> dict`: record exact known(bool), outcome,
tied(bool). Known outcomes correct/wrong/rejected/nonresponse; unknown outcomes
accepted/rejected/nonresponse. tied=true requires rejected. Empty list allowed.
Return exact keys known, unknown, tiedSearches. Known exact trials/responses/
nonResponses/correct/wrong/rejected/tpirOnResponse/fnirOnResponse/nonResponseRate.
Unknown exact trials/responses/nonResponses/accepted/rejected/fpirOnResponse/
nonResponseRate. Conditional rates have response denominators; nonresponse rates
have total denominators; zero denominator -> null. fnir includes wrong+rejected.
No confidence bounds or independence assumptions are manufactured.

## Reports and limits

Output schemaVersion1, kind=identity-calibration-score-summary, task/split,
manifestCanonicalSHA256, scoresCanonicalSHA256(sorted compact ASCII JSON), declared
binding, dataClass, caller threshold/direction, protocol and aggregate metrics.
Always validationStatus=pass-bounded, calibrationStatus=unverified,
thresholdOrigin=caller-supplied-unverified. Never sample/subject IDs, paths, images,
embeddings or per-sample scores. PAI names/aggregates remain potentially sensitive.

Verification exposes FAR/FRR explicitly named farOnResponse/frrOnResponse plus
binary counts. PAD reports bpcerOnResponse, per-PAI apcerOnResponse/counts and
apcerWorstPAIOnResponse; worst is null if any PAI has no responses. Also keep all
nonresponse counts/rates. These conditional metrics do not pretend an unspecified
ISO failure-handling policy, full accuracy acceptance, ROC/DET or trial independence.
Identification exposes search counts and declared gallery scale, rank1 and tie
policy. Missing rate denominators remain null. Manifest declaration checks do not
authenticate legal authority, actual score provenance or labels. Protocol completeness
and representative real data, statistical inference/ROC/DET, calibrated thresholds,
production policy and release approval remain open.

## Verification

Hand-checked synthetic outcomes for both score directions and equality, nulls/zero
denominators, genuine/impostor pairs, PAD per-PAI weakness, and known/unknown rank1
searches. Mutating binding/hash/split/purpose, duplicating/omitting/misreferencing
rows, mixing labels/roles or injecting bool/NaN/huge scores must fail. Reject ties
across identities but accept same-identity template ties. Prove 1:N FPIR denominator
is unknown searches. Confirm input preservation, aggregate privacy, no writes and
calibration remains unverified. Synthetic files only, no existing device inference.
