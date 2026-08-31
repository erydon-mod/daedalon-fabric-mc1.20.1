package com.oliver.daedalon.mixin.client.texturealias;

import com.oliver.daedalon.client.texturealias.CollectionResourcePackCompatibility;
import com.oliver.daedalon.client.texturealias.FamilyTextureAliasCoordinator;
import net.fabricmc.fabric.impl.resource.loader.GroupResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ResourcePackSourceTracker;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ResourcePackProfile.class, priority = 900)
abstract class ResourcePackProfileMixin {
    @Unique
    private Boolean daedalon$legacyCollectionPack;

    @Inject(method = "getCompatibility", at = @At("HEAD"), cancellable = true)
    private void daedalon$markLegacyCollectionPackTooOld(
            CallbackInfoReturnable<ResourcePackCompatibility> info
    ) {
        if (!FamilyTextureAliasCoordinator.isLeader()) {
            return;
        }

        ResourcePackProfile profile = (ResourcePackProfile) (Object) this;
        if (daedalon$legacyCollectionPack == null) {
            daedalon$legacyCollectionPack =
                    CollectionResourcePackCompatibility.shouldMarkTooOld(
                            profile.getName(),
                            profile.getDescription().getString()
                    );
        }
        if (daedalon$legacyCollectionPack) {
            info.setReturnValue(ResourcePackCompatibility.TOO_OLD);
        }
    }

    @Inject(method = "createResourcePack", at = @At("RETURN"), cancellable = true)
    private void daedalon$wrapExactTextureAliasPack(
            CallbackInfoReturnable<ResourcePack> info
    ) {
        if (!FamilyTextureAliasCoordinator.isLeader()) {
            return;
        }

        ResourcePack original = info.getReturnValue();
        ResourcePackProfile profile = (ResourcePackProfile) (Object) this;
        ResourcePack pack = CollectionResourcePackCompatibility.disableIfLegacy(
                profile.getName(),
                profile.getDescription().getString(),
                original
        );
        if (pack != original) {
            ResourcePackSourceTracker.setSource(
                    pack,
                    ResourcePackSourceTracker.getSource(original)
            );
        }
        if (pack instanceof GroupResourcePack) {
            info.setReturnValue(pack);
            return;
        }

        ResourcePack wrapped =
                FamilyTextureAliasCoordinator.wrapFamilyNamespaces(pack);
        if (wrapped == pack) {
            if (pack != original) {
                info.setReturnValue(pack);
            }
            return;
        }

        ResourcePackSourceTracker.setSource(
                wrapped,
                ResourcePackSourceTracker.getSource(pack)
        );
        info.setReturnValue(wrapped);
    }
}
