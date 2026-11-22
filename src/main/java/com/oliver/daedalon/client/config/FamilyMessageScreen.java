package com.oliver.daedalon.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** A readable, scrollable error page that preserves the parent's unsaved draft. */
final class FamilyMessageScreen extends DaedalonSettingsPage {
    private final Text message;

    FamilyMessageScreen(Screen parent, Text message) {
        super(parent, parent.getTitle());
        this.message = message;
    }

    @Override protected void buildContent() {
        paragraph(message, DaedalonConfigUi.ERROR_COLOR);
    }
}
