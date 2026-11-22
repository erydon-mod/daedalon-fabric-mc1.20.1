package com.oliver.daedalon.client.glaux.interaction;

import com.oliver.daedalon.client.glaux.dialogue.GlauxDialogueService;
import com.oliver.daedalon.client.glaux.dialogue.HandoffCodec;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Deliberate handoff review only; the ordinary conversation never opens a Screen. */
final class GlauxHandoffReviewScreen extends Screen {
    private final GlauxDialogueService service;
    private final GlauxInteraction.View view;
    private final GlauxDialogueService.Context context;
    private TextFieldWidget question;
    private ButtonWidget open;
    private String status = "";
    private int panelX, panelY, panelWidth;

    GlauxHandoffReviewScreen(GlauxDialogueService service, GlauxInteraction.View view, GlauxDialogueService.Context context) {
        super(Text.translatable("gui.daedalon.glaux.review_title"));
        this.service = service;
        this.view = view;
        this.context = context;
    }

    private Text label(String key) { return Text.translatable("gui.daedalon.glaux." + key); }

    @Override protected void init() {
        panelWidth = Math.min(470, width - 24);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(10, (height - 184) / 2);
        question = new TextFieldWidget(textRenderer, panelX + 14, panelY + 61, panelWidth - 28, 20, label("composer"));
        question.setMaxLength(2000);
        question.setText(view.handoffDraft == null ? service.enquiryText(view.session) : view.handoffDraft);
        question.setChangedListener(value -> { view.handoffDraft = value; if (open != null) open.active = !value.isBlank(); });
        addDrawableChild(question);
        int buttonWidth = (panelWidth - 40) / 3;
        open = addDrawableChild(ButtonWidget.builder(label("review_open"), ignored -> open()).dimensions(panelX + 12, panelY + 144, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(label("review_copy"), ignored -> copy()).dimensions(panelX + 20 + buttonWidth, panelY + 144, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(label("review_back"), ignored -> close()).dimensions(panelX + 28 + buttonWidth * 2, panelY + 144, buttonWidth, 20).build());
        open.active = !question.getText().isBlank();
        setInitialFocus(question);
    }

    private HandoffCodec.Export export() {
        String text = question.getText();
        if (text.isBlank() || text.length() > 2000) throw new IllegalArgumentException("question");
        view.handoffDraft = text;
        if (view.exported == null || !text.equals(view.exportedQuestion)) {
            view.exported = service.createHandoff(view.session, context, text);
            view.exportedQuestion = text;
        }
        return view.exported;
    }

    private void open() {
        try {
            HandoffCodec.Export exported = export();
            if (exported.uri() == null) { status = label("review_too_long").getString(); return; }
            // Standard Minecraft confirmation; no browser/network activity on arrival.
            ConfirmLinkScreen.open(exported.uri().toASCIIString(), this, false);
        } catch (RuntimeException error) { status = label("review_invalid").getString(); }
    }

    private void copy() {
        if (client == null) return;
        try { client.keyboard.setClipboard(export().fallbackText()); status = label("copied").getString(); }
        catch (RuntimeException error) { status = label("copy_failed").getString(); }
    }

    boolean codePoint(int codePoint) {
        if (question == null || !question.isFocused() || !Character.isValidCodePoint(codePoint)) return false;
        String text = new String(Character.toChars(codePoint));
        int capacity = 2000 - question.getText().length() + question.getSelectedText().length();
        if (text.length() <= capacity) question.write(text);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_V && Screen.hasControlDown() && question.isFocused() && client != null) {
            int capacity = 2000 - question.getText().length() + question.getSelectedText().length();
            question.write(GlauxTextLimits.prefix(client.keyboard.getClipboard(), capacity));
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        // Keep the actual world behind a compact review panel; no fabricated scene.
        draw.fill(panelX, panelY, panelX + panelWidth, panelY + 180, 0xf9f8f3e9);
        draw.drawBorder(panelX, panelY, panelWidth, 180, 0xffaa8855);
        draw.drawText(textRenderer, title, panelX + 14, panelY + 13, 0xff795226, false);
        draw.drawTextWrapped(textRenderer, label("review_intro"), panelX + 14, panelY + 33, panelWidth - 28, 0xff302a22);
        draw.drawTextWrapped(textRenderer, label("review_context"), panelX + 14, panelY + 91, panelWidth - 28, 0xff756a59);
        if (!status.isBlank()) draw.drawTextWrapped(textRenderer, Text.literal(status), panelX + 14, panelY + 121, panelWidth - 28, 0xff795226);
        super.render(draw, mouseX, mouseY, delta);
    }

    @Override public void tick() { question.tick(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(null); }
}
