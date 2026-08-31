#!/usr/bin/env python3
"""Prepare one Meshy finial, plinth, or decor object for Daedalon.

The supplied Blender file is opened read-only. Its single mesh is triangulated,
collapse-decimated to the requested face budget, exported Y-up with one
``none`` material and no authored UV layer, and measured for the locked batch
evidence. Daedalon supplies the bounded runtime UV projection.
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
MODEL_NAME = re.compile(
    r"(?:finial_[a-z0-9_]+|monument_[a-z0-9_]+|basin_[a-z0-9_]+|fountain_[a-z0-9_]+|plinth_[a-z0-9_]+)\Z"
)


def arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--target-faces", type=int, default=6000)
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--blender", type=Path, default=None)
    result = parser.parse_args(argv)
    if not MODEL_NAME.fullmatch(result.name):
        parser.error(
            "--name must be finial_, monument_, basin_, fountain_, or plinth_ plus lowercase underscores"
        )
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
            "--python-exit-code",
            "1",
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
            "decor mesh preparation is locked to Blender 5.2.0 build "
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
    for pass_index in range(4):
        remaining_faces = len(obj.data.polygons)
        if remaining_faces <= round(args.target_faces * 1.02):
            break
        modifier = obj.modifiers.new(
            "Daedalon target faces" if pass_index == 0 else f"Daedalon target faces follow-up {pass_index}",
            "DECIMATE",
        )
        modifier.decimate_type = "COLLAPSE"
        modifier.ratio = min(1.0, args.target_faces / remaining_faces)
        modifier.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=modifier.name)
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
    largest_span = max(span.x, span.y, span.z)
    if largest_span <= 0.0:
        raise RuntimeError("decor mesh has no measurable span")
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
    preview = output_dir / f"{args.name}_preview.png"
    if args.preview:
        render_preview(obj, preview)
    metrics = {
        "name": args.name,
        "source": str(source),
        "source_sha256": sha256(source),
        "source_faces": source_faces,
        "source_triangles": source_triangles,
        "source_vertices": source_vertices,
        "source_span": [round(span.x, 9), round(span.z, 9), round(span.y, 9)],
        "normalised_span": [
            round(span.x / largest_span, 9),
            round(span.z / largest_span, 9),
            round(span.y / largest_span, 9),
        ],
        "output_obj": str(output_obj),
        "output_obj_sha256": sha256(output_obj),
        "output_mtl": str(output_mtl),
        "output_mtl_sha256": sha256(output_mtl),
        "output_faces": len(obj.data.polygons),
        "output_vertices": len(obj.data.vertices),
        "target_faces": args.target_faces,
        "preview": str(preview) if args.preview else None,
        "seconds": round(time.monotonic() - started, 3),
        "blender_version": bpy.app.version_string,
        "blender_build_hash": build_hash,
    }
    print("DECOR_MESH_METRICS " + json.dumps(metrics, sort_keys=True), flush=True)
    return 0


def main() -> int:
    argv = forwarded_arguments() if bpy is not None else sys.argv[1:]
    args = arguments(argv)
    if bpy is None:
        return launch_blender(args, sys.argv[1:])
    return prepare(args)


if __name__ == "__main__":
    raise SystemExit(main())
