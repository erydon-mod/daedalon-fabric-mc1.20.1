"""Downsample approved RGBA particle artwork; preserve colour and transparency."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--drop-source", required=True, type=Path)
    parser.add_argument("--ripple-source", required=True, type=Path)
    args = parser.parse_args()
    destination = Path(__file__).resolve().parents[1] / "src/main/resources/assets/daedalon/textures/particle"
    destination.mkdir(parents=True, exist_ok=True)
    for name, source in (("fountain_drop", args.drop_source), ("fountain_ripple", args.ripple_source)):
        target = destination / f"{name}.png"
        if target.exists():
            raise FileExistsError(f"Refusing to replace an existing approved sprite: {target}")
        with Image.open(source) as image:
            if image.mode != "RGBA" or image.getchannel("A").getextrema()[0] != 0:
                raise ValueError(f"Expected genuine transparent RGBA artwork: {source}")
            # Mechanical resize only: no recolouring, background removal or new painted content.
            sprite = image.resize((64, 64), Image.Resampling.LANCZOS)
            sprite.save(target, format="PNG", optimize=True)
            print(json.dumps({"sprite": name, "size": sprite.size,
                              "alpha_range": sprite.getchannel("A").getextrema(),
                              "alpha_bounds": sprite.getchannel("A").getbbox()}))


if __name__ == "__main__":
    main()
