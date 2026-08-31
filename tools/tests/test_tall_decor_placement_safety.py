from __future__ import annotations

import unittest

from daedalon_test_support import JAVA_ROOT, RESOURCES, load_json


class TallDecorPlacementSafetyTests(unittest.TestCase):
    def test_all_block_items_respect_extended_daedalon_outlines(self) -> None:
        mixins = load_json(RESOURCES / "daedalon.mixins.json")
        self.assertIn("BlockItemMixin", mixins["mixins"])
        self.assertIn("ServerPlayNetworkHandlerMixin", mixins["mixins"])

        mixin = (JAVA_ROOT / "mixin/BlockItemMixin.java").read_text(encoding="utf-8")
        server_mixin = (
            JAVA_ROOT / "mixin/ServerPlayNetworkHandlerMixin.java"
        ).read_text(encoding="utf-8")
        helper = (JAVA_ROOT / "block/TallDecorPlacement.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("@Mixin(BlockItem.class)", mixin)
        self.assertIn(
            'method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;"',
            mixin,
        )
        self.assertIn('at = @At("HEAD")', mixin)
        self.assertIn("argsOnly = true", mixin)
        self.assertIn("context.getSide() != Direction.UP", helper)
        self.assertIn("Daedalon.MOD_ID.equals", helper)
        self.assertIn("findTallSupport(context)", helper)
        self.assertIn("HORIZONTAL_SEARCH_RADIUS = 3", helper)
        self.assertIn("VERTICAL_SEARCH_DEPTH = 8", helper)
        self.assertIn("candidateState.getOutlineShape", helper)
        self.assertIn("containsHorizontalHit", helper)
        self.assertIn("Math.ceil(maximumY - HEIGHT_EPSILON)", helper)
        self.assertIn("this.canReplaceExisting = false", helper)
        self.assertIn("@Mixin(ServerPlayNetworkHandler.class)", server_mixin)
        self.assertIn('method = "onPlayerInteractBlock"', server_mixin)
        self.assertIn(
            "TallDecorPlacement.serverValidationDelta",
            server_mixin,
        )
        self.assertIn("state.getOutlineShape", helper)
        self.assertIn("containsHit(outline, registeredPos, hit)", helper)
        self.assertIn("clampForVanillaPacketCheck", helper)


if __name__ == "__main__":
    unittest.main()
