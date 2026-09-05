from __future__ import annotations

import re
import unittest

from daedalon_test_support import JAVA_ROOT, REPO_ROOT, RESOURCES, all_block_ids, load_json


class GuiItemPerformanceSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        obj_root = JAVA_ROOT / "client/model/obj"
        cls.baked = (obj_root / "ObjMeshBakedModel.java").read_text(
            encoding="utf-8"
        )
        cls.cache = (obj_root / "ObjGuiIconCache.java").read_text(
            encoding="utf-8"
        )
        cls.template = (obj_root / "ObjGuiIconTemplate.java").read_text(
            encoding="utf-8"
        )
        cls.rasterizer = (obj_root / "ObjGuiRasterizer.java").read_text(
            encoding="utf-8"
        )
        cls.plugin = (obj_root / "ObjMeshModelLoadingPlugin.java").read_text(
            encoding="utf-8"
        )
        cls.mixin = (
            JAVA_ROOT / "mixin/client/ItemRendererMixin.java"
        ).read_text(encoding="utf-8")
        cls.axiom_mixin = (
            JAVA_ROOT / "mixin/client/axiom/BlockRenderCacheMixin.java"
        ).read_text(encoding="utf-8")
        cls.mixin_plugin = (
            JAVA_ROOT / "mixin/DaedalonMixinPlugin.java"
        ).read_text(encoding="utf-8")
        cls.client = (JAVA_ROOT / "DaedalonClient.java").read_text(
            encoding="utf-8"
        )

    @staticmethod
    def integer_constant(source: str, name: str) -> int:
        match = re.search(rf"\b{name}\s*=\s*(\d+)", source)
        if match is None:
            raise AssertionError(f"Missing integer constant {name}")
        return int(match.group(1))

    def test_gui_cache_is_narrow_and_complete_mesh_is_the_fallback(self) -> None:
        mixins = load_json(RESOURCES / "daedalon.mixins.json")["client"]
        self.assertIn("client.ItemRendererMixin", mixins)
        self.assertIn("ObjGuiIconCache.register()", self.client)
        self.assertIn("priority = 1100", self.mixin)
        self.assertIn("MatrixStack;push()V", self.mixin)
        self.assertIn("cancellable = true", self.mixin)
        self.assertIn("ObjGuiIconCache.tryRender(", self.mixin)
        self.assertIn("callbackInfo.cancel()", self.mixin)
        self.assertIn(
            "RenderLayer.getEntityTranslucent(cell.atlasId(), false)",
            self.cache,
        )
        self.assertNotIn("RenderLayer.getText(cell.atlasId())", self.cache)
        self.assertIn(".overlay(overlay)", self.cache)
        self.assertIn(".light(LightmapTextureManager.MAX_LIGHT_COORDINATE)", self.cache)
        self.assertIn(".normal(normalMatrix, 0.0F, 1.0F, 0.0F)", self.cache)

        item_method = self.baked[
            self.baked.index("public void emitItemQuads(") :
            self.baked.index("public List<BakedQuad> getQuads(")
        ]
        self.assertIn("mesh.outputTo(context.getEmitter())", item_method)
        self.assertNotIn("guiItemMesh", self.baked)
        self.assertNotIn("bakeGuiItemPreview", self.baked)
        self.assertNotIn("BitSet visibleFaces", self.baked)
        self.assertFalse(
            (JAVA_ROOT / "client/model/obj/ObjItemRenderContext.java").exists()
        )

        self.assertIn("renderMode != ModelTransformationMode.GUI", self.cache)
        self.assertIn("|| leftHanded", self.cache)
        self.assertIn("overlay != OverlayTexture.DEFAULT_UV", self.cache)
        self.assertIn("stack.hasGlint()", self.cache)
        self.assertIn("objModel.guiIconTemplate() == null", self.cache)
        self.assertIn("return false;", self.cache)
        self.assertIn("using complete-mesh fallback", self.cache)

    def test_axiom_palette_reuses_the_bounded_gui_icon_path(self) -> None:
        mixins = load_json(RESOURCES / "daedalon.mixins.json")["client"]
        self.assertIn("client.axiom.BlockRenderCacheMixin", mixins)
        self.assertIn('loader.isModLoaded("axiom")', self.mixin_plugin)
        self.assertIn(
            'targets = "com.moulberry.axiom.render.BlockRenderCache"',
            self.axiom_mixin,
        )
        self.assertIn('method = "shouldRenderAsItem"', self.axiom_mixin)
        self.assertIn("cancellable = true", self.axiom_mixin)
        self.assertIn("Registries.ITEM.getId(stack.getItem())", self.axiom_mixin)
        self.assertIn("callbackInfo.setReturnValue(true)", self.axiom_mixin)

    def test_each_family_template_rasterizes_every_baked_quad(self) -> None:
        self.assertIn("mesh.forEach(quad ->", self.template)
        self.assertIn("ProjectionBounds.measure", self.template)
        self.assertIn("FRAME_PADDING = SUPERSAMPLE", self.template)
        self.assertIn("visitedQuads[0] != expectedQuadCount", self.template)
        self.assertIn("int sourceQuadCount()", self.template)
        self.assertIn(
            "ShaderTerrainNormalBridge.decodeObjMaterialTag(quad.tag())",
            self.template,
        )
        self.assertIn("rasterizer.rasterize(a, b, c, materialTag)", self.template)
        self.assertIn("rasterizer.rasterize(a, c, d, materialTag)", self.template)
        self.assertIn("private ObjGuiIconTemplate guiIconTemplate;", self.plugin)
        self.assertIn(
            "guiTransformation.equals(requestedGuiTransformation)", self.plugin
        )
        self.assertIn("ObjGuiIconTemplate.create(", self.plugin)
        self.assertIn("baked.emittedQuadCount()", self.plugin)
        self.assertIn("guiIconTemplate.sourceQuadCount()", self.plugin)
        self.assertIn("geometry.guiIconTemplate()", self.plugin)
        self.assertIn("Float.NEGATIVE_INFINITY", self.rasterizer)
        self.assertIn("candidateDepth <= depth[index]", self.rasterizer)

    def test_shared_geometry_releases_parsed_obj_data_after_baking(self) -> None:
        shared_geometry = self.plugin[
            self.plugin.index("private static final class SharedGeometry") :
            self.plugin.index("private static final class PreparedModel")
        ]
        bake_method = shared_geometry[
            shared_geometry.index("private synchronized ObjMeshBakedModel.GeometryBakeResult bake(") :
            shared_geometry.index("private ObjGuiIconTemplate guiIconTemplate()")
        ]

        self.assertIn("private ObjMeshData data;", shared_geometry)
        self.assertNotIn("private final ObjMeshData data;", shared_geometry)
        self.assertIn(
            "private final Map<String, Identifier> materialTextures;",
            shared_geometry,
        )
        self.assertIn("this.materialTextures = data.materialTextures();", shared_geometry)
        self.assertIn("ObjMeshData sourceData = Objects.requireNonNull(", bake_method)
        self.assertIn(
            "baked = ObjMeshBakedModel.bakeGeometry(definition, sourceData);",
            bake_method,
        )
        self.assertIn("data = null;", bake_method)
        self.assertLess(
            bake_method.index("baked = ObjMeshBakedModel.bakeGeometry("),
            bake_method.index("data = null;"),
        )
        self.assertNotIn(
            "data.", bake_method[bake_method.index("data = null;") :]
        )
        self.assertIn(
            "return textureId == null ? materialTextures.get(materialName) : textureId;",
            shared_geometry,
        )

    def test_normal_mesh_diagnostics_do_not_pollute_player_logs(self) -> None:
        self.assertIn(
            'Daedalon.LOGGER.debug(\n'
            '                        "[Daedalon OBJ Mesh] prepared',
            self.plugin,
        )
        diagnostics = self.plugin[
            self.plugin.index("String mtl =") :
            self.plugin.index("return baked;", self.plugin.index("String mtl ="))
        ]
        self.assertIn("shared geometry cache MISS", diagnostics)
        self.assertIn("extends outside one block", diagnostics)
        self.assertEqual(2, diagnostics.count("Daedalon.LOGGER.debug("))
        self.assertNotIn("Daedalon.LOGGER.info(", diagnostics)
        self.assertNotIn("Daedalon.LOGGER.warn(", diagnostics)

    def test_lazy_atlas_is_bounded_for_every_generated_obj_variant(self) -> None:
        page_size = self.integer_constant(self.cache, "ATLAS_PAGE_SIZE")
        gutter = self.integer_constant(self.cache, "ICON_GUTTER")
        max_pages = self.integer_constant(self.cache, "MAX_ATLAS_PAGES")
        icon_size = self.integer_constant(self.template, "ICON_RESOLUTION")
        cells_per_row = page_size // (icon_size + gutter * 2)
        capacity = cells_per_row * cells_per_row * max_pages

        self.assertEqual(2048, page_size)
        self.assertEqual(64, icon_size)
        self.assertEqual(5, max_pages)
        self.assertEqual(64, icon_size * self.integer_constant(self.template, "SUPERSAMPLE"))
        self.assertGreaterEqual(capacity, len(all_block_ids()))
        self.assertIn("nextCellIndex >= MAX_CACHED_ICONS", self.cache)
        self.assertIn("cells.put(model, created)", self.cache)
        self.assertIn("try (NativeImage stagedCell", self.cache)
        self.assertLess(
            self.cache.index("page.copyCell(stagedCell, cellX, cellY)"),
            self.cache.index("page.uploadCell(cellX, cellY)"),
        )
        self.assertIn("NativeImageBackedTexture", self.cache)
        self.assertNotIn("Framebuffer", self.cache)
        self.assertNotIn("ShaderProgram", self.cache)
        self.assertNotIn("VertexBuffer", self.cache)

    def test_icons_use_vanilla_lighting_and_display_transforms_are_complete(self) -> None:
        display_generator = REPO_ROOT / "tools/generate_obj_display_models.py"
        self.assertTrue(display_generator.is_file())
        self.assertIn("LIGHT_POWER = 0.6F", self.rasterizer)
        self.assertIn("AMBIENT_LIGHT = 0.4F", self.rasterizer)
        self.assertIn("KEY_LIGHT_X = 0.2F", self.rasterizer)
        self.assertIn("FILL_LIGHT_X = -0.2F", self.rasterizer)
        self.assertIn("LightmapTextureManager.MAX_LIGHT_COORDINATE", self.cache)
        self.assertIn("SRGB_TO_LINEAR[red] * shade", self.cache)
        self.assertIn("createSrgbToLinearTable()", self.cache)
        self.assertIn("linearToSrgb(redPremultiplied / alphaSum)", self.cache)
        self.assertIn("texture.setFilter(true, false)", self.cache)
        self.assertNotIn("OUTLINE", self.cache)
        self.assertNotIn("gradeIconChannel", self.cache)
        self.assertIn("return image.getColor(x, y)", self.cache)
        self.assertNotIn("private static int blend(", self.cache)

        display_root = RESOURCES / "assets/daedalon/models/block/mesh"
        contexts = {
            "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand",
            "ground", "gui", "fixed",
        }
        for path in display_root.glob("*_display.json"):
            display = load_json(path)["display"]
            self.assertEqual(contexts, set(display), path.name)
            if path.name == "corbel_display.json":
                self.assertEqual(10, display["gui"]["rotation"][1])
                self.assertEqual(0.125, display["thirdperson_righthand"]["scale"][0])
                self.assertEqual(0.13, display["firstperson_righthand"]["scale"][0])
            elif path.name.startswith("urn_") or path.name in {
                "capital_display.json", "plinth_display.json",
            }:
                self.assertEqual(190, display["gui"]["rotation"][1])
            elif path.name == "krene_display.json":
                self.assertEqual(10, display["gui"]["rotation"][1])
            if path.name in {"finial_display.json", "plinth_display.json"}:
                self.assertEqual(0.1875, display["thirdperson_righthand"]["scale"][0])
                self.assertEqual(0.2, display["firstperson_righthand"]["scale"][0])

    def test_active_pack_textures_and_reload_cleanup_are_mandatory(self) -> None:
        self.assertIn("manager.getResource(pngId)", self.cache)
        self.assertIn('"textures/" + path + ".png"', self.cache)
        self.assertIn('pngId.getPath() + ".mcmeta"', self.cache)
        self.assertIn("image.getOpacity(x, y)", self.cache)
        self.assertIn("u < -UV_EPSILON", self.cache)
        self.assertIn("v > 1.0F + UV_EPSILON", self.cache)
        self.assertIn("ResourceReloadListenerKeys.TEXTURES", self.cache)
        self.assertIn("ResourceReloadListenerKeys.MODELS", self.cache)
        self.assertIn("textureManager.destroyTexture(page.id())", self.cache)
        self.assertIn("sourceTexture.close()", self.cache)
        self.assertIn("failedModels.clear()", self.cache)


if __name__ == "__main__":
    unittest.main()
