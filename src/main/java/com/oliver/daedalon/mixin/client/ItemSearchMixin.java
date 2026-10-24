package com.oliver.daedalon.mixin.client;

import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Give recognized item-only selectors the same Creative aliases as decorations. */
@Mixin(Item.class)
abstract class ItemSearchMixin {
    @Inject(method = "appendTooltip", at = @At("RETURN"))
    private void daedalon$appendItemCreativeTerms(ItemStack stack, @Nullable World world, List<Text> tooltip,
                                                 TooltipContext context, CallbackInfo callback) {
        if (world != null || !context.isCreative() || stack.getItem() instanceof BlockItem) return;
        for (String term : DaedalonSearchVocabulary.terms(stack)) tooltip.add(Text.literal(term));
    }
}
