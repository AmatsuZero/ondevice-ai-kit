# Sherpa native version static check

`inspect_binary(data)` decodes three version-return functions in the four fixed Sherpa C-API ELF members using Python standard-library code. It reads defined dynamic symbols and file-backed `PT_LOAD` mappings, then accepts only the observed AArch64, ARM Thumb, i386 and x86-64 return sequences. Unknown encodings fail with `ValueError`; it does not execute native code or search for expected strings.

Run the focused checks from the foundation worktree:

```sh
rtk proxy python3 -B -m unittest discover -s probes/sherpa-native-version-check -p 'test_*.py' -v
```

The caller must separately pin binary/member identities and compare returned values to independent source/CI evidence. The tool has no CLI and does not perform downloads, builds, device calls or dynamic loading.

[Accepted evidence](../../docs/validation/w3-native-version-binding-20261116.md) covers only three C-API functions in four fixed ELF libraries. Version strings can be forged; this does not establish whole-source identity, arbitrary ELF validity, complete static composition, runtime behavior, production adoption or P1/P2/P4/W3 completion. Historical inspector-mode errors and rejected candidates remain in the evidence record.
