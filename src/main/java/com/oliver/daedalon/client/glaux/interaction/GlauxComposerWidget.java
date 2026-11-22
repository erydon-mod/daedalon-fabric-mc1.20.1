package com.oliver.daedalon.client.glaux.interaction;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Vanilla editing with Glaux's flat parchment rendering. Vanilla's text field
 * always draws a dark drop shadow, which doubles dark glyphs on this light HUD.
 */
final class GlauxComposerWidget extends TextFieldWidget {
    private final TextRenderer renderer;
    private final GlauxComposerViewport.Metrics metrics;
    private int selectionAnchor, ticks, color = 0xff302a22;
    private Text placeholder;
    private String cachedText;
    private int cachedCursor = -1, cachedAnchor = -1, cachedWidth = -1;
    private GlauxComposerViewport.Window viewport;

    GlauxComposerWidget(TextRenderer renderer, int x, int y, int width, int height, Text message) {
        super(renderer, x, y, width, height, message);
        this.renderer = renderer;
        this.metrics = new GlauxComposerViewport.Metrics() {
            @Override public int width(String value) { return renderer.getWidth(value); }
            @Override public String trim(String value, int width, boolean backwards) { return renderer.trimToWidth(value, width, backwards); }
        };
        setDrawsBackground(false);
    }

    @Override public void setSelectionEnd(int index) {
        super.setSelectionEnd(index);
        selectionAnchor = Math.max(0, Math.min(getText().length(), index));
    }

    @Override public void setEditableColor(int color) { super.setEditableColor(color); this.color = color; }
    @Override public void setPlaceholder(Text text) { super.setPlaceholder(text); placeholder = text; }
    @Override public void tick() { super.tick(); ticks++; }
    @Override public void setFocused(boolean focused) {
        // The HUD places the composer each frame; that must not restart its blink.
        if (focused != isFocused()) { super.setFocused(focused); ticks = 0; }
    }

    void invalidateLayout() { cachedText = null; }

    GlauxComposerViewport.Window viewport() {
        String text = getText();
        int cursor = getCursor(), width = getWidth();
        if (!text.equals(cachedText) || cursor != cachedCursor || selectionAnchor != cachedAnchor || width != cachedWidth) {
            viewport = GlauxComposerViewport.layout(text, cursor, selectionAnchor, viewport == null ? 0 : viewport.start(), width, metrics);
            cachedText = text; cachedCursor = cursor; cachedAnchor = selectionAnchor; cachedWidth = width;
        }
        return viewport;
    }

    @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;
        GlauxComposerViewport.Window line = viewport();
        int x = getX(), y = getY();
        draw.enableScissor(x, y - 1, x + getWidth(), y + renderer.fontHeight + 1);
        if (line.selectionLeft() != line.selectionRight()) {
            draw.fill(x + line.selectionLeft(), y - 1, x + line.selectionRight(), y + renderer.fontHeight, 0xffe0c89f);
        }
        if (line.text().isEmpty() && getText().isEmpty() && placeholder != null) {
            draw.drawText(renderer, renderer.trimToWidth(placeholder.getString(), Math.max(0, getWidth() - 1)), x, y, 0xff756a59, false);
        } else {
            // One complete run, without a shadow or cursor-split duplicate.
            draw.drawText(renderer, line.text(), x, y, color, false);
        }
        if (isFocused() && (ticks / 6) % 2 == 0) {
            draw.fill(x + line.caret(), y - 1, x + line.caret() + 1, y + renderer.fontHeight, color);
        }
        draw.disableScissor();
    }
}
