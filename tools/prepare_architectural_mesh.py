#!/usr/bin/env python3
"""Prepare one Meshy corbel or capital for Daedalon.

The supplied Blender file is opened read-only.  Its single mesh is collapse-
decimated, exported with one ``none`` material and no authored UV layer, and
measured for the deterministic runtime transform.  Corbels are fitted as a
three-block-high source mesh.  At runtime the large state is uniformly fitted
to one block of width, its height follows the source proportions, and its top
is anchored at Minecraft Y=16.
Capitals record a horizontal scale that fits their lower rim over ERYDON's
circular-column shaft while preserving the model's original proportions.
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
from collections import defaultdict
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
MODEL_NAME = re.compile(r"(?:corbel|capital)_[a-z0-9_]+\Z")
CAPITAL_JOIN_DIAMETER = 11.5 / 16.0


def arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--target-faces", type=int, default=6000)
    parser.add_argument(
        "--flatten-capital-top",
        action="store_true",
        help="lock the broad upper deck to its source horizontal plane after decimation",
    )
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--blender", type=Path, default=None)
    result = parser.parse_args(argv)
    if not MODEL_NAME.fullmatch(result.name):
        parser.error("--name must begin corbel_ or capital_ and use lowercase underscores")
    if result.target_faces < 100:
        parser.error("--target-faces must be at least 100")
    if result.flatten_capital_top and not result.name.startswith("capital_"):
        parser.error("--flatten-capital-top is only valid for capital models")
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
    command = [
        str(blender),
        "--background",
        "--factory-startup",
        "--python",
        str(Path(__file__).resolve()),
        "--",
        *forwarded,
    ]
    return subprocess.run(command, check=False).returncode


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


def capital_measurements(points: list) -> dict[str, object]:
    minimum, maximum = bounds(points)
    span = maximum - minimum
    lower_limit = minimum.z + span.z * 0.08
    lower = [point for point in points if point.z <= lower_limit]
    if len(lower) < 8:
        lower_limit = minimum.z + span.z * 0.15
        lower = [point for point in points if point.z <= lower_limit]
    if len(lower) < 8:
        raise RuntimeError("capital lower rim has too few vertices to measure")
    join_x = max(point.x for point in lower) - min(point.x for point in lower)
    join_y = max(point.y for point in lower) - min(point.y for point in lower)
    largest_span = max(span.x, span.y, span.z)
    join_diameter = max(join_x, join_y)
    if min(span.z, largest_span, join_diameter) <= 0.0:
        raise RuntimeError("capital bounds are not finite three-dimensional geometry")
    horizontal_scale = CAPITAL_JOIN_DIAMETER * largest_span / join_diameter
    vertical_scale = largest_span / span.z
    transformed_span = {
        "x": span.x / largest_span * horizontal_scale,
        "y": 1.0,
        "z": span.y / largest_span * horizontal_scale,
    }
    return {
        "join_sample_vertices": len(lower),
        "join_span_x": round(join_x, 9),
        "join_span_z": round(join_y, 9),
        "target_join_diameter": CAPITAL_JOIN_DIAMETER,
        "definition_scale": [
            round(horizontal_scale, 9),
            round(vertical_scale, 9),
            round(horizontal_scale, 9),
        ],
        "transformed_span": {key: round(value, 9) for key, value in transformed_span.items()},
    }


def capital_top_plane(obj) -> dict[str, float | int]:
    """Find the broad source top deck by projected horizontal surface area."""
    points = mesh_points(obj)
    minimum, maximum = bounds(points)
    height = maximum.z - minimum.z
    if height <= 0.0:
        raise RuntimeError("capital has no measurable vertical span")
    normal_matrix = obj.matrix_world.to_3x3().inverted().transposed()
    bucket_width = height / 400.0
    buckets: dict[int, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0])
    for polygon in obj.data.polygons:
        normal = (normal_matrix @ polygon.normal).normalized()
        center = obj.matrix_world @ polygon.center
        if normal.z < 0.80 or center.z < minimum.z + height * 0.55:
            continue
        weight = polygon.area * normal.z
        bucket = int(round((center.z - minimum.z) / bucket_width))
        values = buckets[bucket]
        values[0] += weight
        values[1] += center.z * weight
        values[2] += 1.0
    if not buckets:
        raise RuntimeError("capital has no broad upward-facing top surface")
    best_bucket, values = max(
        buckets.items(), key=lambda item: (item[1][0], item[0])
    )
    return {
        "plane_z": values[1] / values[0],
        "source_projected_area": values[0],
        "source_polygons": int(values[2]),
        "bucket": best_bucket,
        "source_height": height,
    }


def flatten_capital_top(obj, top: dict[str, float | int]) -> dict[str, float | int]:
    """Flatten only the thin top-deck band; preserve the bevel and ornament below."""
    plane_z = float(top["plane_z"])
    height = float(top["source_height"])
    threshold = plane_z - height * 0.0125
    inverse = obj.matrix_world.inverted()
    flattened = 0
    pre_min = plane_z
    pre_max = plane_z
    for vertex in obj.data.vertices:
        world = obj.matrix_world @ vertex.co
        if world.z < threshold:
            continue
        pre_min = min(pre_min, world.z)
        pre_max = max(pre_max, world.z)
        world.z = plane_z
        vertex.co = inverse @ world
        flattened += 1
    if flattened < 4:
        raise RuntimeError("capital top correction found fewer than four deck vertices")
    obj.data.update()
    return {
        "plane_z": round(plane_z, 9),
        "flattened_vertices": flattened,
        "pre_flatten_range": round(pre_max - pre_min, 9),
        "post_flatten_range": 0.0,
        "source_projected_area": round(float(top["source_projected_area"]), 9),
        "source_polygons": int(top["source_polygons"]),
    }


def render_preview(obj, output: Path) -> None:
    scene = bpy.context.scene
    minimum, maximum = bounds(mesh_points(obj))
    center = (minimum + maximum) * 0.5
    size = maximum - minimum
    for existing in list(scene.objects):
        if existing.type in {"CAMERA", "LIGHT"}:
            bpy.data.objects.remove(existing, do_unlink=True)
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = 512
    scene.render.resolution_y = 512
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.world.color = (0.94, 0.94, 0.94)
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "SINGLE"
    scene.display.shading.single_color = (0.62, 0.64, 0.68)
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    camera_data = bpy.data.cameras.new("Preview Camera")
    camera = bpy.data.objects.new("Preview Camera", camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(size.x, size.z) * 1.25
    distance = max(size.length, 1.0) * 3.0
    camera.location = Vector((center.x, center.y - distance, center.z))
    camera.rotation_euler = (center - camera.location).to_track_quat("-Z", "Y").to_euler()
    scene.render.filepath = str(output)
    bpy.ops.render.render(write_still=True)


def prepare(args: argparse.Namespace) -> int:
    build_hash = bpy.app.build_hash.decode("ascii")
    if tuple(bpy.app.version) != REQUIRED_BLENDER_VERSION or build_hash != REQUIRED_BLENDER_BUILD_HASH:
        raise RuntimeError(
            "architectural mesh preparation is locked to Blender 5.2.0 build "
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
    top_plane = capital_top_plane(obj) if args.flatten_capital_top else None
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
        follow_up = obj.modifiers.new(
            f"Daedalon target faces follow-up {pass_index}", "DECIMATE"
        )
        follow_up.decimate_type = "COLLAPSE"
        follow_up.ratio = min(1.0, args.target_faces / remaining_faces)
        follow_up.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=follow_up.name)
    top_correction = flatten_capital_top(obj, top_plane) if top_plane is not None else None
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
    span = maximum - minimum
    kind = args.name.split("_", 1)[0]
    if kind == "capital":
        placement = capital_measurements(points)
    else:
        transformed_width = 3.0 * span.x / span.z
        transformed_depth = 3.0 * span.y / span.z
        large_runtime_scale = 1.0 / transformed_width
        placement = {
            "definition_scale": [3.0, 3.0, 3.0],
            "transformed_span": {
                "x": round(transformed_width, 9),
                "y": 3.0,
                "z": round(transformed_depth, 9),
            },
            "large_target_width": 1.0,
            "large_runtime_scale": round(large_runtime_scale, 9),
            "large_transformed_span": {
                "x": 1.0,
                "y": round(3.0 * large_runtime_scale, 9),
                "z": round(transformed_depth * large_runtime_scale, 9),
            },
            "top_anchor_y": 1.0,
            "source_top_y": 3.0,
        }

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
    preview = output_dir / f"{args.name}_preview.png"
    if args.preview:
        render_preview(obj, preview)
    metrics = {
        "name": args.name,
        "kind": kind,
        "source": str(source),
        "source_sha256": sha256(source),
        "source_faces": source_faces,
        "source_triangles": source_triangles,
        "source_vertices": source_vertices,
        "source_span": [round(span.x, 9), round(span.z, 9), round(span.y, 9)],
        "output_obj": str(output_obj),
        "output_obj_sha256": sha256(output_obj),
        "output_faces": len(obj.data.polygons),
        "output_vertices": len(obj.data.vertices),
        "target_faces": args.target_faces,
        "placement": placement,
        "top_correction": top_correction,
        "preview": str(preview) if args.preview else None,
        "seconds": round(time.monotonic() - started, 3),
        "blender_version": bpy.app.version_string,
        "blender_build_hash": build_hash,
    }
    print("ARCHITECTURAL_MESH_METRICS " + json.dumps(metrics, sort_keys=True), flush=True)
    return 0


def main() -> int:
    argv = forwarded_arguments() if bpy is not None else sys.argv[1:]
    args = arguments(argv)
    if bpy is None:
        return launch_blender(args, sys.argv[1:])
    return prepare(args)


if __name__ == "__main__":
    raise SystemExit(main())
