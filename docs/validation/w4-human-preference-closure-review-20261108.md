# W4 human-preference closure review — 2026-11-08

## Code Review Summary

**Scope:** read-only disposition of W4/F06 SR applicability-quality evidence. This
does not approve broad SR quality, release, GPU/energy, or cross-capability budget.

**Files/evidence reviewed:** 9 explicit W4 sources and receipts.

**Total Issues:** 0

### By Severity

- CRITICAL: 0
- HIGH: 0
- MEDIUM: 0
- LOW: 0

### Disposition

**APPROVE_W4** — close W4 as the bounded *applicable-scenario perceptual-benefit*
work package. The approval is not a broad quality/release claim and does not change
the negative matched-fidelity result for this scene.

### Why the W4 standard is now met

1. The last explicitly defined W4 discriminating step was to use the retained
   reference/SR/bicubic outputs for a blind, continuous A/B review and record one
   bounded perceptual preference or failure-mode conclusion; it prohibited new
   inference and post-hoc thresholds
   (`w4-existing-output-decision-20261108.md:55-60`). The current evidence does
   exactly that rather than substituting a terminal-success claim.
2. Before mapping disclosure, the user recorded a single-user, single-retained-scene
   blind preference for A (`human-response.json:2-11`). The retained mapping binds
   A to `sr-srgb` and to its exact output digest, with B as bicubic
   (`unblinded-result.json:2-15`). This closes the previously open waterfall
   perceptual-benefit subitem.
3. “细节更清楚” is useful bounded context, but it was supplied *after* mapping
   disclosure. It is therefore reported only as the user's explanation of the
   preceding blind choice, not as a second independent blind judgment
   (`human-reason-followup.json:2-6`; `unblinded-result.json:8-15`).
4. The retained reference, A, and B bytes were independently tied to the earlier
   receipts and each fully decoded as 480x720, 24 fps, 48 frames
   (`parent-acceptance.json:17-60`; `w4-neutral-playback-20261108.md:17-24`).
   This is adequate binding for the bounded human assessment; it did not rerun
   inference or reencode media.
5. The old measured-fidelity comparison remains negative (SR PSNR/SSIM below
   bicubic), but the prior decision explicitly distinguishes encoded-output
   fidelity from perceptual quality and says not to impose a new numerical quality
   gate (`w4-existing-output-decision-20261108.md:18-32,49-60`). The new preference
   does not erase or contradict that result; it establishes only the required
   limited perceptual benefit.
6. The preceding W4 record already completed the alpha boundary, four processing
   profiles, and a second textured waterfall/rock scene, with the pending reason
   narrowed specifically to perceptual benefit rather than another rerun
   (`sr-acceptance-20261107.md:65-73`). The work-list's former W4 gap is the same
   missing preference/applicability fact, while explicitly assigning resource
   budget to W8 (`remaining-work-dispatch-20261107-2024.md:665`).

### Correct boundaries

- The manual-playback evidence is a user-reported, uninstrumented assessment.
  Browser codec selection, zoom, and frame-perfect synchronization were not
  measured (`human-response.json:8-11`; `parent-acceptance.json:12-16`). This
  limits the conclusion to that user and retained scene; it does not defeat the
  precisely scoped, human-recorded W4 A/B step or justify a claim of automated
  playback verification.
- The retained-output experiment itself says `productionSDKInference=false`
  (`unblinded-result.json:11-15`). That fact cannot be inverted into “there is no
  production-SDK evidence”: separately, the existing public-SDK waterfall consumer
  called `VideoProcessingClient -> AppleVideoToolboxBackend` and completed five
  distinct successful runs with structurally checked outputs
  (`video-sr-backends.md:5-11`). The two evidence sets have different scopes and
  must not be conflated.
- No broad quality, representativeness, transfer-function, lowest-system, or
  production-release conclusion follows. The historical protocol expressly leaves
  such extrapolation unsupported (`video-sr-backends.md:9-11,15-17,21-25`).
  These are not additional W4 acceptance gates after the specified applicability
  check; GPU/energy/long-duration/cross-capability budget and final approval remain
  W8 (`remaining-work-dispatch-20261107-2024.md:669`).

### Remaining W4 work

None under the stated W4/F06 applicability-quality standard. The minimal next
action is bookkeeping only: mark the former W4 perceptual/applicability gap closed
with the above bounded wording. Do not add a post-hoc threshold, request another
user rating, or rerun the same waterfall inference.

### Validation limits

No test, build, device, model, playback automation, network operation, or inference
was run for this review. I did not inspect private media or identity data. This is
an evidence disposition based on the cited retained documents and receipts, not a
fresh technical-runtime validation.
