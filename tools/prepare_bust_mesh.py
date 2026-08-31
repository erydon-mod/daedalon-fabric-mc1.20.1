#!/usr/bin/env python3
"""Prepare one Meshy classical bust for Daedalon.

The supplied Blender file is opened read-only. Its single mesh is triangulated,
collapse-decimated, exported with one ``none`` material and no authored UVs,
then measured as a fitted three-block source mesh. The JSON metrics include the
support point and eight collision bands used by ``BustBlock``.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Sequence

try:
    import bpy  # type: ignore[import-not-found]
    from mathutils import Vector  # type: ignore[import-not-found]
except ModuleNotFoundError:
    bpy = None
    Vector = None


DEFAULT_BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
REQUIRED_BLENDER_VERSION = (5, 2, 0)
REQUIRED_BLENDER_BUILD_HASH = "fbe6228777e7"
MODEL_NAME = re.compile(r"bust_[a-z0-9_]+\Z")
COLLISION_LAYERS = 8
COLLISION_PADDING = 0.02


def arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--target-faces", type=int, default=6000)
    parser.add_argument("--blender", type=Path, default=None)
    result = parser.parse_args(argv)
    if not MODEL_NAME.fullmatch(result.name):
        parser.error("--name must use the form bust_<lowercase_subject>")
    if result.target_faces < 100:
        parser.error("--target-faces must be at least 100")
    return result


def forwarded_arguments() -> list[str]:
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def launch_blender(args: argparse.Namespace, argv: Sequence[str]) -> int:
    blender = args.blender or Path(os.environ.get("DAEDALON_BLENDER", DEFAULT_BLENDER))
    if not blender.is_file():
        raise FileNotFoundError(f"Blender 5.2 was not found at {blender}")
    forwarded = list(argv)
    if "--blender" in forwarded:
        index = forwarded.index("--blender")
        del forwarded[index : index + 2]
    return subprocess.run(
        [
            str(blender),
            "--background",
            "--factory-startup",
            "--python",
            str(Path(__file__).resolve()),
            "--",
            *forwarded,
        ],
        check=False,
    ).returncode


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def mesh_points(obj) -> list:
    return [obj.matrix_world @ vertex.co for vertex in obj.data.vertices]


def bounds(points: list) -> tuple:
    minimum = Vector(tuple(min(point[index] for point in points) for index in range(3)))
    maximum = Vector(tuple(max(point[index] for point in points) for index in range(3)))
    return minimum, maximum


def fitted_points(obj, points: list) -> list[tuple[float, float, float]]:
    minimum, maximum = bounds(points)
    # OBJ export maps Blender X/Z/Y to runtime X/Y/Z (the final Z sign does
    # not affect measured extents). This exactly mirrors fit_to_block + scale 3.
    largest_span = max(
        maximum.x - minimum.x,
        maximum.z - minimum.z,
        maximum.y - minimum.y,
    )
    if largest_span <= 0.0:
        raise RuntimeError("bust bounds have no finite size")
    center_x = (minimum.x + maximum.x) * 0.5
    center_z = (minimum.y + maximum.y) * 0.5
    return [
        (
            (point.x - center_x) / largest_span * 3.0 + 0.5,
            (point.z - minimum.z) / largest_span * 3.0,
            (center_z - point.y) / largest_span * 3.0 + 0.5,
        )
        for point in points
    ]


def collision_measurements(obj, points: list) -> dict[str, object]:
    fitted = fitted_points(obj, points)
    bottom_limit = min(point[1] for point in fitted) + 3.0 * 0.08
    bottom = [point for point in fitted if point[1] <= bottom_limit]
    if len(bottom) < 4:
        bottom = sorted(fitted, key=lambda point: point[1])[: max(4, len(fitted) // 50)]
    support_x = (min(point[0] for point in bottom) + max(point[0] for point in bottom)) * 0.5
    support_z = (min(point[2] for point in bottom) + max(point[2] for point in bottom)) * 0.5

    boxes: list[list[float]] = []
    layer_height = 3.0 / COLLISION_LAYERS
    for layer in range(COLLISION_LAYERS):
        min_y = layer * layer_height
        max_y = (layer + 1) * layer_height
        indices: set[int] = set()
        for polygon in obj.data.polygons:
            polygon_y = [fitted[index][1] for index in polygon.vertices]
            if max(polygon_y) + 1.0e-7 < min_y or min(polygon_y) - 1.0e-7 > max_y:
                continue
            indices.update(polygon.vertices)
        if not indices:
            indices = {
                index
                for index, point in enumerate(fitted)
                if min_y <= point[1] <= max_y
            }
        if not indices:
            raise RuntimeError(f"collision layer {layer} has no geometry")
        xs = [fitted[index][0] for index in indices]
        zs = [fitted[index][2] for index in indices]
        boxes.append(
            [
                round(min(xs) - COLLISION_PADDING, 6),
                round(min_y, 6),
                round(min(zs) - COLLISION_PADDING, 6),
                round(max(xs) + COLLISION_PADDING, 6),
                round(max_y, 6),
                round(max(zs) + COLLISION_PADDING, 6),
            ]
        )
    return {
        "model_support_center_x": round(support_x, 7),
        "model_support_center_z": round(support_z, 7),
        "collision_boxes": boxes,
    }


def prepare(args: argparse.Namespace) -> int:
    build_hash = bpy.app.build_hash.decode("ascii")
    if tuple(bpy.app.version) != REQUIRED_BLENDER_VERSION or build_hash != REQUIRED_BLENDER_BUILD_HASH:
        raise RuntimeError(
            "bust preparation is locked to Blender 5.2.0 build "
            f"{REQUIRED_BLENDER_BUILD_HASH}; found {bpy.app.version_string} build {build_hash}"
        )
    source = args.input.resolve()
    if not source.is_file():
        raise FileNotFoundError(source)
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    bpy.ops.wm.open_mainfile(filepath=str(source))
    objects = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if len(objects) != 1:
        raise RuntimeError(f"expected exactly one mesh object; found {len(objects)}")
    obj = objects[0]
    source_faces = len(obj.data.polygons)
    source_vertices = len(obj.data.vertices)
    if source_faces <= args.target_faces:
        raise RuntimeError(
            f"source already has {source_faces} faces, not more than target {args.target_faces}"
        )

    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    triangulate = obj.modifiers.new("Daedalon source triangulation", "TRIANGULATE")
    bpy.ops.object.modifier_apply(modifier=triangulate.name)
    source_triangles = len(obj.data.polygons)
    modifier = obj.modifiers.new("Daedalon target faces", "DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    modifier.ratio = args.target_faces / source_triangles
    modifier.use_collapse_triangulate = True
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    for pass_index in range(1, 4):
        remaining_faces = len(obj.data.polygons)
        if remaining_faces <= round(args.target_faces * 1.02):
            break
        follow_up = obj.modifiers.new(f"Daedalon target faces follow-up {pass_index}", "DECIMATE")
        follow_up.decimate_type = "COLLAPSE"
        follow_up.ratio = min(1.0, args.target_faces / remaining_faces)
        follow_up.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=follow_up.name)

    for polygon in obj.data.polygons:
        polygon.use_smooth = True
    obj.name = args.name
    obj.data.name = args.name
    obj.data.materials.clear()
    material = bpy.data.materials.get("none") or bpy.data.materials.new("none")
    material.diffuse_color = (0.58, 0.60, 0.64, 1.0)
    material.roughness = 0.38
    obj.data.materials.append(material)

    points = mesh_points(obj)
    minimum, maximum = bounds(points)
    placement = collision_measurements(obj, points)
    output_obj = output_dir / f"{args.name}.obj"
    bpy.ops.wm.obj_export(
        filepath=str(output_obj),
        check_existing=False,
        forward_axis="NEGATIVE_Z",
        up_axis="Y",
        apply_modifiers=True,
        export_selected_objects=True,
        export_uv=False,
        export_normals=True,
        export_colors=False,
        export_materials=True,
        export_triangulated_mesh=True,
        export_object_groups=False,
        export_material_groups=False,
        export_vertex_groups=False,
        export_smooth_groups=False,
    )
    output_mtl = output_dir / f"{args.name}.mtl"
    if not output_mtl.is_file():
        raise RuntimeError(f"Blender did not export {output_mtl.name}")
    metrics = {
        "name": args.name,
        "source": str(source),
        "source_sha256": sha256(source),
        "source_faces": source_faces,
        "source_triangles": source_triangles,
        "source_vertices": source_vertices,
        "source_bounds": [
            [round(minimum.x, 9), round(minimum.z, 9), round(minimum.y, 9)],
            [round(maximum.x, 9), round(maximum.z, 9), round(maximum.y, 9)],
        ],
        "output_obj": str(output_obj),
        "output_obj_sha256": sha256(output_obj),
        "output_mtl_sha256": sha256(output_mtl),
        "output_faces": len(obj.data.polygons),
        "output_vertices": len(obj.data.vertices),
        "target_faces": args.target_faces,
        "placement": placement,
        "seconds": round(time.monotonic() - started, 3),
        "blender_version": bpy.app.version_string,
        "blender_build_hash": build_hash,
    }
    print("BUST_MESH_METRICS " + json.dumps(metrics, sort_keys=True), flush=True)
    return 0


def main() -> int:
    argv = forwarded_arguments() if bpy is not None else sys.argv[1:]
    args = arguments(argv)
    if bpy is None:
        return launch_blender(args, sys.argv[1:])
    return prepare(args)


if __name__ == "__main__":
    raise SystemExit(main())
