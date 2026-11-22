package com.oliver.daedalon.client.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.block.LanternChainBlock;
import com.oliver.daedalon.block.LanternGeometry;
import com.oliver.daedalon.block.LanternPlacement;
import com.oliver.daedalon.client.model.obj.LanternPreview;
import com.oliver.daedalon.client.model.obj.ObjGuiIconCache;
import com.oliver.daedalon.mixin.client.axiom.AxiomWorldRenderContextAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks decoration anchors as Axiom edits a region; never scans the world. */
public final class AxiomDecorBillboards {
    private static final int MAX_DRAWN = 1024;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private final AxiomLanternChains chains = new AxiomLanternChains();
    private final AxiomLanternSupports supports = new AxiomLanternSupports();
    private VertexConsumerProvider.Immediate buffers;

    public synchronized void update(int x, int y, int z, BlockState state) {
        long key = BlockPos.asLong(x, y, z);
        boolean supportChanged = supports.set(x, y, z, selectedSupport(state));
        boolean chainChanged = chains.set(x, y, z,
                state != null && state.getBlock() instanceof LanternChainBlock ? state.get(LanternBlock.SIZE) : null,
                state != null && state.getBlock() instanceof LanternChainBlock && state.get(LanternBlock.CANOPY));
        if (DecorPreviewBounds.supports(state)) {
            entries.put(key, entry(x, y, z, state));
        } else entries.remove(key);
        if (supportChanged) {
            // Support and owner can enter a copied region in either order.
            for (Direction face : Direction.Type.HORIZONTAL) {
                int ownerX = x + face.getOffsetX(), ownerZ = z + face.getOffsetZ();
                long ownerKey = BlockPos.asLong(ownerX, y, ownerZ);
                Entry owner = entries.get(ownerKey);
                if (owner != null && owner.state.getBlock() instanceof LanternBlock
                        && owner.state.get(LanternBlock.MOUNT) == LanternBlock.Mount.WALL
                        && owner.state.get(LanternBlock.FACING) == face) {
                    entries.put(ownerKey, entry(ownerX, y, ownerZ, owner.state));
                }
            }
        }
        if (chainChanged) {
            // At most the edited chain's owner changes, regardless of insertion order.
            for (int down = 1; down <= LanternPlacement.MAX_CHAIN + 1; down++) {
                int ownerY = y - down;
                long ownerKey = BlockPos.asLong(x, ownerY, z);
                Entry owner = entries.get(ownerKey);
                if (owner != null && owner.state.getBlock() instanceof LanternBlock
                        && owner.state.get(LanternBlock.MOUNT) == LanternBlock.Mount.HANGING) {
                    entries.put(ownerKey, entry(x, ownerY, z, owner.state));
                    break;
                }
                if (!chains.contains(x, ownerY, z)) break;
            }
        }
    }

    private Entry entry(int x, int y, int z, BlockState state) {
        Box bounds = DecorPreviewBounds.bounds(state);
        LanternPreview preview = null;
        if (state.getBlock() instanceof LanternBlock) {
            var span = chains.span(x, y, z, state.get(LanternBlock.SIZE), state.get(LanternBlock.CANOPY));
            Vec3d offset = state.get(LanternBlock.MOUNT) == LanternBlock.Mount.WALL
                    ? supports.offset(x, y, z, state.get(LanternBlock.FACING)) : Vec3d.ZERO;
            preview = LanternPreview.of(state, span.count(), span.canopy(), offset);
            bounds = bounds.offset(offset);
            if (preview.mount() == LanternBlock.Mount.HANGING && preview.chainCount() > 0) {
                bounds = bounds.union(LanternGeometry.chain(preview.size(), preview.canopy())
                        .getBoundingBox().offset(0, preview.chainCount(), 0));
            }
        }
        return new Entry(x + (bounds.minX + bounds.maxX) * .5,
                y + (bounds.minY + bounds.maxY) * .5, z + (bounds.minZ + bounds.maxZ) * .5,
                (float) Math.max(bounds.maxY - bounds.minY, Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ)) * 256 / 254,
                state.getBlock().asItem().getDefaultStack(), state, preview);
    }

    private static Box selectedSupport(BlockState state) {
        if (state == null) return null;
        String type = state.getBlock().getClass().getSimpleName();
        if (!type.equals("LayerVerticalBlock") && !type.equals("LayerVerticalTranslucentBlock")) return null;
        // Read the selected layer's intrinsic shape. The BlockState collision path
        // can run third-party hooks that expect the queried position to be in a world.
        var boxes = state.getBlock().getCollisionShape(state, EmptyBlockView.INSTANCE,
                BlockPos.ORIGIN, ShapeContext.absent()).getBoundingBoxes();
        return boxes.size() == 1 ? boxes.get(0) : null;
    }

    public synchronized void clear() { entries.clear(); chains.clear(); supports.clear(); }

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
                ObjGuiIconCache.renderAxiomPreview(entry.stack, entry.preview, matrices, buffers, opacity);
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

    private record Entry(double x, double y, double z, float height, ItemStack stack,
                         BlockState state, LanternPreview preview) {}
}
