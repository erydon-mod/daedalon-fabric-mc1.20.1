package com.oliver.daedalon.mixin.client.axiom;

import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The palette capture inputs shared by Axiom 5 and 6. */
@Pseudo
@Mixin(targets = "com.moulberry.axiom.render.BlockRenderCache$SizedBlock", remap = false)
public interface AxiomPaletteStateAccessor {
    @Accessor("state")
    BlockState daedalon$state();

    @Accessor("forceAsBlock")
    boolean daedalon$forceAsBlock();
}
