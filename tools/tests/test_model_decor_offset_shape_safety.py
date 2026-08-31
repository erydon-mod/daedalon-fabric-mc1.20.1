from __future__ import annotations

import re
import unittest

from daedalon_test_support import JAVA_ROOT


URN_SOURCE = JAVA_ROOT / "block/UrnBlock.java"
STATUE_SOURCE = JAVA_ROOT / "block/StatueBlock.java"
TRANSFORM_SOURCE = JAVA_ROOT / "block/DecorShapeTransforms.java"
BAKED_SOURCE = JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"


def method_body(source: str, method_name: str) -> str:
    match = re.search(
        rf"private static VoxelShape {method_name}\(\) \{{(?P<body>.*?)\n    \}}",
        source,
        re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"Missing shape method {method_name}")
    return match.group("body")


def calls(
    source: str, call_name: str, argument_count: int
) -> list[tuple[float, ...]]:
    result: list[tuple[float, ...]] = []
    for match in re.finditer(rf"{re.escape(call_name)}\(([^)]+)\)", source):
        try:
            arguments = tuple(
                float(value.strip()) for value in match.group(1).split(",")
            )
        except ValueError:
            continue
        if len(arguments) == argument_count:
            result.append(arguments)
    return result


def layered_style_boxes(source: str, style: str) -> list[tuple[float, ...]]:
    match = re.search(
        rf'{style}\("[a-z_]+", "[A-Za-z ]+", makeLayeredShape\('
        rf'(?P<body>.*?)\n        \)\)',
        source,
        re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"Missing layered urn profile {style}")
    values = [
        float(value.strip())
        for value in match.group("body").replace("\n", " ").split(",")
        if value.strip()
    ]
    if len(values) < 24 or len(values) % 4:
        raise AssertionError(f"Invalid layered urn profile {style}: {len(values)} values")
    result = []
    for index in range(0, len(values), 4):
        min_y, max_y, half_x, half_z = values[index : index + 4]
        result.append(
            (8.0 - half_x, min_y, 8.0 - half_z,
             8.0 + half_x, max_y, 8.0 + half_z)
        )
    return result


def boxes_bounds(
    boxes: list[tuple[float, float, float, float, float, float]]
) -> tuple[float, ...]:
    if not boxes:
        raise AssertionError("Expected at least one profile box")
    return (
        min(box[0] for box in boxes) / 16.0,
        min(box[1] for box in boxes) / 16.0,
        min(box[2] for box in boxes) / 16.0,
        max(box[3] for box in boxes) / 16.0,
        max(box[4] for box in boxes) / 16.0,
        max(box[5] for box in boxes) / 16.0,
    )


def transform_bounds(
    bounds: tuple[float, ...],
    scale: float,
    distance: float,
    base_y: float,
    turns: int,
) -> tuple[float, ...]:
    min_x, min_y, min_z, max_x, max_y, max_z = bounds
    result = (
        0.5 + (min_x - 0.5) * scale,
        min_y * scale + base_y,
        0.5 + (min_z - 0.5) * scale - distance,
        0.5 + (max_x - 0.5) * scale,
        max_y * scale + base_y,
        0.5 + (max_z - 0.5) * scale - distance,
    )
    for _ in range(turns):
        min_x, min_y, min_z, max_x, max_y, max_z = result
        result = (
            1.0 - max_z,
            min_y,
            min_x,
            1.0 - min_z,
            max_y,
            max_x,
        )
    return result


class DecorOffsetShapeContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.urn = URN_SOURCE.read_text(encoding="utf-8")
        cls.statue = STATUE_SOURCE.read_text(encoding="utf-8")
        cls.transform = TRANSFORM_SOURCE.read_text(encoding="utf-8")
        cls.baked = BAKED_SOURCE.read_text(encoding="utf-8")

    @staticmethod
    def constant(source: str, name: str) -> float:
        match = re.search(rf"\b{name}\s*=\s*([0-9.]+)F", source)
        if match is None:
            raise AssertionError(f"Missing constant {name}")
        return float(match.group(1))

    def test_shape_transform_matches_visible_model_transform(self) -> None:
        self.assertEqual(
            self.constant(self.baked, "OFFSET_DISTANCE"),
            self.constant(self.transform, "OFFSET_DISTANCE"),
        )
        self.assertEqual(
            self.constant(self.baked, "OFFSET_BASE_Y"),
            self.constant(self.transform, "OFFSET_BASE_Y"),
        )
        self.assertIn("0.5 + (minX - 0.5) * scale", self.transform)
        self.assertIn("minY * scale + baseY", self.transform)
        self.assertIn(
            "0.5 + (minZ - 0.5) * scale - northOffset", self.transform
        )
        self.assertRegex(
            self.transform,
            r"VoxelShapes\.cuboid\(\s*1\.0 - maxZ,",
        )

    def test_all_urn_profiles_cover_every_size_offset_and_facing(self) -> None:
        styles = {
            "KONCHE": ("konche", "Konche"),
            "DIOTA": ("diota", "Diota"),
            "KYLIX": ("kylix", "Kylix"),
            "KALYX": ("kalyx", "Kalyx"),
            "AMPHORA": ("amphora", "Amphora"),
            "LEKYTHOS": ("lekythos", "Lekythos"),
            "PELIKE": ("pelike", "Pelike"),
            "PITHOS": ("pithos", "Pithos"),
            "RHABDOS": ("rhabdos", "Rhabdos"),
            "SALPINX": ("salpinx", "Salpinx"),
            "STAMNOS": ("stamnos", "Stamnos"),
        }
        sizes = (0.6156, 1.0, 1.3844)
        distance = self.constant(self.transform, "OFFSET_DISTANCE")
        base_y = self.constant(self.transform, "OFFSET_BASE_Y")

        for style in styles:
            if style == "DIOTA":
                body = method_body(self.urn, "makeDiotaShape")
                profile_boxes = calls(body, "Block.createCuboidShape", 6)
                profile_boxes += calls(
                    body, "DecorShapeTransforms.octagonalLayer", 6
                )
            else:
                profile_boxes = layered_style_boxes(self.urn, style)
            self.assertGreaterEqual(len(profile_boxes), 6, style)
            bounds = boxes_bounds(profile_boxes)
            self.assertAlmostEqual(0.0, bounds[1], places=7)
            self.assertGreater(bounds[4], 0.6, style)
            self.assertLessEqual(bounds[4], 1.0, style)

            previous_height = 0.0
            previous_width = 0.0
            for scale in sizes:
                centred = transform_bounds(bounds, scale, 0.0, 0.0, 0)
                height = centred[4] - centred[1]
                width = max(
                    centred[3] - centred[0], centred[5] - centred[2]
                )
                self.assertGreater(height, previous_height, (style, scale))
                self.assertGreater(width, previous_width, (style, scale))
                previous_height = height
                previous_width = width

                for offset in (False, True):
                    for turns in range(4):
                        transformed = transform_bounds(
                            bounds,
                            scale,
                            distance if offset else 0.0,
                            base_y if offset else 0.0,
                            turns,
                        )
                        self.assertTrue(
                            all(
                                -1.000001 <= value <= 2.000001
                                for value in transformed
                            ),
                            (style, scale, offset, turns, transformed),
                        )
                        if offset:
                            crosses_target_edge = (
                                transformed[2] < 0.0,
                                transformed[3] > 1.0,
                                transformed[5] > 1.0,
                                transformed[0] < 0.0,
                            )[turns]
                            self.assertTrue(
                                crosses_target_edge,
                                (style, scale, turns, transformed),
                            )

        for style, (shape_id, display_name) in styles.items():
            self.assertIn(
                f'{style}("{shape_id}", "{display_name}",', self.urn
            )
        self.assertIn('this.resourceStem = "urn_" + shapeId', self.urn)
        self.assertIn("private static VoxelShape makeLayeredShape", self.urn)
        self.assertIn('SMALL("small", 0.6156F)', self.urn)
        self.assertIn('MEDIUM("medium", 1.0F)', self.urn)
        self.assertIn('LARGE("large", 1.3844F)', self.urn)

    def test_offset_outline_retains_source_cell_edit_target(self) -> None:
        self.assertIn(
            "TARGET_POST =\n"
            "            Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 16.0, 10.0)",
            self.transform,
        )
        self.assertIn("getRaycastShape(", self.urn)
        self.assertIn("DecorShapeTransforms.TARGET_POST", self.urn)
        self.assertIn("getCullingShape(", self.urn)
        self.assertIn("return VoxelShapes.empty();", self.urn)
        self.assertIn("includeTargetPost && offset", self.urn)

    def test_offset_urns_expose_wider_debug_stick_edit_target(self) -> None:
        self.assertIn(
            "DEBUG_EDIT_TARGET = VoxelShapes.union(\n"
            "            Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0),\n"
            "            Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0)",
            self.transform,
        )
        self.assertIn(
            "state.get(OFFSET) && context.isHolding(Items.DEBUG_STICK)",
            self.urn,
        )
        self.assertIn("ShapeCache.DEBUG_EDIT_OUTLINE", self.urn)
        self.assertIn("DecorShapeTransforms.DEBUG_EDIT_TARGET", self.urn)
        self.assertIn("createShapeCache(false)", self.urn)
        self.assertNotIn(
            "DecorShapeTransforms.DEBUG_EDIT_TARGET",
            self.urn[
                self.urn.index("static VoxelShape collisionShapeFor("):
                self.urn.index("static VoxelShape outlineShapeFor(")
            ],
        )

    def test_statues_share_urn_offset_and_lift_without_resizing(self) -> None:
        self.assertIn('BooleanProperty.of("offset")', self.statue)
        self.assertIn(".with(OFFSET, false)", self.statue)
        self.assertIn("builder.add(SIZE, OFFSET, FACING)", self.statue)
        self.assertIn(
            "state.get(OFFSET) && context.isHolding(Items.DEBUG_STICK)",
            self.statue,
        )
        self.assertIn("DecorShapeTransforms.OFFSET_DISTANCE", self.statue)
        self.assertIn("DecorShapeTransforms.OFFSET_BASE_Y", self.statue)
        self.assertIn("DecorShapeTransforms.TARGET_POST", self.statue)
        self.assertIn("DecorShapeTransforms.DEBUG_EDIT_TARGET", self.statue)
        self.assertIn(
            "Map<StatueBlock.Profile, StatueTransform[][][]>", self.baked
        )
        self.assertIn("new StatueTransform[sizes.length][2][4]", self.baked)
        self.assertIn("state.get(StatueBlock.OFFSET) ? 1 : 0", self.baked)
        self.assertNotRegex(
            self.baked,
            r"OFFSET_(?:DISTANCE|BASE_Y)\s*\*\s*size\.scale\(\)",
        )

    def test_shape_cache_initializes_after_urn_style(self) -> None:
        self.assertNotIn(
            "private static final VoxelShape[][][][] COLLISION_SHAPES",
            self.urn,
        )
        self.assertIn("private static final class ShapeCache", self.urn)
        self.assertIn("ShapeCache.COLLISION", self.urn)
        self.assertIn("ShapeCache.OUTLINE", self.urn)
        self.assertIn("ShapeCache.DEBUG_EDIT_OUTLINE", self.urn)


if __name__ == "__main__":
    unittest.main()
