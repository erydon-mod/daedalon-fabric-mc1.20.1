package com.oliver.daedalon.mixin.client.rei;

import com.oliver.daedalon.client.search.DaedalonSearchQueryRules;
import me.shedaniel.rei.api.client.search.method.InputMethod;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.search.argument.Argument", remap = false)
public abstract class ReiGenericBlockArgumentMixin implements DaedalonSearchQueryRules.ReiTextArgument {
    @Unique private boolean daedalon$blockTerm;
    @Unique private boolean daedalon$regular;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
    private void daedalon$cachePlainBlockTerm(@Coerce Object type, String text, boolean regular,
                                              @Coerce Object filterData, int start, int end,
                                              boolean lowercase, CallbackInfo callback) {
        daedalon$blockTerm = type.getClass().getName().equals(
                "me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType")
                && DaedalonSearchQueryRules.isGenericBlockTerm(text);
        daedalon$regular = regular;
    }

    @Override public boolean daedalon$isBlockTerm() { return daedalon$blockTerm; }
    @Override public boolean daedalon$isRegular() { return daedalon$regular; }

    @Inject(method = "matches(Lme/shedaniel/rei/api/common/entry/EntryStack;JLme/shedaniel/rei/impl/client/search/argument/AlternativeArgument;Lme/shedaniel/rei/api/client/search/method/InputMethod;)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void daedalon$matchPlainBlockTerm(EntryStack<?> entry, long hashExact,
                                                    @Coerce Object alternatives, InputMethod<?> inputMethod,
                                                    CallbackInfoReturnable<Boolean> callback) {
        if (entry.getValue() instanceof ItemStack stack && DaedalonSearchQueryRules.isCatalogueStack(stack)) {
            Boolean result = DaedalonSearchQueryRules.reiPlainBlockResult((Iterable<?>) alternatives, true);
            if (result != null) callback.setReturnValue(result);
        }
    }
}
