package com.oliver.daedalon.mixin.glaux;

import com.oliver.daedalon.client.glaux.interaction.GlauxInteraction;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class GlauxKeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void daedalon$glauxKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (GlauxInteraction.onKey(window, key, scanCode, action, modifiers)) ci.cancel();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void daedalon$glauxCharacter(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (GlauxInteraction.onChar(window, codePoint, modifiers)) ci.cancel();
    }
}
