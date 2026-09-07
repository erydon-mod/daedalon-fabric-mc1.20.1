package com.oliver.daedalon.client.fountain;

import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.client.FountainParticleOption;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** One attenuated vanilla water loop per nearby fountain, capped by the controller. */
final class FountainSound extends MovingSoundInstance {
    final FountainBasinBlockEntity basin;
    FountainSound(FountainBasinBlockEntity basin) {
        super(SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.basin = basin;
        x = basin.getPos().getX() + .5;
        y = basin.getPos().getY() + 1;
        z = basin.getPos().getZ() + .5;
        repeat = true;
        repeatDelay = 0;
        volume = .01F;
        pitch = .85F;
    }
    static boolean active(FountainBasinBlockEntity basin, MinecraftClient client) {
        var state = basin.getCachedState();
        return FountainParticleOption.isSoundEnabled() && !basin.isRemoved() && basin.getWorld() == client.world
                && client.getCameraEntity() != null && basin.bowlCount() > 0
                && state.getBlock() instanceof FountainBasinBlock && state.get(FountainBasinBlock.WATERLOGGED)
                && client.getCameraEntity().squaredDistanceTo(basin.getPos().getX()+.5, basin.getPos().getY()+1, basin.getPos().getZ()+.5) < 16*16;
    }
    @Override public void tick() {
        if (!active(basin, MinecraftClient.getInstance())) { setDone(); return; }
        volume = Math.min(.22F + .04F * basin.bowlCount(), volume + .015F);
    }
}
