package com.oliver.daedalon.mixin.glaux;

import com.oliver.daedalon.client.glaux.interaction.GlauxInteraction;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse motion is deliberately untouched: camera look remains locked/in-game. */
@Mixin(Mouse.class)
public abstract class GlauxMouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void daedalon$glauxButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (GlauxInteraction.onMouseButton(window, button, action, modifiers)) ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void daedalon$glauxScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (GlauxInteraction.onScroll(window, vertical)) ci.cancel();
    }
}
