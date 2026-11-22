package com.oliver.daedalon.mixin.client.texturealias;

import com.oliver.daedalon.client.texturealias.CollectionResourcePackCompatibility;
import com.oliver.daedalon.client.texturealias.CollectionPackProfile;
import com.oliver.daedalon.client.texturealias.FamilyTextureAliasCoordinator;
import net.fabricmc.fabric.impl.resource.loader.GroupResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ResourcePackSourceTracker;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ResourcePackProfile.class, priority = 900)
abstract class ResourcePackProfileMixin implements CollectionPackProfile {
    @Shadow @Final private ResourcePackProfile.PackFactory packFactory;
    @Shadow @Final private Text description;

    @Unique private CollectionResourcePackCompatibility.Report daedalon$collectionReport;

    @Override
    public synchronized CollectionResourcePackCompatibility.Report erydon$collectionStatus() {
        if (daedalon$collectionReport == null) {
            ResourcePackProfile profile = (ResourcePackProfile) (Object) this;
            // Read only the small marker through the raw factory: no alias wrapping/hash preload.
            try (ResourcePack raw = packFactory.open(profile.getName())) {
                daedalon$collectionReport = CollectionResourcePackCompatibility.inspect(
                        profile.getName(), description.getString(), raw);
            } catch (RuntimeException exception) {
                daedalon$collectionReport = CollectionResourcePackCompatibility.unavailable(
                        profile.getName(), description.getString());
            }
        }
        return daedalon$collectionReport;
    }

    @Inject(method = "getCompatibility", at = @At("HEAD"), cancellable = true)
    private void daedalon$markIncompatibleCollectionPack(CallbackInfoReturnable<ResourcePackCompatibility> callback) {
        if (FamilyTextureAliasCoordinator.isLeader() && erydon$collectionStatus().rejected()) {
            callback.setReturnValue(ResourcePackCompatibility.TOO_OLD);
        }
    }

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true)
    private void daedalon$explainIncompatibleCollectionPack(CallbackInfoReturnable<Text> callback) {
        if (!FamilyTextureAliasCoordinator.isLeader()) return;
        var status = erydon$collectionStatus();
        if (status.rejected()) {
            callback.setReturnValue(callback.getReturnValue().copy().append("\n")
                    .append(Text.translatable(status.reason().translationKey()).formatted(Formatting.RED)));
        }
    }

    @Inject(method = "createResourcePack", at = @At("HEAD"))
    private void daedalon$invalidateCollectionStatus(CallbackInfoReturnable<ResourcePack> callback) {
        synchronized (this) {
            // Run before any family RETURN callback can cancel later injections.
            daedalon$collectionReport = null;
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
        var status = CollectionResourcePackCompatibility.inspect(profile.getName(), description.getString(), original);
        synchronized (this) {
            // A resource reload may reuse the profile after its pack files changed.
            daedalon$collectionReport = status;
        }
        ResourcePack pack = CollectionResourcePackCompatibility.disableIfLegacy(profile.getName(), original, status);
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
