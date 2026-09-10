"""Offline, deterministic waterline measurement; never scans OBJ files in-game."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MESH_ROOT = ROOT / "src/main/resources/assets/daedalon/models/mesh"
OUTPUT = ROOT / "src/main/resources/data/daedalon/fountain_water"
# Internal resource names, not the supplied export names or player-facing pairing.
PROFILES = {
    "georgian": ("georgian", "basin", 0.19),
    "greek": ("greek", "basin", 0.18),
    "gothic_bowl": ("gothic", "bowl", 0.87),
    "georgian_bowl": ("georgian", "bowl", 0.562),
    "greek_bowl": ("greek", "bowl", 0.63),
}
INSET = 0.003
TOLERANCE = 0.0007


def cross(a, b):
    return a[0] * b[1] - a[1] * b[0]


def sections(style, level, kind="basin"):
    vertices, faces = [], []
    for line in (MESH_ROOT / f"fountain_{style}_{kind}.obj").read_text().splitlines():
        if line.startswith("v "):
            vertices.append(tuple(map(float, line.split()[1:4])))
        elif line.startswith("f "):
            faces.append([int(ref.split("/")[0]) - 1 for ref in line.split()[1:]])
    lo = [min(v[i] for v in vertices) for i in range(3)]
    hi = [max(v[i] for v in vertices) for i in range(3)]
    span = max(hi[i] - lo[i] for i in range(3))
    vertices = [((x - (lo[0] + hi[0]) / 2) / span, (y - lo[1]) / span,
                 (z - (lo[2] + hi[2]) / 2) / span) for x, y, z in vertices]
    result = []
    for face in faces:
        hits = []
        for a, b in zip(face, face[1:] + face[:1]):
            va, vb = vertices[a], vertices[b]
            if (va[1] - level) * (vb[1] - level) < 0:
                t = (level - va[1]) / (vb[1] - va[1])
                hits.append((va[0] + t * (vb[0] - va[0]), va[2] + t * (vb[2] - va[2])))
        if len(hits) == 2:
            result.append(hits)
    return result


def wall_radius(section, angle):
    direction = (math.cos(angle), math.sin(angle))
    hits = []
    for a, b in section:
        edge = (b[0] - a[0], b[1] - a[1])
        denominator = cross(direction, edge)
        if abs(denominator) < 1e-12:
            continue
        distance, fraction = cross(a, edge) / denominator, cross(a, direction) / denominator
        if distance > 0 and -1e-8 <= fraction <= 1 + 1e-8:
            hits.append(distance)
    if not hits:
        raise ValueError(f"No enclosing wall at angle {angle}")
    return min(hits)


def generate(profile_name):
    style, kind, level = PROFILES[profile_name]
    section = sections(style, level, kind)

    def point(angle):
        radius = wall_radius(section, angle) - INSET
        return (radius * math.cos(angle), radius * math.sin(angle))

    def subdivide(start, end, depth=0):
        a, b = point(start), point(end)
        errors = []
        for t in (0.25, 0.5, 0.75):
            x, z = a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])
            errors.append(wall_radius(section, math.atan2(z, x)) - math.hypot(x, z) - INSET)
        if max(map(abs, errors)) > TOLERANCE and depth < 9:
            mid = (start + end) / 2
            return subdivide(start, mid, depth + 1) + subdivide(mid, end, depth + 1)
        return [a]

    points = []
    for i in range(32):
        points.extend(subdivide(i * math.tau / 32, (i + 1) * math.tau / 32))
    points = [[round(x, 7), round(z, 7)] for x, z in points]
    clearances = []
    for a, b in zip(points, points[1:] + points[:1]):
        for i in range(33):
            t = i / 32
            x, z = a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])
            clearances.append(wall_radius(section, math.atan2(z, x)) - math.hypot(x, z))
    if min(clearances) < 0.001 or len(points) > 256:
        raise ValueError(f"Unsafe/oversized {style} outline: {len(points)}, {min(clearances)}")
    # The cached collision prism uses horizontal strips; reject non-monotone input.
    for i in range(1, 256):
        x = min(p[0] for p in points) + (max(p[0] for p in points) - min(p[0] for p in points)) * i / 256
        hits = sum((a[0] <= x < b[0]) or (b[0] <= x < a[0])
                   for a, b in zip(points, points[1:] + points[:1]))
        if hits != 2:
            raise ValueError(f"Non-monotone {style} waterline at {x}")
    return {"water_surface_y": level, "radial_inset": INSET,
            "minimum_clearance": round(min(clearances), 7),
            "maximum_clearance": round(max(clearances), 7), "points": points}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    for style in PROFILES:
        profile = generate(style)
        text = json.dumps(profile, indent=2) + "\n"
        path = OUTPUT / f"{style}.json"
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        elif not path.exists() or path.read_text(encoding="utf-8") != text:
            raise SystemExit(f"Water profile needs regeneration: {style}")
        print(f"{style}: {len(profile['points'])} edges; clearance {profile['minimum_clearance']}..{profile['maximum_clearance']}")


if __name__ == "__main__":
    main()
