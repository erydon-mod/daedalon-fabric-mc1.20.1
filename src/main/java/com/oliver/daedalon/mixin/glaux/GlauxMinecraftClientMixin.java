package com.oliver.daedalon.mixin.glaux;

import com.oliver.daedalon.client.glaux.interaction.GlauxInteraction;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class GlauxMinecraftClientMixin {
    @Inject(method = "onWindowFocusChanged", at = @At("HEAD"))
    private void daedalon$glauxWindowFocus(boolean focused, CallbackInfo ci) {
        GlauxInteraction.onWindowFocusChanged(focused);
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void daedalon$glauxOwnsGameplayInput(CallbackInfo ci) {
        if (GlauxInteraction.isComposing()) ci.cancel();
    }
}
