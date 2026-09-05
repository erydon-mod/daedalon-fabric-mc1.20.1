from __future__ import annotations

import unittest

from daedalon_test_support import JAVA_ROOT


class FountainProxyLifecycleSafetyTests(unittest.TestCase):
    def test_chunk_load_never_reenters_the_chunk_manager_to_rebuild_parts(self) -> None:
        entity = (
            JAVA_ROOT / "block/entity/FountainBasinBlockEntity.java"
        ).read_text(encoding="utf-8")
        registry = (
            JAVA_ROOT / "registry/ModBlockEntities.java"
        ).read_text(encoding="utf-8")

        read_nbt = entity[entity.index("public synchronized void readNbt"):entity.index(
            "private void readLegacyComponents"
        )]
        self.assertIn("invalidateLayout();", read_nbt)
        self.assertIn("currentWorld.updateListeners", read_nbt)
        self.assertNotIn("refreshAssembly", read_nbt)
        self.assertNotIn("getBlockEntity", read_nbt)

        self.assertNotIn("ServerBlockEntityEvents", registry)
        self.assertNotIn("BLOCK_ENTITY_LOAD", registry)
        changed = entity[entity.index("private void changed()"):]
        self.assertIn("FountainBasinBlock.refreshAssembly(currentWorld, pos, state)", changed)
        self.assertIn("currentWorld.updateListeners(pos, state, state, 3)", changed)
        self.assertNotIn("getChunkManager().markForUpdate", changed)
        for forbidden in ("getTicker(", "serverTick(", "scheduleBlockTick("):
            self.assertNotIn(forbidden, entity)

    def test_initial_sync_and_validated_layout_work_are_not_duplicated(self) -> None:
        basin = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(
            encoding="utf-8"
        )
        entity = (
            JAVA_ROOT / "block/entity/FountainBasinBlockEntity.java"
        ).read_text(encoding="utf-8")

        initial_sync = basin[basin.index("public void onBlockAdded"):basin.index(
            "public ActionResult onUse"
        )]
        self.assertEqual(1, initial_sync.count("syncParts(world, pos, state)"))
        self.assertNotIn("public void onPlaced", basin)
        self.assertIn("canOccupyAssembly(world, anchorPos, layout)", basin)

        self.assertEqual(
            3,
            entity.count("FountainAssemblyLayout candidateLayout = createLayout("),
        )
        self.assertEqual(
            3,
            entity.count("canOccupyAssembly(world, pos, candidateLayout)"),
        )
        self.assertEqual(3, entity.count("changed(candidateLayout);"))
        changed = entity[entity.index("private void changed(FountainAssemblyLayout"):]
        self.assertLess(changed.index("cacheLayout(state, validatedLayout)"), changed.index(
            "FountainBasinBlock.refreshAssembly(currentWorld, pos, state)"
        ))

    def test_basin_state_changes_prime_and_reuse_one_candidate_layout(self) -> None:
        basin = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(
            encoding="utf-8"
        )
        entity = (
            JAVA_ROOT / "block/entity/FountainBasinBlockEntity.java"
        ).read_text(encoding="utf-8")

        entity_prepare = entity[entity.index("public boolean prepareStateChange"):
                                entity.index("public FountainAssemblyLayout layout")]
        self.assertIn("candidateLayout = layout(basinState)", entity_prepare)
        self.assertNotIn("createLayout(", entity_prepare)
        self.assertEqual(1, entity_prepare.count("canOccupyAssembly("))

        block_prepare = basin[basin.index("private boolean prepareStateChange"):
                              basin.index("public static boolean canOccupyAssembly")]
        self.assertIn("basin.prepareStateChange(world, updatedState)", block_prepare)

        size_change = basin[basin.index("public boolean canChangeSize"):
                            basin.index("public static VoxelShape collisionShape")]
        self.assertIn("return prepareStateChange(world, pos, updatedState);", size_change)
        self.assertNotIn("canOccupyAssembly(", size_change)

        can_fill = basin[basin.index("public boolean canFillWithFluid"):
                         basin.index("public boolean tryFillWithFluid")]
        try_fill = basin[basin.index("public boolean tryFillWithFluid"):
                         basin.index("public VoxelShape getOutlineShape")]
        self.assertIn("prepareStateChange(world, pos, filledState)", can_fill)
        self.assertIn("prepareStateChange(world, pos, filledState)", try_fill)
        self.assertNotIn("canOccupyParts(", can_fill)
        self.assertNotIn("canOccupyParts(", try_fill)

        layout = entity[entity.index("public FountainAssemblyLayout layout"):
                        entity.index("public VoxelShape collisionPartShape")]
        self.assertIn("matchesStructure(cache, basinState)", layout)
        self.assertIn("private volatile LayoutCache cachedLayouts", entity)
        self.assertIn("private synchronized FountainAssemblyLayout buildMissingLayout", entity)
        self.assertIn("cache.select(!filled).withWaterlogged(filled)", entity)
        self.assertIn("cache.plinth() == plinthState", entity)
        self.assertIn("cache.bowlCount() == bowlCount", entity)
        for mutation in (
            "AdditionResult addPlinthUse", "boolean replacePlinth", "boolean replaceTierCount",
            "BlockState removeTopPlinthUse", "void readNbt",
        ):
            self.assertIn("public synchronized " + mutation, entity)

        assembly = (JAVA_ROOT / "block/FountainAssemblyLayout.java").read_text(encoding="utf-8")
        water_change = assembly[assembly.index("public FountainAssemblyLayout withWaterlogged"):
                                assembly.index("public static int maximumBowlCount")]
        self.assertIn("collisionParts, updatedOutline, maximumY", water_change)
        self.assertNotIn("collisionShape(", water_change)
        self.assertNotIn("copyCollisionPartShapes(", water_change)

    def test_proxy_transforms_only_rewrite_horizontal_offsets(self) -> None:
        part = (JAVA_ROOT / "block/FountainBasinPartBlock.java").read_text(
            encoding="utf-8"
        )
        transforms = part[part.index("public BlockState rotate"):part.index(
            "public VoxelShape getOutlineShape"
        )]
        self.assertIn("FountainPartOffsetTransform.rotatedX", transforms)
        self.assertIn("FountainPartOffsetTransform.rotatedZ", transforms)
        self.assertIn("FountainPartOffsetTransform.mirroredX", transforms)
        self.assertIn("FountainPartOffsetTransform.mirroredZ", transforms)
        self.assertNotIn(".with(OFFSET_Y", transforms)


if __name__ == "__main__":
    unittest.main()
