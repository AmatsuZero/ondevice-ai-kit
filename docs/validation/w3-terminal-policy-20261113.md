# W3 allocator policy collection (2026-11-13)

`terminal_device_policy.collect_policy(root, ctx, *, serial)` collects requested
configuration evidence after the accepted terminal-report collection. It binds
the existing local report pair, collection receipt, terminal observation, ACK
and instrumentation finish before any transport or output. The collector's
local-proof helper now accepts an explicit `check_outputs=False` for this reuse;
the public report collector retains its original output preflight by default.

For a present policy, only three fixed relative/framework/shell paths reach
transport. Directory/file nonlink checks, run-as UID and canonical UID/device/
inode/size records must agree before and after two `head -c 21` reads. The exact
20-byte `EnableCpuMemArena=0` plus newline is required. Each raw read is durably
saved before the next operation. Null policy after a failed constructor records
absence without claiming file bytes or a private root.

Local invalid proof leaves zero transport and zero outputs. Operational failure
preserves existing raw evidence, records a false outcome and rethrows the same
primary BaseException; a secondary journal failure becomes a note. Final outcome
publication is never rewritten or retried. This observes requested configuration,
not native policy effect, quality, true peak or production acceptance.

## Fresh acceptance

- 348 Python methods passed: 201 observation, 18 frozen-contract guard and
  129 terminal methods, including eight policy methods and one read-order fault.
- 56 relevant Python files parsed. No Android source or package changed; the
  previously accepted build and APK evidence was preserved.
- All 120 old frozen files, the original failed heap report/verdict, 95 protected
  files, 81 prepared sources and both APK hashes remain unchanged.
- The current policy, collector and guard hashes match the independently
  supplied-source hy3 review: accept, no findings. Dependency excerpts were also
  supplied. This is a bounded source review, not the pending full-driver dual review.
- A coherent omission of policy from the contract and both review maps is rejected.

## Failures retained

The local first fragment incorrectly nested `allocatorPolicy` under `config`;
an exact external patch moved it to the actual report top level and tightened
the collection schema/model-key checks. The first wrapper response contained a
source placeholder and was not accepted; its distinct corrected response supplied
actual source. No pseudo tool text was executed.

The initial eight-method suite had two actual subcase failures: returned identity
tuples differed from persisted JSON arrays. A hy3 three-field patch converted
only returned records to lists. A separate interruption fault proved that reading
twice before saving lost the first raw read when the second transport failed;
hy3 moved the first durable write before the second read. Both original failures
and green results remain. The first green attempt used a noncanonical tempfile
root and failed the shared path guard; the parent fixture was corrected to the
resolved root, then the unchanged old source failed and the corrected source passed.

## Evidence and remaining work

Raw prompts/responses, exact patches, failed and successful tests, integration
hashes and the bounded review are in
`.omx/artifacts/w3-terminal-observation-20261112/policy-parent/`.
Complete repository results and protection receipts are in
`policy-repository-acceptance/` beside it. Only parent-test root lookups were
relocated when integrating test files.

ADB inventory remains empty. No real-device install, inference, microphone,
AiAi request, model/resource download or prior consumed-run replay occurred.
The complete dispatcher, launch validation, cleanup closure checking
`allRestored`, current full dual reviews, a new freeze and same-run TECNO evidence
remain pending. W3/R4/R5 stay open and the source branch is not pushed.
