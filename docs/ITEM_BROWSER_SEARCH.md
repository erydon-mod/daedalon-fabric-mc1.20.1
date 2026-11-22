# Item browser search and groups

Creative, REI, JEI and EMI use the same bundled material, design, practical-form
and Greek/Roman subject vocabulary. Existing block IDs and tag searches remain
available, including the published form-first Spartan statue IDs.

Plain `daedalon` finds the whole public catalogue, and the common spelling
`deadalon` works too. These text aliases work with default tag and mod-name
searches restricted to explicit prefixes.

REI groups capitals, lanterns, friezes, panels, corbels, finials and plinths by
family. Urns have their own group. Benches contains Exedra and Hedra. Fountains
contains Pege, Krene, Louterion and the Gothic, Georgian and Greek tierable
basins. Statues contains the full figures, Glaux and the Olympian placer.
Busts keep their subject groups. The planter, dome and monument each have a
group for their existing design.

Each collapsible group sits directly in REI's entry panel. The four previously
combined families keep their group IDs. Newly combined families use new group
IDs, so REI's choices saved for the old style IDs do not carry over. JEI
provides design information pages, and EMI uses its native aliases and tag
search.

The pinned REI 12.1.785 has no working Designs/Forms/Materials subset menu.
Its deprecated `SubsetsRegistry` API remains present, but the actual
`SubsetsRegistryImpl.registerPathEntries` method is empty and its path getters
return empty results. Daedalon therefore does not register these obsolete
subset paths. The shared catalogue still provides design, form and material
metadata for search and other browser integrations; this data is not evidence
that REI displays a navigable subset tree. Removing the unused callback does
not redesign the existing grouping or introduce a replacement browsing UI.

Search includes singular terms, plural form names and their existing translated
labels. Statue and Olympian vocabulary links full statues, busts and the
god-placement selector. Fountain vocabulary covers Louterion, the tierable
basins and compatible stone plinths. Decorative metal plinths retain their
existing exclusion from fountain attachment terms.

A collapsed Daedalon group uses fresh Glacium from its first matching design
when that finish matches the search. The representative comes from the filtered
member list, so a search for Bronze or aged stone keeps a matching icon.

The catch-all `daedalon:daedalon_blocks` tag remains useful for commands and
data packs. Browsers that include tags in ordinary searches previously matched
every decor model when searching `block`. A plain `block` or `blocks` query now
excludes Daedalon decor. Explicit tag, identifier, tooltip and mod queries keep
their native behavior. Native AND, OR, quoting and negation still apply to
individual tokens: `block | finial` can find finials, while `-block` keeps decor.

| Browser | Explicit tag search example | Group navigation |
| --- | --- | --- |
| REI 12.1.785 | `$daedalon:daedalon_blocks` | Optional family and design groups, plus text and tag searches |
| JEI 15.16.2.73 | `$daedalon:daedalon_blocks` | Design information pages |
| EMI 1.1.22 | `#daedalon:daedalon_blocks` | Native aliases and tags |

Generated language catalogues supply the group labels and search terms.
White is a plain nonmetal finish; bronze and silver are metal finishes.
The five architectural designs receive white, bronze and silver finish search terms without
acquiring aged/weathered aliases.

Fresh named stone also accepts `honed`, `polished`, `ganosis` and legacy `mirror`, including their
German and Spanish translations, in all four search surfaces. These finish words
remain useful after changing the stone's finish. Aged stone, plain white and
metal finishes do not acquire these aliases.

When ERYDON is installed, fresh stone display names use its active per-stone
finish, for example `Glacium Hedra (Honed)` or `Latmion Zeus Statue (Ganosis)`.
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
the original names. Bundled locale labels come from the same
asset-generation path as the search translations. Focused tests cover captured
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
| REI | Member append inside `EntryListSearchManager.collapse(...)` | Promote a matching fresh Glacium member from the first matching design |
| JEI | `ElementSearch.getSearchResults(TokenInfo)` and `ElementSearchLowMem.getSearchResults(TokenInfo)` | Unprefixed token result only; cached results are copied |
| EMI | `QueryType.fromString(String)`, `CompiledQuery.addQuery(...)`, `LogicalOrQuery.matches(EmiStack)` / `matchesUnbaked(EmiStack)` | Default token's cached query only; native outer boolean tree retained |

JUnit checks cover parsed-token policy, cache immutability, inversion, explicit
query bypasses, thread isolation and real bundled family membership. Generated
asset checks cover every public item, correct material tags and the bundled
locales. These checks do not replace applying the mixins in a real client.

Collapsible groups must remain disjoint. REI's native collapse loop adds an
item to every matching group, so adding overlapping form, style and material
collapse groups would create duplicate tiles rather than a folder hierarchy.
Visible entry-panel order also follows REI's filtered item list and the player's
ordering option; changing the catalogue map order alone does not reorder it.
Daedalon now uses the supported `EntryRegistry` API to order its existing
entries once during reload. The placement selector leads the sculpture section;
each subject's full statues and busts follow together. Other designs follow
their form and canonical design name. Within a design, fresh and aged stone
variants sit in pairs, followed by supported White, Bronze and Silver finishes.
The adapter reuses the original entry objects, including any NBT variants.
When their relative order needs changing, only recognised Daedalon entries are
removed and appended as one sorted block after the remaining entries. Foreign
entries, the emblem and unknown items retain their relative order. An already
ordered selection is left alone. REI's registry/original-order mode preserves this
arrangement; player-selected name sorting and reversal can change its display.
This improves the ordinary list without requiring collapsed groups or another
screen. Search-field shortcuts remain a possible future improvement.

REI 12.1.785 was checked in the Daedalon 1.0.0 + ERYDON 2.0.0 client on
24 October 2026, with JEI and EMI confirmed absent. All 84 design groups
registered automatically. The catalogue also supplied 15 form and 30 material
metadata lists; those registration counts did not demonstrate a working REI
subset menu. With ordinary tag matching enabled, `block` and `blocks` matched no
Daedalon decorations, `$daedalon:daedalon_blocks` matched all 4,678 items,
and `finial`, `block | finial` and `finial -block` each matched 280. Jupiter
matched 112 Zeus statues/busts, White Gothic matched the two white architectural
designs, and vanilla Quartz Block remained searchable. The visible REI panel
also showed the Gothic Frieze group with all 57 finishes. This is REI search and
grouping evidence for that historical client; JEI/EMI live acceptance and shader/material appearance remain
separate checks.

With tag, tooltip, mod-name and identifier searches restricted to explicit
prefixes, both `daedalon` and `deadalon` matched all 4,678 catalogue items; Jupiter
still matched 112 and White Gothic matched two. The native REI list-search and
collapse callback retained all 4,678 items in 84 design groups for both spellings.

The main live acceptance environment is IntelliJ's client using `run-dev`.
CurseForge has deliberately different saved group preferences; preserve them
and do not treat them as a cause of the IntelliJ browsing issues. Source analysis
of registration data and REI's pinned implementation does not establish new
client acceptance. The browsing review remains separate from the texture and
model work, so its independent search and metadata improvements need not wait
for those meshes.

For later live acceptance in the intended development profile, check each
browser with default tag search enabled, then inspect
`block`, `blocks`, `block | finial`, `finial -block`, the explicit tag query in
the table, `jupiter`, and `white gothic`. A Daedalon bench, statue and finial
must be absent from positive plain-block matches and remain present in their
specific searches. Other mods' blocks must retain their native results.
For REI, search `daedalon` with native collapsing enabled and expand Benches,
Urns, Fountains and Statues. Check both benches, Louterion with the other
fountains, and the Olympian placer with the statues. Search `curved bench`,
`Kylix`, `Baroque corbel` and `bronze finial` to check the filtered members and
representative icons. Check `statues`, `olympian` and `fountains` for related
items, then toggle a new group to check the ordinary item list. For JEI, open a
design's information page. Repeat a plural group-name search after a language reload.

Also restrict default tag, tooltip, mod-name and identifier searches to explicit
prefixes and check plain `daedalon`, `deadalon`, `jupiter` and `white gothic`.
The two mod-name spellings must retain every public catalogue item, whether the
profile displays individual entries or collapsed design groups. This isolates
the shared text cache from tag-based matches; use the current generated
catalogue count rather than the older historical totals above.

## Olympian placement selectors

The REI Statues group includes all 56 Olympian God Statue selection items in
stone, aged stone, Bronze and Silver. Their design metadata still supplies
the matching materials and placement vocabulary. They share the
same Creative, REI, JEI and EMI vocabulary, including Olympian, god, cycle and
sequence, while plain block searches exclude them.

The selector places the existing statues without registering another world
block. Its item-only medallion shares one OBJ family across every finish.
The `daedalon_blocks` tag includes placed-decor items, while `daedalon_items`
also includes the selectors. The historical selector review counted 4,678 and
4,734 items respectively, before the three lantern designs were added. Item-only tags
include the selectors in relevant material, finish and sculpture categories
without adding invalid block or mining entries. Their Creative image uses
Oliver's approved medallion entirely in the selected stone or metal finish,
with the shared 128px cache and a 20-degree turn around Y.

Fresh stone selector names use the same captured Honed, Polished or Ganosis
setting as the statues they place. The cached language overlay now covers both
block and item translation keys; aged, white and metal names keep their own
finish descriptions. Changing finish preferences still requires a restart.

Oliver approved the existing finish names in-game on 24 October 2026 with
Daedalon and ERYDON, REI only, Complementary Unbound and the Collection 64x pack.
The historical 84-group results above describe that earlier catalogue. The
subsequent selector review recorded 4,734 items and 85 design groups, including
the selector group and its item-only Creative aliases. Its reported 130 subset
paths were registration intent, not proof of a working subset menu in the
pinned REI implementation. Oliver
approved all pre-publication edits on 24 October 2026. REI was the live review
browser; this does not claim a separate live JEI/EMI or wider shader matrix.

The November source audit, including four new plain Black friezes and four new
coffered panel designs, inventories 5,134 catalogue items in 92 exclusive design
groups, with 16 form and 31 material metadata buckets. The five panel designs
each offer 57 finishes. All four frieze designs are represented, each with 58 finishes.
White and Black sit together before Bronze and Silver within each design;
these plain colours do not claim configurable stone finishes. These are source-level counts;
they do not claim that the current development client has been visually reviewed.
