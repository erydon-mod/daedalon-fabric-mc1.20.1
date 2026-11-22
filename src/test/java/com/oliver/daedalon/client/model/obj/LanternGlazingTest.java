package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class LanternGlazingTest {
    @Test
    void onlyTheDedicatedPaneSpriteReceivesGlassClassification() {
        assertTrue(LanternGlazing.isGlazingTexture(new Identifier("daedalon", "block/lantern_glazing")));
        assertFalse(LanternGlazing.isGlazingTexture(LanternGlazing.LEAD_TEXTURE));
        assertFalse(LanternGlazing.isGlazingTexture(new Identifier("daedalon", "block/bronze")));
        assertFalse(LanternGlazing.isGlazingTexture(new Identifier("daedalon", "block/latmion_block")));
        assertFalse(LanternGlazing.isGlazingTexture(null));
    }

    @Test
    void proxyUsesTheActualShaderMapWithoutChangingStoneOrMetalMappings() {
        var ids = new Object2IntOpenHashMap<String>();
        ids.defaultReturnValue(-1);
        ids.put("erydon_glass", 12057);
        ids.put("vanilla_glass", 10095);
        ids.put("lantern_stone", 12031);
        ids.put("lead", 5000);
        var before = new Object2IntOpenHashMap<>(ids);

        assertEquals(12057, LanternGlazing.selectProxyId(ids, "erydon_glass", "vanilla_glass", 17));
        ids.put("erydon_glass", 23017);
        assertEquals(23017, LanternGlazing.selectProxyId(ids, "erydon_glass", "vanilla_glass", 17));
        ids.put("erydon_glass", 12057);
        assertEquals(before, ids, "Glass classification must never rewrite a block-state material map");
    }

    @Test
    void missingOptionalErydonMappingFallsBackToVanillaThenOriginalIdentity() {
        var ids = new Object2IntOpenHashMap<String>();
        ids.defaultReturnValue(-1);
        ids.put("vanilla_glass", 2311);
        assertEquals(2311, LanternGlazing.selectProxyId(ids, null, "vanilla_glass", 17));
        assertEquals(2311, LanternGlazing.selectProxyId(ids, "missing_erydon", "vanilla_glass", 17));
        ids.put("missing_erydon", -1);
        assertEquals(2311, LanternGlazing.selectProxyId(ids, "missing_erydon", "vanilla_glass", 17));
        ids.removeInt("vanilla_glass");
        assertEquals(17, LanternGlazing.selectProxyId(ids, "missing_erydon", "vanilla_glass", 17));
        assertEquals(17, LanternGlazing.selectProxyId(null, "missing_erydon", "vanilla_glass", 17));
        ids.put("vanilla_glass", 0);
        assertEquals(0, LanternGlazing.selectProxyId(ids, null, "vanilla_glass", 17),
                "An explicitly mapped zero remains the shader pack's own decision");
    }

    @Test
    void nativeCrystalGlassAndLeadRemainExactAuthoredErydonCopies() throws Exception {
        var glass = texture("lantern_glazing.png",
                "54177c079f597b4f1025e3e956ac52b28ee5f6e73a38be0f564ab7614addb515", 16);
        var specular = texture("lantern_glazing_s.png",
                "4f6f4342e8664dd6ab3bf1da502437e42f4266adbcd66807c430cbde666b0a55", 16);
        var lead = texture("lantern_lead.png",
                "6646e771b925129e929c79b1db09cd999a21d0869c519d5b40a8915e15aef753", 64);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(0x14A0BED2, glass.getRGB(x, y));
                assertEquals(0xFFFF0000, specular.getRGB(x, y));
            }
        }
        for (int y = 0; y < 64; y++) {
            // Retain the native artist's 254/255 edge variation; solid frame
            // rendering does not turn this nearly opaque lead into glass.
            for (int x = 0; x < 64; x++) assertTrue((lead.getRGB(x, y) >>> 24) >= 254);
        }
    }

    private static java.awt.image.BufferedImage texture(String name, String hash, int size)
            throws IOException, NoSuchAlgorithmException {
        Path path = Path.of("src/main/resources/assets/daedalon/textures/block", name);
        assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        var image = ImageIO.read(path.toFile());
        assertNotNull(image);
        assertEquals(size, image.getWidth());
        assertEquals(size, image.getHeight());
        return image;
    }
}
