package com.oliver.daedalon.compat.rei;

import com.oliver.daedalon.client.search.DaedalonCatalogue;
import com.oliver.daedalon.client.search.DaedalonBrowseOrder;
import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import com.oliver.daedalon.item.LouterionBasinItem;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Optional native family/design groups; search retains the shared catalogue vocabulary. */
public final class DaedalonReiClientPlugin implements REIClientPlugin {
    @Override
    public void registerEntries(EntryRegistry registry) {
        // REI's default item registration runs at priority -100; this plugin's
        // default priority is 0. It supplies only the standard basin stacks.
        var largeBasins = DaedalonCatalogue.groups().designs().getOrDefault("louterion_basin", List.of())
                .stream().filter(LouterionBasinItem::isLarge).toList();
        registry.addEntries(entries(largeBasins));

        // Keep both sizes adjacent in the established material order.
        var order = DaedalonBrowseOrder.catalogue(DaedalonSearchVocabulary.index(),
                registry.getEntryStacks().toList(), DaedalonReiClientPlugin::identifier);
        if (!order.changed()) return;
        registry.removeEntryIf(order.members()::contains);
        // The public append API preserves all foreign/unknown entries and their
        // relative order. REI's user-selected name ordering can still override this.
        registry.addEntries(order.entries());
    }

    @Override
    public void registerCollapsibleEntries(CollapsibleEntryRegistry registry) {
        // This API supplies membership, not the displayed representative. The
        // optional REI search-result hook promotes a matched fresh Glacium item.
        var catalogue = DaedalonCatalogue.groups();
        DaedalonReiGroups.plan(catalogue.designs(), catalogue.forms()).forEach(group -> {
            Text name = group.form() ? DaedalonCatalogue.formName(group.key()) : DaedalonCatalogue.designName(group.key());
            registry.group(new Identifier("daedalon", "rei_group_" + group.key()),
                    Text.literal("Daedalon ").append(name), entries(group.entries()));
        });
    }

    private static List<EntryStack<?>> entries(List<ItemStack> stacks) {
        return stacks.stream().<EntryStack<?>>map(EntryStacks::of).toList();
    }

    private static String identifier(EntryStack<?> entry) {
        if (!(entry.getValue() instanceof ItemStack item)) return null;
        var id = Registries.ITEM.getId(item.getItem());
        return id.getNamespace().equals("daedalon") ? id.toString() : null;
    }
}
