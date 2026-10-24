package com.oliver.daedalon.mixin.client;

import com.oliver.daedalon.client.finish.DaedalonStoneFinishLabelsPack;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Like ERYDON's label overlay, resolve names during language/resource loading. */
@Mixin(LifecycledResourceManagerImpl.class)
abstract class StoneFinishResourceManagerMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<ResourcePack> daedalon$stoneFinishLabels(List<ResourcePack> packs,
                                                               ResourceType type, List<ResourcePack> original) {
        return DaedalonStoneFinishLabelsPack.append(type, packs);
    }
}
