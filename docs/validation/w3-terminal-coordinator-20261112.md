# W3 local terminal observation coordinator

The local coordinator is integrated and verified. W3/R4/R5 remain open. This
milestone supplies actual local file reads and durable report/receipt/ACK
publication around injected sample callbacks; it does not collect Android
process data or transport a report from a device.

- Source: `probes/android-terminal-observation/terminal_observation_coordinator.py`,
  SHA256 `5fcb411735e2774b6078328e74bbc9cdf67c3aee9e6d2a1769182877d2a943dc`.
- Fresh Python suite: **102 methods passed**, comprising 22 validator, 45 file
  primitive and 35 coordinator methods, with additional boundary subtests.
  All probe Python files pass AST syntax checks.
- The source was delivered by free hy3 and integrated byte for byte. The first
  candidate passed 22 helper tests and seven initial parent methods, but expanded
  parent checks reproduced nine failures and one error. It was rejected before
  integration. A separate 126.688s boundary correction passed the unchanged 22
  helper and 12 parent methods; the final review reproduction adds one parent
  method. Earlier failing fixtures and outputs are retained, not overwritten.
- Missing-feature RED is a real `ModuleNotFoundError`, with no skipped tests.
  Earlier test candidates had fixture/arity defects; only the corrected 22-test
  candidate is integrated. The final expanded test set was not all run against
  the first implementation, so its historical 9/1 count applies to that saved run.

The coordinator preflights the canonical root, distinct valid paths, regular
source and three absent outputs, including all parent directories, before any
callback. It snapshots binding and each callback result. Event order is
clock/sample-before/read1/clock/read2/clock/sample-after/clock, validation, then
durable observed report, strict deterministic UTF-8 JSON receipt and ACK. Clock
regression stops at the first invalid step. Each sample independently passes
identity/type/resource gates; resource aggregation is max RSS/min available
across two samples, not a true peak. Existing 1.5 GiB/512 MiB bounds are unchanged.
Any error stops without retry; previously durable outputs remain. The typed
postpublication exception propagates without being converted to success.

Final free hy3 supplied-context source review completed in 155.837s with `accept`
and no critical/important finding. Its minor claim that missing binding PID or
startTicks causes KeyError is not reproducible: real coordinator checks reject
each as ValueError during the first sample validation, before ACK or output
writes. The independent regression proves both variants; source is unchanged
since review. This is a static source review, not independent device or release
acceptance. No native invocation or retry was made.

The old 120-file heap freeze and both original report reads still match their
hashes. The old false verdict remains
`3575b70f0531560aeecc94935cf41f4415c15e1a5bef7e494b8f57c2cb31681d`;
the consumed physical run remains rejected 19/20. No new ADB, installation,
inference, microphone, AiAi, model/runtime download or production change occurred
in this milestone. The source branch has not been pushed.

Reproduce from foundation:

```sh
rtk proxy python3 -B -m unittest discover -s probes/android-terminal-observation -p 'test_*.py' -v
```

Primary evidence is retained locally under
`.omx/artifacts/w3-terminal-observation-20261112/`: `coordinator-tests-arity-fix/`,
`coordinator-implementation-candidate/`, `coordinator-boundaries-fix/` and
`coordinator-final-review/`. [Machine receipt](w3-terminal-coordinator-20261112.json).
The shared handoff/roadmap/pitfalls prefixes are staged separately from unrelated
dirty content. Remote synchronization, when complete, has a separate receipt.

Next integration gates remain real bounded same-process sampling and fresh report
transport, Android stale-ACK refusal and bounded local reads, a separately named
wrapper/driver, race/fault checks, Android build/lint/APK review and an independent
frozen unconsumed device contract. No old run is replayed. Full physical quality
and resource acceptance, source-to-AAR/recursive composition, production notices
and release review also remain open; local 102/102 is not W3 completion.

Synchronization completed: GitHub #15 comment 6817725675, GitLab #15 note
43492 and GitHub Wiki `aeb41fad` were read back exactly; both Wiki content hashes
match. [Synchronization receipt](w3-terminal-coordinator-sync-20261112.json).
Source commit `1e70b86` remains local.
