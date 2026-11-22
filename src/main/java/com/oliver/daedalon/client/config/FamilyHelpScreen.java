package com.oliver.daedalon.client.config;

import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Explicit links to the current public help and community pages. */
final class FamilyHelpScreen extends DaedalonSettingsPage {
    FamilyHelpScreen(Screen parent) {
        super(parent, Text.translatable("screen.erydon_family.help.title"));
    }

    @Override protected void buildContent() {
        link("support", "https://www.erydon.co.uk/support");
        link("community", "https://discord.gg/MaqUvVgTFm");
        link("product", "https://www.erydon.co.uk/collection/daedalon");
    }

    private void link(String key, String url) {
        button(Text.translatable("screen.erydon_family.help." + key),
                button -> ConfirmLinkScreen.open(url, this, false));
    }
}
