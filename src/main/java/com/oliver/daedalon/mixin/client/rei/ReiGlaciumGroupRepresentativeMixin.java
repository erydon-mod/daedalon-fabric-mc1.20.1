package com.oliver.daedalon.mixin.client.rei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oliver.daedalon.client.search.DaedalonCatalogue;
import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.List;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.gui.widget.entrylist.EntryListSearchManager", remap = false)
public abstract class ReiGlaciumGroupRepresentativeMixin {
    // REI displays ingredient[0] at the front of a collapsed group. Registration
    // lists define membership only; the actual list is built after filtering.
    @WrapOperation(method = "collapse(Ljava/util/List;Ljava/util/function/BooleanSupplier;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lme/shedaniel/rei/impl/common/entry/type/collapsed/CollapsedStack;getIngredient()Ljava/util/List;")),
            require = 0, remap = false)
    private boolean daedalon$preferGlacium(List<Object> members, Object added, Operation<Boolean> original) {
        boolean changed = original.call(members, added);
        if (changed) {
            DaedalonCatalogue.preferAppendedGlacium(DaedalonSearchVocabulary.index(), members,
                    ReiGlaciumGroupRepresentativeMixin::daedalon$path);
        }
        return changed;
    }

    @Unique
    private static String daedalon$path(Object entry) {
        if (entry instanceof EntryStack<?> stack && stack.getValue() instanceof ItemStack item) {
            var id = Registries.ITEM.getId(item.getItem());
            if (id.getNamespace().equals("daedalon")) return id.getPath();
        }
        return null;
    }
}
