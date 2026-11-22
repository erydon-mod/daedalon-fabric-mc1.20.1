#!/usr/bin/env python3
"""Render the actual planter OBJ and production soil's colour/height offline.

Run with ordinary Python and --blender <executable>, DAEDALON_BLENDER, or a
Blender executable on PATH. --width 2 is the pilot; the final view defaults to
3m. Source meshes and textures are read-only inputs. No Minecraft session,
.blend file or installed resource pack is opened or changed. Temporary raw
render passes are removed before the script exits.

Height is real preview geometry, sampled from the production LabPBR _n alpha:
surface_y + (alpha / 255 - 1) * height_depth. The default 0.25m full-range
reference makes alpha 237..255 a maximum depth of 17.65mm, without amplification.
The actual 64x64 files repeat once per metre with the production owner-relative
phase. Height uses periodic bilinear samples; smooth vertex normals approximate
the normal-mapped material's continuous lighting without altering any height
samples. Colour uses nearest sampling. No extra noise, filtering of the source
height, or height normalization is added.
Shader packs choose their own parallax settings; this is an explicit material
reference preview, not a claim about a particular in-game shader.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/daedalon"
EVIDENCE = ROOT / "docs/evidence/anthophoros-fill.json"
PIXELS_PER_METRE = 64
HEIGHT_SAMPLES_PER_TEXEL = 2


def arguments(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--width", type=int, choices=(2, 3, 4), default=3)
    parser.add_argument("--blender", type=Path)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "docs/previews")
    parser.add_argument("--height-depth", type=float, default=.25,
                        help="Metres represented by the complete alpha range 0..255; default .25")
    parser.add_argument("--samples", type=int, default=64)
    parser.add_argument("--render-worker", action="store_true", help=argparse.SUPPRESS)
    result = parser.parse_args(argv)
    if not math.isfinite(result.height_depth) or result.height_depth <= 0:
        parser.error("--height-depth must be a positive finite number")
    if result.samples < 1:
        parser.error("--samples must be positive")
    return result


def source_paths(width: int) -> tuple[dict, Path, Path, Path]:
    profiles = json.loads(EVIDENCE.read_text(encoding="utf-8"))["profiles"]
    profile = next(value for value in profiles if value["width_m"] == width)
    mesh = ASSETS / "models/mesh" / profile["mesh"]
    colour = ASSETS / f"textures/block/anthophoros_earth_{width}m.png"
    normal = colour.with_stem(colour.stem + "_n")
    if hashlib.sha256(mesh.read_bytes()).hexdigest() != profile["mesh_sha256"]:
        raise ValueError("Runtime planter mesh differs from its measured profile")
    return profile, mesh, colour, normal


def hashes(paths: tuple[Path, ...]) -> dict[str, str]:
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}


def create_shell(bpy, path: Path):
    """Retain OBJ triangles and corner normals; only rotate runtime Y-up to Z-up."""
    positions, normals, faces, corner_normals = [], [], [], []
    for line in path.read_text(encoding="utf-8").splitlines():
        words = line.split()
        if not words:
            continue
        if words[0] in {"v", "vn"}:
            x, y, z = map(float, words[1:4])
            (positions if words[0] == "v" else normals).append((x, -z, y))
        elif words[0] == "f":
            face, face_normals = [], []
            for word in words[1:]:
                indices = word.split("/")
                index = int(indices[0])
                face.append(index - 1 if index > 0 else len(positions) + index)
                if len(indices) > 2 and indices[2]:
                    normal = int(indices[2])
                    face_normals.append(normals[normal - 1 if normal > 0 else len(normals) + normal])
            if len(face) != 3 or len(face_normals) != 3:
                raise ValueError("Expected the production triangulated OBJ with three corner normals")
            faces.append(face)
            corner_normals.extend(face_normals)
    mesh = bpy.data.meshes.new("Unmodified production Anthophoros shell")
    mesh.from_pydata(positions, [], faces)
    mesh.update()
    for face in mesh.polygons:
        face.use_smooth = True
    mesh.normals_split_custom_set(corner_normals)
    obj = bpy.data.objects.new("Production planter shell", mesh)
    bpy.context.collection.objects.link(obj)
    material = bpy.data.materials.new("Neutral stone preview finish")
    material.use_nodes = True
    shader = material.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (.57, .55, .50, 1)
    shader.inputs["Roughness"].default_value = .88
    mesh.materials.append(material)
    return obj


def create_soil(bpy, np, profile: dict, colour: Path, normal: Path, depth: float):
    image = bpy.data.images.load(str(colour), check_existing=False)
    image.colorspace_settings.name = "sRGB"
    height_image = bpy.data.images.load(str(normal), check_existing=False)
    height_image.colorspace_settings.name = "Non-Color"
    height_image.alpha_mode = "CHANNEL_PACKED"
    pixels_wide, pixels_high = image.size[:]
    if tuple(height_image.size[:]) != (pixels_wide, pixels_high):
        raise ValueError("Production colour and height tiles must have identical dimensions")
    if (pixels_wide, pixels_high) != (64, 64):
        raise ValueError("The production soil files must be literal 64x64 pixel tiles")
    # Blender's pixel storage starts at the bottom; runtime normalized V and
    # ordinary PNG scanlines start at the top. Alpha remains linear data.
    pixels = np.empty(pixels_wide * pixels_high * 4, dtype=np.float32)
    height_image.pixels.foreach_get(pixels)
    alpha = pixels.reshape(pixels_high, pixels_wide, 4)[::-1, :, 3]
    half_width = profile["half_width_m"]
    half_depth = profile["half_depth_m"]
    surface_y = profile["surface_y_m"]
    def axis_samples(half_extent: float) -> list[float]:
        # Include exact texel centres and boundaries, plus the measured ends.
        # This preserves height extrema without adding procedural surface data.
        density = PIXELS_PER_METRE * HEIGHT_SAMPLES_PER_TEXEL
        interior = [index / density for index in range(math.floor(-half_extent * density) + 1,
                                                       math.ceil(half_extent * density))]
        return [-half_extent, *interior, half_extent]

    x_samples, z_samples = axis_samples(half_width), axis_samples(half_depth)
    cells_x, cells_z = len(x_samples) - 1, len(z_samples) - 1
    positions, image_uv, real_heights = [], [], []
    for runtime_z in z_samples:
        for runtime_x in x_samples:
            # The placed floor's owner-relative one-metre repeat phase. Its
            # runtime strips wrap at local X=n+.5; Blender's REPEAT image node
            # gives the same colour mapping with continuous unwrapped UVs.
            u, v = runtime_x + .5, runtime_z + .5
            px, py = (u % 1) * pixels_wide - .5, (v % 1) * pixels_high - .5
            left, top = math.floor(px), math.floor(py)
            tx, ty = px - left, py - top
            x0, y0 = left % pixels_wide, top % pixels_high
            x1, y1 = (left + 1) % pixels_wide, (top + 1) % pixels_high
            a = ((1 - tx) * (1 - ty) * alpha[y0, x0] + tx * (1 - ty) * alpha[y0, x1]
                 + (1 - tx) * ty * alpha[y1, x0] + tx * ty * alpha[y1, x1])
            height = surface_y + (float(a) - 1) * depth
            positions.append((runtime_x, -runtime_z, height))
            image_uv.append((u, 1 - v))
            real_heights.append(height)
    faces = []
    for row in range(cells_z):
        for column in range(cells_x):
            a = row * (cells_x + 1) + column
            # Increasing runtime Z is decreasing Blender Y; this order faces up.
            faces.append((a, a + cells_x + 1, a + cells_x + 2))
            faces.append((a, a + cells_x + 2, a + 1))
    mesh = bpy.data.meshes.new("Actual production alpha displaced soil")
    mesh.from_pydata(positions, [], faces)
    mesh.update()
    uv = mesh.uv_layers.new(name="Production one-metre tile repeat")
    for loop in mesh.loops:
        uv.data[loop.index].uv = image_uv[loop.vertex_index]
    for face in mesh.polygons:
        # Production shaders light a continuous normal field, not separate
        # flat triangles. Interpolate lighting normals only: vertices retain
        # every sampled clod and the unchanged packed-alpha depth exactly.
        face.use_smooth = True
    obj = bpy.data.objects.new("Production soil colour and height", mesh)
    bpy.context.collection.objects.link(obj)
    material = bpy.data.materials.new("Production soil - genuine height geometry")
    material.use_nodes = True
    shader = material.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = 1
    texture = material.node_tree.nodes.new("ShaderNodeTexImage")
    texture.image = image
    texture.interpolation = "Closest"
    texture.extension = "REPEAT"
    material.node_tree.links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    # Do not add a second bump map: the same alpha already changes real vertices.
    mesh.materials.append(material)
    return obj, real_heights


def render_worker(args: argparse.Namespace) -> None:
    import bpy
    import numpy as np
    from mathutils import Vector

    profile, mesh_path, colour, normal = source_paths(args.width)
    inputs = (EVIDENCE, mesh_path, colour, normal)
    original_hashes = hashes(inputs)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = args.samples
    scene.cycles.seed = 0
    scene.cycles.use_denoising = True
    scene.render.threads_mode = "FIXED"
    scene.render.threads = max(1, min(8, os.cpu_count() or 1))
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = False
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1
    scene.world = bpy.data.worlds.new("Neutral preview studio")
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes.get("Background")
    background.inputs["Color"].default_value = (.18, .20, .23, 1)
    background.inputs["Strength"].default_value = .15
    create_shell(bpy, mesh_path)
    soil, real_heights = create_soil(bpy, np, profile, colour, normal, args.height_depth)
    bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, -.015))
    ground = bpy.context.object
    ground.name = "Neutral studio floor"
    ground_material = bpy.data.materials.new("Matte studio floor")
    ground_material.use_nodes = True
    ground_shader = ground_material.node_tree.nodes.get("Principled BSDF")
    ground_shader.inputs["Base Color"].default_value = (.14, .15, .17, 1)
    ground_shader.inputs["Roughness"].default_value = 1
    ground.data.materials.append(ground_material)
    for name, location, energy, size in (
            ("Low grazing key", (-3.5, -.15, 2.2), 450, .08),
            ("Soft neutral fill", (2, 1.5, 4), 35, 4)):
        lamp = bpy.data.lights.new(name, "AREA")
        lamp.energy, lamp.shape, lamp.size = energy, "DISK", size
        obj = bpy.data.objects.new(name, lamp)
        bpy.context.collection.objects.link(obj)
        obj.location = location
        obj.rotation_euler = (Vector((0, 0, .9)) - obj.location).to_track_quat("-Z", "Y").to_euler()
    camera = bpy.data.objects.new("Measured planter preview camera", bpy.data.cameras.new("Camera"))
    bpy.context.collection.objects.link(camera)
    scene.camera = camera
    camera.data.type = "ORTHO"
    camera.data.lens = 70

    def camera_at(location: tuple, target: tuple, scale: float) -> None:
        camera.location = location
        camera.rotation_euler = (Vector(target) - camera.location).to_track_quat("-Z", "Y").to_euler()
        camera.data.ortho_scale = scale

    def render(name: str, width: int, height: int) -> None:
        scene.render.resolution_x, scene.render.resolution_y = width, height
        scene.render.filepath = str(args.output_dir / name)
        bpy.ops.render.render(write_still=True)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    camera_at((args.width * .95, -3.7, 3.15), (0, 0, .55), max(3.35, args.width * 1.40))
    render("full.png", 1800, 1100)
    camera_at((.25, -1.6, 1.52), (.0, .0, .900), .70)
    # The two close-ups keep exactly the same camera, lights, albedo and finish.
    for vertex in soil.data.vertices:
        vertex.co.z = profile["surface_y_m"]
    soil.data.update()
    render("close-flat.png", 1000, 800)
    for vertex, height in zip(soil.data.vertices, real_heights):
        vertex.co.z = height
    soil.data.update()
    render("close-height.png", 1000, 800)
    if hashes(inputs) != original_hashes:
        raise RuntimeError("A preview input changed while the render was in progress")
    print(json.dumps({"preview_width_m": args.width,
                      "blender": bpy.app.version_string,
                      "height_depth_fullrange_m": args.height_depth,
                      "sampled_depth_range_mm": [round((profile["surface_y_m"] - max(real_heights)) * 1000, 4),
                                                  round((profile["surface_y_m"] - min(real_heights)) * 1000, 4)],
                      "source_sha256": original_hashes}, indent=2))


def compose_outputs(raw: Path, destination: Path, args: argparse.Namespace) -> None:
    from PIL import Image, ImageDraw, ImageFont

    def font(size: int):
        for name in ("DejaVuSans.ttf", "Arial.ttf"):
            try:
                return ImageFont.truetype(name, size)
            except OSError:
                pass
        return ImageFont.load_default(size=size)

    def labelled(body, title: str, subtitle: str, footer: str):
        image = Image.new("RGB", (body.width, body.height + 132), "#171a20")
        image.paste(body.convert("RGB"), (0, 80))
        draw = ImageDraw.Draw(image)
        draw.text((28, 13), title, font=font(29), fill="#f2eee5")
        draw.text((28, 49), subtitle, font=font(20), fill="#bdc1c8")
        draw.text((28, image.height - 37), footer, font=font(20), fill="#bdc1c8")
        return image

    with Image.open(ASSETS / f"textures/block/anthophoros_earth_{args.width}m_n.png") as height:
        low, high = height.getchannel("A").getextrema()
    maximum_depth = (1 - low / 255) * args.height_depth * 1000
    footer = (f"Height alpha {low}..{high} | {args.height_depth:g}m full-range reference | "
              f"Maximum depth {maximum_depth:.2f}mm | Offline preview; shader height varies")
    with Image.open(raw / "full.png") as full:
        labelled(full, f"OFFLINE 3D PREVIEW | ANTHOPHOROS {args.width}m | 64x64 PIXEL TILES",
                 "Production tile files are 64x64 pixels, repeated at 1m; actual OBJ, neutral stone preview finish",
                 footer).save(destination / "planter-earth-64x.png")
    with Image.open(raw / "close-flat.png") as flat, Image.open(raw / "close-height.png") as raised:
        body = Image.new("RGB", (flat.width + raised.width, flat.height))
        body.paste(flat.convert("RGB"), (0, 0))
        body.paste(raised.convert("RGB"), (flat.width, 0))
        comparison = labelled(body, "OFFLINE HEIGHT PREVIEW | 64x64 TILE: FLAT / HEIGHT",
                              "Same camera, light and production albedo. Right: real vertices displaced by the production _n alpha.",
                              footer)
        draw = ImageDraw.Draw(comparison)
        for x, label in ((24, "FLAT"), (flat.width + 24, "PRODUCTION HEIGHT")):
            draw.rounded_rectangle((x - 8, 96, x + 330, 139), radius=5, fill="#171a20")
            draw.text((x, 102), label, font=font(24), fill="#f2eee5")
        comparison.save(destination / "planter-earth-64x-height.png")


def main() -> None:
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    args = arguments(argv)
    args.output_dir = args.output_dir.resolve()
    if args.render_worker:
        render_worker(args)
        return
    blender = str(args.blender) if args.blender else os.environ.get("DAEDALON_BLENDER") or shutil.which("blender")
    if not blender or not Path(blender).is_file():
        raise SystemExit("Use --blender, DAEDALON_BLENDER, or put Blender on PATH")
    source_paths(args.width)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    # These are raw passes for this active render only, never retained rollback
    # copies. The context removes them on success and on a failed render.
    with tempfile.TemporaryDirectory(prefix="daedalon-earth-render-") as temporary:
        raw = Path(temporary)
        subprocess.run([blender, "--background", "--factory-startup", "--disable-autoexec",
                        "--python", str(Path(__file__).resolve()), "--", "--render-worker",
                        "--width", str(args.width), "--height-depth", str(args.height_depth),
                        "--samples", str(args.samples), "--output-dir", str(raw)], check=True)
        compose_outputs(raw, args.output_dir, args)
    print(f"Rendered {args.output_dir / 'planter-earth-64x.png'}")
    print(f"Rendered {args.output_dir / 'planter-earth-64x-height.png'}")


if __name__ == "__main__":
    main()
