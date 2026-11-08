# W4 retained-output blind comparison

The neutral A/B page is prepared; human preference and browser playback remain
unverified. This is a bounded preparation acceptance, not W4 closure.

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
- An asynchronous user assessment request links the local page. The user may
  report A, B, no preference, both unacceptable, or a playback failure.

Evidence directory: `artifacts/verification/w4-neutral-playback-20261108-2223/`.
Open `review.html` locally; `parent-acceptance.json` records measured checks and
the remaining browser/human gaps. Local artifacts are not guaranteed Git delivery.

The prior fidelity result remains FAILED versus matched bicubic on this single
scene; perceptual benefit remains UNVERIFIED. A single-scene preference cannot
establish broad production quality or change `productionSDKInference=false`.

Task cleanup found no disposable large build outputs; original media, helper
prompt/raw response and unique receipts remain. Available space was 44.63 GiB.
