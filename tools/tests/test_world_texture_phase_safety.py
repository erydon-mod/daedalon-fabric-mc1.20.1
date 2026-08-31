import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "src/main/java/com/oliver/daedalon/client/model/obj"
TEXTURE_ROOT = ROOT / "src/main/resources/assets/daedalon/textures/block"
BAKED_MODEL = JAVA_ROOT / "ObjMeshBakedModel.java"
PHASE_LOGIC = JAVA_ROOT / "WorldTexturePhase.java"
PLUGIN = JAVA_ROOT / "ObjMeshModelLoadingPlugin.java"
ICON_CACHE = JAVA_ROOT / "ObjGuiIconCache.java"
EXTENDER = ROOT / "tools/extend_repeat_surface_textures.py"


class WorldTexturePhaseSafetyTest(unittest.TestCase):
    def test_every_variant_uses_repeat_surface_with_correct_source_window(self) -> None:
        plugin = PLUGIN.read_text(encoding="utf-8")

        self.assertIn("detailedTextureBlockId(textureBlockId)", plugin)
        self.assertIn("baseTextureBlockId(textureBlockId)", plugin)
        self.assertIn("WorldTexturePhase.detailedSurface()", plugin)
        self.assertIn("WorldTexturePhase.blockSurface()", plugin)
        self.assertIn("WorldTexturePhase.urnSurface()", plugin)
        self.assertNotIn('id("block/urn_surface/" + textureBlockId)', plugin)

    def test_block_position_selects_one_of_six_real_repeat_origins(self) -> None:
        baked = BAKED_MODEL.read_text(encoding="utf-8")
        phase = PHASE_LOGIC.read_text(encoding="utf-8")
        block_method = baked[
            baked.index("public void emitBlockQuads("):
            baked.index("public void emitItemQuads(")
        ]
        item_method = baked[
            baked.index("public void emitItemQuads("):
            baked.index("public List<BakedQuad> getQuads(")
        ]

        self.assertIn("REPEAT_PERIOD = 6", phase)
        self.assertIn("SHEET_COLUMNS = REPEAT_PERIOD * 2", phase)
        self.assertIn("Math.floorMod(x - y + z, phaseCount)", phase)
        self.assertIn(
            "worldTexturePhase.index(pos.getX(), pos.getY(), pos.getZ())",
            block_method,
        )
        self.assertIn(
            "context.pushTransform(worldTextureTransforms[worldPhaseIndex])",
            block_method,
        )
        self.assertIn("context.pushTransform(textureTransform)", item_method)
        self.assertNotIn("worldPhaseIndex", item_method)

    def test_uv_window_is_scaled_and_shifted_before_sprite_bake(self) -> None:
        baked = BAKED_MODEL.read_text(encoding="utf-8")
        transform = baked[
            baked.index("private record TextureTransform"):
            baked.index("private static final class StatueTransform")
        ]

        self.assertLess(
            transform.index("worldTexturePhase.mapU"),
            transform.index("quad.spriteBake"),
        )
        self.assertLess(
            transform.index("worldTexturePhase.mapV"),
            transform.index("quad.spriteBake"),
        )
        self.assertNotIn("quad.u(vertex) + uOffset", transform)
        self.assertNotIn("floorMod(quad", transform)

    def test_item_icons_use_phase_zero_and_dedicated_materials_remain_fixed(self) -> None:
        baked = BAKED_MODEL.read_text(encoding="utf-8")
        icon_cache = ICON_CACHE.read_text(encoding="utf-8")
        plugin = PLUGIN.read_text(encoding="utf-8")

        self.assertIn("worldTexturePhase.mapU(u, 0)", baked)
        self.assertIn("worldTexturePhase.mapV(v)", baked)
        self.assertIn("model.guiTextureU", icon_cache)
        self.assertIn("model.guiTextureV", icon_cache)
        self.assertIn("worldPhaseMaterials.add(textureId == null)", plugin)
        self.assertIn("materials.worldPhaseMaterials()", plugin)

    def test_native_repeat_surfaces_are_two_exact_six_by_six_periods(self) -> None:
        paths = sorted(TEXTURE_ROOT.glob("statue_spartan_promachos_*.png"))
        self.assertEqual(108, len(paths))
        for path in paths:
            with Image.open(path) as image:
                image.load()
                self.assertEqual((192, 96), image.size, path.name)
                left = image.crop((0, 0, 96, 96))
                right = image.crop((96, 0, 192, 96))
                self.assertEqual(left.mode, right.mode, path.name)
                self.assertEqual(left.tobytes(), right.tobytes(), path.name)

        extender = EXTENDER.read_text(encoding="utf-8")
        self.assertIn("right repeat does not exactly match", extender)
        self.assertIn("temporary.replace(path)", extender)


if __name__ == "__main__":
    unittest.main()
