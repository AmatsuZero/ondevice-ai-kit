# Qwen3-VL video251 final-frame coverage diagnostic

Goal: distinguish a demonstrated omitted final count state from remaining model/configuration failure, without changing the original valid36 score.

Spec: `docs/validation/f08-qwen3vl-valid36-20261114.md`, bounded visibility finding and next step. This is posthoc diagnosis with the answer known, not a new benchmark or sampler adoption.

Execution: primary agent, already authorized; no helper retry after recorded provider failures. Use existing foundation worktree, existing MLX Swift Metal runtime/checkpoint, eight frames, 512 budget, balanced512 prefill, temperature0, maxTokens32, online A/B/C then EOS. No CPU/Torch/Ollama model inference, downloads, device changes or production edits.

Frozen intervention: replace only original frame7 (26.39305972639306s) with the already decoded tail frame7 (29.896563229896564s), where the third object is visible. Preserve frames0..6, question/options, expected C and frame count. No alternate frame/prompt search after results.

Three ordered arms, each once in one host Swift Metal process:

1. Original eight-frame baseline, for same-process host calibration against retained phone A=No.
2. Natural replacement: new frame7 and its actual time; recompute official text/BPE/positions.
3. Artificial pixel-only replacement: identical new frame list, original timestamps/tokens/positions; verify pixels equal arm2. Explicitly artificial time metadata.

Tasks and acceptance:

- [x] Preserve prior final-file hashes; freeze selection before model inference. Derive natural Python CPU preprocessing expectations using existing accepted tools; official text/packing and RoPE AST checks.
- [x] Run existing Swift preparation-only executable on three arms; require exact expected tokens/positions/THW, finite packed pixels, baseline hash equal retained phone, new arm pixel hashes equal, temporal groups0..2 unchanged and only group3 pixels changed. Negative checks reject changing any other frame/time/question/label.
- [x] Derive isolated host source by changing only run identity, claim text and required case count3; preserve all model/protocol/grammar/resource guards. Freeze sources before build; build against cached dependency revisions without updates.
- [x] Freeze final input/source/binary/model identities, confirm empty GPU queues and normal host thermal start, run once. Preserve all terminal/guard failures; no automatic model retry.
- [x] Independently audit six complete151936-dimension vectors, grammar argmax/EOS/decode/28KV/rope/input hashes; compare three outputs and retained phone baseline separately. Rehash protected artifacts, document limits, inspect disk/reproducible outputs. Keep W6 open and original16/36 unchanged.

Stop condition: all three bounded diagnostic results verified, or a concrete execution failure retained. No same-case search beyond these frozen arms. Host results do not establish phone thermal behavior or product acceptance. Native429 prevents a new independent reviewer; author acceptance review is explicitly weaker.

Outcome: all three answers A (No), gold C (Yes); six full vectors accepted. Coverage-only explanation insufficient for this case. Original16/36 and W6open unchanged. Tooling failure before launch retained; one actual model process, no retry. Author review only under native/provider stop rule.
