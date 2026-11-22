package com.oliver.daedalon.mixin.glaux;

import com.oliver.daedalon.client.glaux.interaction.GlauxInteraction;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public abstract class GlauxKeyBindingMixin {
    @Inject(method = "updatePressedStates", at = @At("RETURN"))
    private static void daedalon$glauxAfterWindowPoll(CallbackInfo ci) {
        GlauxInteraction.afterBindingPoll();
    }
}
