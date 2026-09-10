package com.oliver.daedalon.client.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.client.model.obj.ObjGuiIconCache;
import com.oliver.daedalon.mixin.client.axiom.AxiomWorldRenderContextAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks decoration anchors as Axiom edits a region; never scans the world. */
public final class AxiomDecorBillboards {
    private static final int MAX_DRAWN = 1024;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private VertexConsumerProvider.Immediate buffers;

    public void update(int x, int y, int z, BlockState state) {
        long key = BlockPos.asLong(x, y, z);
        if (DecorPreviewBounds.supports(state)) {
            Box bounds = DecorPreviewBounds.bounds(state);
            entries.put(key, new Entry(x + (bounds.minX + bounds.maxX) * .5,
                    y + (bounds.minY + bounds.maxY) * .5, z + (bounds.minZ + bounds.maxZ) * .5,
                    (float) Math.max(bounds.maxY - bounds.minY, Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ)) * 256 / 254,
                    state.getBlock().asItem().getDefaultStack()));
        } else entries.remove(key);
    }

    public void clear() { entries.clear(); }

    public void render(Object rawContext, Vec3d translation, Quaternionf rotation,
                        float opacity, Framebuffer target) {
        if (entries.isEmpty() || opacity <= .01F) return;
        var context = (AxiomWorldRenderContextAccessor) rawContext;
        if (buffers == null) buffers = VertexConsumerProvider.immediate(new BufferBuilder(4096));
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        int previousFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        modelView.push();
        modelView.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f(context.daedalon$projection()), sorting);
            if (target != null) target.beginWrite(false);
            MatrixStack matrices = new MatrixStack();
            int drawn = 0;
            for (Entry entry : entries.values()) {
                if (++drawn > MAX_DRAWN) break;
                matrices.push();
                matrices.multiplyPositionMatrix(AxiomBillboardPlacement.matrix(
                        context.daedalon$matrices().peek().getPositionMatrix(),
                        context.daedalon$cameraPosition(), translation, rotation,
                        context.daedalon$cameraRotation(), entry.x, entry.y, entry.z, entry.height));
                ObjGuiIconCache.renderAxiomPreview(entry.stack, matrices, buffers, opacity);
                matrices.pop();
            }
            buffers.draw();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private record Entry(double x, double y, double z, float height, ItemStack stack) {}
}
