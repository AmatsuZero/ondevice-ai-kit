# W3 isolated Qwen CPU arena hypothesis

Intent: test one evidence-backed allocator hypothesis after the consumed default-allocator FILE run exceeded 1.5GiB. The root cause of that growth is unproved. This is a new diagnostic candidate, not a replay or acceptance of the failed run.

The primary agent chooses a one-variable CPU arena-off policy because fixed Sherpa source exposes `EnableCpuMemArena=0`, the actual AAR has matching parser strings, the actual JVM provider DTO round-trips the suffix, and source-fragment checks confirm CPU dispatch. This supports preparation, not claimed Android execution or savings. The user's standing autonomous continuation instructions replace a new design-approval handoff; this design is the agent's call, not a user-approved technical conclusion.

Considered alternatives: changing memory-pattern plus arena would confound attribution; graph/thread/token/audio changes would change more than allocation policy; a larger memory budget would conceal the failed gate. Prepare only arena-off. Memory-pattern stays at runtime default.

Create a small pure Kotlin `QwenArenaPolicy` candidate in `probes/android-qwen-arena/`. It writes exactly `EnableCpuMemArena=0\n` into a newly reserved alias-owned private child directory, syncs and re-reads exact bytes, then returns the canonical `cpu:<absolute config path>` and SHA/size receipt. Reject symlink ancestry, existing policy directory/file, bad content or changed bytes. Reverify immediately before use; no fallback to plain CPU if policy fails. No JNI, Android, download, production, process or device operations in this module.

Parent validation uses real Kotlin/JDK and filesystem behavior. It must prove exact bytes/receipt/provider, exclusive collisions, no existing file overwrite, symlink rejection including ancestors, tampering detection, changed size/SHA, no extra keys and unchanged external file. The module alone cannot prove native policy consumption or lower RSS.

Future wrapper integration needs a distinct instrumentation class/run/report identity; record actual provider string and config bytes/SHA before the constructor, verify both again immediately before native dispatch, and retain one recognizer / unchanged whole-PCM two-stage case core. Keep old wrapper/source/APK/report immutable. Build/package and independent review must pass before any new native run is considered. This document authorizes local candidate preparation only; it freezes no new physical run.

Every future native experiment needs its own explicit frozen contract. Existing models/AAR/65 inputs/reference, language admission/forced decoding, CPU2/128mel/greedy/seed42/temperature1e-6/topP0.8/512/128 and 1.5GiB/512MiB/120s gates remain. No microphone/AiAi/new model or altered production defaults. No automatic replay, parameter sweep or threshold relaxation. W3 remains open.
