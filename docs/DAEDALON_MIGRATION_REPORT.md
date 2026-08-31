# Daedalon OBJ Decor Migration Report

Date: 26 July 2026

## Outcome

Daedalon is now an independent Fabric 1.20.1 / Java 17 mod. The approved
freestanding OBJ decor was moved from ERYDON into the `daedalon` namespace
without an ERYDON or Themelios runtime dependency.

Daedalon deliberately has no finished logo yet. Fabric metadata omits an icon,
and the creative tab temporarily uses the Konche urn.

### 12 August 2026 development update

The urn catalogue now contains Amphora, Konche, Diota, Kylix, Kalyx,
Lekythos, Pelike, Pithos, Rhabdos, Salpinx, and Stamnos. The ten Meshy-supplied
models were prepared locally with Blender 5.2 to exactly 3,200 triangles each;
Diota's existing geometry, materials, bronze handles, and appearance were left
unchanged. All urns retain four facings, three sizes, centred/offset placement,
shared material textures, and the established cylindrical runtime projection.

Because these registry IDs remain unpublished development content, the urn IDs
were corrected directly to the programme grammar: `<material>_<shape>_urn` and
`<material>_aged_<shape>_urn`. The complete development catalogue is now 32
mesh families and 1,728 block/item IDs. Exact source-folder mappings, hashes,
counts, Blender build, and output evidence are recorded in
`docs/evidence/urn-batch-source.json`.

## Repository lineage

Daedalon is maintained in this repository. ERYDON and Themelios are separate
projects; their source histories and local development paths are not included
here.

## Migrated catalogue

The inventory found exactly six OBJ files, six matching MTL files, and six
Daedalon mesh definitions. No other or ambiguous OBJ decor family remained.
All 27 standard stone finishes and their aged variants are included.

| Family | OBJ | MTL | Definition | IDs | Behaviour |
| --- | --- | --- | --- | ---: | --- |
| Spartan Promachos | `spartan_siplified.obj` | `spartan_siplified.mtl` | `statue_spartan_promachos.json` | 54 | Four facings; 1, 2, or 3 blocks high; 2 high by default |
| Konche | `urn_konche.obj` | `urn_konche.mtl` | `urn_konche.json` | 54 | Four facings; small, medium, or large; centred and alcove offsets |
| Diota | `urn_diota.obj` | `urn_diota.mtl` | `urn_diota.json` | 54 | Urn controls plus isolated bronze handles and yellow specular |
| Kylix | `urn_kylix.obj` | `urn_kylix.mtl` | `urn_kylix.json` | 54 | Urn controls |
| Kalyx | `urn_kalyx.obj` | `urn_kalyx.mtl` | `urn_kalyx.json` | 54 | Urn controls plus required double-sided faces |

The catalogue contains 324 block IDs, 324 blockstates, 324 item models, and
six display models. Urn collision and outline geometry follows facing, size,
and offset while retaining a narrow selection target in the original block
cell so an offset object remains editable.

The unpublished `urn_plinthos` development family was retired on 12 August
2026 and replaced by Daedalon's separately sourced Astragalos, Bathron, Kion,
Stephanos, and Triphyllon plinth families. Their current source and runtime
evidence is locked in `docs/evidence/plinth-batch-source.json`.

## Mesh integrity

| Mesh | Vertices | Texture coordinates | Normals | Faces | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| Spartan Promachos | 5,173 | 40,815 | 13,605 | 13,605 | `E2F4A7486A8A9F438CD3607B172ED27AFD7BF1C54C50EFD054F4AE935CD189D9` |
| Konche | 1,226 | 7,344 | 2,448 | 2,448 | `DC9B577A5324B84844CC011D5B783DE4BA360664107C323795BEF2E65524F439` |
| Diota | 1,983 | 7,912 | 1,991 | 1,991 | `056F640C64745F69AB144C77CF196234016DF40BDE1C44E0AFB311B48EB45C01` |
| Kylix | 799 | 3,199 | 805 | 805 | `FB030D10B251B9B2C48DD22B2F3FBC1C157B131F0D7B45CC8E723A610BA080C9` |
| Kalyx | 1,279 | 6,356 | 1,928 | 1,928 | `02F267E0C3ED27EBA0D84F406121ED44E8206D913BD40DD00CA35A2E788964E9` |

The exact source meshes, UV handling, render caching, shader-normal bridge,
and existing transforms were preserved. Smooth normals use an 80-degree
threshold as the current automatic hard-edge treatment.

## Independence and registry safety

- Daedalon owns its initializer, client initializer, registries, creative tab,
  blocks, items, shape handling, material catalogue, and OBJ loader.
- It owns its blockstates, item/display models, loot tables, tags, three
  language files, native textures, PBR sidecars, asset generator, contract
  tests, and JAR audit.
- Its metadata supplies its own optional Mod Menu link. Search results come
  from its own creative-tab entries; no ERYDON gallery or swap-tool runtime
  hook is required.
- Its Fabric metadata has no ERYDON or Themelios dependency.
- The only asset namespace in its JAR is `assets/daedalon`. Non-Daedalon data
  is limited to conventional `data/minecraft` and `data/material` tags.
- No migrated blockstate path overlaps the current ERYDON JAR.
- The migrated IDs were developed after the latest public ERYDON release and
  were not present in published ERYDON 1.5.2 artifacts. The namespace change
  therefore avoids a known public-world registry migration problem.
- Pre-migration development or test worlds containing the old unpublished
  `erydon:*` object IDs are not automatically remapped. Those old blocks may
  disappear when such a development world is opened without its original
  registry; public release worlds are unaffected because those IDs never
  shipped.
- No crafting recipes existed for these objects in ERYDON. Daedalon preserves
  their creative-only availability instead of inventing cross-mod ingredients.

## ERYDON cleanup

ERYDON no longer registers or packages the six moved OBJ families. Their block
classes, registry entries, generated assets, OBJ loader, and OBJ-specific Iris
normal bridge were removed.

The following similarly named or shared systems remain intentionally:

- architectural surround plinth models;
- shared material-block textures used by ERYDON architecture;
- `DecorShapeTransforms`, still used by Oil Burners; and
- the Indium triangle-order safeguard used by steep-slope POM rendering.

No CTM `.properties`, CTM service, slope geometry, slope UV, or slope-rendering
file was changed by the migration.

## Textures and shared resource packs

Daedalon's native assets use the current 16x Lite material resources:

- 326 native PNG files;
- 163 colour maps;
- 81 normal maps; and
- 82 specular maps.

Both shared packs contain `daedalon`, `erydon`, `minecraft`, and `themelios`
namespaces. The 64x PBR pack contains every Daedalon path in the 16x pack plus
81 aged specular maps. All 490 moved pack textures are byte-identical to their
approved ERYDON versions, and shared architectural material surfaces remain
in ERYDON.

The pack metadata and READMEs now describe compatibility with any installed
combination of ERYDON, Themelios, and Daedalon. Daedalon's migrated 16x decor
subset is already native. A separate future task can move the broader family
16x resources to native mod assets and establish a distinct 32x pack; no 32x
restructure was performed during this migration.

## Licence and attribution

Daedalon, ERYDON, and both active Themelios projects identify
`OLIVER'S MINECRAFT MODS RESTRICTED LICENSE 1.0` in mod metadata as
`LicenseRef-Oliver-Restricted-1.0`. Both shared resource packs include the same
text as `LICENSE.txt`. All six copies are byte-identical, with SHA-256:

`3F47A880CFAE2AE4A06CC80CBAF6DE9CBBF66E81A74DDFB8361328B03B6E98CF`

The licence permits unmodified personal use and free attributed modpacks. It
prohibits sale, paid access, modification, derivative works, and other
redistribution without written permission. Third-party components remain
under their own licences.

Daedalon packages this required notice:

> "Neoclassical Urn on Pedestal | Lowpoly Asset" by tina.hill,
> <https://skfb.ly/pGAGK>, licensed under CC BY 4.0.

That credited source is used for Diota, Kylix, and Kalyx.

Themelios received no functional code, content, registry, or resource change
during this migration. Its only requested change was the shared licence in
source metadata and rebuilt packaging.

## Build and test commands

Daedalon:

```powershell
python .\tools\generate_daedalon_assets.py --families all --check
.\gradlew.bat clean build --no-daemon
```

ERYDON:

```powershell
.\gradlew.bat clean build --no-daemon
.\gradlew.bat build --no-daemon
```

The final incremental ERYDON build repackaged the adjusted licence after the
full clean migration build and all audits had passed.

Each active Themelios project:

```powershell
.\gradlew.bat clean build --no-daemon
```

Dedicated-server harness:

```powershell
java -Xms512M -Xmx8G -Dfile.encoding=UTF-8 -jar fabric-server-launch.jar nogui
```

Combined ERYDON runs used `-Xmx10G`. After each `Done` line, the harness sent
`stop` and verified the dimension-save messages.

## Automated verification

| Check | Result |
| --- | --- |
| Daedalon asset generator `--families all --check` | Passed |
| Daedalon clean build and JAR audit | Passed; 17/17 contract tests |
| Daedalon standalone dedicated server | Passed; reached `Done`, then saved and stopped cleanly |
| ERYDON build and JAR audit | Passed |
| ERYDON CTM path validation | Passed; 1,163 files, 43,980 references, 0 missing |
| ERYDON CTM isolation | Passed; 169 foreign IDs inspected, 0 altered |
| ERYDON slope/world-UV and geometry checks | Passed |
| Themelios 1.20.1 clean build | Passed |
| Themelios 1.21.1 clean build | Passed |
| Dedicated-server combination matrix | Passed; all six combinations |

Server checks never launched a Minecraft client.

### Dedicated-server matrix

Each run used Fabric Loader 0.15.7, Fabric API 0.92.2, Java 17, a fresh
isolated world, and an ephemeral local port. Every combination reached
`Done`, accepted `stop`, and saved all dimensions. The logs contained no
entrypoint failure, exception, registry collision, duplicate ID, or
datapack/resource failure.

| Combination | Launcher to `Done` | Minecraft `Done` time | Result |
| --- | ---: | ---: | --- |
| ERYDON | 109 s | 9.609 s | Passed |
| Themelios | 21 s | 9.071 s | Passed |
| ERYDON + Themelios | 102 s | 10.224 s | Passed |
| Daedalon + ERYDON | 151 s | 10.288 s | Passed |
| Daedalon + Themelios | 28 s | 10.209 s | Passed |
| All three | 118 s | 9.106 s | Passed |

The matrix began before the final licence wording rebuilds. Entry-by-entry
SHA-256 comparison proved that the tested and final JARs have identical entry
sets and identical executable/resource content. Exactly one entry differed in
each JAR: its packaged `LICENSE_*` text.

The temporary raw server logs were reviewed before the test harness was moved
to the Recycle Bin during the requested tidy-up. The complete per-combination
results and timings are retained above.

An excluded ERYDON calibration run used only 1.5 GiB and exhausted that
insufficient heap during its large registry setup. Its fresh 8 GiB replacement
is the passing ERYDON row above; this was a harness calibration issue rather
than a mod failure.

## Build artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `daedalon-fabric-mc1.20.1-0.1.0-beta.jar` | 3,311,804 | `189CDB8E1AC1F56D0D4D9334B86F4C04C46C9AFEF44F769146093FC2D7F2D13C` |
| `erydon-fabric-mc1.20.1-1.5.12.jar` | 50,318,346 | `4EC5566C273D3C1A29B84AF9051B9D01C8F73BE0BA3769B7A620578D320C795D` |
| `themelios-fabric-mc1.20.1-1.5.12.jar` | 32,916,932 | `F42420B38D7CD67FFE62FEF6D27E2213309BEEE079FC603966B0BBC43D39250E` |
| `themelios-fabric-mc1.21.1-1.5.12.jar` | 32,915,268 | `31AD143D4EA36F3E607A10A639E2CDC36CF8685ECD2BD7157D04EEA2B2F8FCFD` |

Each completed artifact contains `LicenseRef-Oliver-Restricted-1.0`, and its
packaged licence matches the source licence exactly.

## Git commits

Daedalon:

- `7c2a61c` - scaffold standalone Daedalon mod
- `86e82a5` - add independent Konche OBJ pilot
- `2423e5c` - adopt restricted project licence
- `bd4549e` - migrate complete OBJ decor catalogue
- `500ae0b` - align shared restricted licence

ERYDON:

- `57e01e856` - adopt restricted project licence
- `f80cfe62a` - move OBJ decor into Daedalon
- `b521dfc97` - licence the shared mod-family resource packs

The ERYDON branch is pushed to
`origin/codex/daedalon-migration`. Unrelated ERYDON `.idea` changes remain
uncommitted.

Daedalon has no configured Git remote, so its branch and commits remain local.
The two active Themelios directories are not Git repositories; their licence
and rebuilt JAR changes are present locally but could not be committed.

## Changelog readback

The live Google Sheet `Erydon` was updated and read back:

- `CL Erydon!A103:D104`
- `CL Deadalon!A2:D2` (existing tab spelling)
- `CL Themelios!A14:D14`
- `CL 64x and 16x!C13:D13`

## Manual visual validation

Pending user testing. No client was launched automatically.

Recommended test:

1. Run Daedalon alone without an external resource pack and place all six
   families. Confirm they appear in the creative tab and search.
2. Check statue facings and all three heights, especially the default 2-high
   form and ground contact.
3. Check all urn facings, sizes, offsets, selection targets, outlines, and
   collision.
4. Break each family and verify its self-drop, then check handheld and GUI item
   models.
5. Enable the shared 16x Lite pack with all three mods and inspect every family.
6. Enable the 64x PBR pack and shaders; inspect all stone materials, Diota
   bronze/yellow specular, Kalyx faces, UV seams, and triangle reflections.
7. Reload resources and check the log for model, texture, MTL, shader, or
   resource-pack errors.
8. Confirm ERYDON Oil Burners, architectural plinths, slopes, and POM corners
   are unchanged.
