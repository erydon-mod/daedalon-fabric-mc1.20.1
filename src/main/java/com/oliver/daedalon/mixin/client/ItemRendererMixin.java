package com.oliver.daedalon.mixin.client;

import com.oliver.daedalon.client.model.obj.ObjGuiIconCache;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemRenderer.class, priority = 1100)
abstract class ItemRendererMixin {
    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void daedalon$renderCachedObjGuiIcon(
            ItemStack stack,
            ModelTransformationMode renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            BakedModel model,
            CallbackInfo callbackInfo
    ) {
        if (ObjGuiIconCache.tryRender(
                stack,
                renderMode,
                leftHanded,
                matrices,
                vertexConsumers,
                light,
                overlay,
                model
        )) {
            callbackInfo.cancel();
        }
    }
}
