"""Assign the supplied medallion's raised relief without changing any geometry.

The locked recipe uses a front depth-buffer, a local morphological opening and
edge-connected face patches. It adds usemtl directives only. Standalone previews
are preparation evidence; production raster/client checks remain separate.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict, deque
from pathlib import Path

import numpy as np
import scipy
from PIL import Image, ImageDraw
from scipy.ndimage import grey_opening

ROOT = Path(__file__).resolve().parents[1]
MESH = ROOT / "src/main/resources/assets/daedalon/models/mesh/olympian_god_medallion.obj"
EVIDENCE = ROOT / "docs/evidence/olympian-medallion-materials.json"
NATIVE = ROOT / "src/main/resources/assets/daedalon/textures/block"
COLLECTION = ROOT.parent / "erydon-collection-resource-packs/packs/64x/assets/daedalon/textures/block"
SOURCE_SHA256 = "09ca7e114c9fe5a9eb491fbb44e7be8929b7aeb13b2660b51f5c072d142ec117"
RESOLUTION = 512
OPENING_SIZE = 31
RELIEF_HEIGHT = .0035
PORTRAIT_SEED_Z = .008
BLACK_RGB = (48, 48, 48)  # Charcoal retains the portrait's shaded facial detail.
RELIEF = "medallion_relief"
BACKGROUND = "medallion_background"


def load_mesh():
    original = MESH.read_bytes()
    lines = [line for line in original.splitlines(keepends=True)
             if not line.startswith((b"usemtl ", b"mtllib "))]
    source = b"".join(lines)
    if hashlib.sha256(source).hexdigest() != SOURCE_SHA256:
        raise ValueError("Source geometry/normal records changed; review the supplied pilot before preparing it")
    vertices, normals, faces, normal_indices = [], [], [], []
    for line in source.decode().splitlines():
        parts = line.split()
        if parts[:1] == ["v"]: vertices.append(tuple(map(float, parts[1:])))
        elif parts[:1] == ["vn"]: normals.append(tuple(map(float, parts[1:])))
        elif parts[:1] == ["f"]:
            corners = [part.split("/") for part in parts[1:]]
            if len(corners) != 3: raise ValueError("Supplied medallion must remain triangulated")
            faces.append([int(corner[0]) - 1 for corner in corners])
            normal_indices.append([int(corner[2]) - 1 for corner in corners])
    return lines, np.asarray(vertices), np.asarray(normals), np.asarray(faces), np.asarray(normal_indices)


def raster(vertices, normals, faces, normal_indices, rotation=0, resolution=RESOLUTION):
    angle = np.radians(rotation)
    matrix = np.array([[np.cos(angle), 0, np.sin(angle)], [0, 1, 0], [-np.sin(angle), 0, np.cos(angle)]])
    positions = (vertices - (vertices.min(0) + vertices.max(0)) * .5) @ matrix.T
    transformed_normals = normals @ matrix.T
    xy = positions[:, :2].copy()
    xy[:, 1] *= -1
    xy = (xy - (xy.min(0) + xy.max(0)) * .5) / np.ptp(xy, axis=0).max() * (resolution - 4) + resolution * .5
    depth = np.full((resolution, resolution), -np.inf)
    indices = np.full((resolution, resolution), -1, dtype=int)
    shading = np.zeros((resolution, resolution))
    source_xy = np.zeros((resolution, resolution, 2))
    key = np.array([.2, 1, -.7]); key /= np.linalg.norm(key)
    fill = np.array([-.2, 1, .7]); fill /= np.linalg.norm(fill)
    for index, face in enumerate(faces):
        triangle = xy[face]
        lo = np.maximum(np.floor(triangle.min(0)).astype(int), 0)
        hi = np.minimum(np.ceil(triangle.max(0)).astype(int), resolution - 1)
        x, y = np.meshgrid(np.arange(lo[0], hi[0] + 1) + .5, np.arange(lo[1], hi[1] + 1) + .5)
        a, b, c = triangle
        denominator = (b[1]-c[1])*(a[0]-c[0]) + (c[0]-b[0])*(a[1]-c[1])
        if abs(denominator) < 1e-10: continue
        wa = ((b[1]-c[1])*(x-c[0]) + (c[0]-b[0])*(y-c[1])) / denominator
        wb = ((c[1]-a[1])*(x-c[0]) + (a[0]-c[0])*(y-c[1])) / denominator
        wc = 1-wa-wb
        weights = np.stack([wa, wb, wc], axis=-1)
        z = weights @ positions[face, 2]
        region = np.s_[lo[1]:hi[1]+1, lo[0]:hi[0]+1]
        selected = (wa >= 0) & (wb >= 0) & (wc >= 0) & (z > depth[region])
        if not np.any(selected): continue
        normal = weights @ transformed_normals[normal_indices[index]]
        normal /= np.maximum(np.linalg.norm(normal, axis=-1, keepdims=True), 1e-15)
        shade = np.minimum(1, .4 + .6 * (np.maximum(0, normal @ key) + np.maximum(0, normal @ fill)))
        depth[region][selected] = z[selected]
        indices[region][selected] = index
        shading[region][selected] = shade[selected]
        source_xy[region][selected] = (weights @ vertices[face, :2])[selected]
    return depth, indices, shading, source_xy, xy


def classify(vertices, normals, faces, normal_indices):
    depth, visible, shade, source_xy, xy = raster(vertices, normals, faces, normal_indices)
    # Recover source Z after the centered projection. The empty exterior is below
    # the front field so a narrow raised outer rim is retained by the opening.
    source_depth = depth + (vertices[:, 2].min() + vertices[:, 2].max()) * .5
    field = np.where(visible >= 0, source_depth, -.045)
    background = grey_opening(field, size=(OPENING_SIZE, OPENING_SIZE), mode="nearest")
    centers = vertices[faces].mean(1)
    pixel = np.rint(xy[faces].mean(1)).astype(int).clip(0, RESOLUTION - 1)
    local_height = centers[:, 2] - background[pixel[:, 1], pixel[:, 0]]
    cross = np.cross(vertices[faces[:, 1]]-vertices[faces[:, 0]], vertices[faces[:, 2]]-vertices[faces[:, 0]])
    area = np.linalg.norm(cross, axis=1) * .5
    nz = cross[:, 2] / np.maximum(area * 2, 1e-15)
    candidate = (centers[:, 2] > -.045) & ((local_height >= RELIEF_HEIGHT) | (centers[:, 2] >= PORTRAIT_SEED_Z))
    edges = defaultdict(list)
    for index, face in enumerate(faces):
        for a, b in zip(face, np.roll(face, -1)):
            edges[tuple(sorted((int(a), int(b))))].append(index)
    neighbors = [set() for _ in faces]
    for touching in edges.values():
        for index in touching: neighbors[index].update(other for other in touching if other != index)
    # A missing single triangle inside a coherent relief patch should not create a
    # stone pinhole. Fill only shared-edge majorities above the recessed field.
    for _ in range(2):
        fill = [index for index, touching in enumerate(neighbors)
                if not candidate[index] and centers[index, 2] > -.035
                and len(touching) >= 3 and sum(candidate[other] for other in touching) >= 3]
        candidate[fill] = True
    seen, components = set(), []
    for start in np.flatnonzero(candidate):
        if int(start) in seen: continue
        pending, component = deque([int(start)]), []
        seen.add(int(start))
        while pending:
            index = pending.popleft(); component.append(index)
            for other in neighbors[index]:
                if candidate[other] and other not in seen:
                    seen.add(other); pending.append(other)
        components.append(component)
    removed = 0
    for component in components:
        # Remove detached tiny fragments rather than speckling flat field triangles.
        projected_area = float(np.sum(area[component] * np.maximum(0, nz[component])))
        if len(component) < 4 and projected_area < .00015:
            candidate[component] = False; removed += len(component)
    kept = [component for component in components if any(candidate[component])]
    metrics = {
        "depth_buffer_resolution": RESOLUTION, "local_opening_size": OPENING_SIZE,
        "local_relief_height": RELIEF_HEIGHT, "portrait_seed_z": PORTRAIT_SEED_Z,
        "relief_triangles": int(candidate.sum()), "background_triangles": int((~candidate).sum()),
        "connected_relief_patches": len(kept), "removed_isolated_triangles": removed,
        "largest_relief_patches": sorted((len(c) for c in kept), reverse=True)[:16],
        "front_relief_pixel_fraction": float(np.mean(candidate[visible[visible >= 0]])),
    }
    return candidate, metrics


def material_bytes(lines, labels):
    output, material, index = [], None, 0
    for line in lines:
        if line.startswith(b"f "):
            next_material = RELIEF if labels[index] else BACKGROUND
            if material != next_material:
                output.append(f"usemtl {next_material}\n".encode()); material = next_material
            index += 1
        output.append(line)
    return b"".join(output)


def luminance(rgb):
    srgb = np.asarray(rgb) / 255.0
    linear = np.where(srgb <= .04045, srgb / 12.92, ((srgb + .055) / 1.055) ** 2.4)
    return linear @ np.array([.2126, .7152, .0722])


def palette():
    import generate_daedalon_assets as generator
    colors = {"white": np.array([255,255,255]), "black": np.array(BLACK_RGB), "bronze": np.array([229,160,29])}
    result = {}
    for material in list(generator.MATERIALS) + ["bronze", "silver"]:
        for aged in ((False, True) if material not in ("bronze", "silver") else (False,)):
            key = material + ("_aged" if aged else "")
            filename = f"statue_spartan_promachos_{key}.png" if material not in ("bronze", "silver") else f"{material}.png"
            values = []
            for root in (NATIVE, COLLECTION):
                path = root / filename
                if not path.is_file(): path = NATIVE / filename
                rgb = np.asarray(Image.open(path).convert("RGB"))
                values.append(float(np.median(luminance(rgb))))
            scores = {}
            for name, color in colors.items():
                relief = float(luminance(color))
                scores[name] = min((max(relief, value) + .05)/(min(relief, value) + .05) for value in values)
            selected = max(scores, key=scores.get)
            result[key] = {"relief": selected, "median_background_luminance_native64": values, "worst_pack_contrast": scores}
    return result


def render(vertices, normals, faces, normal_indices, labels, texture, relief, resolution=128):
    depth, ids, shade, xy, _ = raster(vertices, normals, faces, normal_indices, 20, resolution)
    valid = ids >= 0
    image = np.zeros((resolution, resolution, 4), dtype=np.uint8)
    # Source preparation preview uses the front planar repeat window. The native
    # production preview additionally verifies its axis-stabilized triangle UVs.
    uv = (xy - vertices[:, :2].min(0)) / np.ptp(vertices[:, :2], axis=0).max()
    u = np.rint(np.clip(uv[...,0], 0, 1)*(texture.shape[1]-1)).astype(int)
    v = np.rint(np.clip(1-uv[...,1], 0, 1)*(texture.shape[0]-1)).astype(int)
    color = texture[v, u].copy()
    mask = valid & labels[np.maximum(ids, 0)]
    color[mask] = relief
    rgb = np.rint(np.clip((color / 255.0)**2.2 * shade[...,None], 0, 1)**(1/2.2) * 255).astype(np.uint8)
    image[valid,:3] = rgb[valid]; image[valid,3] = 255
    return Image.fromarray(image)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="add deterministic material directives to the shared runtime OBJ")
    mode.add_argument("--check", action="store_true", help="verify the locked assignment without writing any files")
    args = parser.parse_args()
    lines, vertices, normals, faces, normal_indices = load_mesh()
    labels, metrics = classify(vertices, normals, faces, normal_indices)
    choices = palette()
    prepared = material_bytes(lines, labels)
    if args.check:
        if prepared != MESH.read_bytes(): raise ValueError("Medallion material assignment is stale")
        print("Medallion material assignment matches the locked recipe")
        return
    if args.write:
        MESH.write_bytes(prepared)
        Image.new("RGB", (16, 16), BLACK_RGB).save(NATIVE / "black.png", compress_level=9)
    colors = {"white": np.array([255,255,255]), "black": np.array(BLACK_RGB), "bronze": np.array([229,160,29])}
    contact = Image.new("RGB", (3*176, 4*166+32), (38,40,46)); draw = ImageDraw.Draw(contact)
    draw.text((10,8), "20 deg Y - actual source triangles; preparation raster", fill="white")
    row = 0
    for pack, textures in (("Native16", NATIVE), ("Collection64", COLLECTION)):
        for material in ("glacium", "noxoplis"):
            texture = np.asarray(Image.open(textures / f"statue_spartan_promachos_{material}.png").convert("RGB"))
            texture = texture[:, :texture.shape[1]//2]  # Existing phase-zero 6x6 window.
            for column, (name, color) in enumerate(colors.items()):
                x, y = column*176, row*166+32
                picture = render(vertices, normals, faces, normal_indices, labels, texture, color)
                contact.paste(picture, (x+24,y+22), picture)
                draw.text((x+8,y+4), f"{pack} {material} / {name}", fill="white")
            row += 1
    preview = ROOT / "docs/evidence/olympian-medallion-material-comparison.png"
    contact.save(preview)
    evidence = {
        "source_sha256_without_material_directives": SOURCE_SHA256,
        "runtime_sha256": hashlib.sha256(MESH.read_bytes()).hexdigest(),
        "face_material_assignment_sha256": hashlib.sha256(labels.astype(np.uint8).tobytes()).hexdigest(),
        "preparation_versions": {"numpy": np.__version__, "scipy": scipy.__version__},
        "source_counts": {"vertices": len(vertices), "triangles": len(faces), "normals": len(normals)},
        "geometry_policy": "Only usemtl directives change; every original byte remains in order after those directives are removed",
        "classification": metrics, "material_choices": choices,
        "relief_palette_rgb": {key: list(map(int, color)) for key, color in colors.items()},
        "native_black_texture": "assets/daedalon/textures/block/black.png",
        "native_black_texture_sha256": hashlib.sha256((NATIVE / "black.png").read_bytes()).hexdigest(),
        "component_texture_provenance": "Uniform RGB48 charcoal authored procedurally at native16, no external artwork",
        "gui_rotation": [0,20,0], "preview": str(preview.relative_to(ROOT)).replace("\\","/"),
        "preview_boundary": "Standalone geometry preparation raster with actual normals, source pack colours and approximate planar front UVs; native/client proof remains separate",
    }
    EVIDENCE.write_text(json.dumps(evidence, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(metrics, indent=2))
    print("Relief choices:", {name: [key for key, value in choices.items() if value["relief"] == name] for name in colors})
    print(preview)


if __name__ == "__main__": main()
