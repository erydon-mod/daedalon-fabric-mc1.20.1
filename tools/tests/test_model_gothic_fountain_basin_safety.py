from __future__ import annotations

import math
import re
import struct
import unittest
from collections import Counter

from daedalon_test_support import (
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    REPO_ROOT,
    all_block_ids,
    load_json,
    parse_obj,
    sha256,
)


class GothicFountainBasinSafetyTests(unittest.TestCase):
    FAMILY = "gothic_fountain_basin"
    MESH = "fountain_gothic_basin"

    def test_canonical_ids_are_complete_and_material_first(self) -> None:
        ids = GENERATOR.family_block_ids(self.FAMILY)
        self.assertEqual(54, len(ids))
        self.assertEqual(54, len(set(ids)))
        self.assertTrue(set(ids).issubset(all_block_ids()))
        self.assertIn("aganite_gothic_fountain_basin", ids)
        self.assertIn("aganite_aged_gothic_fountain_basin", ids)
        self.assertNotIn("gothic_fountain_basin_aganite", ids)
        self.assertNotIn("aganite_gothic_fountain_basin_aged", ids)
        self.assertNotIn("bronze_gothic_fountain_basin", ids)

    def test_mesh_and_source_evidence_are_hash_locked(self) -> None:
        evidence = load_json(
            REPO_ROOT / "docs/evidence/gothic-fountain-basin-source.json"
        )
        expected = MESH_EXPECTATIONS[self.MESH]
        source = evidence["source"]
        runtime = evidence["runtime"]

        self.assertEqual("fountain_gothic_basin_BE.obj", source["file"])
        self.assertEqual(
            expected["obj_sha256"],
            source["sha256"],
        )
        self.assertTrue(source["provenance"])
        self.assertNotIn(":\\", source["file"])
        self.assertEqual(source["triangles"], evidence["preparation"]["target_faces"])
        self.assertEqual("preserve exact supplied geometry", evidence["preparation"]["face_policy"])
        self.assertEqual(source["triangles"], runtime["faces"])
        self.assertEqual(source["sha256"], runtime["obj_sha256"])
        self.assertEqual(expected["faces"], runtime["faces"])
        self.assertEqual(expected["vertices"], runtime["vertices"])
        self.assertEqual(expected["normals"], runtime["normals"])
        self.assertEqual(expected["obj_sha256"], runtime["obj_sha256"])
        self.assertEqual(expected["mtl_sha256"], runtime["mtl_sha256"])
        placement = evidence["placement"]
        self.assertEqual("medium", placement["default_size"])
        self.assertEqual({"small", "medium", "large"}, set(placement["sizes"]))
        self.assertEqual([3.0, 3.0], placement["sizes"]["small"]["footprint_blocks"])
        self.assertEqual([4.0, 4.0], placement["sizes"]["medium"]["footprint_blocks"])
        self.assertEqual([5.0, 5.0], placement["sizes"]["large"]["footprint_blocks"])

        obj_path = MESH_ROOT / expected["obj"]
        actual = parse_obj(obj_path)
        self.assertEqual(0, actual["uvs"])
        self.assertEqual({3}, actual["face_sizes"])
        self.assertEqual({"v//vn"}, actual["face_index_styles"])
        self.assertEqual(expected["bounds"], actual["bounds"])
        self.assertEqual(expected["obj_sha256"], sha256(obj_path))
        self.assertEqual(expected["mtl_sha256"], sha256(MESH_ROOT / expected["mtl"]))

        definition = load_json(MESH_ROOT / expected["definition"])
        self.assertTrue(definition["fit_to_block"])
        self.assertTrue(definition["force_uv_projection"])
        self.assertTrue(definition["repair_degenerate_uvs"])
        self.assertEqual("axis_stabilized_box", definition["uv_projection"])
        self.assertFalse(definition["smooth_normals"])
        self.assertEqual(80.0, definition["smooth_angle_degrees"])
        self.assertEqual([5.0, 5.0, 5.0], definition["scale"])

        display = load_json(
            DAEDALON_ASSETS / "models/block/mesh/fountain_basin_display.json"
        )
        self.assertEqual([0.15, 0.15, 0.15], display["display"]["gui"]["scale"])

    def test_states_items_languages_and_search_tags_are_complete(self) -> None:
        ids = GENERATOR.family_block_ids(self.FAMILY)
        refs = {f"daedalon:{block_id}" for block_id in ids}
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }

        for block_id in ids:
            state = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")
            self.assertEqual(
                {
                    f"size={size},waterlogged={waterlogged}"
                    for size in ("small", "medium", "large")
                    for waterlogged in ("false", "true")
                },
                set(state["variants"]),
            )
            self.assertEqual(1, len({entry["model"] for entry in state["variants"].values()}))
            self.assertTrue(all("raised_base" not in key for key in state["variants"]),
                            "The shared runtime wrapper handles both raised values without duplicate asset bindings")
            self.assertEqual(
                "daedalon:block/mesh/fountain_basin_display",
                load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")["parent"],
            )
            self.assertFalse(
                (DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json").exists()
            )
            for language in languages.values():
                self.assertIn(f"block.daedalon.{block_id}", language)

        for kind in ("blocks", "items"):
            tag_root = DAEDALON_DATA / f"tags/{kind}"
            self.assertEqual(
                refs,
                set(load_json(tag_root / f"{self.FAMILY}.json")["values"]),
            )
            self.assertEqual(
                {
                    "#daedalon:louterion_basin",
                    "#daedalon:gothic_fountain_basin",
                    "#daedalon:georgian_fountain_basin",
                    "#daedalon:greek_fountain_basin",
                },
                set(load_json(tag_root / "basin.json")["values"]),
            )
            for relation in ("fountain", "water_feature", "gothic"):
                self.assertIn(
                    "#daedalon:gothic_fountain_basin",
                    load_json(tag_root / f"{relation}.json")["values"],
                )

        registry = (JAVA_ROOT / "registry/ModBlocks.java").read_text(encoding="utf-8")
        basin = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("new FountainBasinBlock(decorSettings().dropsNothing(), style)", registry)
        self.assertNotIn("Block.dropStack", basin)
        self.assertNotIn("giveItemStack", basin)

    def test_bucket_keeps_bowl_water_contained_and_only_wets_partial_raised_base_cells(self) -> None:
        block = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(
            encoding="utf-8"
        )
        geometry = (JAVA_ROOT / "block/FountainBasinGeometry.java").read_text(
            encoding="utf-8"
        )
        part = (JAVA_ROOT / "block/FountainBasinPartBlock.java").read_text(
            encoding="utf-8"
        )
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(
            encoding="utf-8"
        )
        shader_bridge = (
            JAVA_ROOT / "client/model/obj/ShaderTerrainNormalBridge.java"
        ).read_text(encoding="utf-8")
        indium_mixin = (
            JAVA_ROOT / "mixin/client/indium/TerrainRenderContextMixin.java"
        ).read_text(encoding="utf-8")
        iris_mixin = (
            JAVA_ROOT / "mixin/client/iris/XHFPTerrainVertexMixin.java"
        ).read_text(encoding="utf-8")
        mod_blocks = (JAVA_ROOT / "registry/ModBlocks.java").read_text(
            encoding="utf-8"
        )
        entity_mixin = (JAVA_ROOT / "mixin/EntityMixin.java").read_text(
            encoding="utf-8"
        )
        breaking_mixin = (
            JAVA_ROOT / "mixin/ServerPlayerInteractionManagerMixin.java"
        ).read_text(encoding="utf-8")
        tall_placement = (JAVA_ROOT / "block/TallDecorPlacement.java").read_text(
            encoding="utf-8"
        )
        mixins = load_json(REPO_ROOT / "src/main/resources/daedalon.mixins.json")

        self.assertIn("implements Waterloggable", block)
        self.assertIn('EnumProperty.of("size", Size.class)', block)
        self.assertIn("Properties.WATERLOGGED", block)
        self.assertIn("builder.add(SIZE, RAISED_BASE, WATERLOGGED)", block)
        self.assertIn('BooleanProperty.of("raised_base")', block)
        self.assertIn("with(RAISED_BASE, false)", block)
        self.assertIn("with(SIZE, Size.MEDIUM)", block)
        self.assertIn("tryFillWithFluid", block)
        self.assertIn("state.with(WATERLOGGED, true)", block)
        self.assertNotIn("public FluidState getFluidState", block)
        bucket_fill = block[block.index("public boolean tryFillWithFluid"):block.index("public VoxelShape getOutlineShape")]
        self.assertNotIn("scheduleFluidTick", bucket_fill)
        self.assertIn("anchorState.get(RAISED_BASE) && anchorState.get(WATERLOGGED)", block)
        self.assertIn('SMALL("small", 3.0F)', block)
        self.assertIn('MEDIUM("medium", MEDIUM_MODEL_SCALE)', block)
        self.assertIn('LARGE("large", LARGE_MODEL_SCALE)', block)
        self.assertIn("MEDIUM_MODEL_SCALE = 4.0F", block)
        self.assertIn("LARGE_MODEL_SCALE = 5.0F", block)
        self.assertIn("NORMALIZED_MODEL_HEIGHT = 0.26585404F", block)
        self.assertIn("MEDIUM_BASIN_FLOOR_Y = 0.40F", block)
        self.assertIn("MEDIUM_WATER_SURFACE_Y = 0.9066667F", block)
        self.assertIn("MEDIUM_WATER_HALF_WIDTH = 1.5866667F", block)
        self.assertIn("MEDIUM_WATER_BEVEL = 0.6533333F", block)
        self.assertIn("MEDIUM_WATER_SURFACE_Y * mediumRatio()", block)
        self.assertIn("MEDIUM_WATER_HALF_WIDTH * mediumRatio()", block)
        self.assertIn("MEDIUM_WATER_BEVEL * mediumRatio()", block)
        self.assertIn("style.modelHeight(size)", geometry)
        self.assertIn("settings.dynamicBounds()", block)
        self.assertIn("syncParts(world, pos, state)", block)
        self.assertIn("removeOwnedParts(world, pos)", block)
        self.assertIn("canOccupyParts", block)
        self.assertIn("outlinePartShape(", block)

        self.assertIn("BooleanBiFunction.ONLY_FIRST", geometry)
        self.assertIn("createPartShapes", geometry)
        self.assertIn("WATER_PICK_THICKNESS", geometry)
        self.assertIn("containedWaterHeight", geometry)
        self.assertIn("nearestX + nearestZ", geometry)
        self.assertIn('IntProperty.of("offset_x", 0, 4)', part)
        self.assertIn('"offset_y", 0, FountainBasinGeometry.MAX_PART_Y', part)
        self.assertIn("MAX_PART_Y = 6", geometry)
        self.assertIn('IntProperty.of("offset_z", 0, 4)', part)
        self.assertIn("HORIZONTAL_OFFSET_BIAS = 2", part)
        self.assertIn("BlockRenderType.INVISIBLE", part)
        self.assertIn("resolveAnchorPos", part)
        self.assertIn("FountainBasinBlock.outlinePartShape", part)
        self.assertIn("FountainBasinBlock.collisionPartShape", part)
        self.assertEqual(
            "minecraft:block/air",
            load_json(
                DAEDALON_ASSETS / "blockstates/fountain_basin_part.json"
            )["variants"][""]["model"],
        )

        self.assertIn("FountainBasinBlock.containedWaterSurfaceHeight", entity_mixin)
        self.assertEqual(3, entity_mixin.count("@Redirect"))
        self.assertIn("World;getFluidState", entity_mixin)
        self.assertIn("FluidState;getHeight", entity_mixin)
        self.assertIn("FluidState;getVelocity", entity_mixin)
        self.assertIn("Vec3d.ZERO", entity_mixin)
        self.assertNotIn("HashSet", entity_mixin)
        self.assertNotIn("CallbackInfoReturnable", entity_mixin)
        self.assertIn("FluidTags.WATER", entity_mixin)
        self.assertFalse((JAVA_ROOT / "mixin/client/BlockViewMixin.java").exists())
        self.assertIn("implements Waterloggable", part)
        self.assertIn(".canFillWithFluid(world, anchor.pos(), anchor.state(), fluid)", part)
        self.assertIn(".tryFillWithFluid(world, anchor.pos(), anchor.state(), fluid)", part)
        self.assertIn(".tryDrainFluid(world, anchor.pos(), anchor.state())", part)
        self.assertIn("if (state.get(WATERLOGGED)) world.scheduleFluidTick", part)
        self.assertIn("public FluidState getFluidState", part)
        self.assertIn("Properties.WATERLOGGED", part)
        self.assertIn("state.get(WATERLOGGED) ? Fluids.WATER.getStill(false)", part)
        self.assertIn("serverBreakingDistanceSquared", breaking_mixin)
        self.assertIn("outline.getClosestPointTo(localEye)", tall_placement)
        self.assertNotIn("containsOwnedBasinLevelPartHit", tall_placement)
        self.assertIn("EntityMixin", mixins["mixins"])
        self.assertIn("ServerPlayerInteractionManagerMixin", mixins["mixins"])
        self.assertNotIn("client.BlockViewMixin", mixins["client"])

        self.assertIn("WATER_STILL_TEXTURE", plugin)
        self.assertIn('"block/water_still"', plugin)
        self.assertIn("FountainBasinBlock.Style.values()", plugin)
        self.assertIn("instanceof FountainBasinBlock", baked)
        self.assertIn("BiomeColors.getWaterColor", baked)
        self.assertIn("BlendMode.TRANSLUCENT", baked)
        self.assertIn("!state.get(FountainBasinBlock.WATERLOGGED)", baked)
        self.assertIn("ShaderTerrainNormalBridge.containedWaterTag()", baked)
        self.assertIn("CONTAINED_WATER_TAG", shader_bridge)
        self.assertIn("isContainedWaterQuad", indium_mixin)
        self.assertIn("publishContainedWater", indium_mixin)
        self.assertIn("Fluids.WATER.getDefaultState().getBlockState()", iris_mixin)
        self.assertIn("IRIS_FLUID_RENDER_TYPE = 1", iris_mixin)
        self.assertEqual(3, iris_mixin.count("isClaimedContainedWater()"))
        self.assertIn("BasinTransform.forState(state)", baked)
        self.assertIn("size.renderScale()", baked)
        self.assertIn("style.waterHalfWidth(size)", baked)
        self.assertIn("style.waterBevel(size)", baked)
        self.assertIn("style.waterSurfaceY(size)", baked)
        self.assertIn("waterOutlineX(index, center, halfWidth, bevel)", baked)
        self.assertIn("waterOutlineZ(next, center, halfWidth, bevel)", baked)
        self.assertIn("emitWaterVertex(emitter, 3, currentX, currentZ", baked)
        self.assertNotIn("float[][] outline", baked)
        self.assertNotIn("float[] x =", baked)
        self.assertNotIn("float[] z =", baked)
        basin_transform = baked[baked.index("record BasinTransform"):baked.index("class FacingDecorTransform")]
        self.assertIn("quad.pos", basin_transform)
        self.assertIn("quad.uv", basin_transform)
        self.assertIn("exactly 4678 decor blocks and items", mod_blocks)
        self.assertIn('id("fountain_basin_part")', mod_blocks)
        self.assertNotIn(
            'registerBlock("fountain_basin_part"',
            mod_blocks,
        )

    def test_all_authored_base_footprints_are_hash_locked_closed_and_inside_existing_widths(self) -> None:
        for style in ("gothic", "georgian", "greek"):
            with self.subTest(style=style):
                data = load_json(DAEDALON_DATA / f"fountain_base/{style}.json")
                source = MESH_ROOT / f"fountain_{style}_basin.obj"
                mesh = parse_obj(source)
                self.assertEqual(sha256(source), data["source_sha256"])
                self.assertEqual(mesh["faces"], data["source_faces"])
                self.assertGreater(data["selected_bottom_faces"], 0)
                self.assertLess(data["selected_bottom_faces"], mesh["faces"])
                points, triangles = data["points"], data["triangles"]
                self.assertGreater(len(points), 8, "Keep the authored outline rather than a rectangle/octagon substitute")
                self.assertEqual(len(points) - 2, len(triangles))
                polygon_area = sum(a[0] * b[1] - a[1] * b[0]
                                   for a, b in zip(points, points[1:] + points[:1])) / 2
                self.assertGreater(polygon_area, 0)
                triangle_area = 0
                edges = Counter()
                for triangle in triangles:
                    self.assertEqual(3, len(set(triangle)))
                    a, b, c = (points[index] for index in triangle)
                    area = ((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])) / 2
                    self.assertGreater(area, 0, "Caps must retain consistent winding")
                    triangle_area += area
                    edges.update(tuple(sorted(pair)) for pair in zip(triangle, triangle[1:] + triangle[:1]))
                self.assertAlmostEqual(polygon_area, triangle_area, delta=1e-10)
                boundary = {tuple(sorted((i, (i + 1) % len(points)))) for i in range(len(points))}
                self.assertEqual(boundary, {edge for edge, count in edges.items() if count == 1})
                self.assertTrue(all(count in (1, 2) for count in edges.values()))
                for scale in (3, 4, 5):
                    for axis in (0, 1):
                        low, high = min(point[axis] for point in points), max(point[axis] for point in points)
                        self.assertGreaterEqual(0.5 + low * scale, 0.5 - scale / 2 - 1e-6)
                        self.assertLessEqual(0.5 + high * scale, 0.5 + scale / 2 + 1e-6)
                        self.assertGreater(high - low, 0.9, "The continuation must retain the basin's full authored base width")

    def test_every_raised_base_top_vertex_meets_the_actual_float_fitted_underside(self) -> None:
        def f32(value: float) -> float:
            return struct.unpack("<f", struct.pack("<f", value))[0]

        checked = 0
        for style in ("gothic", "georgian", "greek"):
            with self.subTest(style=style):
                profile = load_json(DAEDALON_DATA / f"fountain_base/{style}.json")
                definition = load_json(MESH_ROOT / f"fountain_{style}_basin.json")
                self.assertTrue(definition["fit_to_block"])
                self.assertEqual([5.0, 5.0, 5.0], definition["scale"])
                self.assertEqual([0.0, 0.0, 0.0], definition["translate"])
                vertices, faces = [], []
                for line in (MESH_ROOT / f"fountain_{style}_basin.obj").read_text(encoding="utf-8").splitlines():
                    fields = line.split()
                    if fields and fields[0] == "v":
                        vertices.append(tuple(f32(float(value)) for value in fields[1:4]))
                    elif fields and fields[0] == "f":
                        faces.append(tuple(int(value.split("/")[0]) - 1 for value in fields[1:]))

                low = tuple(min(vertex[axis] for vertex in vertices) for axis in range(3))
                high = tuple(max(vertex[axis] for vertex in vertices) for axis in range(3))
                span = max(f32(high[axis] - low[axis]) for axis in range(3))
                fit = f32(1.0 / span)
                center_x = f32(f32(low[0] + high[0]) * 0.5)
                center_z = f32(f32(low[2] + high[2]) * 0.5)
                # Replay Java's float operations, rather than importing the
                # authoring tool or trusting its stored hash/height metadata.
                fitted = [
                    (
                        f32(f32(f32(f32(x - center_x) * fit) * 5.0) + 0.5),
                        f32(f32(f32(y - low[1]) * fit) * 5.0),
                        f32(f32(f32(f32(z - center_z) * fit) * 5.0) + 0.5),
                    )
                    for x, y, z in vertices
                ]
                bottom_indices = {
                    index for face in faces
                    if all(vertices[index][1] <= low[1] + span / 400 for index in face)
                    for index in face
                }
                underside = [fitted[index] for index in bottom_indices]
                self.assertEqual(len(profile["points"]), len(profile["top_y"]))
                self.assertTrue(underside)
                for index, ((x, z), top_y) in enumerate(zip(profile["points"], profile["top_y"])):
                    expected_x, expected_z = 0.5 + x * 5, 0.5 + z * 5
                    matching = [vertex[1] for vertex in underside
                                if abs(vertex[0] - expected_x) < 1e-8
                                and abs(vertex[2] - expected_z) < 1e-8]
                    self.assertTrue(matching, f"{style} boundary vertex {index} lost its authored underside join")
                    original_y = min(matching)
                    self.assertAlmostEqual(top_y * 5, original_y, delta=1e-9,
                                           msg=f"{style} boundary vertex {index} leaves a gap or penetrates the basin")
                    for size in (3.0, 4.0, 5.0):
                        # Compare the final emitted floats at all three sizes:
                        # original BasinTransform versus the new foundation.
                        raised_y = f32(f32(original_y * f32(size / 5.0)) + 1.0)
                        support_y = f32(1.0 + top_y * size)
                        self.assertAlmostEqual(raised_y, support_y, delta=2e-7)
                    checked += 1
        self.assertEqual(349, checked, "Exercise every authored boundary height across all three basin styles")

    def test_rendered_water_outline_stays_inside_the_measured_bowl(self) -> None:
        block = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(
            encoding="utf-8"
        )

        def constant(name: str) -> float:
            match = re.search(rf"{name} = ([0-9.]+)F", block)
            self.assertIsNotNone(match, name)
            return float(match.group(1))

        model_scale = constant("MEDIUM_MODEL_SCALE")
        water_y = constant("MEDIUM_WATER_SURFACE_Y")
        half_width = constant("MEDIUM_WATER_HALF_WIDTH")
        bevel = constant("MEDIUM_WATER_BEVEL")

        vertices: list[tuple[float, float, float]] = []
        faces: list[tuple[int, int, int]] = []
        for line in (MESH_ROOT / "fountain_gothic_basin.obj").read_text(
            encoding="utf-8"
        ).splitlines():
            if line.startswith("v "):
                _, x, y, z = line.split()[:4]
                vertices.append((float(x), float(y), float(z)))
            elif line.startswith("f "):
                indices = tuple(
                    int(value.split("/")[0]) - 1 for value in line.split()[1:]
                )
                self.assertEqual(3, len(indices))
                faces.append(indices)

        minimum = tuple(min(vertex[axis] for vertex in vertices) for axis in range(3))
        maximum = tuple(max(vertex[axis] for vertex in vertices) for axis in range(3))
        fit_scale = 1.0 / max(
            maximum[axis] - minimum[axis] for axis in range(3)
        )
        center_x = (minimum[0] + maximum[0]) * 0.5
        center_z = (minimum[2] + maximum[2]) * 0.5
        placed = [
            (
                0.5 + (x - center_x) * fit_scale * model_scale,
                (y - minimum[1]) * fit_scale * model_scale,
                0.5 + (z - center_z) * fit_scale * model_scale,
            )
            for x, y, z in vertices
        ]

        sections: list[tuple[tuple[float, float], tuple[float, float]]] = []
        for face in faces:
            triangle = [placed[index] for index in face]
            cuts: list[tuple[float, float]] = []
            for start_index, end_index in ((0, 1), (1, 2), (2, 0)):
                start = triangle[start_index]
                end = triangle[end_index]
                start_delta = start[1] - water_y
                end_delta = end[1] - water_y
                if start_delta * end_delta < 0.0:
                    fraction = (water_y - start[1]) / (end[1] - start[1])
                    cuts.append(
                        (
                            start[0] - 0.5 + fraction * (end[0] - start[0]),
                            start[2] - 0.5 + fraction * (end[2] - start[2]),
                        )
                    )
            if len(cuts) == 2:
                sections.append((cuts[0], cuts[1]))

        def cross(left: tuple[float, float], right: tuple[float, float]) -> float:
            return left[0] * right[1] - left[1] * right[0]

        def inner_wall_radius(x: float, z: float) -> float:
            radius = math.hypot(x, z)
            direction = (x / radius, z / radius)
            intersections: list[float] = []
            for start, end in sections:
                edge = (end[0] - start[0], end[1] - start[1])
                denominator = cross(direction, edge)
                if abs(denominator) < 1.0e-8:
                    continue
                distance = cross(start, edge) / denominator
                fraction = cross(start, direction) / denominator
                if distance >= 0.0 and -1.0e-6 <= fraction <= 1.0 + 1.0e-6:
                    intersections.append(distance)
            self.assertTrue(intersections)
            return min(intersections)

        outline = (
            (-bevel, -half_width),
            (bevel, -half_width),
            (half_width, -bevel),
            (half_width, bevel),
            (bevel, half_width),
            (-bevel, half_width),
            (-half_width, bevel),
            (-half_width, -bevel),
        )
        clearances: list[float] = []
        for index, start in enumerate(outline):
            end = outline[(index + 1) % len(outline)]
            for step in range(17):
                fraction = step / 16.0
                x = start[0] + fraction * (end[0] - start[0])
                z = start[1] + fraction * (end[1] - start[1])
                clearances.append(inner_wall_radius(x, z) - math.hypot(x, z))

        self.assertGreaterEqual(min(clearances), 0.04)

    def test_obj_preparation_path_is_supported(self) -> None:
        source = (REPO_ROOT / "tools/prepare_decor_mesh.py").read_text(
            encoding="utf-8"
        )
        self.assertIn('elif source.suffix.lower() == ".obj":', source)
        self.assertIn("bpy.ops.wm.obj_import", source)
        self.assertIn('default=15000', source)
        self.assertNotIn("source already has", source)


if __name__ == "__main__":
    unittest.main()
