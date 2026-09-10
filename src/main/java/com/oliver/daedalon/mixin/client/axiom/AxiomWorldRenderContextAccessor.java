package com.oliver.daedalon.mixin.client.axiom;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.render.AxiomWorldRenderContext", remap = false)
public interface AxiomWorldRenderContextAccessor {
    @Accessor("poseStack") MatrixStack daedalon$matrices();
    @Accessor("position") Vec3d daedalon$cameraPosition();
    @Accessor("rotation") Quaternionfc daedalon$cameraRotation();
    @Accessor("projection") Matrix4fc daedalon$projection();
}
