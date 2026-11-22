# Settings style assets

Daedalon's settings use the shared ERYDON marble panel, dark Portorium controls
and bronze frame with Oliver's authorisation. The five GUI PNGs are exact copies
from the authoritative ERYDON source repository. Their filter metadata retains
the same settings with the LF line endings used by this repository; no textures were resized,
repainted or taken from resource-pack output. Oliver's ownership of the Portoro
source photograph and the family artwork is recorded in [BRANDING.md](../BRANDING.md).

[client-settings-style-assets.json](../config/client-settings-style-assets.json)
records the original repository paths, hashes, sizes and licences. Runtime
identifiers use only the `daedalon` namespace, so these screens remain usable
without ERYDON installed. The renderer shares a small subset of the family GUI
design; it does not copy the complete ERYDON settings framework.

Titles and controls use the unmodified Cinzel Regular font under SIL OFL 1.1.
Its full copyright and licence notice is retained under
[src/main/resources/assets/daedalon/font/OFL-1.1.txt](../src/main/resources/assets/daedalon/font/OFL-1.1.txt). Body copy uses
Minecraft's default font at its normal scale with additional line spacing.
Long paragraphs and controls scroll within the panel; footer actions stay fixed.
The slim scrollbar handle uses the shared family artwork and the same minimum
height and pointer travel as ERYDON. Each button captures its own texture sample
when its unscrolled layout is built, so scrolling moves the control without
changing its marble crop.

Existing Daedalon logo artwork and animation exports are unchanged. The settings
header uses the product name as display text until an approved GUI logo export
is supplied through the branding workflow.
