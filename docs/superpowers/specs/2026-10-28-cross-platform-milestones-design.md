# GitHub / GitLab milestone tracking design

## Intent

Give the Foundation follow-up queue one tracking surface on both remotes. Each
F01–F12专项 gets the same milestone title and one tracking issue per platform;
the issue is the status entry point and the repository validation document
remains the evidence source. Milestones are for the current follow-up queue,
not a retrospective replacement for the P0–P11 roadmap.

## Scope

- Create twelve milestones named `F01` through `F12` on GitHub and GitLab, using
  these stable titles:

  | ID | Milestone title | Primary scope |
  | --- | --- | --- |
  | F01 | `F01 iOS Picker and EXIF` | Real PHPicker provider to controller/Vision behavior |
  | F02 | `F02 Compose and Skia Resources` | AtlasTextOp native destruction/free/TLS ownership |
  | F03 | `F03 Minimum OS Runtime` | iOS 15 and Android API 26 device acceptance |
  | F04 | `F04 iOS Speech and TTS` | Microphone lifecycle, natural speech quality, offline matrix |
  | F05 | `F05 Android Speech ASR` | Authorized on-device speech assets and runtime acceptance |
  | F06 | `F06 Video SR and Export` | Android production backend, quality/resource evidence |
  | F07 | `F07 Android LiteRT-LM` | Authorized model, production generation and drain proof |
  | F08 | `F08 Apple MLXVLM` | Background, natural quality, resource and interaction acceptance |
  | F09 | `F09 Apple MLX Distribution` | Enhanced binary archive and downstream consumer build |
  | F10 | `F10 Identity and PAD` | Production model, authorized data, calibration and accuracy |
  | F11 | `F11 P11 Quality and Resources` | Per-capability quality/performance/resource report closure |
  | F12 | `F12 Final Review and Freeze` | Final review and frozen candidate evidence |

- Create one issue for each milestone on each platform, using the same title,
  scope, current status, blocker, exit criteria, and local evidence link.
- Keep P0–P11 as historical roadmap stages in repository docs; do not create a
  second remote milestone set for them.
- Mark completed sub-items inside F01, F06, and F08 in the issue description,
  while keeping the parent F milestone open until its full exit criteria pass.

## Approaches considered

1. One milestone per F01–F12 (chosen): matches the existing follow-up IDs and
   lets each platform calculate progress from linked issues.
2. Four broad phase milestones: fewer remote objects, but loses the existing
  专项 boundaries and makes resumption harder.

## Platform mapping

GitHub `AmatsuZero/ondevice-ai-kit` and GitLab
`home/common/ondevice-ai-kit` use identical milestone titles and issue bodies.
Each issue title is the exact matching milestone title. The creation operation
is idempotent: look up exact titles first, then create only missing objects.
Existing objects with matching title are reused and must not be reset or
rewritten. It must never close, delete, or rewrite unrelated objects. The local
registry records both URLs and IDs. No dates, assignees, labels, or automated
cross-platform sync are introduced.

## Status source

Issue descriptions include current state, completed sub-items, remaining gaps,
blocker/input, exit criteria, and links to
`docs/validation/foundation-followups.md` plus the specific evidence document.
F01, F06, and F08 stay open even though scoped sub-items have passed. Milestone
completion is driven by the single tracking issue state; detailed gates and
raw evidence stay in the repository. A committed registry maps local F IDs to
both remote milestone and issue URLs. No due dates, assignees, or automation
are introduced. Remote creation requires authenticated GitHub and internal
GitLab CLI sessions, and a live duplicate check immediately before writes.

## Validation and stop conditions

- Before mutation, list existing milestones and issues on both projects and
  confirm exact-title reuse/create decisions.
- After mutation, re-read all 12 milestones and issues on both projects and
  verify exact title parity, milestone association, open state, and registry
  URLs/IDs.
- Stop on permission errors, duplicate title conflicts, or platform-specific
  state that would require overwriting an existing object; report the object
  and do not alter it.
- This design does not authorize closing issues or milestones, changing the
  P11 gate, publishing a release, or editing unrelated tracker items.
