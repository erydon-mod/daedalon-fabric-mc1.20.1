#!/usr/bin/env python3
"""Generate the shared Daedalon OBJ item-display models.

Keeping the display transforms in one manifest makes the GUI, held, dropped,
and fixed sizes reproducible across the large material catalogue. Corbels are
the sole facing exception because their supplied front was already approved.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DISPLAY_ROOT = ROOT / "src/main/resources/assets/daedalon/models/block/mesh"


def transform(
    rotation: list[float] | None = None,
    translation: list[float] | None = None,
    scale: float | None = None,
) -> dict[str, object]:
    result: dict[str, object] = {}
    if rotation is not None:
        result["rotation"] = rotation
    if translation is not None:
        result["translation"] = translation
    if scale is not None:
        result["scale"] = [scale, scale, scale]
    return result


ONE_BLOCK_DISPLAY = {
    "thirdperson_righthand": transform([75, 225, 0], [0, 2.5, 0], 0.375),
    "thirdperson_lefthand": transform([75, 45, 0], [0, 2.5, 0], 0.375),
    "firstperson_righthand": transform([0, 225, 0], [0, 0, 0], 0.4),
    "firstperson_lefthand": transform([0, 45, 0], [0, 0, 0], 0.4),
    "ground": transform(translation=[0, 3, 0], scale=0.25),
    "gui": transform([20, 190, 0], scale=0.6),
    "fixed": transform([0, 180, 0], scale=0.5),
}

KRENE_DISPLAY = {
    **ONE_BLOCK_DISPLAY,
    "gui": transform([20, 10, 0], scale=0.6),
}


def scaled_source_display(factor: float) -> dict[str, object]:
    """Preserve the approved item size for oversized baked geometry."""
    return {
        context: {
            **entry,
            "scale": [value * factor for value in entry["scale"]],
        }
        for context, entry in ONE_BLOCK_DISPLAY.items()
    }


def two_block_source_display() -> dict[str, object]:
    return scaled_source_display(0.5)


def three_block_display(gui_rotation: list[float]) -> dict[str, object]:
    return {
        "thirdperson_righthand": transform([75, 135, 0], [0, 0.5, 0], 0.125),
        "thirdperson_lefthand": transform([75, -45, 0], [0, 0.5, 0], 0.125),
        "firstperson_righthand": transform([0, 135, 0], [0, -2, 0], 0.13),
        "firstperson_lefthand": transform([0, -45, 0], [0, -2, 0], 0.13),
        "ground": transform(translation=[0, 1.5, 0], scale=0.0833),
        "gui": transform(gui_rotation, [0, -3.5, 0], 0.25),
        "fixed": transform([0, 0, 0], [0, -2.5, 0], 0.1667),
    }


CORBEL_DISPLAY = {
    "thirdperson_righthand": transform([75, 45, 0], [0, 0.5, 0], 0.125),
    "thirdperson_lefthand": transform([75, -135, 0], [0, 0.5, 0], 0.125),
    "firstperson_righthand": transform([0, 45, 0], [0, -2, 0], 0.13),
    "firstperson_lefthand": transform([0, -135, 0], [0, -2, 0], 0.13),
    "ground": transform(translation=[0, 1.5, 0], scale=0.0833),
    "gui": transform([20, 10, 0], [0, -3.5, 0], 0.25),
    "fixed": transform([0, 180, 0], [0, -2.5, 0], 0.1667),
}


def payload(particle: str, display: dict[str, object]) -> bytes:
    value = {
        "parent": "minecraft:block/block",
        "textures": {"particle": particle},
        "display": display,
    }
    return (json.dumps(value, indent=2) + "\n").encode("utf-8")


def expected_files() -> dict[str, bytes]:
    files = {
        "capital_display.json": payload(
            "daedalon:block/aganite_block", ONE_BLOCK_DISPLAY
        ),
        "plinth_display.json": payload(
            "daedalon:block/aganite_block", two_block_source_display()
        ),
        "fountain_basin_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            scaled_source_display(0.25),
        ),
        "fountain_bowl_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            two_block_source_display(),
        ),
        "krene_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite", KRENE_DISPLAY
        ),
        "decor_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite", ONE_BLOCK_DISPLAY
        ),
        "finial_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            two_block_source_display(),
        ),
        "monument_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            three_block_display([25, 190, 0]),
        ),
        "corbel_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite", CORBEL_DISPLAY
        ),
        "bust_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            three_block_display([20, 335, 0]),
        ),
        "zeus_statue_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            three_block_display([30, 315, 0]),
        ),
        "statue_spartan_promachos_display.json": payload(
            "daedalon:block/statue_spartan_promachos_aganite",
            three_block_display([30, 45, 0]),
        ),
    }
    for shape in (
        "amphora", "diota", "kalyx", "konche", "kylix", "lekythos",
        "pelike", "pithos", "rhabdos", "salpinx", "stamnos",
    ):
        files[f"urn_{shape}_display.json"] = payload(
            "daedalon:block/urn_surface/aganite_block", ONE_BLOCK_DISPLAY
        )
    return files


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    changed: list[str] = []
    for name, content in expected_files().items():
        path = DISPLAY_ROOT / name
        if path.is_file() and path.read_bytes() == content:
            continue
        if args.check:
            raise ValueError(f"OBJ display model is missing or stale: {path.relative_to(ROOT)}")
        path.write_bytes(content)
        changed.append(name)
    print(f"Daedalon OBJ display models current; changed={len(changed)}")


if __name__ == "__main__":
    main()
