"""Derive the two shared collar outlines from ERYDON's actual authored shaft tops.

Authoring only; requires Shapely 2.1. Never modifies ERYDON or any supplied model.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

from shapely import constrained_delaunay_triangles
from shapely.geometry import Polygon
from shapely.geometry import box
from shapely.geometry.polygon import orient
from shapely.ops import unary_union
from shapely.affinity import translate

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    "square_shaft": "models/block/column/square/column_square_pillar_shared.json",
    "gothic_shaft": "authoring_models/block/column/gothic/column_gothic_pillar.json",
}


def prepare(path: Path, relative: str, footprint: int = 1) -> dict:
    polygons = []
    for element in json.loads(path.read_text(encoding="utf-8"))["elements"]:
        if "up" not in element.get("faces", {}):
            continue
        rotation = element.get("rotation", {})
        if rotation.get("axis", "y") != "y" or rotation.get("rescale", False):
            raise ValueError(f"Unsupported shaft element transform: {path}")
        angle = math.radians(rotation.get("angle", 0))
        cosine, sine = math.cos(angle), math.sin(angle)
        ox, _, oz = rotation.get("origin", [8, 8, 8])
        minimum, maximum = element["from"], element["to"]
        corners = [(minimum[0], minimum[2]), (maximum[0], minimum[2]),
                   (maximum[0], maximum[2]), (minimum[0], maximum[2])]
        points = [(ox + (x - ox) * cosine + (z - oz) * sine,
                   oz - (x - ox) * sine + (z - oz) * cosine) for x, z in corners]
        polygon = Polygon(points)
        if polygon.area > 1e-8:
            polygons.append(polygon)
    union = unary_union(polygons)
    if footprint == 2:
        x0, z0, x1, z1 = union.bounds
        # One solid bridge carries the large capital over the four separate
        # shafts; its outer perimeter still follows the actual shaft flutes.
        union = unary_union([translate(union, xoff=x * 16, yoff=z * 16)
                             for x in range(2) for z in range(2)]
                            + [box(8, z0, 24, z1 + 16), box(x0, 8, x1 + 16, 24)])
    if union.geom_type != "Polygon":
        raise ValueError("The shaft must have one connected outer outline")
    # Internal flute voids are covered by the solid necking. Simplify only
    # sub-0.001-pixel collinear noise; keep the visible flutes and Gothic lobes.
    outline = orient(Polygon(union.exterior).simplify(0.001, preserve_topology=True), sign=1)
    points = [(round(x / (16 * footprint), 10), round(z / (16 * footprint), 10))
              for x, z in list(outline.exterior.coords)[:-1]]
    start = max(range(len(points)), key=lambda i: (points[i][0], -abs(points[i][1] - 0.5)))
    points = points[start:] + points[:start]
    index = {point: i for i, point in enumerate(points)}
    polygon = Polygon(points)
    triangles = [[index[(round(x, 10), round(z, 10))] for x, z in list(triangle.exterior.coords)[:-1]]
                 for triangle in constrained_delaunay_triangles(polygon).geoms]
    for triangle in triangles:
        a, b, c = [points[i] for i in triangle]
        if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) < 0:
            triangle[1], triangle[2] = triangle[2], triangle[1]
    lengths = [math.dist(points[i], points[(i + 1) % len(points)]) for i in range(len(points))]
    perimeter = sum(lengths)
    angle = math.atan2(points[0][1] - 0.5, points[0][0] - 0.5)
    angles = []
    distance = 0.0
    for length in lengths:
        angles.append(angle + distance / perimeter * math.tau)
        distance += length
    radius = 11.5 / 32
    for step in range(21):
        fraction = step / 20
        ring = [(x * (1 - fraction) + (0.5 + math.cos(a) * radius) * fraction,
                 z * (1 - fraction) + (0.5 + math.sin(a) * radius) * fraction)
                for (x, z), a in zip(points, angles)]
        if not Polygon(ring).is_valid:
            raise ValueError("The shaft-to-circle loft would intersect itself")
    return {"source_model": "erydon:" + relative,
            "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "source_bounds_pixels": list(union.bounds), "source_up_polygons": len(polygons),
            "footprint_cells": footprint,
            "covered_internal_voids": len(union.interiors), "simplification_tolerance_pixels": 0.001,
            "outline": points, "circle_angles": angles, "bottom_triangles": triangles}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--erydon-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=REPO_ROOT / "src/main/resources/assets/daedalon/geometry/capital_shaft_profiles.json")
    args = parser.parse_args()
    root = args.erydon_root / "src/main/resources/assets/erydon"
    profiles = {name: prepare(root / relative, relative) for name, relative in SOURCES.items()}
    profiles.update({name + "_double": prepare(root / relative, relative, footprint=2)
                     for name, relative in SOURCES.items()})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"profiles": profiles}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({name: {"outline_vertices": len(profile["outline"]),
                             "bottom_triangles": len(profile["bottom_triangles"]),
                             "source_bounds_pixels": profile["source_bounds_pixels"]}
                      for name, profile in profiles.items()}, indent=2))


if __name__ == "__main__":
    main()
