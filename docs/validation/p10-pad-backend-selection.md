# P10 platform PAD backend selection

Date: 2026-10-19. Status: **blocked-unverified; no production PAD backend selected**.

This record is Task 10's fail-closed evidence gate. Task 7's liveness session
and its internal fixture backend remain the only available implementation. No
third-party SDK, cloud service, platform entitlement, model weight, attack
dataset or production threshold was added.

## Missing selection evidence

There is no approved candidate record covering all of the following:

- exact Apple/Android SDK or runtime revision, license and commercial use;
- offline behavior, upload/credential/region requirements and privacy review;
- passive/active mode, challenge and frame protocol, supported platform floors;
- model binding, preprocessing, resource envelope and categorical versus
  continuous score semantics;
- authorized presentation-attack taxonomy and data, operating point and
  device matrix; and
- calibrated PAD policy revision with denominators and confidence intervals.

An API that reports face detection, tracking or a generic liveness boolean is
not treated as a PAD identity decision without those bindings. No APCER, BPCER,
ACER, FRR, FAR or standards-compliance claim is inferred from fixtures or
documentation.

## Runtime behavior while blocked

The public `FaceLiveness` contract remains usable for lifecycle and integration
testing with the internal fixture backend. It preserves the nonce-bound active
challenge, authorization/revocation gate, bounded frame queue, deadline/stop
drain and one-terminal-result rules. A missing or explicitly unavailable
production PAD backend is surfaced as typed `backendUnavailable`; an unverified
`PADPolicy` is rejected as `thresholdUnverified`, with categorical/continuous
score fields left null. The Task 7 backend is not registered as a production
default and its synthetic outcomes are not accuracy evidence.

## Required before enabling a platform backend

1. Approve one exact backend/SDK per platform and capture the license, privacy
   behavior, version, model binding and platform/API floors.
2. Approve an attack taxonomy and authorized PAD data, with genuine-user and
   attack denominators; keep data out of the repository and logs.
3. Fix passive/active challenge policy and a calibrated operating point, then
   bind the verified PAD policy to backend/model/challenge revisions.
4. Run cancellation, revocation, timeout, device and resource checks plus the
   authorized attack harness. Publish grouped metrics only after those gates.

No credentials, registrations, external writes, model downloads or real
biometric/PAD samples were used for this audit.
