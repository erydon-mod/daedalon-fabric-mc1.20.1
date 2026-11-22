package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ObjGuiIconLanternTextureTest {
    @Test void onlyTheAuthoredLanternRolesAcceptAlphaInTheSharedIconCache() {
        for (var texture : List.of(LanternGlazing.TEXTURE, LanternGlazing.LEAD_TEXTURE, LanternFlame.TEXTURE))
            assertTrue(ObjGuiIconCache.allowsTransparentSource(texture), texture.toString());
        for (var texture : List.of("daedalon:block/bronze", "daedalon:block/silver",
                "daedalon:block/statue_spartan_promachos_aganite", "minecraft:block/glass",
                "other:block/lantern_glazing", "daedalon:block/lantern_glazing_s"))
            assertFalse(ObjGuiIconCache.allowsTransparentSource(new Identifier(texture)), texture);
    }

    @Test void actualLanternSourcesKeepTheirAlphaAndColourInTheRasterSampler() throws Exception {
        for (String name : List.of("lantern_glazing", "lantern_lead", "lantern_flame")) {
            var image = ImageIO.read(Path.of("src/main/resources/assets/daedalon/textures/block", name + ".png").toFile());
            assertNotNull(image);
            int[] pixels = new int[image.getWidth() * image.getHeight()];
            boolean foundAlpha = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixels[y * image.getWidth() + x] = (argb & 0xFF00FF00) | (argb >>> 16 & 0xFF) | ((argb & 0xFF) << 16);
                foundAlpha |= argb >>> 24 != 255;
            }
            assertTrue(foundAlpha, "This source reproduces the former opaque-only cache rejection: " + name);
            var source = new ObjGuiIconCache.SourceTexture(image.getWidth(), image.getHeight(), pixels);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                assertEquals(pixels[y * image.getWidth() + x],
                        source.sample(x / (float) (image.getWidth() - 1), y / (float) (image.getHeight() - 1)));
        }
    }
}
