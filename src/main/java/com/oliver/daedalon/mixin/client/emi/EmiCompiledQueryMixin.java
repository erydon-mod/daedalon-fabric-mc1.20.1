package com.oliver.daedalon.mixin.client.emi;

import com.oliver.daedalon.client.search.DaedalonSearchQueryRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;

@Pseudo
@Mixin(targets = "dev.emi.emi.search.EmiSearch$CompiledQuery", remap = false)
public abstract class EmiCompiledQueryMixin {
    @Inject(method = "addQuery", at = @At("RETURN"), require = 0, remap = false)
    private static void daedalon$markDefaultBlockQuery(String text, boolean negated, List<?> queries,
                                                       Function<?, ?> normal, Function<?, ?> regex,
                                                       CallbackInfo callback) {
        if (DaedalonSearchQueryRules.consumeEmiBlockToken() && !queries.isEmpty()
                && queries.get(queries.size() - 1) instanceof DaedalonSearchQueryRules.EmiDefaultQuery query) {
            query.daedalon$setBlockTerm();
        }
    }
}
