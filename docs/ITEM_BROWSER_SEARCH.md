# Item browser search and groups

Creative, REI, JEI and EMI use the same bundled material, design, practical-form
and Greek/Roman subject vocabulary. Existing block IDs and tag searches remain
available, including the published form-first Spartan statue IDs.

REI has one collapsible group per design: Zeus Statues, Zeus Busts, Spartan
Promachos Statues, each urn shape, each capital style, and so on. Expanding a
design shows all its stone, aged and supported metal/white finishes. Subsets
also provide Designs, Forms, and Materials and finishes under Daedalon. JEI
provides matching design information pages. EMI uses its native shared aliases
and tag search, as in ERYDON; it has no added artificial recipe category.

The catch-all `daedalon:daedalon_blocks` tag remains useful for commands and
data packs. Browsers that include tags in ordinary searches previously matched
every decor model when searching `block`. A plain `block` or `blocks` query now
excludes Daedalon decor. Explicit tag, identifier, tooltip and mod queries keep
their native behavior. Native AND, OR, quoting and negation still apply to
individual tokens: `block | finial` can find finials, while `-block` keeps decor.

| Browser | Explicit tag search example | Group navigation |
| --- | --- | --- |
| REI 12.1.785 | `#daedalon:daedalon_blocks` | Collapsible design groups and subsets |
| JEI 15.16.2.73 | `$daedalon:daedalon_blocks` | Design information pages |
| EMI 1.1.22 | `#daedalon:daedalon_blocks` | Native aliases and tags |

All group labels and search terms are generated for English, German and
Spanish. White is a plain nonmetal finish; bronze and gold are metal finishes.
The five architectural designs receive the correct finish search terms without
acquiring aged/weathered aliases.

## Implementation and validation

`DaedalonSearchIndex` factors the generated vocabulary into family and material
entries. `DaedalonCatalogue` builds immutable design/form/material lists once
from registered public items. Optional plugins reuse these lists. Search
classification is cached by item; REI and EMI also cache their plain-token flag
when a query is parsed. There is no tick, render, world-tag scan or per-frame
catalogue rebuilding.

The optional query hooks were audited against the pinned browser sources:

| Browser | Hook | Scope |
| --- | --- | --- |
| REI | `Argument` constructor and its single-`AlternativeArgument` `matches` overload | Plain text argument; native inversion retained |
| JEI | `ElementSearch.getSearchResults(TokenInfo)` and `ElementSearchLowMem.getSearchResults(TokenInfo)` | Unprefixed token result only; cached results are copied |
| EMI | `QueryType.fromString(String)`, `CompiledQuery.addQuery(...)`, `LogicalOrQuery.matches(EmiStack)` / `matchesUnbaked(EmiStack)` | Default token's cached query only; native outer boolean tree retained |

JUnit checks cover parsed-token policy, cache immutability, inversion, explicit
query bypasses, thread isolation and real bundled family membership. Generated
asset checks cover every public item, correct material tags and all three
locales. These checks do not replace applying the mixins in a real client.

For live acceptance, enable each browser's default tag search, then check
`block`, `blocks`, `block | finial`, `finial -block`, the explicit tag query in
the table, `jupiter`, and `white gothic`. A Daedalon bench, statue and finial
must be absent from positive plain-block matches and remain present in their
specific searches. Other mods' blocks must retain their native results.
For REI expand Zeus Statue and Zeus Bust separately and inspect the bronze,
silver, fresh and aged entries, then check Form and Material subsets. For JEI
open a design's information page. Repeat one search after a language reload.
