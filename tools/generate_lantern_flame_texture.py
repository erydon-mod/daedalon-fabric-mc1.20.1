"""Author the shared soft, translucent gas-flame sprite deterministically."""
from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "src/main/resources/assets/daedalon/textures/block/lantern_flame.png"
SPECULAR_TARGET = TARGET.with_name("lantern_flame_s.png")


def flame_texture() -> Image.Image:
    image = Image.new("RGBA", (64, 128))
    pixels = image.load()
    for y in range(image.height):
        height = 1.0 - (y + 0.5) / image.height
        radius = 0.82 * (1.0 - height) ** 0.70
        radius *= 0.92 + 0.08 * math.sin(math.pi * height)
        base_fade = min(1.0, (1.0 - height) / 0.045, height / 0.025)
        for x in range(image.width):
            distance = abs((x + 0.5) / image.width * 2.0 - 1.0) / radius
            if distance >= 1.0:
                continue
            edge = min(1.0, (1.0 - distance) / 0.28)
            core = max(0.0, 1.0 - distance / 0.48) ** 0.7
            pale = min(1.0, height * 0.68 + core * 0.58)
            colour = tuple(round(low + (high - low) * pale)
                           for low, high in ((255, 255), (180, 248), (22, 188)))
            alpha = round((115 + 75 * core) * edge * base_fade)
            pixels[x, y] = (*colour, alpha)
    return image


def emission_texture(albedo: Image.Image) -> Image.Image:
    # LabPBR reserves 255 as disabled; 254 is full emission. The colour sprite
    # supplies the flame colour and transparency, with no reflective surface.
    # https://shaderlabs.org/wiki/LabPBR_Material_Standard#Alpha_Channel
    image = Image.new("RGBA", albedo.size)
    pixels = albedo.load()
    image.putdata([(0, 0, 0, 254 if pixels[x, y][3] else 0)
                   for y in range(albedo.height) for x in range(albedo.width)])
    return image


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    authored = flame_texture()
    for path, expected in ((TARGET, authored), (SPECULAR_TARGET, emission_texture(authored))):
        if args.check:
            with Image.open(path) as current:
                if current.mode != "RGBA" or current.size != expected.size or current.tobytes() != expected.tobytes():
                    raise SystemExit(f"Lantern flame texture is stale: {path.name}")
        else:
            expected.save(path)
    print("Lantern gas-flame texture current")


if __name__ == "__main__":
    main()
