package com.oliver.daedalon.mixin.client.axiom;

import com.oliver.daedalon.block.DecorAssemblySelection;
import com.oliver.daedalon.client.compat.AxiomAssemblySelection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.buildertools.BuilderToolSelectionState", remap = false)
abstract class BuilderToolSelectionStateMixin {
    @Shadow public abstract void resetSelection();
    @Unique private boolean daedalon$assemblySelected;

    @Inject(method = "leftClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void daedalon$selectCompleteObject(BlockHitResult hit, CallbackInfo callback) {
        if (daedalon$trySelectAssembly(hit)) {
            callback.cancel();
        } else if (daedalon$assemblySelected) {
            // Starting a normal box selection must not retain the previous object.
            resetSelection();
        }
    }

    @Inject(method = "middleClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void daedalon$magicSelectCompleteObject(BlockHitResult hit, CallbackInfo callback) {
        if (daedalon$trySelectAssembly(hit)) callback.cancel();
    }

    @Unique
    private boolean daedalon$trySelectAssembly(BlockHitResult hit) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return false;
        List<BlockPos> cells = DecorAssemblySelection.cells(world, hit.getBlockPos());
        if (cells.isEmpty() || !AxiomAssemblySelection.select(this, cells)) return false;
        daedalon$assemblySelected = true;
        return true;
    }

    @Inject(method = "resetSelection", at = @At("TAIL"), remap = false)
    private void daedalon$clearSelectionMarker(CallbackInfo callback) {
        daedalon$assemblySelected = false;
    }
}
