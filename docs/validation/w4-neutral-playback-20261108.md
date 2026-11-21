# W4 retained-output blind comparison

W4 is **closed for the existing applicable-scenario perceptual-benefit scope**.
The sole native reviewer returned `APPROVE_W4`; the parent rechecked the original
public-SDK consumer raw report, summary and all five retained output hashes. The
package acceptance receipt is `w4-package-acceptance.json` in the evidence directory.
GPU/energy, sustained/cross-capability budgets and final release approval remain W8.

The user watched the comparison and preferred A. After preserving that blind
response, the mapping was revealed: A is SR, B is bicubic. The user's follow-up
reason was “细节更清楚” (clearer detail). This establishes a single-scene user
preference. See [the bounded closure review](w4-human-preference-closure-review-20261108.md).

The first response and subsequent reason are retained separately as
`human-response.json` and `human-reason-followup.json`; the reason was provided
after disclosure of the mapping. `unblinded-result.json` binds them to the selected
output hash. Playback is user-reported; browser codec, zoom and frame timing were
not independently measured. Do not report automated playback as successful.

- CBC hy3 delivered one actual `review.html` (2,106 bytes), integrated unchanged;
  the requested `DELIVERY.md` was absent. Candidate SHA256:
  `962a9c1b1334e6a2fb53562941a7cfbfc6df6cb9fd1d0ded384b153ed526b61c`.
- Three retained videos passed fresh full-frame FFmpeg decoding and ffprobe:
  480×720, 24 fps, 48 frames each. The reference matches the retained summary hash;
  both outputs match their original device terminal SHA256 and byte counts.
- The page uses neutral reference/A/B labels and the predeclared brightness,
  detail and motion-artifact rubric. The mapping is retained separately until
  the user's response; no human preference was supplied by an assistant.
- Local media are hardlinks to retained files: no inference, reencoding,
  download or extra large media copy. The attribution and fixture are retained.
- Browser automation reported an unavailable browser host; Safari and Edge native
  window lookup returned `cgWindowNotFound`. The page was not opened through UI.
  FFmpeg decoding does not establish browser codec support or frame-perfect sync.
- The asynchronous user assessment returned “看了比较，偏向 A”; the follow-up
  returned “细节更清楚”. Preserve both user statements and their evidence scope.

Evidence directory: `artifacts/verification/w4-neutral-playback-20261108-2223/`.
Open `review.html` locally; `parent-acceptance.json` records measured checks and
the remaining browser/human gaps. Local artifacts are not guaranteed Git delivery.

The prior fidelity result remains FAILED versus matched bicubic on this single
scene. Perceptual preference is now RECORDED for this user and this output, with
clearer detail as the reported reason. This does not establish broad production
quality or change this experiment's `productionSDKInference=false`.

Task cleanup found no disposable large build outputs; original media, helper
prompt/raw response and unique receipts remain. Available space was 44.63 GiB.
