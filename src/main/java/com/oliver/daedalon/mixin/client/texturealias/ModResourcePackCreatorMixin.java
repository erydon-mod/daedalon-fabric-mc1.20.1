package com.oliver.daedalon.mixin.client.texturealias;

import com.oliver.daedalon.client.texturealias.FamilyTextureAliasCoordinator;
import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.minecraft.resource.ResourceType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ModResourcePackCreator.class)
abstract class ModResourcePackCreatorMixin {
    @Shadow
    @Final
    private ResourceType type;

    @ModifyVariable(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/impl/resource/loader/ModResourcePackUtil;"
                            + "appendModResourcePacks(Ljava/util/List;Lnet/minecraft/resource/ResourceType;"
                            + "Ljava/lang/String;)V",
                    shift = At.Shift.AFTER
            ),
            ordinal = 0
    )
    private List<ModResourcePack> daedalon$wrapModResourcePacks(
            List<ModResourcePack> packs
    ) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return packs;
        }
        return FamilyTextureAliasCoordinator.wrapAllFamilyModPacks(packs);
    }
}
