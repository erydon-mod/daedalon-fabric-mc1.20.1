package com.oliver.daedalon.block.entity;

import com.oliver.daedalon.block.FountainAssemblyLayout;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.List;

/** Stores one controlling plinth and the number of automatic bowls above it. */
public final class FountainBasinBlockEntity extends BlockEntity {
    private static final String PLINTH_NBT = "Plinth";
    private static final String BOWL_COUNT_NBT = "BowlCount";
    private static final String LEGACY_COMPONENTS_NBT = "Components";
    private static final String LEGACY_GOTHIC_BOWL_SUFFIX = "_gothic_fountain_bowl";

    // Component writes share the cache-build monitor so an old layout cannot
    // be published under a newly edited component key by a chunk worker.
    private volatile BlockState plinthState;
    private volatile int bowlCount;
    // Publish the complete immutable cache together to render/chunk threads.
    // Retain at most two views of this assembly; they share collision geometry.
    private volatile LayoutCache cachedLayouts;

    public FountainBasinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.fountainBasin(), pos, state);
    }

    public BlockState plinthState() {
        return plinthState;
    }

    public int bowlCount() {
        return bowlCount;
    }

    public FountainBowlModel.TierCount tierCount() {
        return FountainBowlModel.TierCount.fromBowlCount(bowlCount);
    }

    public List<FountainBowlModel.TierCount> allowedTierCounts() {
        if (plinthState == null) {
            return List.of(FountainBowlModel.TierCount.NONE);
        }
        return FountainBowlModel.TierCount.allowed();
    }

    public boolean hasPlinth() {
        return plinthState != null;
    }

    public boolean isFull() {
        return hasPlinth() && bowlCount >= FountainAssemblyLayout.maximumBowlCount();
    }

    public synchronized AdditionResult addPlinthUse(BlockState requestedState) {
        if (world == null || world.isClient
                || !(requestedState.getBlock() instanceof PlinthBlock)) {
            return AdditionResult.BLOCKED;
        }

        BlockState normalized = FountainAssemblyLayout.normalizePlinth(requestedState);
        BlockState updatedPlinth = plinthState;
        int updatedBowlCount = bowlCount;
        AdditionResult result;
        if (updatedPlinth == null) {
            updatedPlinth = normalized;
            updatedBowlCount = 0;
            result = AdditionResult.PLINTH_ADDED;
        } else {
            if (!updatedPlinth.isOf(normalized.getBlock())) {
                return AdditionResult.WRONG_PLINTH;
            }
            if (isFull()) {
                return AdditionResult.COMPLETE;
            }
            updatedBowlCount++;
            result = AdditionResult.BOWL_ADDED;
        }

        FountainAssemblyLayout candidateLayout = createLayout(
                getCachedState(),
                updatedPlinth,
                updatedBowlCount
        );
        if (!FountainBasinBlock.canOccupyAssembly(world, pos, candidateLayout)) {
            return AdditionResult.BLOCKED;
        }
        plinthState = updatedPlinth;
        bowlCount = updatedBowlCount;
        changed(candidateLayout);
        return result;
    }

    public synchronized boolean replacePlinth(BlockState state) {
        if (world == null || world.isClient || plinthState == null
                || !(state.getBlock() instanceof PlinthBlock)
                || !plinthState.isOf(state.getBlock())) {
            return false;
        }
        BlockState normalized = FountainAssemblyLayout.normalizePlinth(state);
        FountainAssemblyLayout candidateLayout = createLayout(
                getCachedState(),
                normalized,
                bowlCount
        );
        if (!FountainBasinBlock.canOccupyAssembly(world, pos, candidateLayout)) {
            return false;
        }
        plinthState = normalized;
        changed(candidateLayout);
        return true;
    }

    public synchronized boolean replaceTierCount(FountainBowlModel.TierCount tierCount) {
        if (world == null || world.isClient || plinthState == null || tierCount == null
                || !allowedTierCounts().contains(tierCount)) {
            return false;
        }
        int updatedBowlCount = tierCount.bowlCount();
        if (updatedBowlCount == bowlCount) {
            return true;
        }
        FountainAssemblyLayout candidateLayout = createLayout(
                getCachedState(),
                plinthState,
                updatedBowlCount
        );
        if (!FountainBasinBlock.canOccupyAssembly(world, pos, candidateLayout)) {
            return false;
        }
        bowlCount = updatedBowlCount;
        changed(candidateLayout);
        return true;
    }

    /** Removes one paid plinth step: a bowl first, then the base plinth. */
    public synchronized BlockState removeTopPlinthUse() {
        if (world == null || world.isClient || plinthState == null) {
            return null;
        }
        BlockState returned = plinthState;
        if (bowlCount > 0) {
            bowlCount--;
        } else {
            plinthState = null;
        }
        changed();
        return returned;
    }

    /**
     * Validates an imminent basin state edit and keeps its assembled geometry ready for
     * {@link FountainBasinBlock#onStateReplaced}. Repeated validation of the same target
     * state reuses the candidate geometry while still checking the current surroundings.
     */
    public boolean prepareStateChange(BlockView blockView, BlockState basinState) {
        if (!(basinState.getBlock() instanceof FountainBasinBlock)
                || !getCachedState().isOf(basinState.getBlock())) {
            return false;
        }
        FountainAssemblyLayout candidateLayout = layout(basinState);
        if (!FountainBasinBlock.canOccupyAssembly(blockView, pos, candidateLayout)) {
            return false;
        }
        return true;
    }

    public FountainAssemblyLayout layout(BlockState basinState) {
        LayoutCache cache = cachedLayouts;
        if (matchesStructure(cache, basinState)) {
            FountainAssemblyLayout result = cache.select(basinState.get(FountainBasinBlock.WATERLOGGED));
            if (result != null) {
                return result;
            }
        }
        return buildMissingLayout(basinState);
    }

    private synchronized FountainAssemblyLayout buildMissingLayout(BlockState state) {
        LayoutCache cache = cachedLayouts;
        boolean filled = state.get(FountainBasinBlock.WATERLOGGED);
        if (matchesStructure(cache, state)) {
            FountainAssemblyLayout result = cache.select(filled);
            if (result != null) {
                return result;
            }
            result = cache.select(!filled).withWaterlogged(filled);
            cacheLayout(state, result);
            return result;
        }
        FountainAssemblyLayout result = createLayout(state, plinthState, bowlCount);
        cacheLayout(state, result);
        return result;
    }

    public VoxelShape collisionPartShape(BlockState basinState,
                                         int offsetX,
                                         int offsetY,
                                         int offsetZ) {
        return layout(basinState).collisionPartShape(offsetX, offsetY, offsetZ);
    }

    public VoxelShape outlinePartShape(BlockState basinState,
                                       int offsetX,
                                       int offsetY,
                                       int offsetZ) {
        return layout(basinState).outlinePartShape(offsetX, offsetY, offsetZ);
    }

    @Override
    protected synchronized void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (plinthState != null) {
            nbt.put(PLINTH_NBT, NbtHelper.fromBlockState(plinthState));
            nbt.putInt(BOWL_COUNT_NBT, bowlCount);
        }
    }

    @Override
    public synchronized void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        plinthState = null;
        bowlCount = 0;
        if (nbt.contains(PLINTH_NBT, NbtElement.COMPOUND_TYPE)) {
            BlockState state = NbtHelper.toBlockState(
                    Registries.BLOCK.getReadOnlyWrapper(),
                    nbt.getCompound(PLINTH_NBT)
            );
            if (state.getBlock() instanceof PlinthBlock) {
                plinthState = FountainAssemblyLayout.normalizePlinth(state);
                bowlCount = MathHelper.clamp(
                        nbt.getInt(BOWL_COUNT_NBT),
                        0,
                        FountainAssemblyLayout.maximumBowlCount()
                );
            }
        } else {
            readLegacyComponents(nbt.getList(
                    LEGACY_COMPONENTS_NBT,
                    NbtElement.COMPOUND_TYPE
            ));
        }
        invalidateLayout();
        World currentWorld = world;
        if (currentWorld != null && currentWorld.isClient) {
            BlockState state = getCachedState();
            currentWorld.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    private void readLegacyComponents(NbtList components) {
        int legacyBowlCount = 0;
        for (int index = 0; index < components.size(); index++) {
            NbtCompound component = components.getCompound(index);
            String name = component.getString("Name");
            if (name.endsWith(LEGACY_GOTHIC_BOWL_SUFFIX)) {
                if (plinthState != null) {
                    legacyBowlCount++;
                }
                continue;
            }
            BlockState state = NbtHelper.toBlockState(
                    Registries.BLOCK.getReadOnlyWrapper(),
                    component
            );
            if (state.getBlock() instanceof PlinthBlock) {
                if (plinthState == null) {
                    plinthState = FountainAssemblyLayout.normalizePlinth(state);
                } else {
                    legacyBowlCount++;
                }
            }
        }
        bowlCount = MathHelper.clamp(
                legacyBowlCount,
                0,
                FountainAssemblyLayout.maximumBowlCount()
        );
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    private void changed() {
        changed(null);
    }

    private void changed(FountainAssemblyLayout validatedLayout) {
        BlockState state = getCachedState();
        if (validatedLayout == null) {
            invalidateLayout();
        } else {
            cacheLayout(state, validatedLayout);
        }
        markDirty();
        World currentWorld = world;
        if (currentWorld == null) {
            return;
        }
        FountainBasinBlock.refreshAssembly(currentWorld, pos, state);
        currentWorld.updateListeners(pos, state, state, 3);
    }

    private FountainAssemblyLayout createLayout(BlockState basinState,
                                                BlockState candidatePlinth,
                                                int candidateBowlCount) {
        return FountainAssemblyLayout.create(
                ((FountainBasinBlock) basinState.getBlock()).style(),
                basinState.get(FountainBasinBlock.SIZE),
                basinState.get(FountainBasinBlock.WATERLOGGED),
                candidatePlinth,
                candidateBowlCount
        );
    }

    private boolean matchesStructure(LayoutCache cache, BlockState basinState) {
        return basinState.getBlock() instanceof FountainBasinBlock basinBlock
                && cache != null
                && cache.style() == basinBlock.style()
                && cache.size() == basinState.get(FountainBasinBlock.SIZE)
                && cache.plinth() == plinthState
                && cache.bowlCount() == bowlCount;
    }

    private synchronized void cacheLayout(BlockState state, FountainAssemblyLayout layout) {
        LayoutCache previous = cachedLayouts;
        boolean matches = matchesStructure(previous, state);
        boolean filled = state.get(FountainBasinBlock.WATERLOGGED);
        cachedLayouts = new LayoutCache(((FountainBasinBlock) state.getBlock()).style(),
                state.get(FountainBasinBlock.SIZE), plinthState, bowlCount,
                filled ? (matches ? previous.dry() : null) : layout,
                filled ? layout : (matches ? previous.wet() : null));
    }

    private synchronized void invalidateLayout() {
        cachedLayouts = null;
    }

    private record LayoutCache(FountainBasinBlock.Style style, FountainBasinBlock.Size size,
                               BlockState plinth, int bowlCount,
                               FountainAssemblyLayout dry, FountainAssemblyLayout wet) {
        FountainAssemblyLayout select(boolean filled) {
            return filled ? wet : dry;
        }
    }

    public enum AdditionResult {
        PLINTH_ADDED,
        BOWL_ADDED,
        COMPLETE,
        WRONG_PLINTH,
        BLOCKED
    }
}
