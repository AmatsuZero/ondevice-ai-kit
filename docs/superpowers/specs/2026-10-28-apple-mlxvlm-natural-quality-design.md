# Apple MLXVLM Natural Video Quality Evidence Design

## Intent

Add a bounded, reproducible natural-video quality evidence package for the
existing public `common-swifts-24fps.mp4` fixture. The package should make the
semantic quality claim auditable without changing the production SDK, model,
Demo, or device input path.

## Goals

- Freeze the fixture identity and observable scene facts: 2 seconds, 320×180,
  24 fps, 48 frames, BT.709 SDR; multiple birds flying against blue sky with
  clouds.
- Define a small rubric for the existing prompt “Describe what happens in this
  video in one brief sentence.” The rubric scores only: subject (birds), action
  (flying/in flight), and scene (blue sky/clouds).
- Preserve raw per-run output, model/backend identity, timing, and fixture hash.
- Run repeated physical-device inference using the already-installed model and
  public fixture, then report rubric results and temporal-observation limits.

## Non-goals and anti-goals

- No model, prompt, tokenizer, SDK, or Demo behavior changes.
- No claim of aesthetic quality, factual completeness, temporal grounding,
  human preference, GPU/energy efficiency, or production readiness from this
  rubric alone.
- No Photos writes, private-media reads, minimum-OS claims, or Android backend
  work.
- A passing semantic rubric is bounded evidence, not closure of the complete
  F08 natural-quality or P11 quality/resource gates.

## Approach

Use the existing standalone MLXVLM consumer and its fixed public fixture. Add a
read-only Python rubric validator that consumes a normalized run report and
checks exact fixture/model/prompt identity plus conservative token/phrase
coverage. The validator must reject missing fields, wrong fixture hashes,
unknown model revisions, and outputs that match only generic scene words without
the required subject/action/scene categories. It emits per-run category results
and an aggregate summary; it never edits raw reports.

The initial rubric is deliberately lexical and conservative. It is a contract
for what the report proves, not a substitute for human or task-set quality
judgment. Temporal quality remains `unverified` unless a future task supplies a
multi-event reference set and an explicit temporal scoring rule.

## Evidence contract

Each normalized run contains `runId`, `device`, `osVersion`, `bundle`,
`sourceCommit`, `backend`, `modelRevision`, `runtimeRevision`, `fixtureSha256`,
`prompt`, `output`, `durationMs`, `qualityScope`, and the authoritative source
report path/SHA-256. The aggregate records the
rubric revision, input manifest hash, expected run count, per-category counts,
and an explicit list of unverified dimensions.

## Stop condition

Stop after the validator has focused regression coverage, the physical-device
runs are retained with raw logs, and the aggregate report is independently
reproducible. Keep F08 and P11 open if any required run or quality dimension is
missing.
