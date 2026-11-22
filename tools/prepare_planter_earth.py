"""Export literal 64x64 ImageGen soil tiles and rough, periodic LabPBR companions.

The checked-in albedos are source art. --check never calls an image API and
recomputes only the deterministic normal/height and matte specular companions.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/daedalon/textures/block"
TEXELS_PER_METRE = 64
SIZES = {width: (64, 64) for width in (2, 3, 4)}
HEIGHT_DEPTH_METRES = .25  # Explicit reference scale used by the offline preview.
HEIGHT_MINIMUM = 237
MAXIMUM_SLOPE = .45


def normal_height(albedo: Image.Image) -> Image.Image:
    # Colour variation supplies an artistic height field, not reconstructed
    # geometry. Preserve crisp 64x64 samples: no blur or extra high-res noise.
    # DirectX Y-, B=unoccluded, A=height. Derivatives wrap across the tile seams.
    # https://shaderlabs.org/wiki/LabPBR_Material_Standard#Normal_Texture_(_n)
    detail = ImageOps.autocontrast(albedo.convert("L"), cutoff=2)
    heights = detail.point(lambda value: HEIGHT_MINIMUM + round(value / 255 * (255-HEIGHT_MINIMUM)))
    pixels = heights.load()
    output = Image.new("RGBA", albedo.size)
    values = []
    for y in range(albedo.height):
        for x in range(albedo.width):
            left, right = pixels[(x-1) % albedo.width, y], pixels[(x+1) % albedo.width, y]
            top, bottom = pixels[x, (y-1) % albedo.height], pixels[x, (y+1) % albedo.height]
            slope = HEIGHT_DEPTH_METRES * TEXELS_PER_METRE / (2 * 255)
            nx = max(-MAXIMUM_SLOPE, min(MAXIMUM_SLOPE, -(right-left) * slope))
            ny = max(-MAXIMUM_SLOPE, min(MAXIMUM_SLOPE, -(bottom-top) * slope))
            length = math.sqrt(1 + nx*nx + ny*ny)
            values.append((round(127.5 * (1 + nx/length)),
                           round(127.5 * (1 + ny/length)), 255,
                           pixels[x, y]))
    output.putdata(values)
    return output


def companions(albedo: Image.Image) -> tuple[Image.Image, Image.Image]:
    # Rough, nonmetallic, porous and nonemissive. Never inherit stone/bronze PBR.
    return normal_height(albedo), Image.new("RGBA", albedo.size, (0, 10, 64, 255))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path)
    parser.add_argument("--width", type=int, choices=SIZES)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.input:
        if args.check or args.width is None:
            parser.error("--input requires --width and cannot be combined with --check")
        # Mechanical framing/downsampling only; all earth imagery comes from ImageGen.
        with Image.open(args.input) as source:
            albedo = ImageOps.fit(source.convert("RGB"), (64, 64), Image.Resampling.BOX)
        albedo.save(TEXTURES / f"anthophoros_earth_{args.width}m.png")
    for width in ((args.width,) if args.width else SIZES):
        path = TEXTURES / f"anthophoros_earth_{width}m.png"
        with Image.open(path) as source:
            albedo = source.convert("RGB")
            if source.size != SIZES[width] or source.mode != "RGB":
                raise SystemExit(f"Unexpected planter albedo format: {path}")
        for suffix, expected in zip(("_n", "_s"), companions(albedo)):
            target = path.with_stem(path.stem + suffix)
            if args.check:
                with Image.open(target) as actual:
                    if actual.mode != expected.mode or actual.size != expected.size or actual.tobytes() != expected.tobytes():
                        raise SystemExit(f"Stale planter companion: {target}")
            else:
                expected.save(target)
        print(f"Planter earth {width}m current: literal 64x64 pixel albedo, normal/height and roughness tiles")


if __name__ == "__main__":
    main()
