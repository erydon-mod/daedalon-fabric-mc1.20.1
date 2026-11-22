package com.oliver.daedalon.glaux;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class GlauxItem extends BlockItem {
    public GlauxItem(GlauxBlock block,Settings settings){super(block,settings);}
    @Override public void appendTooltip(ItemStack stack,@Nullable World world,List<Text> tooltip,TooltipContext context){
        super.appendTooltip(stack,world,tooltip,context);
        tooltip.add(Text.translatable("tooltip.daedalon.glaux.description").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.daedalon.glaux.speak").formatted(Formatting.DARK_GRAY));
    }
}
