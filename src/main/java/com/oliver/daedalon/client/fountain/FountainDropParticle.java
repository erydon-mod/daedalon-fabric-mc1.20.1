package com.oliver.daedalon.client.fountain;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;

/** Ordinary lit sprite prototype; deliberately not advertised as refractive shader water. */
final class FountainDropParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final double landingY;
    private final float dropletScale;
    private int splashAge = -1;

    FountainDropParticle(ClientWorld world, SpriteProvider sprites,
                         double x, double y, double z, double velocityX, double velocityY, double velocityZ, double landingY,
                         int color, float dropletScale) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.landingY = landingY;
        this.velocityY = velocityY;
        this.velocityX = velocityX;
        this.velocityZ = velocityZ;
        this.dropletScale = dropletScale;
        maxAge = FountainSprayPlan.MAX_AGE;
        collidesWithWorld = false;
        scale = dropletScale;
        setColor(channel(color, 16), channel(color, 8), channel(color, 0));
        setAlpha(0.9F);
        setSprite(sprites.getSprite(0, 1));
    }

    private static float channel(int color, int shift) {
        return 0.3F + 0.7F * ((color >> shift) & 255) / 255.0F;
    }

    @Override
    public void tick() {
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        if (++age >= maxAge) {
            markDead();
        } else if (splashAge >= 0) {
            if (++splashAge >= 4) {
                markDead();
            } else {
                scale = FountainSprayPlan.splashScale(dropletScale, splashAge);
                setAlpha(0.85F - splashAge * 0.18F);
            }
        } else {
            velocityY = FountainSprayPlan.velocityAfterTick(velocityY);
            double nextY = y + velocityY;
            if (velocityY < 0 && nextY <= landingY) {
                setPos(x + velocityX, landingY + 0.006, z + velocityZ);
                velocityY = 0;
                splashAge = 0;
                scale = FountainSprayPlan.splashScale(dropletScale, 0);
                setAlpha(0.85F);
                // Both dedicated sprites are neutral, so landing retains the droplet's tint.
                setSprite(sprites.getSprite(1, 1));
            } else {
                setPos(x + velocityX, nextY, z + velocityZ);
            }
        }
    }

    @Override
    public float getSize(float tickDelta) {
        // The smooth drop fills half its texture instead of the old vanilla sprite's quarter.
        return splashAge < 0 ? scale * 0.5F : scale;
    }

    @Override
    public void buildGeometry(VertexConsumer vertices, Camera camera, float tickDelta) {
        if (splashAge < 0) {
            var cameraPos = camera.getPos();
            float px = (float)(prevPosX + (x - prevPosX) * tickDelta - cameraPos.x);
            float py = (float)(prevPosY + (y - prevPosY) * tickDelta - cameraPos.y);
            float pz = (float)(prevPosZ + (z - prevPosZ) * tickDelta - cameraPos.z);
            FountainStreakGeometry.emit(vertices, camera.getRotation(), px, py, pz, scale * .5F,
                    (float)velocityX, (float)velocityY, (float)velocityZ,
                    getMinU(), getMaxU(), getMinV(), getMaxV(), red, green, blue, alpha, getBrightness(tickDelta));
            return;
        }
        // Ripples lie on the receiving water plane, never on the camera's billboard plane.
        // In particular, do not interpolate from the last airborne Y into a tilted/floating splash.
        var cameraPos = camera.getPos();
        FountainRippleGeometry.emit(vertices, (float) (x - cameraPos.x), (float) (y - cameraPos.y),
                (float) (z - cameraPos.z), scale, getMinU(), getMaxU(), getMinV(), getMaxV(),
                red, green, blue, alpha, getBrightness(tickDelta));
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }
}
