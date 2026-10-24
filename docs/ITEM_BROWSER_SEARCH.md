# Item browser search and groups

Creative, REI, JEI and EMI use the same bundled material, design, practical-form
and Greek/Roman subject vocabulary. Existing block IDs and tag searches remain
available, including the published form-first Spartan statue IDs.

Plain `daedalon` finds the whole public catalogue, and the common spelling
`deadalon` works too. These text aliases work with default tag and mod-name
searches restricted to explicit prefixes.

REI has one collapsible group per design: Zeus Statues, Zeus Busts, Spartan
Promachos Statues, each urn shape, each capital style, and so on. Expanding a
design shows all its stone, aged and supported metal/white finishes. Subsets
also provide Designs, Forms, and Materials and finishes under Daedalon. JEI
provides matching design information pages. EMI uses its native shared aliases
and tag search, as in ERYDON; it has no added artificial recipe category.

Collapsed Daedalon designs use their fresh Glacium member at the front so the
shape is easier to recognise. This changes only the new group's matching member
list; global registry order, Creative order and the remaining finish order stay
unchanged. A search restricted to bronze or aged finishes keeps its matching
members and does not acquire an excluded Glacium item.

The catch-all `daedalon:daedalon_blocks` tag remains useful for commands and
data packs. Browsers that include tags in ordinary searches previously matched
every decor model when searching `block`. A plain `block` or `blocks` query now
excludes Daedalon decor. Explicit tag, identifier, tooltip and mod queries keep
their native behavior. Native AND, OR, quoting and negation still apply to
individual tokens: `block | finial` can find finials, while `-block` keeps decor.

| Browser | Explicit tag search example | Group navigation |
| --- | --- | --- |
| REI 12.1.785 | `$daedalon:daedalon_blocks` | Collapsible design groups and subsets |
| JEI 15.16.2.73 | `$daedalon:daedalon_blocks` | Design information pages |
| EMI 1.1.22 | `#daedalon:daedalon_blocks` | Native aliases and tags |

All group labels and search terms are generated for English, German and
Spanish. White is a plain nonmetal finish; bronze and silver are metal finishes.
The five architectural designs receive white, bronze and silver finish search terms without
acquiring aged/weathered aliases.

Fresh named stone also accepts `honed`, `polished` and `mirror`, including their
German and Spanish translations, in all four search surfaces. All three words
remain useful after changing the stone's finish. Aged stone, plain white and
metal finishes do not acquire these aliases.

When ERYDON is installed, fresh stone display names use its active per-stone
finish, for example `Glacium Honed Hedra` or `Latmion Mirror Zeus Statue`.
The names follow the same captured launch preference as rendering, so saved
setting changes require a restart. Standalone Daedalon retains its original
names. The optional adapter uses ERYDON's actual eligibility classifier,
including support for the published form-first Spartan statue IDs; older
companions that do not classify those IDs leave those names unchanged.

## Implementation and validation

`DaedalonSearchIndex` factors the generated vocabulary into family and material
entries. `DaedalonCatalogue` builds immutable design/form/material lists once
from registered public items. Optional plugins reuse these lists. Search
classification is cached by item; REI and EMI also cache their plain-token flag
when a query is parsed. There is no tick, render, world-tag scan or per-frame
catalogue rebuilding.

Finish labels are an immutable language-only resource overlay built once per
launch and reused by resource reloads. It reads the bundled language catalogue
and ERYDON's optional public client methods without scanning registries, worlds
or resource-pack textures. An unavailable or incompatible optional API retains
the original names. English, German and Spanish labels come from the same
asset generator as the search translations. Focused tests cover captured
preferences, all fresh catalogue names, exclusions and real text-index matches;
the live combined-client wording remains a separate acceptance check.

REI's text-cache integration appends aliases while constructing the cache value,
before other family mods' return injections run. An early return for a foreign
item therefore retains the aliases, so a shared installation keeps plain
Daedalon search without enabling broad tag searches or requiring a newer loader.

The optional query hooks were audited against the pinned browser sources:

| Browser | Hook | Scope |
| --- | --- | --- |
| REI | `Argument` constructor and its single-`AlternativeArgument` `matches` overload | Plain text argument; native inversion retained |
| REI | Member append inside `EntryListSearchManager.collapse(...)` | Promote an already matched fresh Glacium member in its own design group |
| JEI | `ElementSearch.getSearchResults(TokenInfo)` and `ElementSearchLowMem.getSearchResults(TokenInfo)` | Unprefixed token result only; cached results are copied |
| EMI | `QueryType.fromString(String)`, `CompiledQuery.addQuery(...)`, `LogicalOrQuery.matches(EmiStack)` / `matchesUnbaked(EmiStack)` | Default token's cached query only; native outer boolean tree retained |

JUnit checks cover parsed-token policy, cache immutability, inversion, explicit
query bypasses, thread isolation and real bundled family membership. Generated
asset checks cover every public item, correct material tags and all three
locales. These checks do not replace applying the mixins in a real client.

REI 12.1.785 was checked in the Daedalon 1.0.0 + ERYDON 2.0.0 client on
24 October 2026, with JEI and EMI confirmed absent. All 84 design groups
registered automatically; the catalogue also supplied 15 form and 30 material
subsets. With ordinary tag matching enabled, `block` and `blocks` matched no
Daedalon decorations, `$daedalon:daedalon_blocks` matched all 4,678 items,
and `finial`, `block | finial` and `finial -block` each matched 280. Jupiter
matched 112 Zeus statues/busts, White Gothic matched the two white architectural
designs, and vanilla Quartz Block remained searchable. The visible REI panel
also showed the Gothic Frieze group with all 57 finishes. This is REI search and
grouping proof; JEI/EMI live acceptance and shader/material appearance remain
separate checks.

With tag, tooltip, mod-name and identifier searches restricted to explicit
prefixes, both `daedalon` and `deadalon` matched all 4,678 catalogue items; Jupiter
still matched 112 and White Gothic matched two. The native REI list-search and
collapse callback retained all 4,678 items in 84 design groups for both spellings.

For live acceptance, enable each browser's default tag search, then check
`block`, `blocks`, `block | finial`, `finial -block`, the explicit tag query in
the table, `jupiter`, and `white gothic`. A Daedalon bench, statue and finial
must be absent from positive plain-block matches and remain present in their
specific searches. Other mods' blocks must retain their native results.
For REI expand Zeus Statue and Zeus Bust separately and inspect the bronze,
silver, fresh and aged entries, then check Form and Material subsets. For JEI
open a design's information page. Repeat one search after a language reload.

Also restrict default tag, tooltip, mod-name and identifier searches to explicit
prefixes and check plain `daedalon`, `deadalon`, `jupiter` and `white gothic`.
The two mod-name spellings must retain all 4,678 catalogue items in the native
collapsed REI panel. This isolates the shared text cache from tag-based matches.

## Olympian placement selectors

Olympian God Statue selection items have their own design group with 56 stone,
aged, Bronze and Silver finishes, including the usual fresh Glacium preview.
They also appear under Statues and the matching material groups. They share the
same Creative, REI, JEI and EMI vocabulary, including Olympian, god, cycle and
sequence, while plain block searches exclude them.

The selector places the existing statues; it does not register another world
block or OBJ family. The `daedalon_blocks` tag retains the 4,678 placed-decor
items, while `daedalon_items` includes all 4,734 catalogue items. Item-only tags
include the selectors in relevant material, finish and sculpture categories
without adding invalid block or mining entries. Their current Creative image is
one shared Daedalon emblem placeholder, ready for Oliver's final image.

Fresh stone selector names use the same captured Honed, Polished or Mirror
setting as the statues they place. The cached language overlay now covers both
block and item translation keys; aged, white and metal names keep their own
finish descriptions. Changing finish preferences still requires a restart.

Oliver approved the existing finish names in-game on 24 October 2026 with
Daedalon and ERYDON, REI only, Complementary Unbound and the Collection 64x pack.
The historical 84-group results above describe that earlier catalogue. The new
selector group and its item-only Creative aliases still need their own live
viewer acceptance; this approval does not cover the full JEI/EMI or shader matrix.
