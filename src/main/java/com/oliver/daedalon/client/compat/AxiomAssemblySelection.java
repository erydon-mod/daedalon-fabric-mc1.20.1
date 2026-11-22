package com.oliver.daedalon.client.compat;

import com.oliver.daedalon.Daedalon;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Optional Axiom 5/6 builder selection bridge. No bundled Axiom classes or frame hooks. */
public final class AxiomAssemblySelection {
    private AxiomAssemblySelection() {}

    public static boolean select(Object selectionState, List<BlockPos> cells) {
        return select(selectionState, cells, Holder.BRIDGE);
    }

    static boolean select(Object selectionState, List<BlockPos> cells, Bridge bridge) {
        if (bridge == null || cells.isEmpty()) return false;
        try {
            Object positions = bridge.positions.newInstance();
            for (BlockPos pos : cells) bridge.add.invoke(positions, pos.getX(), pos.getY(), pos.getZ());
            // Axiom 6 uses this world-space cell as its fill anchor; sparse positions stay absolute.
            Object restore = bridge.restore.create(positions, cells.get(0).toImmutable());
            // Axiom owns the sparse selection, preview, copy, move and undo from here.
            bridge.restoreFrom.invoke(selectionState, restore);
            return true;
        } catch (ReflectiveOperationException exception) {
            Daedalon.LOGGER.warn("Unable to select a complete decor assembly in Axiom", exception);
            return false;
        }
    }

    private static final class Holder {
        private static final Bridge BRIDGE = resolve();

        private static Bridge resolve() {
            try {
                Class<?> positions = Class.forName("com.moulberry.axiom.collections.PositionSet");
                Class<?> restore = Class.forName("com.moulberry.axiom.buildertools.BuilderToolSelectionState$Restore");
                Class<?> selection = Class.forName("com.moulberry.axiom.buildertools.BuilderToolSelectionState");
                return AxiomAssemblySelection.resolve(positions, restore, selection);
            } catch (ReflectiveOperationException | LinkageError exception) {
                Daedalon.LOGGER.warn("This Axiom version does not expose the supported builder selection API", exception);
                return null;
            }
        }
    }

    static Bridge resolve(Class<?> positions, Class<?> restore, Class<?> selection)
            throws ReflectiveOperationException {
        return new Bridge(positions.getConstructor(), positions.getMethod("add", int.class, int.class, int.class),
                resolveRestore(restore, positions), selection.getMethod("restoreFrom", restore));
    }

    static RestoreApi resolveRestore(Class<?> restore, Class<?> positions) throws NoSuchMethodException {
        try {
            return new RestoreApi(restore.getConstructor(BlockPos.class, BlockPos.class, positions, BlockPos.class), true);
        } catch (NoSuchMethodException unsupportedCenter) {
            return new RestoreApi(restore.getConstructor(BlockPos.class, BlockPos.class, positions), false);
        }
    }

    record RestoreApi(Constructor<?> constructor, boolean hasCenter) {
        Object create(Object positions, BlockPos center) throws ReflectiveOperationException {
            return hasCenter ? constructor.newInstance(null, null, positions, center)
                    : constructor.newInstance(null, null, positions);
        }
    }

    record Bridge(Constructor<?> positions, Method add, RestoreApi restore, Method restoreFrom) {}
}
