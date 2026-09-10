package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.client.render.model.json.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

final class ObjGuiPreviewResolutionTest {
    @Test void previewRasterUses256PixelsWhileInventoryRemains64() {
        float[][] points = {{0,0,0},{1,0,0},{1,1,0},{0,1,0}};
        QuadView quad = (QuadView) Proxy.newProxyInstance(QuadView.class.getClassLoader(),
                new Class<?>[]{QuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x", "y", "z" -> points[(int)args[0]]["xyz".indexOf(method.getName())];
                    case "u" -> points[(int)args[0]][0];
                    case "v" -> points[(int)args[0]][1];
                    case "hasNormal" -> false;
                    case "faceNormal" -> new Vector3f(0,0,1);
                    case "tag" -> ShaderTerrainNormalBridge.encodeObjTag(0);
                    default -> throw new AssertionError(method.getName());
                });
        Mesh mesh = (Mesh) Proxy.newProxyInstance(Mesh.class.getClassLoader(),
                new Class<?>[]{Mesh.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("forEach")) throw new AssertionError(method.getName());
                    ((Consumer<QuadView>) args[0]).accept(quad);
                    return null;
                });
        Transformation transform = new Transformation(new Vector3f(), new Vector3f(), new Vector3f(1));
        var inventory = ObjGuiIconTemplate.create(mesh, transform, 1, 1);
        var preview = ObjGuiIconTemplate.create(mesh, transform, 1, 1, 256);
        assertEquals(64, inventory.resolution());
        assertEquals(256, preview.resolution());
        assertTrue(preview.coveredSampleCount() > inventory.coveredSampleCount() * 15);
        assertFalse(preview.isCovered(0));
        assertTrue(preview.isCovered(128 * 256 + 128));
        assertTrue(preview.isCovered(240 * 256 + 240), "High-resolution raster must extend past the old 64px corner");
        assertThrows(IllegalArgumentException.class, () -> ObjGuiIconTemplate.create(mesh, transform, 1, 1, 1024));
    }
}
