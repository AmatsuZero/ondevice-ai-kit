# Single-image Demo Input Ownership Design

## Intent and scope

After the locally completed shared UI package, split host/picker work into an independently testable input-ownership package first. Both platforms need opaque selected references and private copies whose lifetime survives replacement/cancellation/close until the bridge releases all operation leases. This package supplies those providers only. Picker presentation, host lifecycle glue, default opt-in platform calls, fixed ModelStore/MLXVLM wiring and real-device inference follow separately.

No new dependency/model, download, private-media read, recording, signing mutation or push. Use tiny synthetic local files in tests. Keep base iOS15/macOS12/API26 and the optional MLX boundary. Protected files and all scratch/artifacts remain untouched.

## Approach

Chosen: reuse the existing Apple SpeechInputStore private-copy/ref-count implementation through a capability-specific `MultimodalInputStore: IOSMultimodalInputProvider`; Android adds a small native-file store implementing the existing AndroidMultimodalInputProvider. A cross-capability store extraction would expand existing Speech/Video behavior, and embedding this storage inside the bridge would break the host-owned input boundary; neither is selected.

API on both platforms: `importImage(from:)` / `importImage(File)` returns an unpredictable opaque token; `acquire(reference)` returns a native file lease; `remove(reference)` retires selection; `close()` seals imports/acquires and retires all selections. Multiple operation leases are independent; each release is idempotent and last release permits deletion. Retired tokens cannot be acquired again. Unknown/path-like references never resolve a path. A store requires a fresh owned directory and never adopts/deletes another directory or source file.

Imports accept local regular nonsymlink files only. Each successful import copies bytes into the owned directory; later source edits do not change the selected copy. The stores do not decode or assert image validity, format, quality or model support: the native multimodal backend retains bounded PNG/JPEG/snapshot admission. This is a generic host ownership layer, not a substitute for backend validation. Android uses Java NIO NOFOLLOW_LINKS and synchronized ownership transitions (API26). Apple remains MainActor and wraps existing release semantics. Deletion failures retain owned entries for retry; closing never recursively removes unknown files.

## Verification

Test public behavior on actual files: original mutation after import, two concurrent leases, replacement/remove/close retention until last release, repeat release/close, closed and unknown-reference admission, symlink/directory/nonlocal rejection and fresh-directory/foreign sentinel preservation. Android unit tests run offline; Apple generated Demo-ABI XCTest project includes the store and existing protocol/store files, with no MLX product. Update generator behavioral tests to inspect real generated project source/product lists; Binding ABI stays unchanged. Every Task observes RED before implementation, then full relevant tests and scope author review/local commit. Independent review remains pending under user's inline/no-subagent instruction.
