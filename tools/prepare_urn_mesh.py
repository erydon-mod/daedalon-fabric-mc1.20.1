#!/usr/bin/env python3
"""Decimate one Meshy Blender urn into a Daedalon-ready OBJ/MTL pair.

The source .blend is never modified.  The exported mesh keeps Blender's Y-up
OBJ orientation, one material named ``none``, smooth shading, and no authored
UV layer; Daedalon's established urn projection supplies the runtime UVs.
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
MODEL_NAME = re.compile(r"urn_[a-z0-9_]+\Z")


def arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--target-faces", type=int, default=3200)
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--blender", type=Path, default=None)
    result = parser.parse_args(argv)
    if not MODEL_NAME.fullmatch(result.name):
        parser.error("--name must use the form urn_<lowercase_shape>")
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


def add_area_light(scene, name: str, location, center, energy: float, size: float) -> None:
    light_data = bpy.data.lights.new(name, "AREA")
    light_data.energy = energy
    light_data.shape = "DISK"
    light_data.size = size
    light = bpy.data.objects.new(name, light_data)
    scene.collection.objects.link(light)
    light.location = Vector(location)
    light.rotation_euler = (center - light.location).to_track_quat("-Z", "Y").to_euler()


def render_preview(obj, output: Path) -> None:
    scene = bpy.context.scene
    points = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    minimum = Vector(tuple(min(point[index] for point in points) for index in range(3)))
    maximum = Vector(tuple(max(point[index] for point in points) for index in range(3)))
    center = (minimum + maximum) * 0.5
    size = maximum - minimum
    for existing in list(scene.objects):
        if existing.type in {"CAMERA", "LIGHT"}:
            bpy.data.objects.remove(existing, do_unlink=True)
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 512
    scene.render.resolution_y = 512
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.world.color = (0.025, 0.025, 0.025)
    camera_data = bpy.data.cameras.new("Preview Camera")
    camera = bpy.data.objects.new("Preview Camera", camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(size.x, size.z) * 1.25
    distance = max(size.length, 1.0) * 3.0
    camera.location = Vector((center.x, center.y - distance, center.z))
    camera.rotation_euler = (center - camera.location).to_track_quat("-Z", "Y").to_euler()
    add_area_light(
        scene,
        "Key",
        (center.x - distance * 0.6, center.y - distance * 0.8, center.z + distance * 0.7),
        center,
        1300.0,
        distance * 0.65,
    )
    add_area_light(
        scene,
        "Fill",
        (center.x + distance * 0.7, center.y - distance * 0.4, center.z + distance * 0.15),
        center,
        700.0,
        distance * 0.75,
    )
    add_area_light(
        scene,
        "Rim",
        (center.x, center.y + distance * 0.6, center.z + distance * 0.5),
        center,
        900.0,
        distance * 0.55,
    )
    scene.render.filepath = str(output)
    bpy.ops.render.render(write_still=True)


def prepare(args: argparse.Namespace) -> int:
    build_hash = bpy.app.build_hash.decode("ascii")
    if tuple(bpy.app.version) != REQUIRED_BLENDER_VERSION or build_hash != REQUIRED_BLENDER_BUILD_HASH:
        raise RuntimeError(
            "urn preparation is locked to Blender 5.2.0 build "
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
    modifier = obj.modifiers.new("Daedalon target faces", "DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    modifier.ratio = args.target_faces / source_faces
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
        "source": str(source),
        "source_sha256": sha256(source),
        "source_faces": source_faces,
        "source_vertices": source_vertices,
        "output_obj": str(output_obj),
        "output_obj_sha256": sha256(output_obj),
        "output_faces": len(obj.data.polygons),
        "output_vertices": len(obj.data.vertices),
        "target_faces": args.target_faces,
        "preview": str(preview) if args.preview else None,
        "seconds": round(time.monotonic() - started, 3),
        "blender_version": bpy.app.version_string,
        "blender_build_hash": build_hash,
    }
    print("URN_MESH_METRICS " + json.dumps(metrics, sort_keys=True), flush=True)
    return 0


def main() -> int:
    argv = forwarded_arguments() if bpy is not None else sys.argv[1:]
    args = arguments(argv)
    if bpy is None:
        return launch_blender(args, sys.argv[1:])
    return prepare(args)


if __name__ == "__main__":
    raise SystemExit(main())
