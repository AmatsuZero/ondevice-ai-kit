# F02 instrumented Skia resource investigation design

## Intent

Determine whether the 18 `GrOp` / 2304 B objects still live after the three-cycle P4 camera page run are constructed objects, already destructed storage retained in `AtlasTextOp`'s thread-local one-slot cache, or storage lost when worker threads exit. The current allocation stack, thread-label calibration and page weak-reference results cannot distinguish those states. This is a research build, not a production dependency change or a release artifact.

## Constraints and prior evidence

- Production Compose Multiplatform remains `1.11.1`; its Skiko chain is `0.144.6` at `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0`, Skia `m144-22f58c9fd4` at `22f58c9fd43d55bde818821c04b48fda5d7ec939`.
- The actual P4 trace retains 18 `GrOp` allocations after the final page owner, bridge, Compose controller and Metal layer disappear. The existing trace gives allocation stacks and approximate thread correlation, but no destructor, free or TLS-slot values.
- Keep the protected `project.pbxproj`, identity draft and camera probe files untouched; all fetched source, build outputs, traces and device reports stay in ignored `artifacts/verification/f02-instrumented-skia-*` paths.
- Do not change the production Gradle graph, lockfiles, published packages, ordinary Demo, signing assets or model assets. Do not suppress Leaks or call private Skia cleanup APIs.

## Approaches considered

1. **Repeat Instruments Leaks/Allocations:** preserves production binary, but another identical trace still cannot establish whether `operator delete` cached a destructed object or which thread's TLS slot held it. Stop this path unless a new supported deallocation event source is available.
2. **Attach a debugger to the current device binary:** would observe breakpoints with no dependency rebuild, but five documented `devicectl`/LLDB variants yielded no inspectable image or process. Do not repeat them without a new supported attach path.
3. **Build fixed Skia/Skiko with narrow event instrumentation:** changes only an isolated research binary and can record allocation, destruction, cache insertion/reuse, `ClearCache`, free and thread identity at the exact allocator. This is the chosen path, subject to ABI and runtime-identity checks before any device inference.

## Research build

Use the fixed JetBrains Skiko/Skia revisions above. The pinned Skiko [local Skia build task](https://github.com/JetBrains/skiko/blob/9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0/skiko/buildSrc/src/main/kotlin/BuildLocalSkiaTask.kt) accepts `:prepareLocalSkiaBuild -Pskia.repo.dir=<absolute Skia checkout> -Pskia.target=ios`. Its [native configuration](https://github.com/JetBrains/skiko/blob/9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0/skiko/buildSrc/src/main/kotlin/tasks/configuration/NativeTasksConfiguration.kt) embeds the Skia static library into Kotlin/Native, with iOS arm64 and simulator arm64 targets. Publish only to an isolated Maven repository for the probe; the documented `publishToMavenLocal` task is a build reference, not authorization to overwrite the user's normal Maven cache. A fresh generated P4 page-cycle app consumes the research framework through an isolated Gradle dependency substitution. Before running it, compare Kotlin/Native framework exports, Compose renderer/Metal-layer identity and pinned dependency versions with the ordinary validation build; record each distinct Mach-O UUID against its own build and trace.

Instrument the fixed `AtlasTextOp` allocator with a bounded event stream. Each event includes monotonic timestamp, process/run ID, kernel thread ID, full pthread pointer, object address, allocation generation ID and event kind. Required kinds are `new-allocated`, `new-reused`, destructor-entry/exit, `delete-cached`, `delete-freed`, `clear-cache-freed`, and thread-exit observation. Record TLS slot address/value before and after cache operations; do not dereference another thread's TLS. The log must be non-recursive for allocator instrumentation, bounded, and report dropped events explicitly. A stress fixture must exercise same-thread reuse, different-thread deallocation, `ClearCache` on owner vs non-owner thread, and worker exit before any device run.

## Device correlation and interpretation

Run the existing three-page P4 protocol only after a signed research app is proven to contain the instrumented Skia binary and no production dependency override. Keep the normal run UUID, exact PID, source/build manifest, start trigger and 300-second Leaks recording. Match the instrumented binary UUID, marker calibration, each relevant address/generation and every event timestamp to the trace. A lost event, unmatched address, unknown generation, failed ABI check or absent render path means the run is diagnostic only.

The acceptance output is an address-by-address ledger for every research-run residual of the same class: constructed/destructed state, cache ownership, free status, thread lifetime and trace identity. The historical 18 addresses belong to a different process/run and remain historical evidence, not directly matchable objects. Only a reproducible mechanism plus a bounded fix/ownership decision and sustained reentry validation may change P4 native-resource status. This design does not close the separate physical PHPicker, lowest-OS or P11 resource/energy gates.
