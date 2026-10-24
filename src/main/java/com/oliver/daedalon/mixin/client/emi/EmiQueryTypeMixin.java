package com.oliver.daedalon.mixin.client.emi;

import com.oliver.daedalon.client.search.DaedalonSearchQueryRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.emi.emi.search.QueryType", remap = false)
public abstract class EmiQueryTypeMixin {
    @Inject(method = "fromString", at = @At("HEAD"), require = 0, remap = false)
    private static void daedalon$cachePlainToken(String token, CallbackInfoReturnable<Object> callback) {
        DaedalonSearchQueryRules.beginEmiToken(token);
    }
}
