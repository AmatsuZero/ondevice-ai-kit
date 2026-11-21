# Explicit CPU text and GPU vision

User approved this optional policy on 2026-11-04 and requested continued implementation.

Design: retain CPU_ONLY as the public and descriptor default. Add CPU_TEXT_GPU_VISION as an explicit model policy, carry it unchanged through the model spec/session/config, validate actual text and selected vision backend before engine creation, and keep runtime 0.18.1 fixed. No automatic fallback. Preserve asset/hash/license/admission/lifecycle gates. Resolve the model budget sentinel from fixed official sources before changing that check.

1. RED→GREEN public policy/default, session propagation and descriptor default/explicit/rejection behavior.
2. RED→GREEN selected-backend metadata checks and fixed-runtime budget semantics; configure native engine accordingly.
3. Update metadata and real-model probes, construct the authorized local bundle, run fresh offline suites/build/lint and one bounded physical-device inference attempt with the synthetic red PNG.
4. Independent bounded review, fix substantive findings, update validation/handoff with precise gaps and check protected hashes. Keep logs and unrelated dirty files. No push/merge/release.

Success: explicit mixed path works in code and passing local checks; real inference is reported only from an actual terminal report. Device/runtime failures remain explicit.
