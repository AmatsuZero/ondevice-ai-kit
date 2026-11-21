# Fixed HarfBuzz generated-data source review

This is a local notice supplement draft, not an independent license approval.
HarfBuzz source revision: 08b52ae2e44931eef163dbad71697f911fadc323.
The scope is332 actual native build inputs. HARFBUZZ-INPUTS.json binds every
input hash and eleven generated/external-source tables to the fixed source.

## File-level grants

255 inputs have leading Old MIT grants, hb-ucd.cc has a separate ISC grant,
and hb-unicode-emoji-table.hh carries a Unicode data copyright/reference.
The115 unique leading notice blocks from those257 inputs are retained verbatim
in harfbuzz-compiled-notices.txt. The other75 inputs have no leading grant;
the existing HarfBuzz root COPYING remains included. Absence of a leading
notice is not treated as absence of a grant. This scan is limited to leading
blocks; it is not a general legal parser or a complete source-origin verdict.

## Unicode16 and Microsoft ms-use

Seven tables/functions identify Unicode16 data: joining-list, arabic-table,
indic-table, use-table, vowel-constraints, ucd-table and emoji-table. Generators
identify the inputs and URLs. The USE/vowel generators also read three local
ms-use input files; their Microsoft MIT COPYING is already retained in .3.

Unicode16 ReadMe points to Unicode terms. The current primary terms define
computer data files under Public/ as Unicode Data Files and apply License v3
unless a source-specific exception is declared. The current license and the
Unicode16 ReadMe already present in .3 are preserved. This distinguishes data
from charts/specification prose, which have different permissions. Original
raw Unicode16 inputs and release-time exceptions have not been reproduced.

- https://www.unicode.org/Public/16.0.0/ucd/ReadMe.txt
- https://www.unicode.org/copyright.html
- https://www.unicode.org/license.txt

## OpenType / Windows tables and SIL / IANA mappings

hb-ot-tag-table.hh records Microsoft page header2024-12-06 and IANA registry
2025-03-10. gen-tag-table.py also cites SIL ISO639 mapping input. Two other
tables cite Microsoft OpenType OS/2 ranges and Windows language identifiers.

IANA's2021 joint statement applies CC0 to its rights in protocol registries;
the Language Subtag Registry is linked from its protocol index. This supports
the IANA rights classification only; it does not relicense SIL/Microsoft
inputs. The current registry has changed since the fixed2025 generator input,
and it was not substituted into the candidate.

- https://www.iana.org/help/licensing-terms
- https://www.iana.org/protocols (Language Tags)

The Microsoft pages identify the table sources. A publicly readable page or
the description 'open source project' is insufficient to choose a particular
grant. This bounded check found no table-specific redistribution grant.
The Learn general terms reserve rights unless otherwise specified; they are
not a verdict that HarfBuzz's generated mappings are prohibited. Do not apply
the separate ms-use MIT grant to all OpenType/Windows page content.

- https://learn.microsoft.com/en-us/typography/opentype/spec/languagetags
- https://learn.microsoft.com/en-us/typography/opentype/spec/os2
- https://learn.microsoft.com/en-us/windows/win32/intl/language-identifier-constants-and-strings
- https://learn.microsoft.com/en-us/legal/termsofuse

SIL's primary code-table endpoints returned403. No challenge bypass, new
dataset, or source download was attempted. Third-party transcriptions and
search excerpts are not accepted as the primary grant. Retain source
attribution and leave the precise SIL input terms unverified.

- https://iso639-3.sil.org/code_tables/download_tables
- https://www-01.sil.org/iso639-3/iso-639-3.tab

## Arabic PUA and generated machinery

gen-arabic-pua.py reads the two local ArabicPUA*.txt mappings via packTab;
those inputs have no separate leading copyright/license declaration.
Their hashes and the generator are retained, with root HarfBuzz COPYING;
no new grant or missing-grant conclusion is inferred. Eight Ragel outputs
are separately listed in src/meson.build and retain their original Old MIT
leading blocks. They are generator machinery, not extra Unicode datasets.

## Remaining delivery boundary

The explicit hb-ucd.cc ISC omission is corrected in this prepared draft.
No .4 Maven package or consumer/App proof is claimed. Frozen .3 remains
unchanged, including its known omission. Before a new candidate is published,
settle source-specific terms/approval, add any required material to a new
version, then perform normal publication and consumer/App retention checks.
Independent review, remote consumption and production resources remain open.
