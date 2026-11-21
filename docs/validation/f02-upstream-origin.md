# F02 upstream source follow-up (2026-11-06)

This read-only follow-up adds primary upstream evidence to the [.4 packet](f02-harfbuzz-notices.md).
It does not change native sources, notice bytes, Maven artifacts, App builds or release status.

HarfBuzz's [mapping introduction](https://github.com/harfbuzz/harfbuzz/commit/6e29060af2e4490e67e24ec2ef5172541275a855)
is authored by Khaled Hosny and adds `ArabicPUA1.txt` / `ArabicPUA2.txt`.
The later [generator change](https://github.com/harfbuzz/harfbuzz/commit/a15ad778fede9e94428f6811293c71a63216234a)
renames them without changes and adds the packTab generator. These establish the repository
entry points; they do not independently establish the underlying mappings' original ownership.
The local checkout is shallow, so its boundary commit must not be mistaken for file introduction.

An existing [upstream discussion](https://github.com/harfbuzz/harfbuzz/discussions/6323) contains
an October 5 reply from `behdad`: the name-language table began with joined sources and then
manual edits. The maintainer also states that packTab output has no generator-imposed copyright
restriction when significant static code is not embedded. This narrows the generator question;
it does not establish every external input's terms or a rule for all jurisdictions. The reply
defers Arabic PUA and language-tag specifics to their respective authors. At this observation,
the discussion is unanswered with one visible reply. No message was sent.

The discussion's question concerns revision `3ef8709829a5884517ad91a97b32b9435b2f20d1`,
with an IANA header of 2025-01-21. Our fixed `08b52ae2e44931eef163dbad71697f911fadc323`
table says **2025-03-10**. We retain our actual hashes/header, and do not substitute the other
revision's input identity or treat a matching Microsoft timestamp as byte equivalence.

Microsoft's [official Typography issues README](https://github.com/MicrosoftDocs/typography-issues/blob/main/README.md)
identifies that repository as an issue placeholder and says the content docs are held privately.
Its CC-BY/MIT license files therefore are not evidence of a grant for the fixed OpenType input.
SIL's primary code-table page again returned 403. No bypass, new source/model/dataset download,
Git fetch, artifact rebuild, signing, device action, publication or credential use was performed.

Evidence: `artifacts/verification/f02-upstream-origin-20261106/primary-sources.json` binds
observations/URLs to eight fixed native input hashes. Existing `.4` notices and delivery remain
frozen. No license material has been newly determined necessary by this follow-up; there is no
reason to produce `.5` or rerun successful builds merely to package these review observations.

Next evidence needed is the source-specific author clarification or an authorized applicability
review. Do not repeatedly fetch unchanged blocked pages or treat a third-party transcription
as a grant. Nexus anonymous reads/publisher permissions remain settled. Independent approval,
remote consumption, maintenance ownership and production resource acceptance remain open;
foundation 10/12, P4/P11 and release remain open/not-ready.
