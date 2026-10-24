from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
NAMESPACE = "daedalon"

STATIC_LANGUAGE_ENTRIES = {
    "en_us": {
        "itemGroup.daedalon": "ERYDON Daedalon",
        "item.daedalon.emblem": "ERYDON Daedalon Emblem",
        "message.erydon_family.resource_packs.notice": (
            "ERYDON now uses native 16x textures. Download the optional 32x "
            "and 64x packs from %s or %s."
        ),
        "message.erydon_family.resource_packs.modrinth": "Modrinth",
        "message.erydon_family.resource_packs.curseforge": "CurseForge",
        "message.erydon_family.resource_packs.open_modrinth": (
            "Open the ERYDON Modrinth page"
        ),
        "message.erydon_family.resource_packs.open_curseforge": (
            "Open the ERYDON CurseForge projects page"
        ),
        "message.daedalon.fountain_basin_size_blocked": (
            "Not enough clear space for that fountain size"
        ),
        "message.daedalon.fountain_plinth_added": "Fountain plinth added",
        "tooltip.daedalon.fountain.raised_base": "Debug stick: Raised base lifts the whole fountain by one block.",
        "tooltip.daedalon.fountain.raised_base_water": "When waterlogged, water can surround the lower base.",
        "search.daedalon.plinth.fountain": "fountain fountains fountain plinth tiered fountain water feature",
        "tooltip.daedalon.plinth.fountain": "Use to build larger, tiered fountains.",
        "tooltip.daedalon.plinth.fountain_use": (
            "Right-click a fountain basin repeatedly with the same plinth to add its base and bowls."
        ),
        "message.daedalon.fountain_bowl_added": "Fountain bowl added",
        "message.daedalon.fountain_assembly_blocked": (
            "Not enough clear space for that fountain piece"
        ),
        "message.daedalon.fountain_assembly_complete": "This fountain is complete",
        "message.daedalon.fountain_plinth_mismatch": (
            "Use the same plinth to continue this fountain"
        ),
    },
    "de_de": {
        "itemGroup.daedalon": "ERYDON Daedalon",
        "item.daedalon.emblem": "ERYDON Daedalon-Emblem",
        "message.erydon_family.resource_packs.notice": (
            "ERYDON verwendet jetzt native 16x-Texturen. Lade die optionalen "
            "32x- und 64x-Pakete bei %s oder %s herunter."
        ),
        "message.erydon_family.resource_packs.modrinth": "Modrinth",
        "message.erydon_family.resource_packs.curseforge": "CurseForge",
        "message.erydon_family.resource_packs.open_modrinth": (
            "Die ERYDON-Seite auf Modrinth öffnen"
        ),
        "message.erydon_family.resource_packs.open_curseforge": (
            "Die ERYDON-Projektseite auf CurseForge öffnen"
        ),
        "message.daedalon.fountain_basin_size_blocked": (
            "Nicht genug freier Platz für diese Brunnengröße"
        ),
        "message.daedalon.fountain_plinth_added": "Brunnensockel hinzugefügt",
        "tooltip.daedalon.fountain.raised_base": "Debug-Stab: Erhöhter Sockel hebt den ganzen Brunnen um einen Block.",
        "tooltip.daedalon.fountain.raised_base_water": "Bei Wasserfüllung kann Wasser den unteren Sockel umgeben.",
        "search.daedalon.plinth.fountain": "Brunnen Brunnensockel mehrstufiger Brunnen Wasserspiel",
        "tooltip.daedalon.plinth.fountain": "Zum Bau größerer, mehrstufiger Brunnen.",
        "tooltip.daedalon.plinth.fountain_use": (
            "Klicke ein Brunnenbecken wiederholt mit demselben Sockel rechts an, um Sockel und Schalen hinzuzufügen."
        ),
        "message.daedalon.fountain_bowl_added": "Brunnenschale hinzugefügt",
        "message.daedalon.fountain_assembly_blocked": (
            "Nicht genug freier Platz für dieses Brunnenteil"
        ),
        "message.daedalon.fountain_assembly_complete": "Dieser Brunnen ist vollständig",
        "message.daedalon.fountain_plinth_mismatch": (
            "Verwende denselben Sockel, um diesen Brunnen weiterzubauen"
        ),
    },
    "es_es": {
        "itemGroup.daedalon": "ERYDON Daedalon",
        "item.daedalon.emblem": "Emblema de ERYDON Daedalon",
        "message.erydon_family.resource_packs.notice": (
            "ERYDON ahora usa texturas nativas de 16x. Descarga los paquetes "
            "opcionales de 32x y 64x en %s o %s."
        ),
        "message.erydon_family.resource_packs.modrinth": "Modrinth",
        "message.erydon_family.resource_packs.curseforge": "CurseForge",
        "message.erydon_family.resource_packs.open_modrinth": (
            "Abrir la página de ERYDON en Modrinth"
        ),
        "message.erydon_family.resource_packs.open_curseforge": (
            "Abrir la página de proyectos de ERYDON en CurseForge"
        ),
        "message.daedalon.fountain_basin_size_blocked": (
            "No hay suficiente espacio libre para ese tamaño de fuente"
        ),
        "message.daedalon.fountain_plinth_added": "Pedestal de fuente añadido",
        "tooltip.daedalon.fountain.raised_base": "Palo de depuración: Base elevada sube toda la fuente un bloque.",
        "tooltip.daedalon.fountain.raised_base_water": "Si está anegada, el agua puede rodear la base inferior.",
        "search.daedalon.plinth.fountain": "fuente fuentes pedestal de fuente fuente de varios niveles juego de agua",
        "tooltip.daedalon.plinth.fountain": "Úsalo para construir fuentes más grandes de varios niveles.",
        "tooltip.daedalon.plinth.fountain_use": (
            "Haz clic derecho varias veces en una pila de fuente con el mismo pedestal para añadir su base y cuencos."
        ),
        "message.daedalon.fountain_bowl_added": "Cuenco de fuente añadido",
        "message.daedalon.fountain_assembly_blocked": (
            "No hay suficiente espacio libre para esa pieza de fuente"
        ),
        "message.daedalon.fountain_assembly_complete": "Esta fuente está completa",
        "message.daedalon.fountain_plinth_mismatch": (
            "Usa el mismo pedestal para continuar esta fuente"
        ),
    },
}

MATERIALS = (
    "aganite",
    "aterzon",
    "borealis",
    "brectite",
    "calacattum",
    "chalstrom",
    "chrysonyx",
    "etruscus",
    "gelastrum",
    "glacium",
    "hesperion",
    "imperium",
    "kylorion",
    "kelastrion",
    "latmion",
    "laurentium",
    "mielonyx",
    "nerium",
    "noxoplis",
    "porphyros",
    "psamatheon",
    "portorium",
    "rosinium",
    "sanguenite",
    "selenephos",
    "solistra",
    "striatus",
)
BRONZE_MATERIAL = "bronze"
SILVER_MATERIAL = "silver"
WHITE_FINISH = "white"
NO_DROP_FAMILIES = frozenset(
    {
        "gothic_fountain_basin",
        "georgian_fountain_basin",
        "greek_fountain_basin",
    }
)

MATERIAL_TAGS = {
    "aganite": ("gemstone", "agate"),
    "aterzon": ("marble",),
    "borealis": ("gemstone", "labradorite"),
    "brectite": ("marble",),
    "calacattum": ("marble",),
    "chalstrom": ("marble",),
    "chrysonyx": ("onyx",),
    "etruscus": ("marble",),
    "gelastrum": ("quartzite",),
    "glacium": ("marble",),
    "hesperion": ("sodalite", "blue_stone"),
    "imperium": ("marble",),
    "kylorion": ("marble",),
    "kelastrion": ("limestone",),
    "latmion": ("limestone",),
    "laurentium": ("marble",),
    "mielonyx": ("onyx",),
    "nerium": ("marble",),
    "noxoplis": ("opal",),
    "porphyros": ("marble",),
    "psamatheon": ("sandstone",),
    "portorium": ("marble",),
    "rosinium": ("marble",),
    "sanguenite": ("marble",),
    "selenephos": ("alabaster",),
    "solistra": ("marble",),
    "striatus": ("travertine",),
}

URN_FAMILIES = {
    "amphora": ("urn_amphora", "Amphora"),
    "konche": ("urn_konche", "Konche"),
    "diota": ("urn_diota", "Diota"),
    "kylix": ("urn_kylix", "Kylix"),
    "kalyx": ("urn_kalyx", "Kalyx"),
    "lekythos": ("urn_lekythos", "Lekythos"),
    "pelike": ("urn_pelike", "Pelike"),
    "pithos": ("urn_pithos", "Pithos"),
    "rhabdos": ("urn_rhabdos", "Rhabdos"),
    "salpinx": ("urn_salpinx", "Salpinx"),
    "stamnos": ("urn_stamnos", "Stamnos"),
}
CORBEL_FAMILIES = {
    "baroque": ("corbel_baroque", "Baroque", "Barocke", "Barroca"),
    "corinthian": ("corbel_corinthian", "Corinthian", "Korinthische", "Corintia"),
    "georgian": ("corbel_georgian", "Georgian", "Georgische", "Georgiana"),
    "ionic": ("corbel_ionic", "Ionic", "Ionische", "Jónica"),
    "renaissance": ("corbel_renaissance", "Renaissance", "Renaissance", "Renacentista"),
}
CAPITAL_FAMILIES = {
    "byzantine": ("capital_byzantine", "Byzantine", "Byzantinisch", "Bizantino"),
    "corinthian_capital": ("capital_corinthian", "Corinthian", "Korinthisch", "Corintio"),
    "gothic": ("capital_gothic", "Gothic", "Gotisch", "Gótico"),
    "greek_ionic": ("capital_greek_ionic", "Greek Ionic", "Griechisch-Ionisch", "Jónico Griego"),
    "roman_composite": ("capital_roman_composite", "Roman Composite", "Römisch-Komposit", "Compuesto Romano"),
    "tuscan": ("capital_tuscan", "Tuscan", "Toskanisch", "Toscano"),
}
BUST_FAMILIES = {
    "bust_aphrodite": ("aphrodite", "Aphrodite", "Aphrodite", "Afrodita"),
    "bust_apollo": ("apollo", "Apollo", "Apollo", "Apolo"),
    "bust_ares": ("ares", "Ares", "Ares", "Ares"),
    "bust_artemis": ("artemis", "Artemis", "Artemis", "Artemisa"),
    "bust_athena": ("athena", "Athena", "Athene", "Atenea"),
    "bust_demeter": ("demeter", "Demeter", "Demeter", "Deméter"),
    "bust_dionysus": ("dionysus", "Dionysus", "Dionysos", "Dioniso"),
    "bust_hephaestus": ("hephaestus", "Hephaestus", "Hephaistos", "Hefesto"),
    "bust_hera": ("hera", "Hera", "Hera", "Hera"),
    "bust_hermes": ("hermes", "Hermes", "Hermes", "Hermes"),
    "bust_hestia": ("hestia", "Hestia", "Hestia", "Hestia"),
    "bust_poseidon": ("poseidon", "Poseidon", "Poseidon", "Poseidón"),
    "bust_zeus": ("zeus", "Zeus", "Zeus", "Zeus"),
}
FIXED_DECOR_FAMILIES = {
    "balanos_finial": ("finial_balanos", "Balanos Finial", "Balanos-Finial", "Remate Balanos"),
    "kynara_finial": ("finial_kynara", "Kynara Finial", "Kynara-Finial", "Remate Kynara"),
    "phlox_finial": ("finial_phlox", "Phlox Finial", "Phlox-Finial", "Remate Phlox"),
    "sphaira_finial": ("finial_sphaira", "Sphaira Finial", "Sphaira-Finial", "Remate Sphaira"),
    "strobilos_finial": ("finial_strobilos", "Strobilos Finial", "Strobilos-Finial", "Remate Strobilos"),
    "louterion_basin": ("basin_louterion", "Louterion Basin", "Louterion-Becken", "Cuenca Louterion"),
    "pege_fountain": ("fountain_pege", "Pege Fountain", "Pege-Brunnen", "Fuente Pege"),
}
FINIAL_FAMILIES = tuple(
    family for family in FIXED_DECOR_FAMILIES if family.endswith("_finial")
)
FACING_DECOR_FAMILIES = {
    "krene_fountain": ("fountain_krene", "Krene Fountain", "Krene-Brunnen", "Fuente Krene"),
}
FOUNTAIN_BASIN_FAMILIES = {
    "gothic_fountain_basin": (
        "fountain_gothic_basin",
        "Gothic Fountain Basin",
        "Gotisches Brunnenbecken",
        "Cuenca de Fuente Gótica",
    ),
    "georgian_fountain_basin": (
        "fountain_georgian_basin",
        "Georgian Fountain Basin",
        "Georgisches Brunnenbecken",
        "Cuenca de Fuente Georgiana",
    ),
    "greek_fountain_basin": (
        "fountain_greek_basin",
        "Greek Fountain Basin",
        "Griechisches Brunnenbecken",
        "Cuenca de Fuente Griega",
    ),
}
SIZED_DECOR_FAMILIES = {
    "obeliskos_monument": ("monument_obeliskos", "Obeliskos Monument", "Obeliskos-Monument", "Monumento Obeliskos"),
}
PLINTH_FAMILIES = {
    "astragalos_plinth": ("plinth_astragalos", "Astragalos", "Astragalos", "Astragalos"),
    "bathron_plinth": ("plinth_bathron", "Bathron", "Bathron", "Bathron"),
    "kion_plinth": ("plinth_kion", "Kion", "Kion", "Kion"),
    "stephanos_plinth": ("plinth_stephanos", "Stephanos", "Stephanos", "Stephanos"),
    "triphyllon_plinth": ("plinth_triphyllon", "Triphyllon", "Triphyllon", "Triphyllon"),
}
BENCH_FAMILIES = ("exedra", "hedra")
WIDTH_FAMILIES = (*BENCH_FAMILIES, "anthophoros_planter")
PANEL_FAMILIES = ("gothic_wall_panel",)
ARCHITECTURAL_FINISH_FAMILIES = (
    "gothic_wall_panel", "byzantine_frieze", "gothic_frieze",
    "corinthian_frieze", "ionic_frieze",
)
ARCHITECTURAL_FINISHES = (WHITE_FINISH, BRONZE_MATERIAL, SILVER_MATERIAL)
NEW_DECOR_FAMILIES = {
    "gothic_wall_panel": ("gothic_panel", "Gothic Panel", "Gotisches Paneel", "Panel Gótico"),
    "byzantine_frieze": ("byzantine_frieze", "Byzantine Frieze", "Byzantinischer Fries", "Friso Bizantino"),
    "gothic_frieze": ("gothic_frieze", "Gothic Frieze", "Gotischer Fries", "Friso Gótico"),
    "corinthian_frieze": ("corinthian_frieze", "Corinthian Frieze", "Korinthischer Fries", "Friso Corintio"),
    "ionic_frieze": ("ionic_frieze", "Ionic Frieze", "Ionischer Fries", "Friso Jónico"),
    "anthophoros_planter": ("anthophoros_3m", "Anthophoros Planter", "Anthophoros-Pflanzgefäß", "Jardinera Anthophoros"),
    "monopteros_dome": ("monopteros_6m", "Monopteros Dome", "Monopteros-Kuppel", "Cúpula Monópteros"),
    "hedra": ("hedra_3m", "Hedra Bench", "Hedra-Bank", "Banco Hedra"),
    "exedra": ("exedra_3m", "Exedra", "Exedra", "Exedra"),
    **FIXED_DECOR_FAMILIES,
    **FACING_DECOR_FAMILIES,
    **FOUNTAIN_BASIN_FAMILIES,
    **SIZED_DECOR_FAMILIES,
}
LEGACY_UNPUBLISHED_URN_FAMILIES = ("konche", "diota", "kylix", "kalyx")
CANONICAL_STATUE_FAMILIES = {
    "zeus": ("Zeus", "Zeus", "Zeus"),
    "aphrodite": ("Aphrodite", "Aphrodite", "Afrodita"),
    "apollo": ("Apollo", "Apollo", "Apolo"),
    "ares": ("Ares", "Ares", "Ares"),
    "artemis": ("Artemis", "Artemis", "Artemisa"),
    "athena": ("Athena", "Athene", "Atenea"),
    "demeter": ("Demeter", "Demeter", "Deméter"),
    "dionysus": ("Dionysus", "Dionysos", "Dioniso"),
    "helios": ("Helios", "Helios", "Helios"),
    "hephaestus": ("Hephaestus", "Hephaistos", "Hefesto"),
    "hera": ("Hera", "Hera", "Hera"),
    "hermes": ("Hermes", "Hermes", "Hermes"),
    "hestia": ("Hestia", "Hestia", "Hestia"),
    "phaeton": ("Phaeton", "Phaethon", "Faetón"),
    "poseidon": ("Poseidon", "Poseidon", "Poseidón"),
    "bellerophon": ("Bellerophon", "Bellerophon", "Belerofonte"),
    "heracles": ("Heracles", "Herakles", "Heracles"),
    "orpheus": ("Orpheus", "Orpheus", "Orfeo"),
    "perseus": ("Perseus", "Perseus", "Perseo"),
    "theseus": ("Theseus", "Theseus", "Teseo"),
    "lion_couchant": ("Lion Couchant", "Liegender Löwe", "León Recostado"),
    "lion_statant": ("Lion Statant", "Stehender Löwe", "León Erguido"),
}
# Placement selectors are items, never additional world-block or OBJ families.
OLYMPIAN_GOD_ITEM_FAMILY = "olympian_god_statue"
OLYMPIAN_GOD_CYCLE = (
    "zeus", "hera", "poseidon", "demeter", "athena", "apollo", "artemis",
    "ares", "aphrodite", "hephaestus", "hermes", "dionysus",
)


OLYMPIAN_STATUE_FAMILIES = (
    "zeus",
    "aphrodite",
    "apollo",
    "ares",
    "artemis",
    "athena",
    "demeter",
    "dionysus",
    "hephaestus",
    "hera",
    "hermes",
    "hestia",
    "poseidon",
)
HERO_STATUE_FAMILIES = (
    "bellerophon",
    "heracles",
    "orpheus",
    "perseus",
    "theseus",
)
LION_STATUE_FAMILIES = ("lion_couchant", "lion_statant")
METAL_DECOR_FAMILIES = (
    *FINIAL_FAMILIES,
    "spartan",
    *CANONICAL_STATUE_FAMILIES,
    *URN_FAMILIES,
    *BUST_FAMILIES,
    *CORBEL_FAMILIES,
    *PLINTH_FAMILIES,
)
BRONZE_FAMILIES = (
    *METAL_DECOR_FAMILIES,
    "obeliskos_monument",
    "monopteros_dome",
    "anthophoros_planter",
    *ARCHITECTURAL_FINISH_FAMILIES,
)
METAL_FAMILIES = {
    BRONZE_MATERIAL: BRONZE_FAMILIES,
    SILVER_MATERIAL: (*METAL_DECOR_FAMILIES, *ARCHITECTURAL_FINISH_FAMILIES),
}
STATUE_DISPLAY_MODELS = {
    "spartan": "statue_spartan_promachos",
    **{family: "zeus_statue" for family in CANONICAL_STATUE_FAMILIES},
}
ALL_FAMILIES = (
    "amphora",
    "konche",
    "diota",
    "kylix",
    "kalyx",
    "lekythos",
    "pelike",
    "pithos",
    "rhabdos",
    "salpinx",
    "stamnos",
    *CORBEL_FAMILIES,
    *CAPITAL_FAMILIES,
    *BUST_FAMILIES,
    *NEW_DECOR_FAMILIES,
    "spartan",
    *CANONICAL_STATUE_FAMILIES,
    *PLINTH_FAMILIES,
)


def json_bytes(payload: object) -> bytes:
    return (json.dumps(payload, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def write(path: Path, content: bytes, check: bool, changed: list[Path]) -> None:
    if path.exists() and path.read_bytes() == content:
        return
    if check:
        raise ValueError(f"Generated asset is missing or stale: {path.relative_to(ROOT)}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    changed.append(path)


def metal_block_id(family: str, metal: str) -> str:
    if family not in METAL_FAMILIES.get(metal, ()):
        raise ValueError(f"Family does not support the {metal.capitalize()} finish: {family}")
    if family == "spartan":
        return f"{metal}_spartan_promachos_statue"
    if family in CANONICAL_STATUE_FAMILIES:
        return f"{metal}_{family}_statue"
    if family in URN_FAMILIES:
        return f"{metal}_{family}_urn"
    if family in BUST_FAMILIES:
        return f"{metal}_{BUST_FAMILIES[family][0]}_bust"
    if family in CORBEL_FAMILIES:
        return f"{metal}_{family}_corbel"
    if family in PLINTH_FAMILIES:
        return f"{metal}_{family}"
    if family in FINIAL_FAMILIES or family in ("obeliskos_monument", "monopteros_dome", "anthophoros_planter"):
        return metal + "_" + family
    if family in ARCHITECTURAL_FINISH_FAMILIES:
        return architectural_finish_block_id(family, metal)
    raise ValueError(f"Unknown metal decor family: {family}")


def architectural_finish_block_id(family: str, finish: str) -> str:
    if family not in ARCHITECTURAL_FINISH_FAMILIES or finish not in ARCHITECTURAL_FINISHES:
        raise ValueError(f"Unsupported architectural finish: {family} / {finish}")
    return f"{finish}_{family}"


def bronze_block_id(family: str) -> str:
    return metal_block_id(family, BRONZE_MATERIAL)


def add_metals(family: str, block_ids: list[str]) -> list[str]:
    for metal, families in METAL_FAMILIES.items():
        if family in families:
            block_ids.append(metal_block_id(family, metal))
    return block_ids


def olympian_god_item_ids() -> list[str]:
    return [
        f"{material}{'_aged' if aged else ''}_{OLYMPIAN_GOD_ITEM_FAMILY}"
        for material in MATERIALS for aged in (False, True)
    ] + [f"{metal}_{OLYMPIAN_GOD_ITEM_FAMILY}" for metal in (BRONZE_MATERIAL, SILVER_MATERIAL)]


def generate_olympian_god_items(check: bool, changed: list[Path]) -> None:
    # One item-only medallion mesh supplies every material's cached icon.
    write(RESOURCES / f"assets/{NAMESPACE}/models/item/olympian_god.json",
          json_bytes({"parent": f"{NAMESPACE}:item/olympian_god_display"}),
          check, changed)
    write(RESOURCES / f"assets/{NAMESPACE}/models/item/olympian_god_display.json",
          json_bytes({
              "parent": "minecraft:block/block",
              "textures": {"particle": f"{NAMESPACE}:block/statue_spartan_promachos_aganite"},
              "display": {
                  "gui": {"rotation": [0, 20, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                  "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                  "fixed": {"rotation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
              },
          }), check, changed)
    for item_id in olympian_god_item_ids():
        write(RESOURCES / f"assets/{NAMESPACE}/models/item/{item_id}.json",
              json_bytes({"parent": f"{NAMESPACE}:item/olympian_god"}), check, changed)


def family_block_ids(family: str) -> list[str]:
    result: list[str] = []
    if family == "spartan":
        for material in MATERIALS:
            result.extend(
                (
                    f"statue_spartan_promachos_{material}",
                    f"statue_spartan_promachos_{material}_aged",
                )
            )
        return add_metals(family, result)

    if family in CANONICAL_STATUE_FAMILIES:
        for material in MATERIALS:
            result.extend(
                (
                    f"{material}_{family}_statue",
                    f"{material}_aged_{family}_statue",
                )
            )
        return add_metals(family, result)

    if family in URN_FAMILIES:
        suffix = f"{family}_urn"
        for material in MATERIALS:
            result.extend(
                (
                    f"{material}_{suffix}",
                    f"{material}_aged_{suffix}",
                )
            )
        return add_metals(family, result)

    if family in BUST_FAMILIES:
        subject = BUST_FAMILIES[family][0]
        suffix = f"{subject}_bust"
        for material in MATERIALS:
            result.extend(
                (
                    f"{material}_{suffix}",
                    f"{material}_aged_{suffix}",
                )
            )
        return add_metals(family, result)

    if family in CORBEL_FAMILIES:
        suffix = f"{family}_corbel"
        for material in MATERIALS:
            result.extend(
                (
                    f"{material}_{suffix}",
                    f"{material}_aged_{suffix}",
                )
            )
        return add_metals(family, result)

    if family in CAPITAL_FAMILIES:
        style = "corinthian" if family == "corinthian_capital" else family
        suffix = f"{style}_capital"
        for material in MATERIALS:
            result.extend(
                (
                    f"{material}_{suffix}",
                    f"{material}_aged_{suffix}",
                )
            )
        return add_metals(family, result)

    if family in NEW_DECOR_FAMILIES:
        for material in MATERIALS:
            result.extend((f"{material}_{family}", f"{material}_aged_{family}"))
        if family in ARCHITECTURAL_FINISH_FAMILIES:
            result.append(architectural_finish_block_id(family, WHITE_FINISH))
        return add_metals(family, result)

    if family in PLINTH_FAMILIES:
        for material in MATERIALS:
            result.extend((f"{material}_{family}", f"{material}_aged_{family}"))
        return add_metals(family, result)

    raise ValueError(f"No ID grammar registered for family: {family}")


def remove_obsolete_unpublished_urn_assets(
    check: bool, changed: list[Path]
) -> None:
    obsolete_paths: list[Path] = []
    for family in LEGACY_UNPUBLISHED_URN_FAMILIES:
        for material in MATERIALS:
            for block_id in (
                f"{material}_urn_{family}",
                f"{material}_urn_{family}_aged",
            ):
                obsolete_paths.extend(
                    (
                        RESOURCES
                        / f"assets/{NAMESPACE}/blockstates/{block_id}.json",
                        RESOURCES
                        / f"assets/{NAMESPACE}/models/item/{block_id}.json",
                        RESOURCES
                        / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json",
                    )
                )
        for kind in ("blocks", "items"):
            obsolete_paths.append(
                RESOURCES
                / f"data/{NAMESPACE}/tags/{kind}/urn_{family}.json"
            )

    for path in obsolete_paths:
        if not path.exists():
            continue
        if check:
            raise ValueError(
                "Obsolete unpublished urn asset remains: "
                f"{path.relative_to(ROOT)}"
            )
        path.unlink()
        changed.append(path)


def remove_obsolete_unpublished_architectural_gold_assets(
    check: bool, changed: list[Path]
) -> None:
    # The five gold IDs existed only in the unpublished local cc6b840d change.
    # The approved finish is silver; no published identifier is renamed here.
    obsolete_paths: list[Path] = []
    for family in ARCHITECTURAL_FINISH_FAMILIES:
        block_id = f"gold_{family}"
        obsolete_paths.extend((
            RESOURCES / f"assets/{NAMESPACE}/blockstates/{block_id}.json",
            RESOURCES / f"assets/{NAMESPACE}/models/item/{block_id}.json",
            RESOURCES / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json",
        ))
    for name in ("gold.png", "gold_s.png"):
        obsolete_paths.append(RESOURCES / f"assets/{NAMESPACE}/textures/block/{name}")
    for kind in ("blocks", "items"):
        obsolete_paths.append(RESOURCES / f"data/{NAMESPACE}/tags/{kind}/gold.json")
    for path in obsolete_paths:
        if not path.exists():
            continue
        if check:
            raise ValueError("Obsolete unpublished architectural gold asset remains: "
                             f"{path.relative_to(ROOT)}")
        path.unlink()
        changed.append(path)


def remove_obsolete_unpublished_georgian_plinth_assets(
    check: bool, changed: list[Path]
) -> None:
    obsolete_paths: list[Path] = []
    for material in MATERIALS:
        for block_id in (
            f"{material}_georgian_plinth",
            f"{material}_georgian_plinth_aged",
        ):
            obsolete_paths.extend(
                (
                    RESOURCES / f"assets/{NAMESPACE}/blockstates/{block_id}.json",
                    RESOURCES / f"assets/{NAMESPACE}/models/item/{block_id}.json",
                    RESOURCES / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json",
                )
            )
    for kind in ("blocks", "items"):
        obsolete_paths.append(
            RESOURCES / f"data/{NAMESPACE}/tags/{kind}/georgian_plinth.json"
        )
    for path in obsolete_paths:
        if not path.exists():
            continue
        if check:
            raise ValueError(
                "Retired unpublished Georgian Plinth asset remains: "
                f"{path.relative_to(ROOT)}"
            )
        path.unlink()
        changed.append(path)


def remove_obsolete_unpublished_fountain_bowl_ids(
    check: bool, changed: list[Path]
) -> None:
    obsolete_paths: list[Path] = []
    for material in MATERIALS:
        for style in FOUNTAIN_BASIN_FAMILIES:
            bowl_family = style.removesuffix("_basin") + "_bowl"
            for block_id in (
                f"{material}_{bowl_family}",
                f"{material}_aged_{bowl_family}",
            ):
                obsolete_paths.extend(
                    (
                        RESOURCES / f"assets/{NAMESPACE}/blockstates/{block_id}.json",
                        RESOURCES / f"assets/{NAMESPACE}/models/item/{block_id}.json",
                        RESOURCES / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json",
                    )
                )
    for kind in ("blocks", "items"):
        style_bowl_tags = tuple(
            style.removesuffix("_basin") + "_bowl"
            for style in FOUNTAIN_BASIN_FAMILIES
        )
        for tag in (*style_bowl_tags, "bowl", "fountain_tier"):
            obsolete_paths.append(
                RESOURCES / f"data/{NAMESPACE}/tags/{kind}/{tag}.json"
            )

    for path in obsolete_paths:
        if not path.exists():
            continue
        if check:
            raise ValueError(
                "Retired unpublished fountain bowl ID asset remains: "
                f"{path.relative_to(ROOT)}"
            )
        path.unlink()
        changed.append(path)


def remove_obsolete_unpublished_ceiling_panels(check: bool, changed: list[Path]) -> None:
    # The development-only ceiling item was merged into the existing Gothic Panel.
    paths = []
    for material in MATERIALS:
        for aged in (False, True):
            block_id = f"{material}{'_aged' if aged else ''}_gothic_coffered_ceiling"
            paths.extend((
                RESOURCES / f"assets/{NAMESPACE}/blockstates/{block_id}.json",
                RESOURCES / f"assets/{NAMESPACE}/models/item/{block_id}.json",
                RESOURCES / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json",
            ))
    for kind in ("blocks", "items"):
        paths.append(RESOURCES / f"data/{NAMESPACE}/tags/{kind}/gothic_coffered_ceiling.json")
    for path in paths:
        if not path.exists():
            continue
        if check:
            raise ValueError(f"Retired unpublished ceiling panel asset remains: {path.relative_to(ROOT)}")
        path.unlink()
        changed.append(path)


def remove_creative_only_loot(
    families: tuple[str, ...], check: bool, changed: list[Path]
) -> None:
    for family in families:
        if family not in NO_DROP_FAMILIES:
            continue
        for block_id in family_block_ids(family):
            path = RESOURCES / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json"
            if not path.exists():
                continue
            if check:
                raise ValueError(
                    "Creative-only family loot table remains: "
                    f"{path.relative_to(ROOT)}"
                )
            path.unlink()
            changed.append(path)


def blockstate(family: str, block_id: str) -> dict[str, object]:
    model = f"{NAMESPACE}:mesh/{block_id}"
    if family in ("corinthian_frieze", "ionic_frieze", "gothic_frieze", "byzantine_frieze"):
        return {"variants": {"": {"model": model}}}
    if family == "monopteros_dome":
        return {"variants": {f"diameter={diameter}": {
            "model": model if diameter == 6 else f"{NAMESPACE}:mesh/internal/{block_id}_{diameter}m"
        } for diameter in (4, 6, 8)}}
    if family in WIDTH_FAMILIES:
        return {
            "variants": {
                f"width={width},facing={facing}": {
                    "model": model if width == 3 else f"{NAMESPACE}:mesh/internal/{block_id}_{width}m"
                }
                for width in (2, 3, 4)
                for facing in ("north", "east", "south", "west")
            }
        }
    if family in PANEL_FAMILIES:
        return {"variants": {"": {"model": model}}}
    if family in BUST_FAMILIES:
        # Busts place at Small by default, but retain all three correctly
        # scaling options alongside their shared facing and offset controls.
        return {
            "variants": {
                f"size={size},facing={facing}": {"model": model}
                for size in ("small", "medium", "large")
                for facing in ("north", "east", "south", "west")
            }
        }
    if family in STATUE_DISPLAY_MODELS:
        # OFFSET and orientation deliberately remain wildcards: every pose
        # uses the same baked mesh and cached StatueBlock transform.
        return {
            "variants": {
                f"size={size},facing={facing}": {"model": model}
                for size in ("small", "medium", "large")
                for facing in ("north", "east", "south", "west")
            }
        }
    if family in CORBEL_FAMILIES:
        return {
            "variants": {
                f"size={size},facing={facing}": {"model": model}
                for size in ("small", "medium", "large")
                for facing in ("north", "east", "south", "west")
            }
        }
    if family in CAPITAL_FAMILIES:
        return {"variants": {"": {"model": model}}}
    if family in FINIAL_FAMILIES:
        return {"variants": {f"size={size}": {"model": model} for size in ("small", "large")}}
    if family in FOUNTAIN_BASIN_FAMILIES:
        return {
            "variants": {
                f"size={size},waterlogged={waterlogged}": {"model": model}
                for size in ("small", "medium", "large")
                for waterlogged in ("false", "true")
            }
        }
    if family in FIXED_DECOR_FAMILIES:
        return {"variants": {"": {"model": model}}}
    if family in FACING_DECOR_FAMILIES:
        return {
            "variants": {
                f"facing={facing}": {"model": model}
                for facing in ("north", "east", "south", "west")
            }
        }
    if family in SIZED_DECOR_FAMILIES:
        return {"variants": {f"size={size}": {"model": model} for size in ("small", "medium", "large")}}
    if family in PLINTH_FAMILIES:
        return {
            "variants": {
                f"size={size},offset={offset},facing={facing}": {"model": model}
                for size in ("small", "medium", "large")
                for offset in ("false", "true")
                for facing in ("north", "east", "south", "west")
            }
        }
    return {
        "variants": {
            f"size={size},offset={offset},facing={facing}": {"model": model}
            for size in ("small", "medium", "large")
            for offset in ("false", "true")
            for facing in ("north", "east", "south", "west")
        }
    }


def display_parent(family: str) -> str:
    if family in PANEL_FAMILIES:
        return f"{NAMESPACE}:block/mesh/gothic_panel_display"
    if family in ("corinthian_frieze", "ionic_frieze", "gothic_frieze", "byzantine_frieze"):
        return f"{NAMESPACE}:block/mesh/{family}_display"
    if family == "anthophoros_planter":
        return f"{NAMESPACE}:block/mesh/anthophoros_display"
    if family == "monopteros_dome":
        return f"{NAMESPACE}:block/mesh/monopteros_display"
    if family in BENCH_FAMILIES:
        return f"{NAMESPACE}:block/mesh/{family}_display"
    if family in STATUE_DISPLAY_MODELS:
        name = STATUE_DISPLAY_MODELS[family]
    elif family in BUST_FAMILIES:
        name = "bust_hestia" if family == "bust_hestia" else "bust"
    elif family in CORBEL_FAMILIES:
        name = "corbel"
    elif family in CAPITAL_FAMILIES:
        name = "capital"
    elif family in FACING_DECOR_FAMILIES:
        name = "krene"
    elif family in FINIAL_FAMILIES:
        name = "finial"
    elif family in FOUNTAIN_BASIN_FAMILIES:
        name = "fountain_basin"
    elif family in FIXED_DECOR_FAMILIES:
        name = "decor"
    elif family in SIZED_DECOR_FAMILIES:
        name = "monument"
    elif family in PLINTH_FAMILIES:
        name = "plinth"
    else:
        name = URN_FAMILIES[family][0]
    return f"{NAMESPACE}:block/mesh/{name}_display"


def self_drop(block_id: str) -> dict[str, object]:
    return {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": f"{NAMESPACE}:{block_id}",
                    }
                ],
                "conditions": [
                    {
                        "condition": "minecraft:survives_explosion",
                    }
                ],
            }
        ],
    }


def generate_models_and_loot(
    families: tuple[str, ...], check: bool, changed: list[Path]
) -> None:
    for family in families:
        for block_id in family_block_ids(family):
            write(
                RESOURCES / f"assets/{NAMESPACE}/blockstates/{block_id}.json",
                json_bytes(blockstate(family, block_id)),
                check,
                changed,
            )
            write(
                RESOURCES / f"assets/{NAMESPACE}/models/item/{block_id}.json",
                json_bytes({"parent": display_parent(family)}),
                check,
                changed,
            )
            if family not in NO_DROP_FAMILIES:
                write(
                    RESOURCES / f"data/{NAMESPACE}/loot_tables/blocks/{block_id}.json",
                    json_bytes(self_drop(block_id)),
                    check,
                    changed,
                )


def translated_name(language: str, family: str, material: str, aged: bool) -> str:
    material_name = (
        "Bronce" if material == BRONZE_MATERIAL and language == "es_es"
        else "Silber" if material == SILVER_MATERIAL and language == "de_de"
        else "Plata" if material == SILVER_MATERIAL and language == "es_es"
        else "Weiß" if material == WHITE_FINISH and language == "de_de"
        else "Blanco" if material == WHITE_FINISH and language == "es_es"
        else material.capitalize()
    )
    if family == OLYMPIAN_GOD_ITEM_FAMILY:
        if language == "de_de":
            return f"{material_name} {'Gealterte ' if aged else ''}Olympische Götterstatue"
        if language == "es_es":
            return f"{material_name} Estatua de Dios Olímpico{' Envejecida' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}Olympian God Statue"

    if family == "spartan":
        if language == "de_de":
            name = "Spartanische Promachos-Statue"
            return f"{material_name} {'Gealterte ' if aged else ''}{name}"
        if language == "es_es":
            name = "Estatua Espartana Promachos"
            return f"{material_name} {name}{' Envejecida' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}Spartan Promachos Statue"

    if family in CANONICAL_STATUE_FAMILIES:
        language_index = {"en_us": 0, "de_de": 1, "es_es": 2}[language]
        subject = CANONICAL_STATUE_FAMILIES[family][language_index]
        if language == "de_de":
            return f"{material_name} {'Gealterte ' if aged else ''}{subject}-Statue"
        if language == "es_es":
            return f"{material_name} Estatua de {subject}{' Envejecida' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}{subject} Statue"

    if family in BUST_FAMILIES:
        language_index = {"en_us": 1, "de_de": 2, "es_es": 3}[language]
        subject = BUST_FAMILIES[family][language_index]
        if language == "de_de":
            return f"{material_name} {'Gealterte ' if aged else ''}{subject}-Büste"
        if language == "es_es":
            return f"{material_name} Busto de {subject}{' Envejecido' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}{subject} Bust"

    if family in PLINTH_FAMILIES:
        language_index = {"en_us": 1, "de_de": 2, "es_es": 3}[language]
        style = PLINTH_FAMILIES[family][language_index]
        if language == "de_de":
            return f"{material_name} {'Gealterter ' if aged else ''}{style}-Sockel"
        if language == "es_es":
            return f"{material_name} Pedestal {style}{' Envejecido' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}{style} Plinth"

    if family in CORBEL_FAMILIES:
        language_index = {"en_us": 1, "de_de": 2, "es_es": 3}[language]
        style = CORBEL_FAMILIES[family][language_index]
        if language == "de_de":
            form = f"{style}-Konsole" if family == "renaissance" else f"{style} Konsole"
            return f"{material_name} {'Gealterte ' if aged else ''}{form}"
        if language == "es_es":
            return f"{material_name} Ménsula {style}{' Envejecida' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}{style} Corbel"

    if family in CAPITAL_FAMILIES:
        language_index = {"en_us": 1, "de_de": 2, "es_es": 3}[language]
        style = CAPITAL_FAMILIES[family][language_index]
        if language == "de_de":
            return f"{material_name} {'Gealtertes ' if aged else ''}{style}es Kapitell"
        if language == "es_es":
            return f"{material_name} Capitel {style}{' Envejecido' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}{style} Capital"

    if family in NEW_DECOR_FAMILIES:
        language_index = {"en_us": 1, "de_de": 2, "es_es": 3}[language]
        name = NEW_DECOR_FAMILIES[family][language_index]
        if language == "de_de":
            return f"{material_name} {'Gealtertes ' if aged else ''}{name}"
        if language == "es_es":
            return f"{material_name} {name}{' Envejecido' if aged else ''}"
        return f"{material_name} {'Aged ' if aged else ''}{name}"

    shape_name = URN_FAMILIES[family][1]
    if language == "de_de":
        return f"{material_name} {'Gealterte ' if aged else ''}{shape_name}-Urne"
    if language == "es_es":
        return f"{material_name} Urna {shape_name}{' Envejecida' if aged else ''}"
    return f"{material_name} {'Aged ' if aged else ''}{shape_name} Urn"


# Search keywords, rather than visible sentences: word-by-word forms also let
# players search compound tags using either the English or translated wording.
SEARCH_WORD_TRANSLATIONS = {
    # word: (German, Spanish); proper names retain their established spelling.
    "cycle": ("Reihenfolge", "ciclo"),
    "cycling": ("wechselnd", "cíclico"),
    "sequence": ("Abfolge", "secuencia"),
    "honed": ("geschliffen", "apomazado"),
    "polished": ("poliert", "pulido"),
    "mirror": ("Spiegelglanz", "espejo"),
    "ganosis": ("Ganosis", "Ganosis"),
    "acanthus": ("Akanthus", "acanto"),
    "aged": ("gealtert", "envejecido"),
    "agate": ("Achat", "ágata"),
    "alabaster": ("Alabaster", "alabastro"),
    "animal": ("Tier", "animal"),
    "architecture": ("Architektur", "arquitectura"),
    "baroque": ("Barock", "barroco"),
    "base": ("Basis", "base"),
    "basin": ("Becken", "cuenca"),
    "bench": ("Bank", "banco"),
    "blue": ("blau", "azul"),
    "box": ("Kasten", "caja"),
    "bracket": ("Konsole", "ménsula"),
    "bronze": ("Bronze", "bronce"),
    "bust": ("Büste", "busto"),
    "byzantine": ("byzantinisch", "bizantino"),
    "capital": ("Kapitell", "capitel"),
    "ceiling": ("Decke", "techo"),
    "chariot": ("Streitwagen", "carro"),
    "classical": ("klassisch", "clásico"),
    "coffer": ("Kassette", "casetón"),
    "coffered": ("kassettiert", "artesonado"),
    "column": ("Säule", "columna"),
    "composite": ("Komposit", "compuesto"),
    "corbel": ("Konsole", "ménsula"),
    "corinthian": ("korinthisch", "corintio"),
    "couchant": ("liegend", "yacente"),
    "cross": ("Kreuz", "cruz"),
    "cupola": ("Kuppel", "cúpula"),
    "curved": ("gebogen", "curvo"),
    "decor": ("Dekor", "decoración"),
    "decoration": ("Dekoration", "decoración"),
    "decorative": ("dekorativ", "decorativo"),
    "deity": ("Gottheit", "deidad"),
    "dome": ("Kuppel", "cúpula"),
    "entablature": ("Gebälk", "entablamento"),
    "finial": ("Zierelement", "remate"),
    "feature": ("Gestaltung", "elemento"),
    "flower": ("Blume", "flor"),
    "flowerpot": ("Blumentopf", "maceta"),
    "fountain": ("Brunnen", "fuente"),
    "frieze": ("Fries", "friso"),
    "furniture": ("Möbel", "muebles"),
    "garden": ("Garten", "jardín"),
    "gazebo": ("Pavillon", "cenador"),
    "gemstone": ("Edelstein", "gema"),
    "georgian": ("georgisch", "georgiano"),
    "gothic": ("gotisch", "gótico"),
    "god": ("Gott", "dios"),
    "goddess": ("Göttin", "diosa"),
    "gold": ("Gold", "oro"),
    "greek": ("griechisch", "griego"),
    "guilloche": ("Guilloche", "guilloché"),
    "head": ("Kopf", "cabeza"),
    "hero": ("Held", "héroe"),
    "horse": ("Pferd", "caballo"),
    "interlace": ("Flechtband", "entrelazado"),
    "ionic": ("ionisch", "jónico"),
    "labradorite": ("Labradorit", "labradorita"),
    "lancet": ("Spitzbogen", "lanceta"),
    "leaf": ("Blatt", "hoja"),
    "limestone": ("Kalkstein", "caliza"),
    "lion": ("Löwe", "león"),
    "marble": ("Marmor", "mármol"),
    "medallion": ("Medaillon", "medallón"),
    "metal": ("Metall", "metal"),
    "metallic": ("metallisch", "metálico"),
    "monument": ("Denkmal", "monumento"),
    "neoclassical": ("neoklassisch", "neoclásico"),
    "obelisk": ("Obelisk", "obelisco"),
    "olympian": ("olympisch", "olímpico"),
    "onyx": ("Onyx", "ónice"),
    "opal": ("Opal", "ópalo"),
    "ornament": ("Ornament", "ornamento"),
    "ornamental": ("ornamental", "ornamental"),
    "ornate": ("verziert", "ornamentado"),
    "palm": ("Palme", "palmera"),
    "palmette": ("Palmette", "palmeta"),
    "panel": ("Paneel", "panel"),
    "pavilion": ("Pavillon", "pabellón"),
    "pavillion": ("Pavillon", "pabellón"),
    "pedestal": ("Sockel", "pedestal"),
    "pillar": ("Pfeiler", "pilar"),
    "pinnacle": ("Fiale", "pináculo"),
    "plain": ("schlicht", "sencillo"),
    "plant": ("Pflanze", "planta"),
    "planter": ("Pflanzgefäß", "jardinera"),
    "plinth": ("Sockel", "plinto"),
    "portrait": ("Porträt", "retrato"),
    "pot": ("Topf", "maceta"),
    "pottery": ("Keramik", "cerámica"),
    "procession": ("Prozession", "procesión"),
    "quartzite": ("Quarzit", "cuarcita"),
    "quatrefoil": ("Vierpass", "cuatrifolio"),
    "regency": ("Regency", "regencia"),
    "relief": ("Relief", "relieve"),
    "renaissance": ("Renaissance", "renacimiento"),
    "ribbed": ("gerippt", "acanalado"),
    "rock": ("Gestein", "roca"),
    "roman": ("römisch", "romano"),
    "roof": ("Dach", "tejado"),
    "rotunda": ("Rotunde", "rotonda"),
    "sandstone": ("Sandstein", "arenisca"),
    "sculpture": ("Skulptur", "escultura"),
    "scroll": ("Volute", "voluta"),
    "seat": ("Sitz", "asiento"),
    "seating": ("Sitzmöbel", "asientos"),
    "silver": ("Silber", "plata"),
    "sodalite": ("Sodalith", "sodalita"),
    "spartan": ("spartanisch", "espartano"),
    "statue": ("Statue", "estatua"),
    "statant": ("stehend", "de pie"),
    "stone": ("Stein", "piedra"),
    "straight": ("gerade", "recto"),
    "tracery": ("Maßwerk", "tracería"),
    "travertine": ("Travertin", "travertino"),
    "tuscan": ("toskanisch", "toscano"),
    "urn": ("Urne", "urna"),
    "vase": ("Vase", "jarrón"),
    "vault": ("Gewölbe", "bóveda"),
    "vessel": ("Gefäß", "recipiente"),
    "volute": ("Volute", "voluta"),
    "wall": ("Wand", "pared"),
    "white": ("weiß", "blanco"),
    "water": ("Wasser", "agua"),
    "weathered": ("verwittert", "desgastado"),
    "worn": ("abgenutzt", "desgastado"),
}


# Search-only equivalents: canonical registrations and display names stay Greek.
# Each alias uses the same finite subject membership for statues and busts.
# Apollo and Helios remain separate; Phaeton is not an alias of either god.
SUBJECT_SEARCH_ALIASES = {
    "zeus": {
        "jupiter": ("Jupiter", "Jupiter", "Júpiter"),
        "jove": ("Jove", "Jove", "Jove"),
        "iuppiter": ("Iuppiter", "Iuppiter", "Iuppiter"),
    },
    "hera": {"juno": ("Juno", "Juno", "Juno")},
    "poseidon": {
        "neptune": ("Neptune", "Neptun", "Neptuno"),
        "neptunus": ("Neptunus", "Neptunus", "Neptunus"),
    },
    "demeter": {"ceres": ("Ceres", "Ceres", "Ceres")},
    "athena": {
        "minerva": ("Minerva", "Minerva", "Minerva"),
        "athene": ("Athene", "Athene", "Atenea"),
    },
    "apollo": {"apollon": ("Apollon", "Apollon", "Apolo")},
    "artemis": {"diana": ("Diana", "Diana", "Diana")},
    "ares": {"mars": ("Mars", "Mars", "Marte")},
    "aphrodite": {"venus": ("Venus", "Venus", "Venus")},
    "hephaestus": {
        "vulcan": ("Vulcan", "Vulkan", "Vulcano"),
        "vulcanus": ("Vulcanus", "Vulcanus", "Vulcanus"),
        "hephaistos": ("Hephaistos", "Hephaistos", "Hefesto"),
    },
    "hermes": {
        "mercury": ("Mercury", "Merkur", "Mercurio"),
        "mercurius": ("Mercurius", "Mercurius", "Mercurius"),
    },
    "dionysus": {
        "bacchus": ("Bacchus", "Bacchus", "Baco"),
        "liber": ("Liber", "Liber", "Liber"),
        "liber_pater": ("Liber Pater", "Liber Pater", "Liber Pater"),
        "dionysos": ("Dionysos", "Dionysos", "Dioniso"),
    },
    "hestia": {"vesta": ("Vesta", "Vesta", "Vesta")},
    "helios": {"sol": ("Sol", "Sol", "Sol")},
    "heracles": {
        "hercules": ("Hercules", "Herkules", "Hércules"),
        "herakles": ("Herakles", "Herakles", "Heracles"),
    },
    # The name is shared by Greek and Roman tellings, not a different deity.
    "phaeton": {"phaethon": ("Phaethon", "Phaethon", "Faetón")},
}
SEARCH_ALIAS_TRANSLATIONS = {
    alias: names
    for aliases in SUBJECT_SEARCH_ALIASES.values()
    for alias, names in aliases.items()
}
GODDESS_SEARCH_SUBJECTS = frozenset({"aphrodite", "artemis", "athena", "demeter", "hera", "hestia"})


def translated_search_tag(tag: str, language: str) -> str:
    if tag in SEARCH_ALIAS_TRANSLATIONS:
        return SEARCH_ALIAS_TRANSLATIONS[tag][("en_us", "de_de", "es_es").index(language)]
    if language == "en_us":
        return tag.replace("_", " ")
    index = {"de_de": 0, "es_es": 1}[language]
    names = dict(CANONICAL_STATUE_FAMILIES)
    names.update({subject: (en, de, es) for subject, en, de, es in BUST_FAMILIES.values()})
    return " ".join(
        SEARCH_WORD_TRANSLATIONS[word][index] if word in SEARCH_WORD_TRANSLATIONS
        else names[word][index + 1] if word in names
        else word
        for word in tag.split("_")
    )


def search_vocabulary(
    families: tuple[str, ...], ids_by_family: dict[str, list[str]],
    direct_tags: dict[str, list[str]], relation_tags: dict[str, list[str]],
) -> dict[str, dict[str, list[str]]]:
    """Share tag vocabulary with clients before any server/data-pack tag bind.

    Factor it into family and material groups instead of repeating every alias
    for thousands of items. Family terms must apply to *all* its finishes; the
    client adds the fountain-capable stone-plinth exception separately.
    """
    tags = direct_tags | relation_tags
    resolved: dict[str, set[str]] = {}

    def members(tag: str, visiting: frozenset[str] = frozenset()) -> set[str]:
        if tag in visiting:
            raise ValueError(f"Cyclic Daedalon tag: {tag}")
        if tag not in resolved:
            refs: set[str] = set()
            for value in tags[tag]:
                if value.startswith(f"#{NAMESPACE}:"):
                    refs.update(members(value.split(":", 1)[1], visiting | {tag}))
                else:
                    refs.add(value)
            resolved[tag] = refs
        return resolved[tag]

    for tag in tags:
        members(tag)

    def common_terms(ids: list[str]) -> list[str]:
        refs = {f"{NAMESPACE}:{block_id}" for block_id in ids}
        return sorted(tag for tag, values in resolved.items()
                      if tag not in ("daedalon_blocks", "daedalon_items") and refs and refs <= values)

    material_ids = {
        material: [block_id for ids in ids_by_family.values()
                   for block_id in ids[index * 2:index * 2 + 2]]
        for index, material in enumerate(MATERIALS)
    }
    material_ids.update({
        metal: [item_id for ids in ids_by_family.values() for item_id in ids
                if item_id.startswith(metal + "_")]
        for metal in METAL_FAMILIES
    })
    material_ids[WHITE_FINISH] = [
        architectural_finish_block_id(family, WHITE_FINISH)
        for family in families if family in ARCHITECTURAL_FINISH_FAMILIES
    ]
    return {
        "families": {
            ("spartan_promachos_statue" if family == "spartan" else
             ids_by_family[family][0].removeprefix(f"{MATERIALS[0]}_")):
                common_terms(ids_by_family[family])
            for family in families
        },
        "materials": {
            material: common_terms(ids) for material, ids in material_ids.items() if ids
        },
    }


CATALOGUE_FORM_NAMES = {
    "statues": ("Statues", "Statuen", "Estatuas"),
    "busts": ("Busts", "Büsten", "Bustos"),
    "urns": ("Urns", "Urnen", "Urnas"),
    "capitals": ("Capitals", "Kapitelle", "Capiteles"),
    "corbels": ("Corbels", "Konsolen", "Ménsulas"),
    "plinths": ("Plinths", "Sockel", "Pedestales"),
    "finials": ("Finials", "Zierelemente", "Remates"),
    "friezes": ("Friezes", "Friese", "Frisos"),
    "panels": ("Panels", "Paneele", "Paneles"),
    "benches": ("Benches", "Bänke", "Bancos"),
    "planters": ("Planters", "Pflanzgefäße", "Jardineras"),
    "domes": ("Domes", "Kuppeln", "Cúpulas"),
    "basins": ("Basins", "Becken", "Cuencas"),
    "fountains": ("Fountains", "Brunnen", "Fuentes"),
    "monuments": ("Monuments", "Denkmäler", "Monumentos"),
}


def generate_languages(
    families: tuple[str, ...], check: bool, changed: list[Path],
    search_vocabulary: dict[str, dict[str, list[str]]],
) -> None:
    for language in ("en_us", "de_de", "es_es"):
        entries = dict(STATIC_LANGUAGE_ENTRIES[language])
        search_tags = {"aged", "weathered", "worn", "daedalon", "deadalon", "honed", "polished", "mirror", "ganosis"}
        for group in search_vocabulary.values():
            search_tags.update(tag for tags in group.values() for tag in tags)
        for tag in sorted(search_tags):
            entries[f"search.daedalon.tag.{tag}"] = translated_search_tag(tag, language)
        language_index = ("en_us", "de_de", "es_es").index(language)
        for finish, labels in {
            "honed": ("Honed", "Geschliffen", "Apomazado"),
            "polished": ("Polished", "Poliert", "Pulido"),
            "mirror": ("Ganosis", "Ganosis", "Ganosis"),
        }.items():
            entries[f"finish.daedalon.{finish}"] = labels[language_index]
        for key, labels in {
            "designs": ("Designs", "Modelle", "Diseños"),
            "forms": ("Forms", "Formen", "Formas"),
            "materials": ("Materials and finishes", "Materialien und Oberflächen", "Materiales y acabados"),
            "design_info": (
                "%s: compare all available materials and finishes for this design.",
                "%s: Alle verfügbaren Materialien und Oberflächen dieses Modells vergleichen.",
                "%s: compara todos los materiales y acabados disponibles para este diseño.",
            ),
        }.items():
            entries[f"catalogue.daedalon.{key}"] = labels[language_index]
        for form, names in CATALOGUE_FORM_NAMES.items():
            entries[f"catalogue.daedalon.form.{form}"] = names[language_index]
        for material in search_vocabulary["materials"]:
            label = translated_search_tag(material, language)
            entries[f"catalogue.daedalon.material.{material}"] = label[0].upper() + label[1:]
        for family in families:
            family_key = ("spartan_promachos_statue" if family == "spartan" else
                          family_block_ids(family)[0].removeprefix(f"{MATERIALS[0]}_"))
            entries[f"catalogue.daedalon.design.{family_key}"] = translated_name(
                language, family, MATERIALS[0], False
            ).removeprefix(MATERIALS[0].capitalize() + " ")
        particle_messages = {
            "en_us": ("Fountain droplets: Normal (1x). This computer only.", "Fountain droplets: High (4x). This computer only.", "Could not save the fountain particle setting; the previous setting is unchanged."),
            "de_de": ("Brunnentropfen: Normal (1x). Nur auf diesem Computer.", "Brunnentropfen: Hoch (4x). Nur auf diesem Computer.", "Die Brunnenpartikel-Einstellung konnte nicht gespeichert werden; die bisherige Einstellung bleibt erhalten."),
            "es_es": ("Gotas de fuente: Normal (1x). Solo en este equipo.", "Gotas de fuente: Alto (4x). Solo en este equipo.", "No se pudo guardar la configuración de partículas de fuente; se mantiene la configuración anterior."),
        }[language]
        for key, message in zip(("normal", "high", "save_failed"), particle_messages):
            entries[f"message.daedalon.fountain_particles.{key}"] = message
        sound_messages = {
            "en_us": ("Fountain sound: On. This computer only.", "Fountain sound: Off. This computer only."),
            "de_de": ("Brunnengeräusch: Ein. Nur auf diesem Computer.", "Brunnengeräusch: Aus. Nur auf diesem Computer."),
            "es_es": ("Sonido de fuente: Activado. Solo en este equipo.", "Sonido de fuente: Desactivado. Solo en este equipo."),
        }[language]
        for key, message in zip(("on", "off"), sound_messages):
            entries[f"message.daedalon.fountain_sound.{key}"] = message
        orientation_labels = {
            "en_us": ("Orientation", "Straight", "Diagonal (45°)", "Straight (90°)", "Diagonal (135°)"),
            "de_de": ("Ausrichtung", "Gerade", "Diagonal (45°)", "Gerade (90°)", "Diagonal (135°)"),
            "es_es": ("Orientación", "Recta", "Diagonal (45°)", "Recta (90°)", "Diagonal (135°)"),
        }[language]
        entries["property.daedalon.orientation"] = orientation_labels[0]
        compass_labels = {
            "en_us": ("North", "North-east", "East", "South-east", "South", "South-west", "West", "North-west"),
            "de_de": ("Nord", "Nordost", "Ost", "Südost", "Süd", "Südwest", "West", "Nordwest"),
            "es_es": ("Norte", "Noreste", "Este", "Sureste", "Sur", "Suroeste", "Oeste", "Noroeste"),
        }[language]
        for direction, label in zip(("north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"), compass_labels):
            entries[f"option.daedalon.orientation.{direction}"] = label
        entries["property.daedalon.capital_orientation"] = orientation_labels[0]
        for orientation, label in zip(("straight", "diagonal", "straight_90", "diagonal_135"), orientation_labels[1:]):
            entries[f"option.daedalon.capital.orientation.{orientation}"] = label
        size_labels = {
            "en_us": ("Size", "Small", "Large"),
            "de_de": ("Größe", "Klein", "Groß"),
            "es_es": ("Tamaño", "Pequeño", "Grande"),
        }[language]
        entries["property.daedalon.capital_size"] = size_labels[0]
        for size, label in zip(("standard", "double"), size_labels[1:]):
            entries[f"option.daedalon.capital.size.{size}"] = label
        entries["message.daedalon.capital_size_blocked"] = {
            "en_us": "Not enough clear space for a Large capital (2×2×2 blocks).",
            "de_de": "Nicht genug freier Platz für ein großes Kapitell (2×2×2 Blöcke).",
            "es_es": "No hay espacio libre suficiente para un capitel grande (2×2×2 bloques).",
        }[language]
        entries["message.daedalon.monopteros_size_blocked"] = {
            "en_us": "Not enough clear space for this dome diameter.",
            "de_de": "Nicht genug freier Platz für diesen Kuppeldurchmesser.",
            "es_es": "No hay suficiente espacio libre para este diámetro de cúpula.",
        }[language]
        entries["message.daedalon.obelisk_size_blocked"] = {
            "en_us": "Not enough clear space for that obelisk size",
            "de_de": "Nicht genug freier Platz für diese Obeliskengröße",
            "es_es": "No hay suficiente espacio libre para ese tamaño de obelisco",
        }[language]
        entries["message.daedalon.finial_size_blocked"] = {
            "en_us": "Not enough clear space for that finial size.",
            "de_de": "Nicht genug freier Platz für diese Größe des Zierelements.",
            "es_es": "No hay espacio libre suficiente para ese tamaño de remate.",
        }[language]
        for family in families:
            for material in MATERIALS:
                ids = family_block_ids(family)
                normal = ids[MATERIALS.index(material) * 2]
                aged = ids[MATERIALS.index(material) * 2 + 1]
                entries[f"block.{NAMESPACE}.{normal}"] = translated_name(
                    language, family, material, False
                )
                entries[f"block.{NAMESPACE}.{aged}"] = translated_name(
                    language, family, material, True
                )
            for metal, metal_families in METAL_FAMILIES.items():
                if family in metal_families:
                    block_id = metal_block_id(family, metal)
                    entries[f"block.{NAMESPACE}.{block_id}"] = translated_name(
                        language, family, metal, False
                    )
            if family in ARCHITECTURAL_FINISH_FAMILIES:
                block_id = architectural_finish_block_id(family, WHITE_FINISH)
                entries[f"block.{NAMESPACE}.{block_id}"] = translated_name(
                    language, family, WHITE_FINISH, False
                )
        entries[f"catalogue.daedalon.design.{OLYMPIAN_GOD_ITEM_FAMILY}"] = translated_name(
            language, OLYMPIAN_GOD_ITEM_FAMILY, MATERIALS[0], False
        ).removeprefix(MATERIALS[0].capitalize() + " ")
        for material in (*MATERIALS, BRONZE_MATERIAL, SILVER_MATERIAL):
            for aged in ((False, True) if material in MATERIALS else (False,)):
                item_id = f"{material}{'_aged' if aged else ''}_{OLYMPIAN_GOD_ITEM_FAMILY}"
                entries[f"item.{NAMESPACE}.{item_id}"] = translated_name(
                    language, OLYMPIAN_GOD_ITEM_FAMILY, material, aged
                )
        entries["tooltip.daedalon.olympian_god.cycle"] = (
            "Places the twelve Olympian statues in order, then repeats.",
            "Platziert die zwölf olympischen Götterstatuen der Reihe nach und beginnt danach erneut.",
            "Coloca las doce estatuas olímpicas en orden y luego vuelve a empezar.",
        )[language_index]
        entries["tooltip.daedalon.olympian_god.next"] = (
            "Next: %s", "Als Nächstes: %s", "Siguiente: %s",
        )[language_index]
        entries["tooltip.daedalon.olympian_god.swap"] = (
            "Use on a statue's side to change its god; sneak to go backwards.",
            "Seitlich auf eine Statue anwenden, um den Gott zu wechseln; schleichen für die umgekehrte Reihenfolge.",
            "Úsalo en el lateral de una estatua para cambiar de dios; agáchate para retroceder.",
        )[language_index]
        write(
            RESOURCES / f"assets/{NAMESPACE}/lang/{language}.json",
            json_bytes(entries),
            check,
            changed,
        )


def tag_payload(values: list[str]) -> dict[str, object]:
    return {"replace": False, "values": list(dict.fromkeys(values))}


def write_tag(
    namespace: str,
    kind: str,
    name: str,
    values: list[str],
    check: bool,
    changed: list[Path],
) -> None:
    write(
        RESOURCES / f"data/{namespace}/tags/{kind}/{name}.json",
        json_bytes(tag_payload(values)),
        check,
        changed,
    )


def generate_tags(
    families: tuple[str, ...], check: bool, changed: list[Path]
) -> dict[str, dict[str, list[str]]]:
    ids_by_family = {family: family_block_ids(family) for family in families}
    all_refs = [
        f"{NAMESPACE}:{block_id}"
        for family in families
        for block_id in ids_by_family[family]
    ]
    aged_refs = [
        f"{NAMESPACE}:{block_id}"
        for family in families
        for block_id in ids_by_family[family]
        if "_aged_" in block_id or block_id.endswith("_aged")
    ]
    refs_by_metal = {
        metal: [
            f"{NAMESPACE}:{metal_block_id(family, metal)}"
            for family in families if family in metal_families
        ]
        for metal, metal_families in METAL_FAMILIES.items()
    }
    metal_refs = [ref for refs in refs_by_metal.values() for ref in refs]
    metal_ref_set = set(metal_refs)
    stone_refs = [ref for ref in all_refs if ref not in metal_ref_set]

    direct_tags: dict[str, list[str]] = {
        "daedalon_blocks": all_refs,
        "aged": aged_refs,
        "stone": stone_refs,
    }
    white_refs = [
        f"{NAMESPACE}:{architectural_finish_block_id(family, WHITE_FINISH)}"
        for family in families if family in ARCHITECTURAL_FINISH_FAMILIES
    ]
    if white_refs:
        direct_tags[WHITE_FINISH] = white_refs
    relation_tags: dict[str, list[str]] = {
        "rock": [f"#{NAMESPACE}:stone"],
        "weathered": [f"#{NAMESPACE}:aged"],
        "worn": [f"#{NAMESPACE}:aged"],
        **{tag: [f"#{NAMESPACE}:daedalon_blocks"]
           for tag in ("decor", "decoration", "decorative", "architecture")},
    }
    for metal, refs in refs_by_metal.items():
        if refs:
            direct_tags[metal] = refs
    if metal_refs:
        relation_tags["metal"] = [f"#{NAMESPACE}:{metal}" for metal, refs in refs_by_metal.items() if refs]
        relation_tags["metallic"] = list(relation_tags["metal"])

    urn_families = [family for family in families if family in URN_FAMILIES]
    if urn_families:
        urn_refs = [
            f"{NAMESPACE}:{block_id}"
            for family in urn_families
            for block_id in ids_by_family[family]
        ]
        direct_tags["urn"] = urn_refs
        relation_tags["vase"] = [f"#{NAMESPACE}:urn"]
        relation_tags["pottery"] = [f"#{NAMESPACE}:urn"]
        relation_tags["vessel"] = [f"#{NAMESPACE}:urn"]
        for family in urn_families:
            shape = f"{family}_urn"
            direct_tags[shape] = [
                f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]
            ]

    ornament_values: list[str] = []
    if urn_families:
        ornament_values.append(f"#{NAMESPACE}:urn")

    corbel_families = [family for family in families if family in CORBEL_FAMILIES]
    if corbel_families:
        corbel_refs = [
            f"{NAMESPACE}:{block_id}"
            for family in corbel_families
            for block_id in ids_by_family[family]
        ]
        direct_tags["corbel"] = corbel_refs
        relation_tags["bracket"] = [f"#{NAMESPACE}:corbel"]
        ornament_values.append(f"#{NAMESPACE}:corbel")
        for family in corbel_families:
            tag = f"{family}_corbel"
            direct_tags[tag] = [
                f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]
            ]
            relation_tags.setdefault(family, []).append(f"#{NAMESPACE}:{tag}")
        for synonym, members in {
            "ornate_bracket": ("baroque", "corinthian"),
            "scroll_bracket": ("ionic", "renaissance"),
            "acanthus_corbel": ("baroque", "corinthian", "renaissance"),
            "classical_corbel": ("ionic", "georgian", "corinthian", "renaissance"),
            "volute_bracket": ("ionic",),
            "neoclassical_bracket": ("georgian",),
            "plain_corbel": ("georgian",),
            "regency_corbel": ("georgian",),
            "leaf_corbel": ("corinthian",),
            "palmette_corbel": ("renaissance",),
        }.items():
            relation_tags[synonym] = [
                f"#{NAMESPACE}:{family}_corbel"
                for family in members
                if family in corbel_families
            ]

    capital_families = [family for family in families if family in CAPITAL_FAMILIES]
    if capital_families:
        capital_refs = [
            f"{NAMESPACE}:{block_id}"
            for family in capital_families
            for block_id in ids_by_family[family]
        ]
        direct_tags["capital"] = capital_refs
        relation_tags["column_capital"] = [f"#{NAMESPACE}:capital"]
        relation_tags["pillar_capital"] = [f"#{NAMESPACE}:capital"]
        relation_tags["column_head"] = [f"#{NAMESPACE}:capital"]
        ornament_values.append(f"#{NAMESPACE}:capital")
        for family in capital_families:
            style = "corinthian" if family == "corinthian_capital" else family
            tag = f"{style}_capital"
            direct_tags[tag] = [
                f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]
            ]
            relation_tags.setdefault(style, []).append(f"#{NAMESPACE}:{tag}")
        if "greek_ionic" in capital_families:
            relation_tags.setdefault("greek", []).append(f"#{NAMESPACE}:greek_ionic_capital")
            relation_tags.setdefault("ionic", []).append(f"#{NAMESPACE}:greek_ionic_capital")
        if "roman_composite" in capital_families:
            relation_tags["roman"] = [f"#{NAMESPACE}:roman_composite_capital"]
            relation_tags["composite"] = [f"#{NAMESPACE}:roman_composite_capital"]
    statue_families = [
        family for family in families if family in STATUE_DISPLAY_MODELS
    ]
    if statue_families:
        statue_refs = [
            f"{NAMESPACE}:{block_id}"
            for family in statue_families
            for block_id in ids_by_family[family]
        ]
        direct_tags["statue"] = statue_refs
        relation_tags["sculpture"] = [f"#{NAMESPACE}:statue"]
        ornament_values.append(f"#{NAMESPACE}:statue")
    bust_families = [family for family in families if family in BUST_FAMILIES]
    if bust_families:
        bust_refs = [
            f"{NAMESPACE}:{block_id}"
            for family in bust_families
            for block_id in ids_by_family[family]
        ]
        direct_tags["bust"] = bust_refs
        relation_tags.setdefault("sculpture", []).append(f"#{NAMESPACE}:bust")
        relation_tags["portrait"] = [f"#{NAMESPACE}:bust"]
        relation_tags["head"] = [f"#{NAMESPACE}:bust"]
        relation_tags["classical_bust"] = [f"#{NAMESPACE}:bust"]
        ornament_values.append(f"#{NAMESPACE}:bust")
        for family in bust_families:
            subject = BUST_FAMILIES[family][0]
            tag = f"{subject}_bust"
            direct_tags[tag] = [
                f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]
            ]
            relation_tags.setdefault(subject, []).append(f"#{NAMESPACE}:{tag}")
    greek_values: list[str] = []
    if "spartan" in families:
        spartan_refs = [
            f"{NAMESPACE}:{block_id}" for block_id in ids_by_family["spartan"]
        ]
        direct_tags["statue_spartan_promachos"] = spartan_refs
        greek_values.append(f"#{NAMESPACE}:statue_spartan_promachos")
        relation_tags["spartan"] = [f"#{NAMESPACE}:statue_spartan_promachos"]
        relation_tags["promachos"] = [f"#{NAMESPACE}:statue_spartan_promachos"]
    for family in CANONICAL_STATUE_FAMILIES:
        if family not in families:
            continue
        family_refs = [
            f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]
        ]
        direct_tags[f"{family}_statue"] = family_refs
        relation_tags.setdefault(family, []).append(f"#{NAMESPACE}:{family}_statue")
        if family in OLYMPIAN_STATUE_FAMILIES + HERO_STATUE_FAMILIES + ("helios", "phaeton"):
            greek_values.append(f"#{NAMESPACE}:{family}_statue")
    for group, members in {
        "olympian": OLYMPIAN_STATUE_FAMILIES,
        "hero": HERO_STATUE_FAMILIES,
        "animal": LION_STATUE_FAMILIES,
        "lion": LION_STATUE_FAMILIES,
    }.items():
        values = [
            f"#{NAMESPACE}:{family}_statue"
            for family in members
            if family in families
        ]
        if values:
            relation_tags[group] = values
    if greek_values:
        relation_tags.setdefault("greek", []).extend(greek_values)
    if bust_families:
        bust_tags = [
            f"#{NAMESPACE}:{BUST_FAMILIES[family][0]}_bust"
            for family in bust_families
        ]
        relation_tags.setdefault("greek", []).extend(bust_tags)
        relation_tags.setdefault("olympian", []).extend(bust_tags)
    for subject, aliases in SUBJECT_SEARCH_ALIASES.items():
        if subject in relation_tags:
            for alias in aliases:
                relation_tags[alias] = [f"#{NAMESPACE}:{subject}"]
    divine_subjects = [
        subject for subject in SUBJECT_SEARCH_ALIASES
        if subject != "phaeton" and subject in relation_tags
    ]
    if divine_subjects:
        relation_tags["deity"] = [f"#{NAMESPACE}:{subject}" for subject in divine_subjects]
        relation_tags["god"] = [f"#{NAMESPACE}:deity"]
    goddesses = [subject for subject in divine_subjects if subject in GODDESS_SEARCH_SUBJECTS]
    if goddesses:
        relation_tags["goddess"] = [f"#{NAMESPACE}:{subject}" for subject in goddesses]
    plinth_families = [family for family in families if family in PLINTH_FAMILIES]
    if plinth_families:
        plinth_refs = [
            f"{NAMESPACE}:{block_id}"
            for family in plinth_families
            for block_id in ids_by_family[family]
        ]
        direct_tags["plinth"] = plinth_refs
        for family in plinth_families:
            direct_tags[family] = [
                f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]
            ]
            style = family.removesuffix("_plinth")
            relation_tags.setdefault(style, []).append(f"#{NAMESPACE}:{family}")
        relation_tags["pedestal"] = [f"#{NAMESPACE}:plinth"]
        relation_tags["statue_base"] = [f"#{NAMESPACE}:plinth"]
        relation_tags["monument_base"] = [f"#{NAMESPACE}:plinth"]
        if "triphyllon_plinth" in plinth_families:
            relation_tags.setdefault("gothic", []).append(f"#{NAMESPACE}:triphyllon_plinth")
        greek_plinths = [
            f"#{NAMESPACE}:{family}"
            for family in plinth_families
            if family != "triphyllon_plinth"
        ]
        relation_tags.setdefault("greek", []).extend(greek_plinths)
        ornament_values.append(f"#{NAMESPACE}:plinth")
    new_decor_families = [family for family in families if family in NEW_DECOR_FAMILIES]
    if new_decor_families:
        for family in ("corinthian_frieze", "ionic_frieze", "gothic_frieze", "byzantine_frieze"):
            if family not in families:
                continue
            frieze = f"#{NAMESPACE}:{family}"
            terms={"corinthian_frieze":("acanthus", "corinthian"),
                   "ionic_frieze":("ionic", "horse", "chariot", "palm", "palmette", "procession"),
                   "gothic_frieze":("gothic", "quatrefoil", "tracery", "lancet"),
                   "byzantine_frieze":("byzantine", "guilloche", "interlace", "cross", "medallion", "palm", "palmette")}[family]
            for synonym in ("frieze", "entablature", "wall_relief", *terms):
                relation_tags.setdefault(synonym, []).append(frieze)
            ornament_values.append(frieze)
        for family in PANEL_FAMILIES:
            if family not in families:
                continue
            panel = f"#{NAMESPACE}:{family}"
            terms = ("panel", "gothic", "tracery", "ribbed", "vault", "ornamental_panel")
            terms += ("wall_panel", "wall_relief", "ceiling", "coffer", "coffered_ceiling", "ceiling_panel")
            for synonym in terms:
                relation_tags.setdefault(synonym, []).append(panel)
            ornament_values.append(panel)
        benches = [f"#{NAMESPACE}:{bench}" for bench in BENCH_FAMILIES if bench in families]
        if benches:
            for synonym in ("bench", "seat", "seating", "furniture"):
                relation_tags[synonym] = list(benches)
        if "anthophoros_planter" in families:
            planter = f"#{NAMESPACE}:anthophoros_planter"
            for synonym in ("anthophoros", "planter", "flower_box", "garden_planter"):
                relation_tags[synonym] = [planter]
            relation_tags.setdefault("furniture", []).append(planter)
            ornament_values.append(planter)
        if "exedra" in families:
            relation_tags["curved_bench"] = [f"#{NAMESPACE}:exedra"]
        if "monopteros_dome" in families:
            for synonym in ("monopteros", "dome", "cupola", "pavilion", "pavillion", "gazebo", "rotunda", "roof"):
                relation_tags[synonym] = [f"#{NAMESPACE}:monopteros_dome"]
        if "hedra" in families:
            relation_tags["straight_bench"] = [f"#{NAMESPACE}:hedra"]
        for family in new_decor_families:
            direct_tags[family] = [f"{NAMESPACE}:{block_id}" for block_id in ids_by_family[family]]
        finial_tags = [f"#{NAMESPACE}:{family}" for family in FIXED_DECOR_FAMILIES if family.endswith("_finial") and family in families]
        if finial_tags:
            relation_tags["finial"] = finial_tags
            relation_tags["pinnacle"] = finial_tags
            ornament_values.extend(finial_tags)
        basin_tags = [
            f"#{NAMESPACE}:{family}"
            for family in ("louterion_basin", *FOUNTAIN_BASIN_FAMILIES)
            if family in families
        ]
        if "louterion_basin" in families:
            relation_tags["louterion"] = [f"#{NAMESPACE}:louterion_basin"]
        if basin_tags:
            relation_tags["basin"] = basin_tags
        for fountain_style in ("gothic", "georgian", "greek"):
            basin_family = f"{fountain_style}_fountain_basin"
            if basin_family in families:
                relation_tags.setdefault(fountain_style, []).append(
                    f"#{NAMESPACE}:{basin_family}"
                )
        fountain_tags = [
            f"#{NAMESPACE}:{family}"
            for family in (
                "krene_fountain",
                "pege_fountain",
                *FOUNTAIN_BASIN_FAMILIES,
            )
            if family in families
        ]
        if fountain_tags:
            relation_tags["fountain"] = fountain_tags
            relation_tags["water_feature"] = fountain_tags
        if "obeliskos_monument" in families:
            relation_tags["obeliskos"] = [f"#{NAMESPACE}:obeliskos_monument"]
            relation_tags["obelisk"] = [f"#{NAMESPACE}:obeliskos_monument"]
            relation_tags["monument"] = [f"#{NAMESPACE}:obeliskos_monument"]
    if ornament_values:
        relation_tags["ornament"] = ornament_values

    if plinth_families:
        direct_tags["fountain_plinth"] = [
            f"{NAMESPACE}:{block_id}"
            for family in plinth_families
            for block_id in ids_by_family[family]
            if f"{NAMESPACE}:{block_id}" not in metal_ref_set
        ]
        for synonym in ("fountain", "water_feature"):
            values = relation_tags.setdefault(synonym, [])
            plinth = f"#{NAMESPACE}:fountain_plinth"
            if plinth not in values:
                values.append(plinth)

    # Discoverability tags stay in our namespace: these decorations must not
    # become vanilla flower pots, stone ingredients, or metal storage blocks.
    pot_tags = [f"#{NAMESPACE}:urn"] if urn_families else []
    if "anthophoros_planter" in families:
        planter = f"#{NAMESPACE}:anthophoros_planter"
        pot_tags.append(planter)
        for tag in ("plant_pot", "flower_pot", "flowerpot"):
            relation_tags[tag] = [planter]
    if pot_tags:
        relation_tags["pot"] = pot_tags
    if "byzantine" in relation_tags:
        relation_tags["guilloche"] = list(relation_tags["byzantine"])

    for material in MATERIALS:
        material_refs: list[str] = []
        for family in families:
            family_ids = ids_by_family[family]
            material_index = MATERIALS.index(material) * 2
            material_refs.extend(
                (
                    f"{NAMESPACE}:{family_ids[material_index]}",
                    f"{NAMESPACE}:{family_ids[material_index + 1]}",
                )
            )
        direct_tags[material] = material_refs
        for material_tag in MATERIAL_TAGS[material]:
            direct_tags.setdefault(material_tag, []).extend(material_refs)

    # Item-only selectors participate in the same vocabulary and material tags,
    # without creating invalid block tags, mining rules or loot tables.
    selector_ids = olympian_god_item_ids()
    selector_refs = [f"{NAMESPACE}:{item_id}" for item_id in selector_ids]
    item_direct = {name: list(values) for name, values in direct_tags.items()}
    item_relations = {name: list(values) for name, values in relation_tags.items()}
    item_direct[OLYMPIAN_GOD_ITEM_FAMILY] = selector_refs
    item_direct["daedalon_items"] = all_refs + selector_refs
    selector_stone = [ref for ref in selector_refs if not ref.split(":", 1)[1].startswith(("bronze_", "silver_"))]
    selector_metal = [ref for ref in selector_refs if ref not in selector_stone]
    item_direct.setdefault("statue", []).extend(selector_refs)
    item_direct["stone"].extend(selector_stone)
    item_direct["aged"].extend(ref for ref in selector_stone if "_aged_" in ref)
    for material in (*MATERIALS, BRONZE_MATERIAL, SILVER_MATERIAL):
        refs = [ref for ref in selector_refs if ref.startswith(f"{NAMESPACE}:{material}_")]
        item_direct.setdefault(material, []).extend(refs)
        if material in MATERIALS:
            for material_tag in MATERIAL_TAGS[material]:
                item_direct.setdefault(material_tag, []).extend(refs)
    for tag in ("decor", "decoration", "decorative", "architecture"):
        item_relations[tag] = [f"#{NAMESPACE}:daedalon_items"]
    for tag in ("olympian", "greek", "deity"):
        item_relations.setdefault(tag, []).append(f"#{NAMESPACE}:{OLYMPIAN_GOD_ITEM_FAMILY}")
    for tag in ("olympian_god", "statue_cycle", "cycle", "cycling", "sequence"):
        item_relations[tag] = [f"#{NAMESPACE}:{OLYMPIAN_GOD_ITEM_FAMILY}"]
    for kind, direct, relations in (("blocks", direct_tags, relation_tags), ("items", item_direct, item_relations)):
        for name, values in sorted(direct.items()):
            write_tag(NAMESPACE, kind, name, values, check, changed)
        for name, values in sorted(relations.items()):
            write_tag(NAMESPACE, kind, name, values, check, changed)
    write_tag("material", "blocks", "stone", stone_refs, check, changed)
    write_tag("material", "blocks", "metal", metal_refs, check, changed)
    write_tag("material", "items", "stone", stone_refs + selector_stone, check, changed)
    write_tag("material", "items", "metal", metal_refs + selector_metal, check, changed)
    write_tag("minecraft", "blocks", "mineable/pickaxe", all_refs, check, changed)
    vocabulary = search_vocabulary((*families, OLYMPIAN_GOD_ITEM_FAMILY),
                                  ids_by_family | {OLYMPIAN_GOD_ITEM_FAMILY: selector_ids},
                                  item_direct, item_relations)
    write(RESOURCES / f"assets/{NAMESPACE}/search_vocabulary.json",
          json_bytes(vocabulary), check, changed)
    return vocabulary


def generate(families: tuple[str, ...], check: bool = False) -> list[Path]:
    changed: list[Path] = []
    remove_obsolete_unpublished_urn_assets(check, changed)
    remove_obsolete_unpublished_architectural_gold_assets(check, changed)
    remove_obsolete_unpublished_georgian_plinth_assets(check, changed)
    remove_obsolete_unpublished_fountain_bowl_ids(check, changed)
    remove_obsolete_unpublished_ceiling_panels(check, changed)
    remove_creative_only_loot(families, check, changed)
    generate_models_and_loot(families, check, changed)
    generate_olympian_god_items(check, changed)
    write(RESOURCES / f"assets/{NAMESPACE}/blockstates/obeliskos_part.json",
          json_bytes({"variants": {"": {"model": f"{NAMESPACE}:block/internal/obeliskos_part"}}}), check, changed)
    write(RESOURCES / f"assets/{NAMESPACE}/models/block/internal/obeliskos_part.json",
          json_bytes({"elements": []}), check, changed)
    vocabulary = generate_tags(families, check, changed)
    generate_languages(families, check, changed, vocabulary)
    return changed


def parse_families(values: list[str]) -> tuple[str, ...]:
    expanded: list[str] = []
    for value in values:
        for family in value.split(","):
            family = family.strip().lower()
            if family == "all":
                expanded.extend(ALL_FAMILIES)
            elif family in ALL_FAMILIES:
                expanded.append(family)
            else:
                raise ValueError(f"Unknown family: {family}")
    return tuple(dict.fromkeys(expanded))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate Daedalon blockstates, item models, loot, tags and languages."
    )
    parser.add_argument(
        "--families",
        nargs="+",
        default=["all"],
        help="Family keys to generate, or all.",
    )
    parser.add_argument(
        "--check", action="store_true", help="Fail if generated resources are stale."
    )
    args = parser.parse_args()

    families = parse_families(args.families)
    changed = generate(families, args.check)
    print(
        "Daedalon generated assets current; "
        f"families={','.join(families)} changed={len(changed)}"
    )


if __name__ == "__main__":
    main()
