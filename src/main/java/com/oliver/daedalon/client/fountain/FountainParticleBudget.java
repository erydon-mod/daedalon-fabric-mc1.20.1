package com.oliver.daedalon.client.fountain;

/** Reused limits: selecting Normal never allocates a High-mode particle pool. */
public record FountainParticleBudget(int multiplier, int maxParticles, int maxPerFountain, int maxSpawns) {
    public static final FountainParticleBudget NORMAL = new FountainParticleBudget(1, FountainParticles.MAX_PARTICLES, FountainParticles.MAX_PER_FOUNTAIN, FountainParticles.MAX_SPAWNS_PER_TICK);
    public static final FountainParticleBudget HIGH = new FountainParticleBudget(4, FountainParticles.MAX_PARTICLES * 4, FountainParticles.MAX_PER_FOUNTAIN * 4, FountainParticles.MAX_SPAWNS_PER_TICK * 4);

    public static FountainParticleBudget select(boolean high) { return high ? HIGH : NORMAL; }
    public int emissions(int normalCadence, boolean decreased) {
        return (decreased ? normalCadence / 2 : normalCadence) * multiplier;
    }
    public int spawnLimit(boolean decreased) { return decreased ? maxSpawns / 2 : maxSpawns; }
}
