#!/usr/bin/env python3
"""Extend Daedalon's 6x6 repeat surfaces to two horizontal periods.

The renderer keeps the established six-column UV window and moves that window
by a world-position-derived tile offset. A second horizontal copy provides the
five shifted origins without wrapping outside Minecraft's stitched sprite.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


PREFIX = "statue_spartan_promachos_"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--texture-root",
        action="append",
        type=Path,
        help="textures/block directory to validate or update; may be repeated",
    )
    parser.add_argument("--write", action="store_true", help="write stale square source sheets")
    return parser.parse_args()


def default_root() -> Path:
    return (
        Path(__file__).resolve().parents[1]
        / "src/main/resources/assets/daedalon/textures/block"
    )


def validate_or_extend(path: Path, write: bool) -> bool:
    with Image.open(path) as opened:
        opened.load()
        image = opened.copy()

    width, height = image.size
    if width == height:
        if not write:
            return False
        extended = Image.new(image.mode, (width * 2, height))
        if image.mode == "P":
            extended.putpalette(image.getpalette())
        extended.paste(image, (0, 0))
        extended.paste(image, (width, 0))
        temporary = path.with_name(path.name + ".tmp")
        try:
            extended.save(temporary, format="PNG", optimize=False, compress_level=9)
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)
        return True

    if width != height * 2:
        raise ValueError(
            f"{path} must be a square 6x6 source or a 12x6 extended sheet, got {width}x{height}"
        )

    left = image.crop((0, 0, height, height))
    right = image.crop((height, 0, width, height))
    if left.mode != right.mode or left.tobytes() != right.tobytes():
        raise ValueError(f"{path} right repeat does not exactly match its left 6x6 source")
    return True


def main() -> int:
    args = parse_args()
    roots = args.texture_root or [default_root()]
    stale: list[Path] = []
    checked = 0
    changed = 0

    for root in roots:
        paths = sorted(root.glob(f"{PREFIX}*.png"))
        if not paths:
            raise FileNotFoundError(f"No {PREFIX}*.png files found under {root}")
        for path in paths:
            checked += 1
            was_current = validate_or_extend(path, args.write)
            if not was_current:
                stale.append(path)
            elif args.write:
                with Image.open(path) as image:
                    if image.width == image.height * 2:
                        changed += 1

    if stale:
        print("Repeat surfaces require horizontal extension:")
        for path in stale:
            print(f"  {path}")
        return 1

    action = "validated" if not args.write else "written/validated"
    print(f"Daedalon repeat surfaces {action}: files={checked}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
