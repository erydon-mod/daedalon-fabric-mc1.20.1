from __future__ import annotations

import unittest

from daedalon_test_support import JAVA_ROOT, RESOURCES, load_json


class DebugStickPropertyOrderSafetyTests(unittest.TestCase):
    def test_daedalon_controls_are_ordered_and_remembered_across_models(self) -> None:
        helper = (JAVA_ROOT / "item/DaedalonDebugProperties.java").read_text(encoding="utf-8")
        mixin = (JAVA_ROOT / "mixin/DebugStickItemMixin.java").read_text(encoding="utf-8")
        mixins = load_json(RESOURCES / "daedalon.mixins.json")["mixins"]

        self.assertIn('case "size" -> 0', helper)
        self.assertIn('case "offset" -> 1', helper)
        self.assertIn('case "facing" -> 2', helper)
        self.assertIn('"DaedalonDebugProperty"', helper)
        self.assertIn("DebugStickItemMixin", mixins)
        self.assertIn("getOrCreateNbt().getString(", mixin)
        self.assertIn("getOrCreateNbt().putString(", mixin)
        self.assertNotIn("Registries.BLOCK.getId(state.getBlock()).toString(),\n                    selected.getName()", mixin)
        self.assertIn("Daedalon.MOD_ID.equals", mixin)
        self.assertIn("DaedalonDebugProperties.orderedValues(property)", mixin)
        self.assertIn("public static <T> T cycle(List<T> values", helper)
        self.assertIn("values.indexOf(current)", helper)
        self.assertIn('TIER_CONTROL = "tiers"', mixin)
        self.assertIn('"bowls".equals(rememberedName)', mixin)
        self.assertIn('"size".equals(rememberedName)', mixin)
        self.assertIn(
            "DaedalonDebugProperties.cycle(controls, selected, backwards)",
            mixin,
        )
        self.assertNotIn("Util.next(", mixin)
        self.assertIn('case "small" -> 0', helper)
        self.assertIn('case "medium" -> 1', helper)
        self.assertIn('case "large" -> 2', helper)
        self.assertIn('case "north" -> 0', helper)
        self.assertIn('case "east" -> 1', helper)
        self.assertIn('case "south" -> 2', helper)
        self.assertIn('case "west" -> 3', helper)
        self.assertIn("if (anchorPos == null)", mixin)
        self.assertIn("callback.setReturnValue(false)", mixin)

    def test_source_declarations_follow_the_same_semantic_order(self) -> None:
        statue = (JAVA_ROOT / "block/StatueBlock.java").read_text(encoding="utf-8")
        urn = (JAVA_ROOT / "block/UrnBlock.java").read_text(encoding="utf-8")
        corbel = (JAVA_ROOT / "block/CorbelBlock.java").read_text(encoding="utf-8")
        fountain_basin = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(encoding="utf-8")
        sized = (JAVA_ROOT / "block/SizedDecorBlock.java").read_text(encoding="utf-8")
        two_size = (JAVA_ROOT / "block/TwoSizeDecorBlock.java").read_text(encoding="utf-8")
        plinth = (JAVA_ROOT / "block/PlinthBlock.java").read_text(encoding="utf-8")

        self.assertIn("builder.add(SIZE, OFFSET, FACING)", statue)
        self.assertIn("builder.add(SIZE, OFFSET, FACING)", urn)
        self.assertIn("builder.add(SIZE, FACING)", corbel)
        self.assertIn("builder.add(SIZE, WATERLOGGED)", fountain_basin)
        self.assertIn("builder.add(SIZE, OFFSET, FACING, WATERLOGGED)", plinth)
        self.assertIn("supportsOffsetAndFacing()", two_size)

        for source in (statue, urn, corbel, fountain_basin, sized, plinth):
            self.assertLess(source.index('SMALL("small"'), source.index('MEDIUM("medium"'))
            self.assertLess(source.index('MEDIUM("medium"'), source.index('LARGE("large"'))
        self.assertLess(two_size.index('SMALL("small"'), two_size.index('LARGE("large"'))


if __name__ == "__main__":
    unittest.main()
