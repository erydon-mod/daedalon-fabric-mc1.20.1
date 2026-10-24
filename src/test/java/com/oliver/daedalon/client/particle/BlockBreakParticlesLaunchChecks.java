package com.oliver.daedalon.client.particle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.lang.reflect.Field;

/** Calls the transformed ParticleManager without creating a window, world or graphics context. */
public final class BlockBreakParticlesLaunchChecks {
    private BlockBreakParticlesLaunchChecks() { }

    /** Run after Minecraft bootstrap, while the test registry is still writable. */
    public static void run() throws Exception {
        Field singleton = field(MinecraftClient.class, "instance");
        Object previous = singleton.get(null);
        try {
            MinecraftClient client = allocate(MinecraftClient.class);
            BlockRenderManager renderer = allocate(BlockRenderManager.class);
            field(BlockRenderManager.class, "models").set(renderer, new BlockModels(null) {
                @Override public Sprite getModelParticleSprite(BlockState state) { return null; }
            });
            field(MinecraftClient.class, "blockRenderManager").set(client, renderer);
            field(MinecraftClient.class, "blockColors").set(client, new BlockColors());
            singleton.set(null, client);
            Counter manager = allocate(Counter.class);
            require(count(manager, Blocks.STONE.getDefaultState()) == 64, "Vanilla stone debris changed");
            require(count(manager, fixture("unrelated", "cube", VoxelShapes.fullCube())) == 64,
                    "Other namespaces must retain vanilla debris");
            require(count(manager, fixture("daedalon", "cube", VoxelShapes.fullCube())) == 16,
                    "Own namespace must reach the actual ParticleManager cap");
            int otherExpected = FabricLoader.getInstance().isModLoaded("erydon") ? 16 : 64;
            require(count(manager, fixture("erydon", "cube", VoxelShapes.fullCube())) == otherExpected,
                    "The other mod's independent mixin must compose without double emission");
            require(count(manager, fixture("daedalon", "empty", VoxelShapes.empty())) == 0,
                    "Empty outlines must stay empty");
            require(count(manager, Blocks.AIR.getDefaultState()) == 0, "Air unexpectedly emitted debris");
            VoxelShape fragmented = VoxelShapes.empty();
            for (int i = 0; i < 32; i++) {
                double x = (i % 4) / 4.0, y = (i / 4 % 4) / 4.0, z = (i / 16) / 2.0;
                fragmented = VoxelShapes.union(fragmented,
                        VoxelShapes.cuboid(x, y, z, x + 0.1, y + 0.1, z + 0.1));
            }
            require(fragmented.getBoundingBoxes().size() > 16, "Fragmented regression fixture is trivial");
            require(count(manager, fixture("daedalon", "fragmented", fragmented)) <= 16,
                    "Separate outline boxes multiplied the event budget");
            int urnStates = 0, mostUrnBoxes = 0, largestUrnBurst = 0;
            for (var urnStyle : com.oliver.daedalon.block.UrnBlock.UrnStyle.values()) {
                var urn = Registry.register(Registries.BLOCK,
                        new Identifier("daedalon", "break_budget_probe_urn_" + urnStyle.name().toLowerCase(java.util.Locale.ROOT)),
                        new com.oliver.daedalon.block.UrnBlock(Block.Settings.copy(Blocks.STONE), urnStyle));
                for (BlockState state : urn.getStateManager().getStates()) {
                    var outline = state.getOutlineShape(null, new BlockPos(10, 70, -30));
                    mostUrnBoxes = Math.max(mostUrnBoxes, outline.getBoundingBoxes().size());
                    int emitted = count(manager, state);
                    require(emitted > 0 && emitted <= BlockBreakParticleBudget.MAX_PARTICLES,
                            "An actual urn break bypassed the total event budget: " + urnStyle + " / " + state + " / " + emitted);
                    largestUrnBurst = Math.max(largestUrnBurst, emitted);
                    urnStates++;
                }
            }
            require(urnStates == 264, "Cover every actual urn style, size, offset and facing");
            require(mostUrnBoxes > 16, "Actual urn regression must include nontrivial fragmented outlines");
            System.out.println("DAEDALON_URN_BREAK_PARTICLES_OK: " + urnStates + " actual urn states; maxBurst="
                    + largestUrnBurst + "; maxOutlineBoxes=" + mostUrnBoxes + "; same-owner extended/offset break events");
            var style = java.util.Arrays.stream(com.oliver.daedalon.block.FixedDecorBlock.Style.values())
                    .filter(com.oliver.daedalon.block.FixedDecorBlock.Style::isFinial).findFirst().orElseThrow();
            var finial = Registry.register(Registries.BLOCK, new Identifier("daedalon", "break_budget_probe_finial"),
                    new com.oliver.daedalon.block.FinialBlock(Block.Settings.copy(Blocks.STONE), style));
            int states = 0;
            for (BlockState state : finial.getStateManager().getStates()) {
                int emitted = count(manager, state);
                require(emitted > 0 && emitted <= 16, "Finial outline hook bypassed the total event budget: " + state);
                states++;
            }
            System.out.println("DAEDALON_FINIAL_BREAK_PARTICLES_OK: " + states + " states, including fitted and slab supports");
            System.out.println("DAEDALON_BREAK_PARTICLES_OK: actual mixed-in ParticleManager; own blocks <=16; empty=0; vanilla/foreign=64; other mod loaded=" + FabricLoader.getInstance().isModLoaded("erydon"));
        } finally {
            singleton.set(null, previous);
        }
    }

    private static BlockState fixture(String namespace, String name, VoxelShape shape) {
        return Registry.register(Registries.BLOCK, new Identifier(namespace, "break_budget_probe_daedalon_" + name),
                new Block(Block.Settings.copy(Blocks.STONE).dynamicBounds()) {
                    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
                        return shape;
                    }
                }).getDefaultState();
    }

    private static int count(Counter manager, BlockState state) {
        manager.count = 0;
        manager.addBlockBreakParticles(new BlockPos(10, 70, -30), state);
        return manager.count;
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    private static final class Counter extends ParticleManager {
        int count;
        private Counter() { super(null, null); }
        @Override public void addParticle(Particle particle) {
            require(particle instanceof BlockDustParticle, "Break effect changed particle type");
            count++;
        }
    }
}
