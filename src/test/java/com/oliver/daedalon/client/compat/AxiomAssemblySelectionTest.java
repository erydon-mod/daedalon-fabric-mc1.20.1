package com.oliver.daedalon.client.compat;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AxiomAssemblySelectionTest {
    @Test void legacySelectionContainsEveryAbsoluteAssemblyCell() throws Exception {
        var state = new LegacySelection();
        var bridge = AxiomAssemblySelection.resolve(Positions.class, LegacyRestore.class, LegacySelection.class);
        List<BlockPos> cells = List.of(new BlockPos(-220, 80, 350), new BlockPos(-219, 81, 350));
        assertTrue(AxiomAssemblySelection.select(state, cells, bridge));
        assertEquals(new HashSet<>(cells), state.value.set().cells);
        assertNull(state.value.pos1());
        assertNull(state.value.pos2());
    }

    @Test void axiomSixUsesASelectedWorldCellAsFillAnchorWithoutMovingTheSelection() throws Exception {
        var state = new CenteredSelection();
        var bridge = AxiomAssemblySelection.resolve(Positions.class, CenteredRestore.class, CenteredSelection.class);
        BlockPos.Mutable hit = new BlockPos.Mutable(340, 90, -510);
        BlockPos other = hit.up();
        assertTrue(AxiomAssemblySelection.select(state, List.of(hit, other), bridge));
        hit.move(10, 0, 0);
        assertEquals(new BlockPos(340, 90, -510), state.value.setCenter());
        assertEquals(Set.of(new BlockPos(340, 90, -510), other), state.value.set().cells);
        assertNull(state.value.pos1());
        assertNull(state.value.pos2());
    }

    @Test void emptyOrUnavailableSelectionBridgeLeavesAxiomUntouched() throws Exception {
        var state = new CenteredSelection();
        var bridge = AxiomAssemblySelection.resolve(Positions.class, CenteredRestore.class, CenteredSelection.class);
        assertFalse(AxiomAssemblySelection.select(state, List.of(), bridge));
        assertFalse(AxiomAssemblySelection.select(state, List.of(BlockPos.ORIGIN), null));
        assertNull(state.value);
        assertThrows(NoSuchMethodException.class,
                () -> AxiomAssemblySelection.resolveRestore(String.class, Positions.class));
    }

    @Test void actualOptionalAxiomJarsAcceptAnAbsoluteAssemblyAndFillAnchor() throws Exception {
        String selected = System.getenv("ERYDON_AXIOM_TEST_JARS");
        assumeTrue(selected != null && !selected.isBlank(), "Optional read-only Axiom JAR probes");
        for (String entry : selected.split(Pattern.quote(File.pathSeparator))) {
            Class<?> actualRestore = actualRestore(Path.of(entry));
            var api = AxiomAssemblySelection.resolveRestore(actualRestore, Positions.class);
            BlockPos cell = new BlockPos(-100, 80, 200);
            Positions positions = new Positions();
            positions.cells.add(cell);
            Object value = api.create(positions, cell);
            assertNull(actualRestore.getMethod("pos1").invoke(value), entry);
            assertNull(actualRestore.getMethod("pos2").invoke(value), entry);
            assertSame(positions, actualRestore.getMethod("set").invoke(value), entry);
            if (api.hasCenter()) assertEquals(cell, actualRestore.getMethod("setCenter").invoke(value), entry);
        }
    }

    // Read and remap the actual release record only; never initialize Axiom or its renderer.
    private static Class<?> actualRestore(Path jar) throws Exception {
        String name = "com/moulberry/axiom/buildertools/BuilderToolSelectionState$Restore";
        byte[] source;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            source = zip.getInputStream(zip.getEntry(name + ".class")).readAllBytes();
        }
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor standalone = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public void visitNestHost(String ignored) { }
            @Override public void visitInnerClass(String n, String o, String i, int a) { }
        };
        new ClassReader(source).accept(new ClassRemapper(standalone, new Remapper() {
            @Override public String map(String internal) {
                if (internal.equals(name)) return "com/oliver/daedalon/client/compat/ActualAxiomRestore";
                if (internal.equals("net/minecraft/class_2338")) return "net/minecraft/util/math/BlockPos";
                if (internal.equals("com/moulberry/axiom/collections/PositionSet"))
                    return Positions.class.getName().replace('.', '/');
                return internal;
            }
        }), 0);
        class ProbeLoader extends ClassLoader {
            ProbeLoader() { super(AxiomAssemblySelectionTest.class.getClassLoader()); }
            Class<?> loadRecord() { byte[] b = writer.toByteArray(); return defineClass(null, b, 0, b.length); }
        }
        return new ProbeLoader().loadRecord();
    }

    public static final class Positions {
        final Set<BlockPos> cells = new HashSet<>();
        public Positions() { }
        public boolean add(int x, int y, int z) { return cells.add(new BlockPos(x, y, z)); }
    }
    public record LegacyRestore(BlockPos pos1, BlockPos pos2, Positions set) { }
    public record CenteredRestore(BlockPos pos1, BlockPos pos2, Positions set, BlockPos setCenter) { }
    public static final class LegacySelection {
        LegacyRestore value;
        public void restoreFrom(LegacyRestore value) { this.value = value; }
    }
    public static final class CenteredSelection {
        CenteredRestore value;
        public void restoreFrom(CenteredRestore value) { this.value = value; }
    }
}
