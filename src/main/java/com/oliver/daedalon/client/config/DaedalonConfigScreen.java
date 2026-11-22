package com.oliver.daedalon.client.config;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.client.FountainParticleOption;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import java.io.IOException;

/** Client drafts survive resize and navigation; only Save changes stored or live preferences. */
public final class DaedalonConfigScreen extends DaedalonSettingsPage {
    private final ConfigScreenFactory<?> erydon = ErydonSettingsBridge.find();
    private boolean notice = true;
    private boolean high = FountainParticleOption.isHigh();
    private boolean sound = FountainParticleOption.isSoundEnabled();
    private Text error;

    public DaedalonConfigScreen(Screen parent) {
        super(parent, Text.translatable("screen.daedalon.config.title"));
        try {
            notice = FamilyClientOptions.readNotice();
        } catch (IOException failure) {
            Daedalon.LOGGER.warn("Could not open ERYDON family client preferences", failure);
            error = Text.translatable("screen.erydon_family.config.load_failed");
        }
    }

    @Override protected void buildContent() {
        paragraph(assetInfo());
        paragraph(ownership());
        if (error != null) paragraph(error, DaedalonConfigUi.ERROR_COLOR);
        var shared = button(Text.translatable("screen.erydon_family.config.open_erydon"), button -> openErydon());
        shared.active = erydon != null;
        shared.setTooltip(Tooltip.of(Text.translatable("screen.erydon_family.config.shared_help")));
        button(particleLabel(), button -> {
            high = !high;
            button.setMessage(particleLabel());
        }).setTooltip(Tooltip.of(Text.translatable("screen.daedalon.config.particles_help")));
        button(soundLabel(), button -> {
            sound = !sound;
            button.setMessage(soundLabel());
        }).setTooltip(Tooltip.of(Text.translatable("screen.daedalon.config.sound_help")));
        button(noticeLabel(), button -> {
            notice = !notice;
            button.setMessage(noticeLabel());
        }).setTooltip(Tooltip.of(Text.translatable("screen.erydon_family.config.notice_help")));
        buttonPair(Text.translatable("screen.erydon_family.pack.button"),
                button -> { if (client != null) client.setScreen(new CollectionPackStatusScreen(this)); },
                Text.translatable("screen.erydon_family.help.title"),
                button -> { if (client != null) client.setScreen(new FamilyHelpScreen(this)); });
    }

    @Override protected void buildFooter() {
        int half = (panel.contentWidth() - 8) / 2;
        footerButton(panel.contentLeft(), half, Text.translatable("screen.erydon_family.config.save"),
                button -> save(), DaedalonConfigUi.Button.Style.PRIMARY);
        footerButton(panel.contentLeft() + half + 8, half, ScreenTexts.CANCEL,
                button -> close(), DaedalonConfigUi.Button.Style.QUIET);
    }

    private Text assetInfo() {
        String generation = FabricLoader.getInstance().getModContainer("daedalon")
                .map(mod -> mod.getMetadata().getCustomValue("erydon:family_compatibility_generation"))
                .map(value -> Integer.toString(value.getAsNumber().intValue())).orElse("?");
        return Text.translatable("screen.erydon_family.config.assets", generation);
    }

    private Text ownership() {
        return Text.translatable(erydon == null ? "screen.erydon_family.config.standalone"
                : "screen.erydon_family.config.shared");
    }

    private Text noticeLabel() {
        return Text.translatable("screen.erydon_family.config.notice", notice ? ScreenTexts.ON : ScreenTexts.OFF);
    }

    private Text particleLabel() {
        return Text.translatable("screen.daedalon.config.particles", Text.translatable(
                high ? "screen.daedalon.config.high" : "screen.daedalon.config.normal"));
    }

    private Text soundLabel() {
        return Text.translatable("screen.daedalon.config.sound", sound ? ScreenTexts.ON : ScreenTexts.OFF);
    }

    private void openErydon() {
        if (client == null || erydon == null) return;
        try {
            Screen screen = erydon.create(this);
            if (screen == null) throw new IllegalStateException("ERYDON returned no settings screen");
            client.setScreen(screen);
        } catch (RuntimeException | LinkageError failure) {
            Daedalon.LOGGER.warn("Could not open ERYDON settings", failure);
            showError(Text.translatable("screen.erydon_family.config.open_failed"));
        }
    }

    private void save() {
        boolean fountainSaved = false;
        try {
            if (high != FountainParticleOption.isHigh() || sound != FountainParticleOption.isSoundEnabled()) {
                FountainParticleOption.save(high, sound);
                fountainSaved = true;
            }
            FamilyClientOptions.saveNotice(notice);
            close();
        } catch (IOException failure) {
            Daedalon.LOGGER.warn("Could not save Daedalon client preferences", failure);
            showError(Text.translatable(fountainSaved ? "screen.daedalon.config.partial_save"
                    : "screen.erydon_family.config.save_failed"));
        }
    }

    private void showError(Text message) {
        if (client != null) client.setScreen(new FamilyMessageScreen(this, message));
        else error = message;
    }
}
