#!/usr/bin/env python3
"""Export the four approved meshes from the saved coffered-panel review scene.

Run with Blender in background mode:
blender -b --factory-startup --disable-autoexec --python-exit-code 1 \
    --python tools/export_approved_panel_review.py -- REVIEW.blend OUTPUT_DIRECTORY
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import sys

import bpy
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

from prepare_corinthian_frieze import export


PANELS = {
    "corinthian": ("Corinthian · Final relief · ", 5996, 0.088),
    "byzantine": ("Byzantine · Revised · ", 6000, 0.075),
    "georgian": ("Georgian · Proposal · ", 5984, 0.055),
    "renaissance": ("Renaissance · Proposal · ", 5988, 0.089),
}


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def runtime_axes(values: np.ndarray) -> np.ndarray:
    # The review arranges the local meshes in a Z-up Blender scene. Ignore
    # display translations and restore the established OBJ +Y up, +Z front.
    return np.stack((values[:, 0], values[:, 2], -values[:, 1]), axis=1)


def components(vertices: np.ndarray, triangles: np.ndarray) -> tuple[dict[str, list[int]], np.ndarray]:
    """Keep closed rear shells distinct from overlapping decorative shells."""
    _, welded = np.unique(np.round(vertices, 7), axis=0, return_inverse=True)
    faces = welded[triangles]
    incidence = defaultdict(list)
    for face_id, (a, b, c) in enumerate(faces):
        for edge in ((a, b), (b, c), (c, a)):
            incidence[tuple(sorted(edge))].append((face_id, edge[0] < edge[1]))
    neighbours = defaultdict(list)
    for edge, incident in incidence.items():
        if len(incident) > 2:
            raise ValueError(f"Nonmanifold edge {edge} on faces {incident}")
        if len(incident) == 2:
            (x, forward_x), (y, forward_y) = incident
            flip = forward_x == forward_y
            neighbours[x].append((y, flip))
            neighbours[y].append((x, flip))
    groups = {}
    flips = np.full(len(faces), -1, dtype=np.int8)
    for start in range(len(faces)):
        if flips[start] >= 0:
            continue
        pending = [start]
        flips[start] = 0
        found = []
        while pending:
            face = pending.pop()
            found.append(face)
            for neighbour, flip in neighbours[face]:
                required = int(flips[face]) ^ int(flip)
                if flips[neighbour] < 0:
                    flips[neighbour] = required
                    pending.append(neighbour)
                elif flips[neighbour] != required:
                    raise ValueError(f"Non-orientable relief at face {neighbour}")
        # Preserve the reviewed winding of the majority of each shell. This
        # corrects isolated reversed seams and end caps without moving vertices.
        if np.count_nonzero(flips[found]) * 2 > len(found):
            flips[found] ^= 1
        z = vertices[triangles[found], 2]
        prefix = "backing" if np.any(np.max(z, axis=1) < 1e-7) else "relief"
        groups[f"{prefix}_{len(groups):03}"] = sorted(found)
    if not any(name.startswith("backing") for name in groups):
        raise ValueError("No rear support face")
    return groups, flips.astype(bool)


def export_one(style: str, output: Path) -> dict:
    name, count, depth = PANELS[style]
    reviewed = [o for o in bpy.context.scene.objects if o.type == "MESH" and o.name.startswith(name)]
    if len(reviewed) != 1:
        raise ValueError(f"Expected exactly one approved {style} mesh, got {[o.name for o in reviewed]}")
    obj = reviewed[0]
    mesh = obj.data
    if not mesh.has_custom_normals or any(len(p.vertices) != 3 for p in mesh.polygons):
        raise ValueError(f"Expected triangulated geometry and prepared corner normals for {style}")
    mesh.calc_loop_triangles()
    if len(mesh.loop_triangles) != count or count > 6000:
        raise ValueError(f"Unexpected reviewed triangle count for {style}: {len(mesh.loop_triangles)}")
    positions = np.empty((len(mesh.vertices), 3), np.float32)
    indices = np.empty((count, 3), np.int32)
    loops = np.empty((count, 3), np.int32)
    normals = np.empty((len(mesh.loops), 3), np.float32)
    mesh.vertices.foreach_get("co", positions.ravel())
    mesh.loop_triangles.foreach_get("vertices", indices.ravel())
    mesh.loop_triangles.foreach_get("loops", loops.ravel())
    mesh.corner_normals.foreach_get("vector", normals.ravel())
    positions = runtime_axes(positions).astype(np.float64)
    positions[positions == 0] = 0
    normals = runtime_axes(normals).astype(np.float64)
    normals[normals == 0] = 0
    low, high = positions.min(0), positions.max(0)
    if np.any(low < -1e-7) or np.any(high > np.array([1, 1, depth]) + 1e-7) or abs(low[2]) > 1e-7:
        raise ValueError(f"Reviewed {style} exceeds existing mount: {low}, {high}")
    groups, flips = components(positions, indices)
    indices[flips] = indices[flips][:, [0, 2, 1]]
    loops[flips] = loops[flips][:, [0, 2, 1]]
    records = {}
    normal_repairs = 0
    for key, faces in groups.items():
        records[key] = []
        for f in faces:
            points = positions[indices[f]]
            g = np.cross(points[1] - points[0], points[2] - points[0])
            area = np.linalg.norm(g)
            if area < 1e-12:
                raise ValueError(f"Degenerate geometry in {style}, face {f}")
            g /= area
            row = []
            for k in range(3):
                corner = normals[loops[f,k]]
                # The OBJ renderer expects each normal to remain in its face's
                # outward hemisphere. Keep valid reviewed corners unchanged.
                if np.dot(corner, g) < 0.00001:
                    corner = g
                    normal_repairs += 1
                row.append((points[k], corner))
            records[key].append(row)
    file = output / f"{style}_panel.obj"
    export(file, records, f"approved {style.title()} coffered panel")
    exported_vertices = sum(line.startswith("v ") for line in file.read_text(encoding="utf-8").splitlines())
    return {
        "style": style,
        "review_object": obj.name,
        "vertices": exported_vertices,
        "triangles": count,
        "bounds": [low.tolist(), high.tolist()],
        "collision_depth": depth,
        "group_triangle_counts": {name: len(items) for name,items in groups.items()},
        "backing_names": [name for name in groups if name.startswith("backing")],
        "reoriented_faces": int(np.count_nonzero(flips)),
        "normal_hemisphere_repairs": normal_repairs,
        "object_sha256": sha256(file),
    }


def main() -> None:
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("review", type=Path)
    args.add_argument("output", type=Path)
    args.add_argument("--styles", nargs="+", choices=tuple(PANELS), default=list(PANELS))
    ns = args.parse_args(sys.argv[sys.argv.index("--") + 1:])
    bpy.ops.wm.open_mainfile(filepath=str(ns.review.resolve()), load_ui=False, use_scripts=False)
    if bpy.data.libraries:
        raise ValueError("Use the saved self-contained review file")
    ns.output.mkdir(parents=True, exist_ok=True)
    records = []
    for style in ns.styles:
        record = export_one(style, ns.output)
        records.append(record)
        print(style, record["triangles"], record["bounds"], record["reoriented_faces"], flush=True)
    evidence = {
        "review_file": ns.review.name,
        "review_sha256": sha256(ns.review),
        "blender_version": bpy.app.version_string,
        "blender_build_hash": bpy.app.build_hash.decode(),
        "models": records,
    }
    (ns.output / "panel-review-export.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
