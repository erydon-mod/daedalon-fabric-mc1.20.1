package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MedallionReliefMaterialsTest {
    private static final Identifier MESH = id("models/mesh/olympian_god_medallion.obj");

    @Test void reliefContrastsWithFreshAgedAndMetalBackgrounds() {
        for (String background : new String[]{"glacium", "glacium_aged", "aterzon", "aterzon_aged"}) {
            assertEquals(id("block/black"), material("block/statue_spartan_promachos_" + background));
        }
        for (String background : new String[]{"noxoplis", "noxoplis_aged", "portorium", "aganite_aged"}) {
            assertEquals(id("block/white"), material("block/statue_spartan_promachos_" + background));
        }
        assertEquals(id("block/black"), material("block/bronze"));
        assertEquals(id("block/black"), material("block/silver"));
    }

    @Test void selectedBackgroundAndOtherFamiliesKeepTheirExistingMaterialRules() {
        assertNull(MedallionReliefMaterials.override(MESH, "medallion_background", id("block/bronze")));
        assertNull(MedallionReliefMaterials.override(id("models/mesh/urn_diota.obj"), "bronze_handles", id("block/silver")));
        assertNull(MedallionReliefMaterials.override(id("models/mesh/other.obj"), "medallion_relief", id("block/glacium")));
    }

    private static Identifier material(String texture) {
        return MedallionReliefMaterials.override(MESH, "medallion_relief", id(texture));
    }

    private static Identifier id(String path) { return new Identifier("daedalon", path); }
}
