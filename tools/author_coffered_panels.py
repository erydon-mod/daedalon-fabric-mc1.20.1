#!/usr/bin/env python3
"""Author restrained, face-efficient coffered reliefs from numeric parameters.

Oliver's Meshy panels are visual references only. Their mesh is never loaded,
copied or modified. One shared OBJ/MTL/definition supplies every material.
Run normally with Python; the locked Blender build supplies vector maths,
prepared normals and optional neutral-clay verification renders.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import math
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

try:
    import bpy
    from mathutils import Vector
except ModuleNotFoundError:
    bpy = None
    Vector = None

DEFAULT_BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
BLENDER_VERSION = (5, 2, 0)
BLENDER_HASH = "fbe6228777e7"
BACKING_FRONT_Z = 0.016
EMBEDDED_Z = 0.015
MAX_TRIANGLES = 6000
REFERENCES = {
    "corinthian": ("coffered  2.blend", "417076ee6b2f12eae9d27bd0be32f30917296eb74dfecab33b5fb3fca17be7c0"),
    "byzantine": ("coffered 3.blend", "3eec858bd4b1307b7c4261fec29e695216d1b77ec32724b29ce188968ad31d81"),
    "georgian": ("coffered 4.blend", "0c19cf91c08bb97ba069810ef943dc98ec3aeca21ebc319f592f9035862cebec"),
    "renaissance": ("coffered 5.blend", "fcc02190b3dbdc4ae9e27129837151e99aefc3c13e9f0fa7476f620de38db12a"),
}


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def quad(a, b, c, d):
    return [(a, b, c), (a, c, d)]


class Relief:
    def __init__(self):
        self.groups = {}
        self.motifs = {}

    def add(self, name, triangles, smoothing=30, normals=None):
        from prepare_corinthian_frieze import smooth_relief_normals
        prepared = []
        for index, triangle in enumerate(triangles):
            points = [Vector(p) for p in triangle]
            cross = (points[1] - points[0]).cross(points[2] - points[0])
            if cross.length < 1e-12:
                raise ValueError(f"Degenerate authored face in {name}")
            geometric = cross.normalized()
            ns = normals[index] if normals is not None else [geometric] * 3
            prepared.append([(p, Vector(n).normalized()) for p, n in zip(points, ns)])
        if normals is None:
            prepared = smooth_relief_normals(prepared, width=2, angle_degrees=smoothing)
        # Keep every exported corner in the outward hemisphere of its own face.
        checked = []
        for triangle in prepared:
            a, b, c = [p for p, n in triangle]
            geometric = (b - a).cross(c - a).normalized()
            checked.append([(p, n if n.dot(geometric) > 0.001 else geometric.copy()) for p, n in triangle])
        self.groups[name] = checked

    def dome(self, name, center, a, b, height, angle=0, segments=10):
        """Two smooth elliptical rings and an apex; the buried base stays open."""
        ca, sa = math.cos(angle), math.sin(angle)
        x, y = center
        rings = []
        nrings = []
        for theta in (math.pi / 2, math.pi / 4):
            ring, ns = [], []
            for i in range(segments):
                phi = 2 * math.pi * i / segments
                dx, dy = a * math.sin(theta) * math.cos(phi), b * math.sin(theta) * math.sin(phi)
                z = EMBEDDED_Z + height * math.cos(theta)
                ring.append(Vector((x + dx * ca - dy * sa, y + dx * sa + dy * ca, z)))
                nx, ny, nz = dx / (a * a), dy / (b * b), (z - EMBEDDED_Z) / (height * height)
                ns.append(Vector((nx * ca - ny * sa, nx * sa + ny * ca, nz)).normalized())
            rings.append(ring)
            nrings.append(ns)
        apex, up = Vector((x, y, EMBEDDED_Z + height)), Vector((0, 0, 1))
        ts, normals = [], []
        for i in range(segments):
            j = (i + 1) % segments
            ts.extend(quad(rings[0][i], rings[0][j], rings[1][j], rings[1][i]))
            normals.extend(quad(nrings[0][i], nrings[0][j], nrings[1][j], nrings[1][i]))
            ts.append((rings[1][i], rings[1][j], apex))
            normals.append((nrings[1][i], nrings[1][j], up))
        self.add(name, ts, normals=normals)

    def leaf(self, name, start, end, width, height, ornate=False, bend=0, refined=False):
        """A lobed, ridged leaf skin; its complete boundary is buried in backing."""
        start, end = Vector((*start, 0)), Vector((*end, 0))
        forward = end - start
        side = Vector((-forward.y, forward.x, 0)).normalized()
        if ornate:
            stations = ((0, 0), (.12, .42), (.25, .28), (.38, .86), (.51, .57), (.64, 1), (.77, .66), (.89, .43), (1, 0))
            if refined:
                stations = tuple(sorted((*stations, (.18, .36), (.32, .65), (.57, .82), (.83, .55))))
        else:
            stations = ((0, 0), (.25, .75), (.5, 1), (.75, .6), (1, 0))
        rings = []
        for t, w in stations[1:-1]:
            center = start + forward * t + side * (bend * math.sin(math.pi * t))
            ring = []
            for u in (-1, -.45, 0, .45, 1):
                p = center + side * (width * w * u)
                p.z = EMBEDDED_Z + height * math.sin(math.pi * t) ** .7 * max(0, 1 - u * u) ** .65
                ring.append(p)
            rings.append(ring)
        first, last = start.copy(), end.copy()
        first.z = last.z = EMBEDDED_Z
        ts = []
        for j in range(4):
            ts.append((first, rings[0][j], rings[0][j + 1]))
        for a, b in zip(rings, rings[1:]):
            for j in range(4):
                ts.extend(quad(a[j], b[j], b[j + 1], a[j + 1]))
        for j in range(4):
            ts.append((rings[-1][j], last, rings[-1][j + 1]))
        self.add(name, ts, smoothing=140)

    def sphere(self, name, center, radius, segments=6):
        """A closed, smoothly shaded 24-triangle bead; no tall hidden pedestal."""
        center = Vector(center)
        rings, ns = [], []
        for theta in (math.pi / 3, 2 * math.pi / 3):
            normals = [Vector((math.sin(theta) * math.cos(2 * math.pi * j / segments),
                               math.sin(theta) * math.sin(2 * math.pi * j / segments), math.cos(theta)))
                       for j in range(segments)]
            rings.append([center + n * radius for n in normals])
            ns.append(normals)
        top, bottom = center + Vector((0, 0, radius)), center - Vector((0, 0, radius))
        ts, normals = [], []
        for j in range(segments):
            k = (j + 1) % segments
            ts.extend(quad(rings[0][j], rings[1][j], rings[1][k], rings[0][k]))
            normals.extend(quad(ns[0][j], ns[1][j], ns[1][k], ns[0][k]))
            ts.extend(((top, rings[0][j], rings[0][k]), (bottom, rings[1][k], rings[1][j])))
            normals.extend(((Vector((0, 0, 1)), ns[0][j], ns[0][k]), (Vector((0, 0, -1)), ns[1][k], ns[1][j])))
        self.add(name, ts, normals=normals)

    def tube(self, name, centers, radius, sides=4):
        """A closed fine vein, with smooth authored radial normals."""
        centers = [Vector(p) for p in centers]
        rings, nrings = [], []
        for i, center in enumerate(centers):
            tangent = (centers[min(i + 1, len(centers) - 1)] - centers[max(i - 1, 0)]).normalized()
            side = Vector((-tangent.y, tangent.x, 0)).normalized()
            up = tangent.cross(side).normalized()
            ns = [side * math.cos(2 * math.pi * j / sides) + up * math.sin(2 * math.pi * j / sides) for j in range(sides)]
            nrings.append(ns)
            rings.append([center + n * radius for n in ns])
        ts, normals = [], []
        for i in range(len(rings) - 1):
            for j in range(sides):
                k = (j + 1) % sides
                ts.extend(quad(rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j]))
                normals.extend(quad(nrings[i][j], nrings[i][k], nrings[i + 1][k], nrings[i + 1][j]))
        for j in range(sides):
            k = (j + 1) % sides
            ts.extend(((centers[0], rings[0][k], rings[0][j]), (centers[-1], rings[-1][j], rings[-1][k])))
            normals.extend(([-(centers[1] - centers[0]).normalized()] * 3, [(centers[-1] - centers[-2]).normalized()] * 3))
        self.add(name, ts, normals=normals)


def square_ring(radius, z):
    return [Vector((.5 + x * radius, .5 + y * radius, z)) for x, y in ((-1, -1), (1, -1), (1, 1), (-1, 1))]


def backing(relief):
    profile = ((.5, .016), (.493, .023), (.485, .024), (.474, .020), (.468, .019),
               (.390, .019), (.382, .025), (.369, .026), (.354, .021), (.345, .017),
               (.278, .017), (.268, .026), (.257, .028), (.245, .026), (.231, .044),
               (.212, .044), (.193, .022))
    bottom = square_ring(.5, 0)
    rings = [square_ring(r, z) for r, z in profile]
    ts = []
    for i in range(4):
        j = (i + 1) % 4
        ts.extend(quad(bottom[i], bottom[j], rings[0][j], rings[0][i]))
    for a, b in zip(rings, rings[1:]):
        for i in range(4):
            j = (i + 1) % 4
            ts.extend(quad(a[i], a[j], b[j], b[i]))
    ts.extend(quad(*rings[-1]))
    ts.extend(quad(bottom[3], bottom[2], bottom[1], bottom[0]))
    relief.add("backing_corinthian_panel", ts, smoothing=30)


def corinthian():
    relief = Relief()
    backing(relief)
    # The same frame, egg-and-dart order, inner acanthus band and central flower
    # remain legible; repeated motifs carry their detail in a few smooth strips.
    for edge in range(4):
        theta = edge * math.pi / 2
        tangent = Vector((math.cos(theta), math.sin(theta)))
        outward = Vector((-math.sin(theta), math.cos(theta)))
        def xy(t, r):
            return tuple(Vector((.5, .5)) + tangent * t + outward * r)
        for i in range(14):
            t = -.34 + .68 * i / 13
            relief.dome(f"egg_{edge}_{i}", xy(t, .429), .019, .029, .025, theta, 8)
            relief.dome(f"pearl_{edge}_{i}", xy(t, .383), .0065, .0065, .021, segments=6)
            if i < 13:
                dart = Vector(xy(t + .68 / 26, .429))
                ring = [Vector((*tuple(dart + tangent * a + outward * b), EMBEDDED_Z))
                        for a, b in ((0, -.024), (.005, 0), (0, .024), (-.005, 0))]
                apex = Vector((*dart, .031))
                relief.add(f"dart_{edge}_{i}", [(ring[j], ring[(j + 1) % 4], apex) for j in range(4)], smoothing=30)
        for i in range(6):
            t = -.23 + .46 * i / 5
            relief.leaf(f"band_acanthus_{edge}_{i}", xy(t, .278), xy(t, .342), .041, .031, ornate=True, bend=.010)
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        for band, start_r, end_r, width, height in (("outer", .397, .471, .019, .035), ("inner", .267, .337, .020, .037)):
            start = (.5 + sx * start_r, .5 + sy * start_r)
            for j, (ex, ey) in enumerate(((end_r, end_r), (end_r, end_r - .047), (end_r - .047, end_r))):
                relief.leaf(f"{band}_corner_{sx}_{sy}_{j}", start, (.5 + sx * ex, .5 + sy * ey), width, height)
    for i in range(8):
        angle = i * math.pi / 4
        f = Vector((math.cos(angle), math.sin(angle)))
        at = lambda r: tuple(Vector((.5, .5)) + f * r)
        under_angle = angle + math.pi / 8
        u = Vector((math.cos(under_angle), math.sin(under_angle)))
        relief.leaf(f"flower_underleaf_{i}", tuple(Vector((.5, .5)) + u * .026), tuple(Vector((.5, .5)) + u * .161), .029, .035)
        relief.leaf(f"flower_acanthus_{i}", at(.025), at(.186), .044, .048, ornate=True, refined=True)
    relief.dome("flower_central_boss", (.5, .5), .038, .038, .067, segments=12)
    for ring, count, radius in (("outer", 12, .030), ("inner", 6, .013)):
        for i in range(count):
            angle = 2 * math.pi * i / count
            z = EMBEDDED_Z + .067 * math.sqrt(1 - (radius / .038) ** 2) + .002
            relief.sphere(f"flower_seed_{ring}_{i}", (.5 + radius * math.cos(angle), .5 + radius * math.sin(angle), z), .008)
    relief.motifs = {"egg_and_dart": 56, "frame_pearls": 56, "band_acanthus_leaves": 24,
                     "corner_foliage_leaves": 24, "main_flower_leaves": 8, "under_flower_leaves": 8,
                     "flower_veins": 0, "central_boss": 1, "central_seeds": 18}
    return relief


def definition(stem):
    return {"obj": f"daedalon:models/mesh/{stem}.obj", "mtl": f"daedalon:models/mesh/{stem}.mtl",
            "flip_v": True, "repair_degenerate_uvs": True, "force_uv_projection": True,
            "uv_projection": "axis_stabilized_box", "box_projection_span": 6.0,
            "face_oriented_uvs": True, "smooth_normals": False, "fit_to_block": False,
            "scale": [1., 1., 1.], "translate": [0., 0., 0.],
            "particle": "daedalon:block/statue_spartan_promachos_aganite",
            "materials": {"none": "daedalon:block/statue_spartan_promachos_aganite"}}


def build_style(style):
    """Keep each style's numeric design separate from shared geometry/export."""
    if style == "corinthian":
        return corinthian()
    builder = importlib.import_module("coffered_panel_" + style)
    return getattr(builder, style)(sys.modules[__name__])


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def metrics(relief):
    from collections import Counter
    triangles = [t for group in relief.groups.values() for t in group]
    positions = [tuple(round(v, 7) for v in p) for t in triangles for p, n in t]
    edges = Counter()
    degenerate = 0
    for triangle in triangles:
        points = [tuple(round(v, 7) for v in p) for p, n in triangle]
        if len(set(points)) < 3:
            degenerate += 1
        for a, b in zip(points, points[1:] + points[:1]):
            edges[tuple(sorted((a, b)))] += 1
    boundary = [edge for edge, count in edges.items() if count == 1]
    return {"triangles": len(triangles), "vertices": len(set(positions)),
            "bounds": [[min(p[a] for p in positions) for a in range(3)], [max(p[a] for p in positions) for a in range(3)]],
            "degenerate_faces": degenerate, "nonmanifold_edges": sum(count > 2 for count in edges.values()),
            "embedded_boundary_edges": len(boundary),
            "unburied_boundary_edges": sum(max(a[2], b[2]) > BACKING_FRONT_Z + 1e-7 for a, b in boundary),
            "motifs": relief.motifs, "group_triangle_counts": {name: len(ts) for name, ts in relief.groups.items()}}


def preview(relief, style, directory):
    directory.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    triangles = [t for ts in relief.groups.values() for t in ts]
    vertices = [(p.x, -p.z, p.y) for t in triangles for p, n in t]
    normals = [(n.x, -n.z, n.y) for t in triangles for p, n in t]
    mesh = bpy.data.meshes.new(f"Shared {style} panel")
    mesh.from_pydata(vertices, [], [(i, i + 1, i + 2) for i in range(0, len(vertices), 3)])
    mesh.update()
    for p in mesh.polygons:
        p.use_smooth = True
    mesh.normals_split_custom_set(normals)
    obj = bpy.data.objects.new(f"Shared {style} panel", mesh)
    scene = bpy.context.scene
    scene.collection.objects.link(obj)
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = scene.render.resolution_y = 1000
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    sh = scene.display.shading
    sh.light = "STUDIO"
    sh.studiolight_rotate_z = math.radians(20)
    sh.color_type = "SINGLE"
    sh.single_color = (.62, .64, .66)
    sh.show_shadows = sh.show_cavity = True
    sh.cavity_type = "WORLD"
    sh.show_object_outline = False
    sh.background_type = "WORLD"
    scene.world.color = (.075, .085, .1)
    scene.view_settings.view_transform = "Standard"
    camdata = bpy.data.cameras.new("Measured relief preview")
    camdata.type = "ORTHO"
    camdata.ortho_scale = 1.16
    cam = bpy.data.objects.new("Measured relief preview", camdata)
    scene.collection.objects.link(cam)
    scene.camera = cam
    target = Vector((.5, -.045, .5))
    for view, offset in (("front", (0, -2, .04)), ("oblique", (.82, -2, .625))):
        cam.location = target + Vector(offset)
        cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = str(directory / f"{style}-panel-{view}.png")
        bpy.ops.render.render(write_still=True)


def arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--styles", nargs="+", choices=tuple(REFERENCES), required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--preview-dir", type=Path)
    parser.add_argument("--reference-root", type=Path)
    parser.add_argument("--blender", type=Path)
    return parser.parse_args(argv)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if bpy is not None and "--" in sys.argv else sys.argv[1:]
    args = arguments(argv)
    if bpy is None:
        blender = args.blender or Path(os.environ.get("DAEDALON_BLENDER", DEFAULT_BLENDER))
        command = [str(blender), "--background", "--factory-startup", "--disable-autoexec", "--python-exit-code", "1", "-t", "4", "--python", str(Path(__file__).resolve()), "--", *argv]
        return subprocess.run(command, check=False).returncode
    if tuple(bpy.app.version) != BLENDER_VERSION or bpy.app.build_hash.decode() != BLENDER_HASH:
        raise RuntimeError("Use locked Blender 5.2.0 build fbe6228777e7")
    from prepare_corinthian_frieze import export
    args.output_dir.mkdir(parents=True, exist_ok=True)
    records = []
    for style in dict.fromkeys(args.styles):
        reference, expected_sha = REFERENCES[style]
        reference_path = args.reference_root / reference if args.reference_root else None
        if reference_path and sha256(reference_path) != expected_sha:
            raise ValueError(f"Reference source hash differs: {reference}")
        relief = build_style(style)
        measured = metrics(relief)
        if measured["triangles"] > MAX_TRIANGLES or measured["degenerate_faces"] or measured["unburied_boundary_edges"] or measured["nonmanifold_edges"]:
            raise ValueError(f"Authored mesh safety failed: {measured}")
        stem = style + "_panel"
        export(args.output_dir / f"{stem}.obj", relief.groups, f"{style.title()} coffered panel; simplified numeric authorship with Oliver")
        (args.output_dir / f"{stem}.mtl").write_text("newmtl none\nKd 1.0 1.0 1.0\n", encoding="utf-8", newline="\n")
        write_json(args.output_dir / f"{stem}.json", definition(stem))
        if args.preview_dir:
            preview(relief, style, args.preview_dir)
        if reference_path and sha256(reference_path) != expected_sha:
            raise ValueError("Reference source changed during preparation")
        records.append({"style": style, "mesh": stem, "mesh_name": stem, "family": style + "_wall_panel",
                        "source_index": int(reference.split()[-1].split(".")[0]),
                        "triangles": measured["triangles"], "bounds": measured["bounds"],
                        "collision_depth": math.ceil(measured["bounds"][1][2] * 1000) / 1000,
                        "source_name": reference, "source_sha256": expected_sha,
                        "source_hash_verified": reference_path is not None, "source_files_modified": False,
                        "provenance": "Oliver's own Meshy creation supplied as visual reference; simplified geometry authored from numeric parameters with Codex assistance at Oliver's direction. No source mesh copied.",
                        "geometry_provenance": "Simplified geometry authored directly from numeric parameters with Codex assistance at Oliver's direction; source Meshy mesh not copied or decimated.",
                        "geometry": measured,
                        "preparation": {"tool": "tools/author_coffered_panels.py", "blender_version": "5.2.0", "blender_build_hash": BLENDER_HASH,
                                        "backing_front_z": BACKING_FRONT_Z, "embedded_boundary_z": EMBEDDED_Z,
                                        "backing_objects": [name for name in relief.groups if name.startswith("backing")],
                                        "closure": "Closed complete backing. Open relief skins omit only buried bases, at z=0.015 below the minimum backing front z=0.016. Explicitly closed ornaments remain watertight.",
                                        "normal_policy": "Each numeric builder supplies prepared sharp moulding normals and smooth or analytic ornament corner normals; runtime normal smoothing disabled.",
                                        "shared_geometry": True, "front": "+Z", "up": "+Y", "support_pivot": [.5, .5, 0],
                                        "runtime_sizes_m": [1, 2, 3], "mounts": ["wall", "ceiling"], "maximum_triangles": MAX_TRIANGLES,
                                        "authored_uvs": False, "box_projection_span": 6.0},
                        "outputs": {f"{stem}.{ext}": sha256(args.output_dir / f"{stem}.{ext}") for ext in ("obj", "mtl", "json")}})
    write_json(args.evidence, {"models": records})
    print("COFFERED_PANEL_METRICS " + json.dumps({"models": [{"style": r["style"], "triangles": r["geometry"]["triangles"], "bounds": r["geometry"]["bounds"], "outputs": r["outputs"]} for r in records]}), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
