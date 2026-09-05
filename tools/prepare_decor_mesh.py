#!/usr/bin/env python3
"""Prepare one Meshy finial, plinth, or decor object for Daedalon.

The supplied Blender or OBJ file is opened read-only. Its single mesh is
triangulated, kept intact when already within the requested face budget (or
collapse-decimated only when above it), exported Y-up with one ``none``
material and no authored UV layer, and measured for the locked batch evidence.
Daedalon supplies the bounded runtime UV projection.
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
    r"(?:finial_[a-z0-9_]+|monument_[a-z0-9_]+|basin_[a-z0-9_]+|fountain_[a-z0-9_]+|plinth_[a-z0-9_]+|(?:exedra|hedra)_(?:[234]m|item))\Z"
)


def arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--target-faces", type=int, default=15000)
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--width-meters", type=float)
    parser.add_argument("--height-meters", type=float)
    parser.add_argument("--stretch-middle-meters", type=float,
                        help="Stretch only this central width after uniform height sizing; translate the ends intact")
    parser.add_argument("--proportional-depth", action="store_true",
                        help="Scale depth with width, preserving the source footprint proportions")
    parser.add_argument("--save-blend", action="store_true")
    parser.add_argument("--blender", type=Path, default=None)
    result = parser.parse_args(argv)
    if not MODEL_NAME.fullmatch(result.name):
        parser.error(
            "--name must be finial_, monument_, basin_, fountain_, plinth_, or exedra_/hedra_2m/3m/4m/item"
        )
    if result.target_faces < 100:
        parser.error("--target-faces must be at least 100")
    if (result.width_meters is None) != (result.height_meters is None):
        parser.error("--width-meters and --height-meters must be supplied together")
    if result.proportional_depth and result.width_meters is None:
        parser.error("--proportional-depth requires --width-meters and --height-meters")
    if result.stretch_middle_meters is not None and (
        result.width_meters is None or result.stretch_middle_meters <= 0 or result.proportional_depth
    ):
        parser.error("--stretch-middle-meters requires dimensions and cannot combine with proportional depth")
    if result.width_meters is not None and not (
        0.0 < result.width_meters <= 16.0 and 0.0 < result.height_meters <= 16.0
    ):
        parser.error("dimensions must be positive and no greater than 16 metres")
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
            "--disable-autoexec",
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
    if source.suffix.lower() == ".blend":
        bpy.ops.wm.open_mainfile(filepath=str(source))
    elif source.suffix.lower() == ".obj":
        bpy.ops.object.select_all(action="SELECT")
        bpy.ops.object.delete(use_global=False)
        bpy.ops.wm.obj_import(filepath=str(source))
    else:
        raise RuntimeError("decor mesh source must be a .blend or .obj file")
    objects = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if len(objects) != 1:
        raise RuntimeError(f"expected exactly one mesh object; found {len(objects)}")
    obj = objects[0]
    original_minimum, original_maximum = bounds(mesh_points(obj))
    original_span = original_maximum - original_minimum
    if args.width_meters is not None:
        # Blender is Z-up. Optionally preserve the source X/Y footprint while
        # sizing height independently. Ground and centre the exported vertices.
        height_scale = args.height_meters / original_span.z
        width_scale = args.width_meters / original_span.x
        depth_scale = width_scale if args.proportional_depth else height_scale
        middle = args.stretch_middle_meters
        extra_width = args.width_meters - original_span.x * height_scale
        if middle is not None and not (0 < middle < original_span.x * height_scale and middle + extra_width > 0):
            raise RuntimeError("central stretch band must fit the sized source and retain positive width")
        center = (original_minimum + original_maximum) * 0.5
        points = mesh_points(obj)
        normal_matrix = obj.matrix_world.to_3x3().inverted().transposed()
        source_normals = [normal_matrix @ normal.vector for normal in obj.data.corner_normals]
        obj.matrix_world.identity()
        for vertex, point in zip(obj.data.vertices, points):
            x = (point.x - center.x) * width_scale
            if middle is not None:
                x = (point.x - center.x) * height_scale
                x += max(-0.5, min(0.5, x / middle)) * extra_width
            vertex.co = (
                x,
                (point.y - center.y) * depth_scale,
                (point.z - original_minimum.z) * height_scale,
            )
        obj.data.update()
        # Width changes are non-uniform: normals need the inverse transpose,
        # otherwise the imported custom shading still describes the old width.
        corrected_normals = []
        for loop, normal in zip(obj.data.loops, source_normals):
            local_width_scale = width_scale
            if middle is not None:
                x = (points[loop.vertex_index].x - center.x) * height_scale
                local_width_scale = height_scale * (1 + extra_width / middle if abs(x) < middle / 2 else 1)
            corrected_normals.append(Vector((normal.x / local_width_scale,
                                             normal.y / depth_scale,
                                             normal.z / height_scale)).normalized())
        obj.data.normals_split_custom_set(corrected_normals)
    source_faces = len(obj.data.polygons)
    source_vertices = len(obj.data.vertices)
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
    if args.save_blend:
        bpy.context.preferences.filepaths.save_version = 0
        bpy.context.scene.unit_settings.system = "METRIC"
        bpy.context.scene.unit_settings.scale_length = 1.0
        bpy.ops.wm.save_as_mainfile(filepath=str(output_dir / f"{args.name}.blend"))
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
        "original_source_span": [round(original_span.x, 9), round(original_span.z, 9), round(original_span.y, 9)],
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
        "stretch_middle_meters": args.stretch_middle_meters,
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
