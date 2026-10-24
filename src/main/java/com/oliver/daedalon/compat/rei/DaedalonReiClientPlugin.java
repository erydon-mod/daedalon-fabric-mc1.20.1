package com.oliver.daedalon.compat.rei;

import com.oliver.daedalon.client.search.DaedalonCatalogue;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry;
import me.shedaniel.rei.api.client.subsets.SubsetsRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Match ERYDON's native collapsible groups and Material/Shape subset navigation. */
public final class DaedalonReiClientPlugin implements REIClientPlugin {
    @Override
    public void registerCollapsibleEntries(CollapsibleEntryRegistry registry) {
        DaedalonCatalogue.groups().designs().forEach((family, stacks) -> {
            if (stacks.size() > 1) {
                registry.group(new Identifier("daedalon", "rei_group_" + family),
                        Text.literal("Daedalon ").append(DaedalonCatalogue.designName(family)), entries(stacks));
            }
        });
    }

    @Override
    public void registerSubsets(SubsetsRegistry registry) {
        var groups = DaedalonCatalogue.groups();
        String root = "Daedalon/";
        String designs = root + Text.translatable("catalogue.daedalon.designs").getString() + "/";
        String forms = root + Text.translatable("catalogue.daedalon.forms").getString() + "/";
        String materials = root + Text.translatable("catalogue.daedalon.materials").getString() + "/";
        groups.designs().forEach((family, stacks) -> registry.registerPathEntries(
                designs + DaedalonCatalogue.designName(family).getString(), entries(stacks)));
        groups.forms().forEach((form, stacks) -> registry.registerPathEntries(
                forms + DaedalonCatalogue.formName(form).getString(), entries(stacks)));
        groups.materials().forEach((material, stacks) -> registry.registerPathEntries(
                materials + DaedalonCatalogue.materialName(material).getString(), entries(stacks)));
    }

    private static List<EntryStack<?>> entries(List<ItemStack> stacks) {
        return stacks.stream().<EntryStack<?>>map(EntryStacks::of).toList();
    }
}
