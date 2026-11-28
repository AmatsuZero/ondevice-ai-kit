# Qwen3 constrained choice in the actual isolated Swift decode loop

Goal: move the validated A/B/C→EOS grammar from offline replay into a real
Swift model generation loop, preserving original unconstrained valid18 and all
content errors. Scope is an isolated candidate, not shared SDK defaults.

1. Keep the same pinned model/revision, 18 original inputs, Swift protocol patch,
   balanced512 prefill, greedy temperature0 and strict trim-only scoring. Use
   actual checkpoint tokenizer for singleton label IDs and fixed checkpoint EOS.
2. First run the new output contract against the historical unconstrained
   iPhone result: require exact one legal choice and EOS per row; retain the
   expected failure from its six invalid rows. No model rerun for RED.
3. Copy the validated grammar and derive a candidate ModelSuite. Replace only
   token selection, add explicit allowed-set/raw-argmax telemetry, ensure next
   model call consumes the chosen token, and enforce terminal grammar state.
   Keep the unmasked full logits and 28-layer offsets/rope delta.
4. Re-run existing24 grammar checks; compile the actual target on macOS and
   typecheck the same candidate for iOS using pinned cached packages. Freeze
   sources before build. No downloads, helpers, credentials or device actions.
5. Check empty ComfyUI/Ollama queues and one GPU owner. Run one host MLX Metal
   process on all18 fixed inputs. Every next-step vector must come from the
   actual current prefix, never old replay files. No retries or frame/prompt
   searches.
6. Independently validate full-vocabulary constrained argmax, generated IDs,
   EOS, cache offsets, carried rope delta, exact input protocol/pixels, strict
   scoring and preserved baselines. Compare original answers and offline replay
   while retaining numerical/pixel/prefill differences. Host evidence is not
   phone integration, a new benchmark or overall quality acceptance.

Stop when this isolated online integration has real build/behavior evidence or
an unrecoverable blocker. F08 stays open for content quality, independently
defined product resource budgets, Android and sustained operation.
