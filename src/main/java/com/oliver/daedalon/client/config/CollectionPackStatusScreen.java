package com.oliver.daedalon.client.config;

import com.oliver.daedalon.client.texturealias.CollectionPackProfile;
import com.oliver.daedalon.client.texturealias.CollectionResourcePackCompatibility;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Marker reports only; opening this page never changes the enabled resource packs. */
public final class CollectionPackStatusScreen extends DaedalonSettingsPage {
    private final List<Entry> entries = new ArrayList<>();
    private int page;

    public CollectionPackStatusScreen(Screen parent) {
        super(parent, Text.translatable("screen.erydon_family.pack.title"));
    }

    @Override protected void buildContent() {
        entries.clear();
        if (client != null) {
            for (var profile : client.getResourcePackManager().getEnabledProfiles()) {
                if ((Object) profile instanceof CollectionPackProfile source) {
                    var report = source.erydon$collectionStatus();
                    if (report != null && report.isCollection()) entries.add(new Entry(profile.getDisplayName(), report));
                }
            }
        }
        page = Math.min(page, Math.max(0, entries.size() - 1));
        paragraph(Text.translatable("screen.erydon_family.pack.help"));
        if (entries.isEmpty()) {
            paragraph(Text.translatable("screen.erydon_family.pack.empty"), DaedalonConfigUi.TEXT_COLOR);
        } else {
            Entry entry = entries.get(page);
            var report = entry.report();
            paragraph(entry.name(), DaedalonConfigUi.TEXT_COLOR);
            paragraph(Text.translatable(report.reason().translationKey()),
                    report.compatible() ? DaedalonConfigUi.SUCCESS_COLOR : DaedalonConfigUi.ERROR_COLOR);
            paragraph(Text.translatable("screen.erydon_family.pack.metadata", known(report.release()),
                    report.resolution() == null ? unknown() : report.resolution() + "x",
                    known(report.assetGeneration()), known(report.familyGeneration())));
            paragraph(Text.translatable("screen.erydon_family.pack.page", page + 1, entries.size()));
        }
    }

    @Override protected void buildFooter() {
        int slot = (panel.contentWidth() - 16) / 3;
        var previous = footerButton(panel.contentLeft(), slot, Text.translatable("screen.erydon_family.pack.previous"),
                button -> { page--; rebuildPage(); }, DaedalonConfigUi.Button.Style.QUIET);
        previous.active = page > 0;
        footerButton(panel.contentLeft() + slot + 8, slot, Text.translatable("gui.back"),
                button -> close(), DaedalonConfigUi.Button.Style.QUIET);
        var next = footerButton(panel.contentLeft() + (slot + 8) * 2, slot,
                Text.translatable("screen.erydon_family.pack.next"),
                button -> { page++; rebuildPage(); }, DaedalonConfigUi.Button.Style.QUIET);
        next.active = page + 1 < entries.size();
    }

    private Text known(Object value) { return value == null ? unknown() : Text.literal(value.toString()); }
    private Text unknown() { return Text.translatable("screen.erydon_family.pack.unknown"); }
    private record Entry(Text name, CollectionResourcePackCompatibility.Report report) { }
}
