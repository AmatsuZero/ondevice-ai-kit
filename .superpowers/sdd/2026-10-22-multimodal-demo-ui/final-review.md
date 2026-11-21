# Single-image Demo UI — author final review

Separate author pass over e0fc825..98a3891 plus checkpoint docs, against spec/plan and all five Review Focus items. User explicitly prohibits subagents; this is not independent APPROVE.

No Critical/Important issue found in the final pass. No new Minor finding deferred. Local checkpoint only; no merge/release decision.

1. Optional null model leaves normal Face/Identity/Speech/Video UI unchanged; actual Android optOut result passed. The new entry is on its own row and the opt-in iOS controller retains all legacy host arguments. Original Swift labels compile unchanged against the actual generated header.
2. New-page navigation calls existing Speech/Video leave methods; actual active controlled tasks reached CANCELLED. On new-page exit/Dispose only leavePage is called; native/model close remains host-owned. Reentry refreshes query only and never starts generation.
3. Raw prompt (including whitespace/newline), raw explicit selection, zero/overflow budget, disabled generation, update/cancel terminal wait are real shared UI + VM behavior assertions, not source grep. Request is still constructed only by the existing VM.
4. Real UnavailableMultimodalBridge query and generation show typed unavailable state. Only test APK carries synthetic output. Prompt and opaque media reference do not enter new diagnostic rendering; output identity and platform domain/code are visible.
5. No product dependencies, native host, picker, model/lease management or protected-project edits. Existing max controls height and base floors remain. Simulator build/typecheck and Android API35 fixture UI do not establish iOS touch/minimum-system/real-model acceptance.

Evidence: shared60/60 actually ran; Android full instrumentation status=passed plus all named fields, tools101/101, Demo ABI10/10, iOS generated app build/entry typecheck, APK/lint0errors9existing warnings. Repeated Gradle steps commonly cached and are not new test counts. Device framework link is recorded separately in task-2-final-gate.log.

Limits retained: iOS actual UI, host/picker/lease integration and real MLXVLM-through-KMP device loop pending; natural quality/GPU/energy/Android production/minimum-system/independent review pending. Historical Video automation initialization timeout, P6 recording and Android ASR MISSING_ASSETS are not superseded. No cleanup/push/signing mutation.
