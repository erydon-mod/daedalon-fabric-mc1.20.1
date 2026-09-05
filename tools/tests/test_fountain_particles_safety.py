from __future__ import annotations

import unittest

from PIL import Image

from daedalon_test_support import DAEDALON_ASSETS, JAVA_ROOT, load_json


class FountainParticleSafetyTests(unittest.TestCase):
    def test_client_only_registration_and_dedicated_sprites(self):
        client = (JAVA_ROOT / "DaedalonClient.java").read_text()
        common = (JAVA_ROOT / "Daedalon.java").read_text()
        self.assertIn("FountainParticles.register()", client)
        self.assertNotIn("client.fountain", common)
        self.assertIn("ModParticles.register()", common)
        sprites = load_json(DAEDALON_ASSETS / "particles/fountain_drop.json")["textures"]
        self.assertEqual(["daedalon:fountain_drop", "daedalon:fountain_ripple"], sprites)

    def test_particle_art_is_64px_transparent_and_neutral(self):
        for name in ("fountain_drop", "fountain_ripple"):
            with Image.open(DAEDALON_ASSETS / f"textures/particle/{name}.png") as source:
                self.assertEqual((64, 64), source.size)
                self.assertEqual("RGBA", source.mode)
                alpha = source.getchannel("A")
                for x in range(64):
                    self.assertEqual(0, alpha.getpixel((x, 0)))
                    self.assertEqual(0, alpha.getpixel((x, 63)))
                    self.assertEqual(0, alpha.getpixel((0, x)))
                    self.assertEqual(0, alpha.getpixel((63, x)))
                visible = [pixel for pixel in source.getdata() if pixel[3] >= 32]
                self.assertTrue(visible)
                self.assertLessEqual(max(max(p[:3]) - min(p[:3]) for p in visible), 12)
                if name == "fountain_ripple":
                    self.assertLessEqual(alpha.getpixel((32, 32)), 32, "Ripple needs an open centre")
                    left, top, right, bottom = alpha.point(lambda value: 255 if value >= 32 else 0).getbbox()
                    self.assertLessEqual(abs((right - left) - (bottom - top)), 4, "Ripple must be circular")
                    self.assertLessEqual(abs(left + right - 64), 4)
                    self.assertLessEqual(abs(top + bottom - 64), 4)

    def test_landing_keeps_the_droplet_tint_and_uses_a_neutral_sprite(self):
        source = (JAVA_ROOT / "client/fountain/FountainDropParticle.java").read_text()
        self.assertEqual(1, source.count("setColor("))
        self.assertIn("setSprite(sprites.getSprite(0, 1))", source)
        self.assertIn("setSprite(sprites.getSprite(1, 1))", source)
        self.assertNotIn("getSprite(splashAge", source)
        self.assertIn("setAlpha(0.85F - splashAge * 0.18F)", source)
        self.assertIn("FountainRippleGeometry.emit(", source)
        self.assertIn("super.buildGeometry(vertices, camera, tickDelta)", source)
        geometry = (JAVA_ROOT / "client/fountain/FountainRippleGeometry.java").read_text()
        for forbidden in ("getRotation", "Quaternion", "new Vector", "FACING", "RenderSystem"):
            self.assertNotIn(forbidden, geometry)

    def test_controller_is_bounded_and_does_not_tick_the_server_or_scan_chunks(self):
        source = (JAVA_ROOT / "client/fountain/FountainParticles.java").read_text()
        for constant in ("MAX_PARTICLES = 768", "MAX_PER_FOUNTAIN = 320",
                         "MAX_SPAWNS_PER_TICK = 48", "MAX_SOURCE_CHECKS = 64"):
            self.assertIn(constant, source)
        for required in ("BLOCK_ENTITY_LOAD.register", "BLOCK_ENTITY_UNLOAD.register",
                         "DISCONNECT.register", "source.basin.layout(state).bowls()",
                         "ParticlesMode.MINIMAL", "ParticlesMode.DECREASED",
                         "client.isPaused()", "source.matches()", "drop.generation",
                         "tick - drop.born < FountainSprayPlan.MAX_AGE",
                         "state.get(FountainBasinBlock.WATERLOGGED)"):
            self.assertIn(required, source)
        for required in ("source.plan.emissionsPerTick()", "source.plan.emitterForEmission(source.emitter)",
                         "FountainSprayPlan.dropletScale(world.random.nextFloat())",
                         "cadence / 2", "MAX_SPAWNS_PER_TICK / 2",
                         "emitter.landing().contains(x, z)"):
            self.assertIn(required, source)
        for forbidden in ("ServerTick", "getChunk(", "iterateOutwards", "setBlockState",
                          "scheduleFluidTick", "net.diebuddies", "Thread("):
            self.assertNotIn(forbidden, source)

    def test_particle_motion_has_no_world_collision_or_shape_rebuild(self):
        source = (JAVA_ROOT / "client/fountain/FountainDropParticle.java").read_text()
        self.assertIn("FountainSprayPlan.velocityAfterTick(velocityY)", source)
        self.assertIn("nextY <= landingY", source)
        self.assertIn("splashAge >= 4", source)
        self.assertIn("PARTICLE_SHEET_TRANSLUCENT", source)
        self.assertIn("scale = dropletScale", source)
        self.assertIn("FountainSprayPlan.splashScale(dropletScale, splashAge)", source)
        for forbidden in ("getCollisionShape", "getBlockEntity", "getBlockState", "VoxelShapes",
                          "setBlockState", "containedWaterTag", "Thread("):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
