package com.oliver.daedalon.mixin;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBasinPartBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DebugStickItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Makes Daedalon model controls consistent without changing vanilla blocks. */
@Mixin(DebugStickItem.class)
abstract class DebugStickItemMixin {
    private static final String BASIN_SIZE_CONTROL = "basin_size";
    private static final String PLINTH_SIZE_CONTROL = "plinth_size";
    private static final String TIER_CONTROL = "tiers";
    private static final String WATERLOGGED_CONTROL = "waterlogged";
    private static final String FACING_CONTROL = "facing";

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void daedalon$useOrderedRememberedProperty(
            PlayerEntity player,
            BlockState state,
            WorldAccess world,
            BlockPos pos,
            boolean update,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> callback
    ) {
        BlockState targetState = state;
        BlockPos targetPos = pos;
        if (state.getBlock() instanceof FountainBasinPartBlock) {
            BlockPos anchorPos = FountainBasinPartBlock.resolveAnchorPos(world, pos, state);
            if (anchorPos == null) {
                callback.setReturnValue(false);
                return;
            }
            targetPos = anchorPos;
            targetState = world.getBlockState(anchorPos);
        }

        if (!Daedalon.MOD_ID.equals(Registries.BLOCK.getId(targetState.getBlock()).getNamespace())) {
            return;
        }
        if (!player.isCreativeLevelTwoOp()) {
            callback.setReturnValue(false);
            return;
        }

        if (targetState.getBlock() instanceof FountainBasinBlock basinBlock
                && world.getBlockEntity(targetPos) instanceof FountainBasinBlockEntity basin) {
            editFountain(
                    player,
                    stack,
                    update,
                    player.shouldCancelInteraction(),
                    world,
                    targetPos,
                    targetState,
                    basinBlock,
                    basin,
                    callback
            );
            return;
        }

        List<Property<?>> properties = DaedalonDebugProperties.ordered(
                targetState.getBlock().getStateManager().getProperties()
        );
        String rememberedName = stack.getOrCreateNbt().getString(
                DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT
        );
        Property<?> selected = DaedalonDebugProperties.rememberedOrFirst(properties, rememberedName);
        if (selected == null) {
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.empty",
                    Registries.BLOCK.getId(targetState.getBlock()).toString()
            ));
            callback.setReturnValue(false);
            return;
        }

        if (update) {
            BlockState updated = cycleState(
                    targetState,
                    selected,
                    player.shouldCancelInteraction()
            );
            if (targetState.getBlock() instanceof FountainBasinBlock basin
                    && selected == FountainBasinBlock.SIZE
                    && !basin.canChangeSize(world, targetPos, updated)) {
                sendMessage(player, Text.translatable(
                        "message.daedalon.fountain_basin_size_blocked"
                ));
                callback.setReturnValue(true);
                return;
            }
            world.setBlockState(targetPos, updated, 18);
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.update",
                    selected.getName(),
                    valueName(updated, selected)
            ));
        } else {
            selected = DaedalonDebugProperties.cycle(
                    properties,
                    selected,
                    player.shouldCancelInteraction()
            );
            stack.getOrCreateNbt().putString(
                    DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,
                    selected.getName()
            );
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.select",
                    selected.getName(),
                    valueName(targetState, selected)
            ));
        }
        callback.setReturnValue(true);
    }

    private static void editFountain(PlayerEntity player,
                                     ItemStack stack,
                                     boolean update,
                                     boolean backwards,
                                     WorldAccess world,
                                     BlockPos pos,
                                     BlockState basinState,
                                     FountainBasinBlock basinBlock,
                                     FountainBasinBlockEntity basin,
                                     CallbackInfoReturnable<Boolean> callback) {
        BlockState plinth = basin.plinthState();
        List<String> controls = plinth == null
                ? List.of(BASIN_SIZE_CONTROL, WATERLOGGED_CONTROL)
                : List.of(
                        BASIN_SIZE_CONTROL,
                        PLINTH_SIZE_CONTROL,
                        TIER_CONTROL,
                        WATERLOGGED_CONTROL,
                        FACING_CONTROL
                );
        String rememberedName = stack.getOrCreateNbt().getString(
                DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT
        );
        rememberedName = rememberedFountainControl(rememberedName, plinth != null);
        String selected = controls.contains(rememberedName)
                ? rememberedName
                : controls.get(0);

        if (update) {
            String updatedValue;
            boolean replaced;
            switch (selected) {
                case BASIN_SIZE_CONTROL -> {
                    BlockState updated = cycleState(
                            basinState,
                            FountainBasinBlock.SIZE,
                            backwards
                    );
                    if (!basinBlock.canChangeSize(world, pos, updated)) {
                        sendMessage(player, Text.translatable(
                                "message.daedalon.fountain_basin_size_blocked"
                        ));
                        callback.setReturnValue(true);
                        return;
                    }
                    replaced = world.setBlockState(pos, updated, 18);
                    updatedValue = valueName(updated, FountainBasinBlock.SIZE);
                }
                case PLINTH_SIZE_CONTROL -> {
                    BlockState updated = cycleState(plinth, PlinthBlock.SIZE, backwards);
                    replaced = basin.replacePlinth(updated);
                    updatedValue = valueName(updated, PlinthBlock.SIZE);
                }
                case TIER_CONTROL -> {
                    FountainBowlModel.TierCount updated = DaedalonDebugProperties.cycle(
                            basin.allowedTierCounts(),
                            basin.tierCount(),
                            backwards
                    );
                    replaced = basin.replaceTierCount(updated);
                    updatedValue = updated.asString();
                }
                case WATERLOGGED_CONTROL -> {
                    BlockState updated = cycleState(
                            basinState,
                            FountainBasinBlock.WATERLOGGED,
                            backwards
                    );
                    replaced = basinBlock.canChangeSize(world, pos, updated)
                            && world.setBlockState(pos, updated, 18);
                    updatedValue = valueName(updated, FountainBasinBlock.WATERLOGGED);
                }
                case FACING_CONTROL -> {
                    BlockState updated = cycleState(plinth, PlinthBlock.FACING, backwards);
                    replaced = basin.replacePlinth(updated);
                    updatedValue = valueName(updated, PlinthBlock.FACING);
                }
                default -> {
                    callback.setReturnValue(false);
                    return;
                }
            }
            if (!replaced) {
                sendMessage(player, Text.translatable(
                        "message.daedalon.fountain_assembly_blocked"
                ));
                callback.setReturnValue(true);
                return;
            }
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.update",
                    selected,
                    updatedValue
            ));
        } else {
            selected = DaedalonDebugProperties.cycle(controls, selected, backwards);
            stack.getOrCreateNbt().putString(
                    DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,
                    selected
            );
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.select",
                    selected,
                    fountainValueName(basinState, plinth, basin, selected)
            ));
        }
        callback.setReturnValue(true);
    }

    private static String rememberedFountainControl(String rememberedName, boolean hasPlinth) {
        if ("bowls".equals(rememberedName)) {
            return hasPlinth ? TIER_CONTROL : BASIN_SIZE_CONTROL;
        }
        if ("size".equals(rememberedName)) {
            return hasPlinth ? PLINTH_SIZE_CONTROL : BASIN_SIZE_CONTROL;
        }
        return rememberedName;
    }

    private static String fountainValueName(BlockState basinState,
                                            BlockState plinth,
                                            FountainBasinBlockEntity basin,
                                            String control) {
        return switch (control) {
            case BASIN_SIZE_CONTROL -> valueName(basinState, FountainBasinBlock.SIZE);
            case PLINTH_SIZE_CONTROL -> valueName(plinth, PlinthBlock.SIZE);
            case TIER_CONTROL -> basin.tierCount().asString();
            case WATERLOGGED_CONTROL -> valueName(
                    basinState,
                    FountainBasinBlock.WATERLOGGED
            );
            case FACING_CONTROL -> valueName(plinth, PlinthBlock.FACING);
            default -> "";
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState cycleState(BlockState state, Property property, boolean backwards) {
        Comparable current = state.get(property);
        Comparable next = (Comparable) DaedalonDebugProperties.cycle(
                DaedalonDebugProperties.orderedValues(property),
                current,
                backwards
        );
        return state.with(property, next);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(BlockState state, Property property) {
        return property.name(state.get(property));
    }

    private static void sendMessage(PlayerEntity player, Text message) {
        ((ServerPlayerEntity) player).sendMessageToClient(message, true);
    }
}
