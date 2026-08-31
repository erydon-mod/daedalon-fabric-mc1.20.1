#!/usr/bin/env python3
"""Generate the mechanically identical classical-bust mesh definitions."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MESH_ROOT = ROOT / "src/main/resources/assets/daedalon/models/mesh"
SUBJECTS = (
    "aphrodite", "apollo", "ares", "artemis", "athena", "demeter",
    "dionysus", "hephaestus", "hera", "hermes", "poseidon", "zeus",
)


def definition(subject: str) -> dict[str, object]:
    stem = f"bust_{subject}"
    return {
        "obj": f"daedalon:models/mesh/{stem}.obj",
        "mtl": f"daedalon:models/mesh/{stem}.mtl",
        "flip_v": True,
        "repair_degenerate_uvs": True,
        "force_uv_projection": True,
        "uv_projection": "axis_stabilized_box",
        "face_oriented_uvs": True,
        "smooth_normals": True,
        "smooth_angle_degrees": 80.0,
        "fit_to_block": True,
        "scale": [3.0, 3.0, 3.0],
        "translate": [0.0, 0.0, 0.0],
        "particle": "daedalon:block/statue_spartan_promachos_aganite",
        "materials": {"none": "daedalon:block/statue_spartan_promachos_aganite"},
    }


def main() -> None:
    for subject in SUBJECTS:
        path = MESH_ROOT / f"bust_{subject}.json"
        path.write_text(
            json.dumps(definition(subject), indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
            newline="\n",
        )


if __name__ == "__main__":
    main()
