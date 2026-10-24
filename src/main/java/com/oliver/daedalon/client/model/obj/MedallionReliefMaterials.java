package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import net.minecraft.util.Identifier;

import java.util.Set;

/** Finite per-variant contrast choice; no texture reads or material scans during rendering. */
final class MedallionReliefMaterials {
    private static final Identifier MESH = id("models/mesh/olympian_god_medallion.obj");
    private static final Identifier WHITE = id("block/white");
    private static final Identifier BLACK = id("block/black");
    private static final String PREFIX = "statue_spartan_promachos_";
    // Locked against both native and Collection 64x source sheets in the preparation evidence.
    private static final Set<String> LIGHT_BACKGROUNDS = Set.of(
            "aterzon", "calacattum", "glacium", "latmion", "mielonyx",
            "psamatheon", "rosinium", "selenephos", "striatus", "bronze", "silver");

    private MedallionReliefMaterials() { }

    static Identifier override(Identifier mesh, String material, Identifier variantTexture) {
        if (!MESH.equals(mesh) || !"medallion_relief".equals(material)) return null;
        String name = variantTexture.getPath();
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.startsWith(PREFIX)) name = name.substring(PREFIX.length());
        if (name.endsWith("_aged")) name = name.substring(0, name.length() - "_aged".length());
        return LIGHT_BACKGROUNDS.contains(name) ? BLACK : WHITE;
    }

    private static Identifier id(String path) { return new Identifier(Daedalon.MOD_ID, path); }
}
