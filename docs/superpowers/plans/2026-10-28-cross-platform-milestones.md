# Cross-platform Foundation Milestones Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track the F01–F12 Foundation follow-ups with matching milestones and parent issues on GitHub and GitLab.

**Architecture:** Each F item has one identically named open milestone and one parent issue attached to it on each platform. The repository remains the evidence source; a local index stores the direct links and update rules.

**Tech Stack:** GitHub Issues/Milestones REST API via `gh`; GitLab Issues/Milestones API via `glab`; Markdown registry.

**Spec:** `docs/superpowers/specs/2026-10-28-cross-platform-milestones-design.md`

## Global Constraints

- Use matching F01–F12 titles on both platforms.
- Preserve P0–P11 as historical roadmap stages; do not change the 10/12 count or P4/P11 status.
- Keep F01, F06, and F08 open while only their scoped sub-items have passed.
- Do not set due dates or assignees, and do not add synchronization automation.
- Reuse exact-title objects; never rewrite, close, or delete unrelated objects.

## Review Focus

- Partially completed F items remain open — verify each parent issue starts open.
- Platform issue must attach to its matching milestone — verify all 24 associations.
- Duplicate-title retries must not duplicate objects — query exact titles before create.
- Internal GitLab may have existing tracker state — enumerate both milestones and issues before mutation.
- The 10/12 roadmap gate must not change — inspect status docs for incidental edits.

---

### Task 1: Create matching milestones

**Files:** none; remote state only.

- [x] Confirmed no existing GitHub or GitLab F01–F12 milestones.
- [x] Created milestones F01–F12 on GitHub and internal GitLab.
- [x] Verified all GitHub milestones are open and each reports one open issue.
- [x] Verified internal GitLab project contains twelve F01–F12 milestone records.

### Task 2: Create one parent issue per milestone

**Files:** remote state only.

- [x] Added current blockers, completed scoped sub-items, exit criteria, and local validation-document link.
- [x] Created one open issue for each F01–F12 on both platforms.
- [x] Read back both issue lists: 12 items per platform, matching titles and milestone associations.

### Task 3: Add local link registry and operating rules

**Files:**
- Modify: `docs/validation/foundation-followups.md`
- Create: `docs/validation/foundation-tracking.md`

- [x] Add direct milestone and issue URLs for both platforms.
- [x] Document when to update/close parent issues and note that evidence remains in the repository.
- [ ] Run `git diff --check` and review the final diff.
