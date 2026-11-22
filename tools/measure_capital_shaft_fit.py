"""Measure automatic capital enlargement against the bundled shaft/head outlines.

Offline authoring tool only: NumPy and Shapely are not runtime dependencies.
The real OBJ triangles are intersected with the support plane after uniform
enlargement about the existing top. Closed cross-section regions are polygonized;
no convex hull, source-mesh edit, collision approximation or ERYDON edit is used.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from shapely.affinity import rotate, scale
from shapely.geometry import LineString, Polygon
from shapely.ops import polygonize_full, unary_union


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/daedalon"
STYLES = ("byzantine", "corinthian", "gothic", "greek_ionic", "roman_composite", "tuscan")
SUPPORTS = ("square", "gothic", "square_shaft", "gothic_shaft")
MAX_SCALE = 2.5
SCAN_STEP = 0.01
ROUND_UP = 0.001
BAND_HEIGHTS = (0.0, 1 / 32, 1 / 16, 3 / 32, 1 / 8)
# Final containment uses the actual polygons with no buffering or area allowance.
COVERAGE_TOLERANCE = 0
VALIDATION_HEIGHTS = tuple(i / 160 for i in range(21))


def load_source(style: str):
    definition_path = ASSETS / f"models/mesh/capital_{style}.json"
    mesh_path = ASSETS / f"models/mesh/capital_{style}.obj"
    definition = json.loads(definition_path.read_text(encoding="utf-8"))
    raw, triangles = [], []
    for line in mesh_path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v":
            raw.append(tuple(map(float, fields[1:4])))
        elif fields[0] == "f":
            indexes = [int(field.split("/")[0]) for field in fields[1:]]
            indexes = [index - 1 if index > 0 else len(raw) + index for index in indexes]
            triangles.extend((indexes[0], indexes[i], indexes[i + 1])
                             for i in range(1, len(indexes) - 1))
    positions = np.array(raw)
    minimum, maximum = positions.min(axis=0), positions.max(axis=0)
    center = (minimum + maximum) * 0.5
    center[1] = minimum[1]
    if not definition["fit_to_block"]:
        raise ValueError(f"Unexpected non-fitted capital: {style}")
    positions = ((positions - center) / max(maximum - minimum)
                 * np.array(definition["scale"])
                 + np.array(definition.get("translate", [0, 0, 0])) + [0.5, 0, 0.5])
    return Source(positions, np.array(triangles)), {
        "obj_sha256": hashlib.sha256(mesh_path.read_bytes()).hexdigest(),
        "definition_sha256": hashlib.sha256(definition_path.read_bytes()).hexdigest(),
        "vertices": len(raw), "triangles": len(triangles),
    }


class Source:
    def __init__(self, positions, triangles):
        self.positions = positions
        self.triangles = triangles
        self.top = float(positions[:, 1].max())
        if abs(self.top - 1) > 1e-5:
            raise ValueError(f"Expected an existing one-block capital top; got {self.top}")
        self.faces = positions[triangles]
        self.minimum = self.faces[:, :, 1].min(axis=1)
        self.maximum = self.faces[:, :, 1].max(axis=1)

    def section(self, factor: float, height: float = 0):
        # y' = 1 + factor * (y - 1); solve for each existing collar-band level.
        level = 1 + (height - 1) / factor
        faces = self.faces[(self.minimum <= level) & (self.maximum >= level)]
        segments, directed, coplanar = [], [], []
        for triangle in faces:
            points = []
            for a, b in ((triangle[0], triangle[1]), (triangle[1], triangle[2]),
                         (triangle[2], triangle[0])):
                if abs(a[1] - level) < 1e-12:
                    points.append(tuple(np.round(a[[0, 2]], 9)))
                if (a[1] < level < b[1]) or (b[1] < level < a[1]):
                    # Shared edges interpolate in a canonical order.
                    if tuple(a) > tuple(b):
                        a, b = b, a
                    t = (level - a[1]) / (b[1] - a[1])
                    points.append(tuple(np.round((a + t * (b - a))[[0, 2]], 9)))
            points = list(dict.fromkeys(points))
            if len(points) == 2:
                normal = np.cross(triangle[1] - triangle[0], triangle[2] - triangle[0])
                tangent = np.array([-normal[2], normal[0]])
                if np.dot(np.array(points[1]) - points[0], tangent) < 0:
                    points.reverse()
                segments.append(LineString(points))
                directed.append(points)
            elif len(points) == 3:
                coplanar.append(Polygon(points))
        polygons, cuts, dangles, invalid = polygonize_full(unary_union(segments))
        # Directed winding retains genuine holes; no hull or indiscriminate
        # nested-region fill can conceal a cutout in the supplied sculpture.
        edges = np.array(directed)
        def occupied(polygon):
            point = polygon.representative_point()
            a, b = edges[:, 0], edges[:, 1]
            left = (b[:, 0] - a[:, 0]) * (point.y - a[:, 1]) - (point.x - a[:, 0]) * (b[:, 1] - a[:, 1])
            up = (a[:, 1] <= point.y) & (point.y < b[:, 1]) & (left > 0)
            down = (b[:, 1] <= point.y) & (point.y < a[:, 1]) & (left < 0)
            return np.count_nonzero(up) != np.count_nonzero(down)
        occupied_cells = [polygon for polygon in polygons.geoms if occupied(polygon)]
        region = unary_union(occupied_cells + coplanar)
        region = scale(region, xfact=factor, yfact=factor, origin=(0.5, 0.5))
        return region, {
            "section_polygons": len(polygons.geoms),
            "section_occupied_polygons": len(occupied_cells),
            "section_cut_length": round(cuts.length, 10),
            "section_dangle_length": round(dangles.length, 10),
            "section_invalid_length": round(invalid.length, 10),
        }


def targets(heights=BAND_HEIGHTS):
    profiles = json.loads((ASSETS / "geometry/capital_shaft_profiles.json").read_text())["profiles"]
    result = {}
    for size in ("standard", "double"):
        for name in ("square_shaft", "gothic_shaft"):
            data = profiles[name + ("_double" if size == "double" else "")]
            points, angles = np.array(data["outline"]), np.array(data["circle_angles"])
            circle = np.array([.5 + np.cos(angles) * 11.5 / 32,
                               .5 + np.sin(angles) * 11.5 / 32]).T
            # Double targets are normalized to the two-block footprint, exactly
            # as CapitalTransitionProfile/CapitalTransitionMesh stores them.
            result[(size, name)] = [(height, Polygon(points * (1 - fraction) + circle * fraction))
                                    for height in heights
                                    for fraction in [max(0, (height - 1 / 32) / (3 / 32))]]
    return result


def orientations(style):
    return (0, 45, 90, 135) if style == "greek_ionic" else (0, 45)


def covers(region, target, angles):
    return all(rotate(region, angle, origin=(0.5, 0.5)).covers(target)
               for angle in angles)


def measure(style, source, all_targets):
    angles = orientations(style)
    pending = dict(all_targets)
    brackets, last, cache = {}, 1, {}
    def section(factor, height=0):
        if (factor, height) not in cache:
            cache[(factor, height)] = source.section(factor, height)
        return cache[(factor, height)]
    def fits(factor, band):
        return all(covers(section(factor, height)[0], target, angles) for height, target in band)
    # Bounded scan accepts the first coverage interval, not a monotonicity
    # assumption across all of a sculpted capital's ledges and volutes.
    for step in range(1, round((MAX_SCALE - 1) / SCAN_STEP) + 1):
        factor = 1 + step * SCAN_STEP
        for key, band in list(pending.items()):
            if fits(factor, band):
                brackets[key] = (last, factor)
                del pending[key]
        if not pending:
            break
        last = factor
    if pending:
        raise ValueError(f"{style}: no fit below {MAX_SCALE}: {list(pending)}")
    records = []
    for (size, support), band in all_targets.items():
        low, high = brackets[(size, support)]
        for _ in range(19):
            midpoint = (low + high) * 0.5
            if fits(midpoint, band):
                high = midpoint
            else:
                low = midpoint
        accepted = math.ceil(high / ROUND_UP) * ROUND_UP
        region, diagnostics = section(accepted)
        if not fits(accepted, band):
            # A tiny feature may cross the contact plane during rounding.
            # Advance in bounded authoring increments until coverage is stable.
            for _ in range(20):
                accepted += ROUND_UP
                region, diagnostics = section(accepted)
                if fits(accepted, band):
                    break
            else:
                raise ValueError(f"No safe rounded fit for {style}/{size}/{support}")
        misses = [target.difference(rotate(section(accepted, height)[0], angle, origin=(0.5, 0.5))).area
                  for height, target in band for angle in angles]
        diagnostics = {key: max(section(accepted, height)[1][key] for height, _ in band)
                       for key in diagnostics}
        records.append({"size": size, "support": support,
                        "minimum_scale_estimate": round(high, 8), "safe_scale": round(accepted, 3),
                        "orientations_degrees": list(angles),
                        "maximum_uncovered_area_normalized": max(misses),
                        "rendered_bottom_blocks": round((1 - accepted) * (2 if size == "double" else 1), 6),
                        **diagnostics})
    return records


def verify(report, runtime, heights=VALIDATION_HEIGHTS):
    profile_hash = hashlib.sha256((ASSETS / "geometry/capital_shaft_profiles.json").read_bytes()).hexdigest()
    if report["shaft_profiles_sha256"] != profile_hash:
        raise ValueError("Shaft profile source hash changed")
    if set(runtime["styles"]) != set(STYLES):
        raise ValueError("Unexpected or missing runtime capital style")
    all_targets = targets(heights)
    checked = 0
    for style in STYLES:
        source, info = load_source(style)
        if any(info[key] != report["styles"][style][key] for key in ("obj_sha256", "definition_sha256")):
            raise ValueError(f"Source hash changed: {style}")
        if set(runtime["styles"][style]) != set(SUPPORTS):
            raise ValueError(f"Unexpected or missing support: {style}")
        for support in SUPPORTS:
            if set(runtime["styles"][style][support]) != {"standard", "double"}:
                raise ValueError(f"Unexpected or missing size: {style}/{support}")
            for size in ("standard", "double"):
                factor = runtime["styles"][style][support][size]
                if not math.isfinite(factor) or not 1 <= factor <= MAX_SCALE:
                    raise ValueError(f"Invalid runtime scale: {style}/{support}/{size}")
                if support in ("square", "gothic"):
                    if factor != 1:
                        raise ValueError("Full-head supports must keep existing fitted collars and scale 1")
                    continue
                for height, target in all_targets[(size, support)]:
                    region, _ = source.section(factor, height)
                    if not covers(region, target, orientations(style)):
                        misses = [target.difference(rotate(region, angle, origin=(.5, .5))).area
                                  for angle in orientations(style)]
                        raise ValueError(f"Miss at {style}/{size}/{support}, scale {factor}, height {height}: {misses}")
                    checked += len(orientations(style))
    return checked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/evidence/capital-shaft-fit.json")
    parser.add_argument("--runtime-output", type=Path, default=ASSETS / "geometry/capital_column_fits.json")
    parser.add_argument("--check", action="store_true", help="Validate active factors without scanning or changing files")
    args = parser.parse_args()
    if args.check:
        report = json.loads(args.output.read_text(encoding="utf-8"))
        runtime = json.loads(args.runtime_output.read_text(encoding="utf-8"))
        print(f"Verified {verify(report, runtime)} active shaft/size/orientation/band cases; zero uncovered area")
        return
    report = {"method": "actual OBJ triangle cross-section; directed-winding polygon regions; all permitted rotations",
              "anchor": "Uniform scale about existing top Y=1 (standard) or Y=2 (double)",
              "maximum_search_scale": MAX_SCALE, "scan_step": SCAN_STEP,
              "round_up_scale_step": ROUND_UP, "coverage_tolerance_blocks_normalized": COVERAGE_TOLERANCE,
              "checked_band_heights_blocks_normalized": list(VALIDATION_HEIGHTS),
              "shaft_profiles_sha256": hashlib.sha256((ASSETS / "geometry/capital_shaft_profiles.json").read_bytes()).hexdigest(),
              "head_policy": "Keep existing square/Gothic full-head collars and factor 1.0; only bare-shaft contact is enlarged.",
              "slice_diagnostics": "Maximum over every checked band height at the accepted scale",
              "runtime_dependencies_added": [],
              "limitations": ["Offline geometric evidence; no in-game visual acceptance.",
                              "Directed slice winding preserves enclosed holes and concavities; no convex hull or coverage buffer is used.",
                              "The supplied Byzantine mesh has some open cut contours; only bounded occupied regions count, which can conservatively increase its scale.",
                              "Existing full-head collars are retained and deliberately excluded from the enlargement search."],
              "styles": {}}
    all_targets = targets(VALIDATION_HEIGHTS)
    for style in STYLES:
        source, info = load_source(style)
        records = measure(style, source, all_targets)
        report["styles"][style] = {**info, "fits": records}
        print(style, "; ".join(f"{r['size']}/{r['support']}={r['safe_scale']}" for r in records), flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    runtime = {"styles": {style: {support: ({"standard": 1.0, "double": 1.0} if support in ("square", "gothic")
                                        else {row["size"]: row["safe_scale"]
                                        for row in data["fits"] if row["support"] == support}
                                        )
                                  for support in SUPPORTS}
                          for style, data in report["styles"].items()}}
    args.runtime_output.write_text(json.dumps(runtime, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Verified {verify(report, runtime)} active shaft/size/orientation/band cases; zero uncovered area")


if __name__ == "__main__":
    main()
