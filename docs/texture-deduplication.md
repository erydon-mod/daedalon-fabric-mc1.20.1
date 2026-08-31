# Exact texture deduplication

Daedalon keeps `src/main/resources` as the complete physical source of truth.
Release builds generate an exact-byte inventory and alias plan, then modify only
the staged `processResources` output. Use `-PtextureDedupe=false` to build the
fully physical fallback JAR.

The 2026-07-27 production measurement used the same resolver classes and mod
metadata in both artifacts:

- physical JAR: 3,331,551 bytes
- deduplicated JAR: 3,299,848 bytes
- saving: 31,703 bytes (0.9516%)
- aliases: 140
- canonical blobs: 22

The deduplicated artifact is therefore the release default. The extended JAR
audit proved identical logical resources, `.properties` files, classes, and
`fabric.mod.json`; it also verified every blob hash and required the
deduplicated artifact to be strictly smaller. The fallback build remains
available without changing or deleting any source texture.
