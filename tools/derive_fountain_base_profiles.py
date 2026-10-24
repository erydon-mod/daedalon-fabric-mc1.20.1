"""Derive shared raised-base footprints from existing authored basin triangles.

Authoring-only dependency: Shapely 2.1+. No OBJ, material or texture is changed.
"""
from pathlib import Path
import hashlib
import json
import struct

from shapely import constrained_delaunay_triangles
from shapely.geometry import Point, Polygon
from shapely.geometry.polygon import orient
from shapely.ops import unary_union


ROOT = Path(__file__).resolve().parents[1]
MESHES = ROOT / "src/main/resources/assets/daedalon/models/mesh"
OUTPUT = ROOT / "src/main/resources/data/daedalon/fountain_base"


def f32(value):
    return struct.unpack("f", struct.pack("f", value))[0]


def derive(style):
    path = MESHES / f"fountain_{style}_basin.obj"
    positions, faces = [], []
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v":
            positions.append(tuple(f32(float(value)) for value in fields[1:4]))
        elif fields[0] == "f":
            indices = [int(value.split("/")[0]) - 1 for value in fields[1:]]
            faces.extend((indices[0], indices[i], indices[i + 1]) for i in range(1, len(indices) - 1))
    low = [min(point[i] for point in positions) for i in range(3)]
    high = [max(point[i] for point in positions) for i in range(3)]
    span = max(f32(high[i] - low[i]) for i in range(3))
    fit = f32(1 / span)
    centre_x, centre_z = f32(f32(low[0] + high[0]) * .5), f32(f32(low[2] + high[2]) * .5)
    # Match the runtime's fitted, five-block source geometry before projection.
    baked = [(f32(f32(f32(f32(point[0] - centre_x) * fit) * 5) + .5),
              f32(f32(f32(point[1] - low[1]) * fit) * 5),
              f32(f32(f32(f32(point[2] - centre_z) * fit) * 5) + .5)) for point in positions]
    bottom_band = span / 400  # The supplied Meshy floor has sub-pixel height jitter.
    selected = [face for face in faces if max(positions[index][1] for index in face) <= low[1] + bottom_band]
    triangles = [Polygon([(baked[index][0], baked[index][2]) for index in face]) for face in selected]
    footprint = unary_union([triangle for triangle in triangles if triangle.area > 1e-14])
    if footprint.geom_type != "Polygon" or len(footprint.interiors):
        raise ValueError(f"Unexpected disconnected or hollow final baseface for {style}")
    footprint = orient(footprint, sign=1)
    points = [(round((x - .5) / 5, 12), round((z - .5) / 5, 12)) for x, z in list(footprint.exterior.coords)[:-1]]
    start = min(range(len(points)), key=lambda index: points[index])
    points = points[start:] + points[:start]
    heights = []
    for x, z in points:
        bx, bz = .5 + x * 5, .5 + z * 5
        candidate = []
        for face, footprint_triangle in zip(selected, triangles):
            if footprint_triangle.area <= 1e-14 or footprint_triangle.distance(Point(bx, bz)) > 1e-8:
                continue
            a, b, c = (baked[index] for index in face)
            denominator = (b[0] - a[0]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[0] - a[0])
            wb = ((bx - a[0]) * (c[2] - a[2]) - (bz - a[2]) * (c[0] - a[0])) / denominator
            wc = ((b[0] - a[0]) * (bz - a[2]) - (b[2] - a[2]) * (bx - a[0])) / denominator
            candidate.append((1 - wb - wc) * a[1] + wb * b[1] + wc * c[1])
        if not candidate:
            raise ValueError(f"No authored underside height at {style} {x,z}")
        heights.append(round(max(0, min(candidate)) / 5, 12))
    polygon = Polygon(points)
    if not polygon.is_valid:
        raise ValueError(f"Invalid normalized footprint for {style}")
    indices = {point: index for index, point in enumerate(points)}
    caps = []
    for triangle in constrained_delaunay_triangles(polygon).geoms:
        corners = [indices[point] for point in list(triangle.exterior.coords)[:3]]
        a, b, c = (points[index] for index in corners)
        if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) < 0:
            corners[1], corners[2] = corners[2], corners[1]
        offset = corners.index(min(corners))
        caps.append(corners[offset:] + corners[:offset])
    caps.sort()
    if len(caps) != len(points) - 2:
        raise ValueError(f"Footprint triangulation lost vertices for {style}")
    baked_low = [min(point[i] for point in baked) for i in range(3)]
    baked_high = [max(point[i] for point in baked) for i in range(3)]
    return {
        "source_obj": f"assets/daedalon/models/mesh/{path.name}",
        "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "source_faces": len(faces), "selected_bottom_faces": len(selected),
        "source_bottom_band": bottom_band,
        "method": "Union of authored final baseface triangles within 1/400 source span of the lowest point; original geometry unchanged",
        "source_uv_min_x": baked_low[0], "source_uv_min_z": baked_low[2],
        "source_uv_span": max(f32(baked_high[i] - baked_low[i]) for i in range(3)),
        "points": points, "top_y": heights, "triangles": caps,
    }


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for style in ("gothic", "georgian", "greek"):
        data = derive(style)
        (OUTPUT / f"{style}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        print(style, len(data["points"]), "boundary points", len(data["triangles"]), "cap triangles")


if __name__ == "__main__":
    main()
