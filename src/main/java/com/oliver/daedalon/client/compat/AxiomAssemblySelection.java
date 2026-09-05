package com.oliver.daedalon.client.compat;

import com.oliver.daedalon.Daedalon;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Optional Axiom 5 builder selection bridge. No bundled Axiom classes or frame hooks. */
public final class AxiomAssemblySelection {
    private AxiomAssemblySelection() {}

    public static boolean select(Object selectionState, List<BlockPos> cells) {
        Bridge bridge = Holder.BRIDGE;
        if (bridge == null) return false;
        try {
            Object positions = bridge.positions.newInstance();
            for (BlockPos pos : cells) bridge.add.invoke(positions, pos.getX(), pos.getY(), pos.getZ());
            Object restore = bridge.restore.newInstance(null, null, positions);
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
                return new Bridge(positions.getConstructor(), positions.getMethod("add", int.class, int.class, int.class),
                        restore.getConstructor(BlockPos.class, BlockPos.class, positions),
                        selection.getMethod("restoreFrom", restore));
            } catch (ReflectiveOperationException exception) {
                Daedalon.LOGGER.warn("This Axiom version does not expose the supported builder selection API", exception);
                return null;
            }
        }
    }

    private record Bridge(Constructor<?> positions, Method add, Constructor<?> restore, Method restoreFrom) {}
}
