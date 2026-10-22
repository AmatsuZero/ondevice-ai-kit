# Single-image Demo input ownership — author final review

Separate author pass over 55a425b..caab202 plus Task2 working diff against spec/plan and all five Review Focus cases. User requires inline/no subagents; independent review remains pending, not APPROVE. No Critical/Important finding remains; no new Minor deferred. No merge/release verdict.

1. Both stores separate selected ownership from operation leases. remove/close prohibit new acquisition; each release is independent and idempotent. Existing leases retain bytes through retirement/close; registry retains deletion failures for retry. Android uses one lock for import/admission/retirement/release.
2. Opaque references are registry-only keys, never interpreted as paths. Private destination names come from UUID; Android uses CREATE_NEW and opens source with NOFOLLOW. Local nonsymlink regular-file admission rejects directory/nonlocal or missing sources, with actual file tests. Decoding/bounds are left with backend by design.
3. Fresh directory initialization rejects pre-existing directories and does not adopt another store. Closing removes only registered copies, then atomically removes an empty directory without recursion. Unknown sentinel bytes survive. Source content remains unchanged.
4. Apple reuse exposed an existing recursive-directory deletion bug. Ledger Ruling documents the narrow Darwin.rmdir correction, its cost and real RED evidence. Fresh complete Speech39/Video9 regressions passed after repair; no broad store extraction or unrelated behavior change.
5. Demo ABI generated sources gain only input adapter/tests and existing Speech input interfaces/store; Binding ABI dependency surface remains unchanged. Android adds no dependency/backend or app registration. No picker/host/model/UI/export/signing/protected file change. API26 Java NIO and existing Apple base floors remain; current-host/simulator execution is not minimum-OS acceptance.

Evidence: Apple Demo14/14, Binding10/10, Speech39/39, Video9/9, generator3/3 and strict Swift lint; Android new4/4 and whole app14/14 fresh, APK/lint0errors9existing warnings, tools101/101. Logs retain RED and failed sentinel run. Tests use synthetic files only. Remaining host/picker/device/real-model/quality/resource/minimumOS and independent review gates remain explicit in validation.

Declined to judge: none. No cleanup of workspace/artifacts, no remote effects.
