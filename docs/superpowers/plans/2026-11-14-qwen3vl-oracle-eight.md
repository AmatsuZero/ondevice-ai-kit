# Qwen3-VL video_9676 eight-frame coverage diagnostic

Scope: posthoc oracle event coverage on one known failure, not blind data,
an improved benchmark, a generic sampler or product acceptance.

Hypothesis: uniform eight-frame selection loses pen presentations required to
count five object identities. Test whether supplying those presentations at the
same frame count changes the existing C (3) answer.

Freeze requested seconds before inference: **0, 3, 9, 14, 19, 24, 29, 32.5**.
These cover the initial scene, first cup, spotted cup, black pen, red/white pen,
blue/white pen, repeated spotted cup and repeated first cup. Selection derives
from the existing dense forensic sheets with answers known. Record AVFoundation
actual timestamps; assess actual processed input visibility before inference.

Use the pinned Qwen3-VL-2B-Instruct-4bit revision, existing offline environment,
eight frames, 512 image bound, same question/options/answer, full prefill,
greedy32 and original strict scoring. No output grammar in either new arm.

1. Preserve and verify old evidence. Derive isolated source copies; never edit
   frozen originals, shared dependencies, ordinary Apps or defaults.
2. Export the eight PNGs using the existing AVFoundation admission/export path,
   changing requested times only. Record hashes, requested/actual times.
3. Prepare natural new pixels with new physical time tokens. Independently
   verify ICC/sRGB/resize/packing, actual BPE, official position AST and shape.
4. Add a second, artificial control: replace only original packed pixels with
   the same new pixels, preserving original text/timestamps/tokens/positions.
   This is intentionally not a natural video protocol input. Verify unchanged
   fields and a negative case that rejects changed timestamps.
5. Freeze both rows and run exactly once each in one serialized MLX Metal
   process, after checking ComfyUI and Ollama queues. Save complete logits,
   token IDs, EOS and 28 cache offsets. No Torch/CPU/Ollama inference, downloads
   or device operations.
6. Independently audit vectors, decoding, scoring, cache offsets and frozen
   inputs. Report both outcomes even if still wrong. Stop this branch without
   frame/prompt sweeps. F08 remains open until independent quality and product
   resource/device gates pass.
