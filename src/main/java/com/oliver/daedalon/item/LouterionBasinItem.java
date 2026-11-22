package com.oliver.daedalon.item;

import com.oliver.daedalon.block.LouterionBasinBlock;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** The two basin sizes share a single registered item for each material. */
public final class LouterionBasinItem extends BlockItem {
    public LouterionBasinItem(LouterionBasinBlock block, Settings settings) {
        super(block, settings);
    }

    public ItemStack largeStack() {
        ItemStack stack = getDefaultStack();
        stack.getOrCreateSubNbt("BlockStateTag").putString(LouterionBasinBlock.SIZE.getName(),
                TwoSizeDecorBlock.Size.LARGE.asString());
        return stack;
    }

    public static boolean isLarge(ItemStack stack) {
        if (!(stack.getItem() instanceof LouterionBasinItem)) return false;
        var properties = stack.getSubNbt("BlockStateTag");
        if (properties == null) return false;
        return LouterionBasinBlock.SIZE.parse(properties.getString(LouterionBasinBlock.SIZE.getName()))
                .orElse(TwoSizeDecorBlock.Size.SMALL) == TwoSizeDecorBlock.Size.LARGE;
    }

    @Override
    public Text getName(ItemStack stack) {
        Text name = super.getName(stack);
        return isLarge(stack) ? name.copy().append(" (")
                .append(Text.translatable("option.daedalon.capital.size.double")).append(")") : name;
    }
}
