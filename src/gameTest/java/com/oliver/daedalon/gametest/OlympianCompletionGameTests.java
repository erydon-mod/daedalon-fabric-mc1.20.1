package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.BustBlock;
import com.oliver.daedalon.block.DecorOrientation;
import com.oliver.daedalon.block.StatueBlock;
import com.oliver.daedalon.registry.DecorMaterial;
import com.oliver.daedalon.registry.DecorMetal;
import com.oliver.daedalon.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Real registry coverage for all thirteen Olympians, with both sculptural forms. */
public final class OlympianCompletionGameTests {
    private static final List<String> SUBJECTS = List.of(
            "aphrodite", "apollo", "ares", "artemis", "athena", "demeter",
            "dionysus", "hephaestus", "hera", "hermes", "hestia", "poseidon", "zeus"
    );

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyOlympianHasBothFormsAndSharesOneProfileAcrossAllFinishes(TestContext context) {
        var profiles = Collections.newSetFromMap(new IdentityHashMap<StatueBlock.Profile, Boolean>());
        Set<Identifier> seen = new HashSet<>();
        Set<net.minecraft.item.Item> creativeItems = Set.copyOf(ModItems.blockItems());
        int checkedStates = 0;
        for (String subject : SUBJECTS) {
            for (String form : List.of("statue", "bust")) {
                List<Identifier> ids = familyIds(subject, form);
                context.assertTrue(ids.size() == 56, "Every divine form needs 54 stone and two metal finishes");
                StatueBlock reference = requireStatue(context, ids.get(0), form);
                context.assertTrue(profiles.add(reference.statueProfile()),
                        "Different gods/forms must retain separate measured profiles: " + subject + " " + form);
                BlockState referenceDefault = reference.getDefaultState();
                VoxelShape referenceShape = collision(context, referenceDefault);
                for (Identifier id : ids) {
                    context.assertTrue(seen.add(id), "Duplicate Olympian item: " + id);
                    StatueBlock block = requireStatue(context, id, form);
                    context.assertTrue(block.statueProfile() == reference.statueProfile(),
                            "Material variants must share the same measured mesh profile: " + id);
                    context.assertTrue(Registries.ITEM.get(id) == block.asItem() && creativeItems.contains(block.asItem()),
                            "Matching block item must be available in the Creative catalogue: " + id);
                    BlockState state = block.getDefaultState();
                    context.assertTrue(state.get(StatueBlock.SIZE) == (form.equals("bust")
                                    ? StatueBlock.StatueSize.SMALL : StatueBlock.StatueSize.MEDIUM),
                            "Preserve the form's default size: " + id);
                    context.assertTrue(!state.get(StatueBlock.OFFSET)
                                    && state.get(StatueBlock.ORIENTATION) == DecorOrientation.STRAIGHT
                                    && state.get(StatueBlock.FACING) == Direction.NORTH,
                            "Default placement must be centred and face north: " + id);
                    context.assertTrue(block.getStateManager().getStates().size() == 48,
                            "Each finish needs three sizes, four facings, offset and diagonal controls: " + id);
                    context.assertTrue(collision(context, state) == referenceShape,
                            "Materials must reuse cached collision geometry: " + id);
                }
                // Existing diagonal tests exercise rotation mathematics. This checks every
                // requested divine profile is included and has all cached grounded poses.
                for (BlockState state : reference.getStateManager().getStates()) {
                    VoxelShape shape = collision(context, state);
                    context.assertTrue(!shape.isEmpty() && Math.abs(shape.getMin(Direction.Axis.Y)) < 1.0E-6,
                            "Every Olympian pose must remain grounded: " + subject + " " + form + " " + state);
                    context.assertTrue(collision(context, state) == shape,
                            "Pose shapes must be cached: " + subject + " " + form);
                    checkedStates++;
                }
                if (subject.equals("hestia")
                        || (form.equals("statue") && (subject.equals("hermes") || subject.equals("hephaestus")))) {
                    context.assertTrue(reference.rotationStepsFromSource(Direction.NORTH) == 0,
                            "Newly prepared source meshes must use the measured north-facing profile: " + subject + " " + form);
                }
            }
        }
        context.assertTrue(profiles.size() == 26 && seen.size() == 1456 && checkedStates == 1248,
                "Cover all 13 gods, both forms, 56 finishes and 48 poses per measured profile");
        System.out.println("DAEDALON_OLYMPIAN_COMPLETION passed profiles=" + profiles.size()
                + " variants=" + seen.size() + " poses=" + checkedStates);
        context.complete();
    }

    private static List<Identifier> familyIds(String subject, String form) {
        List<Identifier> ids = new ArrayList<>();
        for (DecorMaterial material : DecorMaterial.values()) {
            ids.add(new Identifier("daedalon", material.id() + "_" + subject + "_" + form));
            ids.add(new Identifier("daedalon", material.id() + "_aged_" + subject + "_" + form));
        }
        for (DecorMetal metal : DecorMetal.values()) {
            ids.add(new Identifier("daedalon", metal.id() + "_" + subject + "_" + form));
        }
        return ids;
    }

    private static StatueBlock requireStatue(TestContext context, Identifier id, String form) {
        context.assertTrue(Registries.BLOCK.containsId(id), "Missing Olympian block: " + id);
        Block block = Registries.BLOCK.get(id);
        context.assertTrue(block instanceof StatueBlock, "Olympian block must use shared statue rendering: " + id);
        context.assertTrue((block instanceof BustBlock) == form.equals("bust"),
                "Olympian block has the wrong sculptural form: " + id);
        return (StatueBlock) block;
    }

    private static VoxelShape collision(TestContext context, BlockState state) {
        return state.getCollisionShape(context.getWorld(), BlockPos.ORIGIN, ShapeContext.absent());
    }
}
