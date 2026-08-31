# Daedalon for Fabric 1.20.1

Daedalon is ERYDON's standalone collection of detailed OBJ decor for
Minecraft 1.20.1.

Version 1.0.1 uses native 16x textures. The unified ERYDON Collection 32x and
64x resource packs support Daedalon, ERYDON, and ERYDON Themelios together.
Daedalon does not require or bundle Continuity: its detailed OBJ models already
use a built-in world-aligned repeat-phase renderer.

This repository is source-available under Oliver's restricted licence. It is
not open-source software; see `LICENSE`. Third-party asset credits are retained
under `src/main/resources/META-INF/THIRD_PARTY_NOTICES.md`.

## Development validation

Daedalon requires Java 17 and Python 3.13. Run the complete compilation, test,
and source-asset safety suite without producing a release JAR:

```text
./gradlew --no-daemon check
```
