package com.oliver.daedalon.client.fountain;

import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.registry.ModParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;

/** Client-only visual test. No server ticker, fluid updates, or block/chunk searches. */
public final class FountainParticles {
    static final int MAX_PARTICLES = 768;
    // Longest tested assembly needs at most 308 slots at full cadence, including splashes.
    static final int MAX_PER_FOUNTAIN = 320;
    static final int MAX_SPAWNS_PER_TICK = 48;
    static final int MAX_SOURCE_CHECKS = 64;
    private static final double RANGE_SQUARED = 32 * 32;
    private static final ArrayList<Source> SOURCES = new ArrayList<>();
    private static final ArrayList<Drop> DROPS = new ArrayList<>();
    private static ClientWorld world;
    private static SpriteProvider sprites;
    private static int cursor;
    private static long tick;

    private FountainParticles() { }

    public static void register() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.FOUNTAIN_DROP, provider -> {
            sprites = provider;
            // The controller supplies the receiving pool; do not spawn orphan particles via /particle.
            return (type, particleWorld, x, y, z, vx, vy, vz) -> null;
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, loadedWorld) -> {
            if (entity instanceof FountainBasinBlockEntity basin) {
                useWorld(loadedWorld);
                for (Source source : SOURCES) if (source.basin == basin) return;
                SOURCES.add(new Source(basin));
            }
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, unloadedWorld) -> {
            if (world != unloadedWorld) return;
            SOURCES.removeIf(source -> {
                if (source.basin != entity) return false;
                source.valid = false;
                return true;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> useWorld(null));
        ClientTickEvents.END_CLIENT_TICK.register(FountainParticles::tick);
    }

    private static void useWorld(ClientWorld next) {
        if (world == next) return;
        for (Drop drop : DROPS) drop.particle.markDead();
        DROPS.clear();
        SOURCES.clear();
        cursor = 0;
        world = next;
    }

    private static void tick(MinecraftClient client) {
        useWorld(client.world);
        if (world == null || client.isPaused()) return;
        tick++;
        ParticlesMode mode = client.options.getParticles().getValue();
        // Also retire detached particles after resource reloads, when the vanilla manager may
        // discard them without ticking/marking them dead. The retained list always stays bounded.
        DROPS.removeIf(drop -> {
            if (drop.particle.isAlive() && drop.source.matches()
                    && drop.generation == drop.source.generation
                    && tick - drop.born < FountainSprayPlan.MAX_AGE
                    && mode != ParticlesMode.MINIMAL) return false;
            drop.particle.markDead();
            drop.source.live--;
            return true;
        });
        if (sprites == null || client.getCameraEntity() == null || mode == ParticlesMode.MINIMAL
                || (mode == ParticlesMode.DECREASED && (tick & 1) != 0)) return;

        int remaining = mode == ParticlesMode.DECREASED
                ? MAX_SPAWNS_PER_TICK / 2 : MAX_SPAWNS_PER_TICK;
        int checks = Math.min(SOURCES.size(), MAX_SOURCE_CHECKS);
        for (int i = 0; i < checks && !SOURCES.isEmpty()
                && remaining > 0 && DROPS.size() < MAX_PARTICLES; i++) {
            if (cursor >= SOURCES.size()) cursor = 0;
            Source source = SOURCES.get(cursor++);
            if (!source.valid || source.basin.isRemoved()) {
                source.valid = false;
                SOURCES.remove(--cursor);
                continue;
            }
            var pos = source.basin.getPos();
            if (client.getCameraEntity().squaredDistanceTo(pos.getX() + 0.5,
                    pos.getY() + 2, pos.getZ() + 0.5) > RANGE_SQUARED) continue;
            BlockState state = source.basin.getCachedState();
            if (!(state.getBlock() instanceof FountainBasinBlock basinBlock)
                    || !state.get(FountainBasinBlock.WATERLOGGED) || source.basin.bowlCount() == 0) continue;
            if (!source.matches() || source.plan == null) {
                source.generation++;
                source.state = state;
                source.plinth = source.basin.plinthState();
                source.bowls = source.basin.bowlCount();
                source.plan = FountainSprayPlan.create(basinBlock.style(), state.get(FountainBasinBlock.SIZE),
                        source.basin.layout(state).bowls());
                source.color = BiomeColors.getWaterColor(world, pos);
                source.emitter = 0;
            }
            int cadence = source.plan.emissionsPerTick();
            int count = mode == ParticlesMode.DECREASED ? cadence / 2 : cadence;
            for (int j = 0; j < count && remaining > 0 && DROPS.size() < MAX_PARTICLES
                    && source.live < MAX_PER_FOUNTAIN; j++) {
                var emitter = source.plan.emitterForEmission(source.emitter);
                source.emitter = (source.emitter + 1) % cadence;
                boolean jet = emitter.velocityY() > 0;
                double spread = jet ? 0.06 : 0.09;
                double jitterX = (world.random.nextDouble() - 0.5) * spread;
                double jitterZ = (world.random.nextDouble() - 0.5) * spread;
                // Widen rim streams along the lip, never inward through the stone bowl.
                if (!jet && emitter.x() != 0) {
                    jitterX = Math.copySign(world.random.nextDouble() * 0.01, emitter.x());
                }
                if (!jet && emitter.z() != 0) {
                    jitterZ = Math.copySign(world.random.nextDouble() * 0.01, emitter.z());
                }
                double x = emitter.x() + jitterX, z = emitter.z() + jitterZ;
                if (!emitter.landing().contains(x, z)) continue;
                var particle = new FountainDropParticle(world, sprites,
                        pos.getX() + 0.5 + x, pos.getY() + emitter.y(), pos.getZ() + 0.5 + z,
                        emitter.velocityY() * (0.88 + world.random.nextDouble() * 0.24),
                        pos.getY() + emitter.landing().y(), source.color,
                        FountainSprayPlan.dropletScale(world.random.nextFloat()));
                client.particleManager.addParticle(particle);
                DROPS.add(new Drop(particle, source, source.generation, tick));
                source.live++;
                remaining--;
            }
        }
    }

    private static final class Source {
        final FountainBasinBlockEntity basin;
        boolean valid = true;
        BlockState state, plinth;
        int bowls, emitter, live, color, generation;
        FountainSprayPlan plan;

        Source(FountainBasinBlockEntity basin) { this.basin = basin; }

        boolean matches() {
            return valid && !basin.isRemoved() && state == basin.getCachedState()
                    && plinth == basin.plinthState() && bowls == basin.bowlCount();
        }
    }

    private record Drop(FountainDropParticle particle, Source source, int generation, long born) { }
}
