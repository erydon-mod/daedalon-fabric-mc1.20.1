"""Prove that selecting the other horizontal frieze repeat preserves every pixel.

Optionally audit both Collection source packs, including native fallback finishes:
python tools/audit_frieze_repeat_surfaces.py --collection-root <collection repo>
    --report build/reports/prepublication/frieze-repeat-surfaces.json
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[1]
NATIVE_ROOT = REPO_ROOT / "src/main/resources/assets/daedalon/textures/block"
SOLID_FINISHES = ("white", "bronze", "silver")
FRIEZE_FAMILIES = ("byzantine", "corinthian", "gothic", "ionic")


def surface_proof(path: Path, solid: bool = False) -> dict:
    return _surface_proof(path.read_bytes(), str(path.resolve()), solid)


def _surface_proof(payload: bytes, path: str, solid: bool) -> dict:
    with Image.open(io.BytesIO(payload)) as source:
        image = source.convert("RGBA")
        width, height = image.size
        if solid:
            valid = all(low == high for low, high in image.getextrema())
        else:
            valid = width == 2 * height and image.crop((0, 0, width // 2, height)).tobytes() == image.crop(
                (width // 2, 0, width, height)).tobytes()
    if not valid:
        raise ValueError(f"Frieze repeat copy would change pixels: {path}")
    stem = Path(path).stem
    role = "normal" if stem.endswith("_n") else "specular" if stem.endswith("_s") else "albedo"
    return {"path": path, "sha256": hashlib.sha256(payload).hexdigest(),
            "dimensions": [width, height], "role": role,
            "pixel_equivalence": "uniform" if solid else "identical_horizontal_halves"}


def audit_pack(path: Path, fallback: Path = NATIVE_ROOT) -> dict:
    """Read the installed deduplicated ZIP, including its real alias manifests.

    A missing pack resource inherits the native map, just as ResourceManager does.
    Check complete RGBA, including normal-map height and specular channels.
    """
    prefix = "assets/daedalon/textures/block/"
    with ZipFile(path) as archive:
        entries = set(archive.namelist())
        manifest = "assets/daedalon/texture_aliases/v1.json"
        aliases = {} if manifest not in entries else {
            "assets/daedalon/" + record["path"]: record["target"]
            for record in json.loads(archive.read(manifest))["aliases"]}
        paths = set(entries) | aliases.keys() | {prefix + p.name for p in fallback.glob("*.png")}
        selected = sorted(logical for logical in paths if logical.startswith(prefix + "statue_spartan_promachos_")
                          and logical.endswith(".png"))
        selected += [prefix + finish + suffix + ".png" for finish in SOLID_FINISHES
                     for suffix in ("", "_n", "_s")]
        surfaces = []
        for logical in selected:
            native = fallback / logical.removeprefix(prefix)
            if logical in entries:
                payload, provider = archive.read(logical), "pack"
            elif logical in aliases:
                payload, provider = archive.read(aliases[logical]), "pack_alias"
            elif native.exists():
                payload, provider = native.read_bytes(), "native_fallback"
            else:
                continue
            proof = _surface_proof(payload, logical, not logical.startswith(prefix + "statue_spartan_promachos_"))
            proof["provider"] = provider
            surfaces.append(proof)
    if not surfaces:
        raise ValueError(f"No frieze surfaces found: {path}")
    return {"root": str(path.resolve()), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "surface_count": len(surfaces),
            "role_counts": {role: sum(record["role"] == role for record in surfaces)
                            for role in ("albedo", "normal", "specular")}, "surfaces": surfaces}


def audit_root(root: Path, fallback: Path | None = None) -> dict:
    repeat_paths = sorted(root.glob("statue_spartan_promachos_*.png"))
    if not repeat_paths:
        raise ValueError(f"No detailed repeat surfaces found: {root}")
    surfaces = [surface_proof(path) for path in repeat_paths]
    for finish in SOLID_FINISHES:
        for suffix in ("", "_n", "_s"):
            path = root / f"{finish}{suffix}.png"
            inherited = False
            if not path.exists() and fallback is not None:
                path = fallback / path.name
                inherited = True
            if not path.exists():
                if not suffix:
                    raise ValueError(f"Missing frieze finish: {path}")
                continue
            proof = surface_proof(path, solid=True)
            proof["native_fallback"] = inherited
            surfaces.append(proof)
    return {"root": str(root.resolve()), "surface_count": len(surfaces),
            "role_counts": {role: sum(record["role"] == role for record in surfaces)
                            for role in ("albedo", "normal", "specular")},
            "surfaces": surfaces}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--collection-root", type=Path)
    parser.add_argument("--collection-pack", type=Path, help="Read the actual installed Collection ZIP and aliases")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    roots = {"native": audit_root(NATIVE_ROOT)}
    if args.collection_root:
        for resolution in ("32x", "64x"):
            root = args.collection_root / "packs" / resolution / "assets/daedalon/textures/block"
            roots[resolution] = audit_root(root, NATIVE_ROOT)
    if args.collection_pack:
        roots["active_collection_pack"] = audit_pack(args.collection_pack)
    report = {"strategy": "whole_quad_horizontal_offset_of_half_sprite_width",
              "frieze_families": FRIEZE_FAMILIES,
              "geometry_changed": False, "vertical_phase_changed": False, "roots": roots}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({name: {key: value for key, value in result.items() if key != "surfaces"}
                      for name, result in roots.items()}, indent=2))


if __name__ == "__main__":
    main()
