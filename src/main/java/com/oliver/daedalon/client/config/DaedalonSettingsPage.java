package com.oliver.daedalon.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Shared chrome and a clipped, scrollable body; footer actions never scroll away. */
abstract class DaedalonSettingsPage extends Screen {
    protected final Screen parent;
    protected DaedalonConfigLayout.Frame panel;
    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final List<Control> controls = new ArrayList<>();
    private int cursor;
    private int scroll;
    private DaedalonConfigLayout.ScrollBar scrollBar;
    private boolean draggingScroll;

    DaedalonSettingsPage(Screen parent, Text title) {
        super(title);
        this.parent = parent;
    }

    @Override protected final void init() {
        panel = DaedalonConfigLayout.frame(width, height);
        paragraphs.clear();
        controls.clear();
        cursor = 0;
        draggingScroll = false;
        buildContent();
        for (Control control : controls) control.enabled = control.button.active;
        buildFooter();
        setScroll(scroll);
    }

    protected abstract void buildContent();

    protected void buildFooter() {
        footerButton(panel.contentLeft(), panel.contentWidth(), Text.translatable("gui.back"), button -> close(),
                DaedalonConfigUi.Button.Style.QUIET);
    }

    protected final void paragraph(Text text) { paragraph(text, DaedalonConfigUi.MUTED_TEXT_COLOR); }

    protected final void paragraph(Text text, int color) {
        List<OrderedText> lines = textRenderer.wrapLines(text, panel.contentWidth());
        paragraphs.add(new Paragraph(text, lines, cursor, color));
        cursor += lines.size() * DaedalonConfigUi.lineHeight(textRenderer) + 8;
    }

    protected final DaedalonConfigUi.Button button(Text text, net.minecraft.client.gui.widget.ButtonWidget.PressAction action) {
        var button = contentButton(panel.contentLeft(), panel.contentWidth(), text, action);
        cursor += DaedalonConfigLayout.BUTTON_HEIGHT + DaedalonConfigLayout.ROW_GAP;
        return button;
    }

    protected final void buttonPair(Text first, net.minecraft.client.gui.widget.ButtonWidget.PressAction firstAction,
                                    Text second, net.minecraft.client.gui.widget.ButtonWidget.PressAction secondAction) {
        int half = (panel.contentWidth() - 8) / 2;
        contentButton(panel.contentLeft(), half, first, firstAction);
        contentButton(panel.contentLeft() + half + 8, half, second, secondAction);
        cursor += DaedalonConfigLayout.BUTTON_HEIGHT + DaedalonConfigLayout.ROW_GAP;
    }

    private DaedalonConfigUi.Button contentButton(int x, int buttonWidth, Text text,
                                                  net.minecraft.client.gui.widget.ButtonWidget.PressAction action) {
        var button = addDrawableChild(new DaedalonConfigUi.Button(x, panel.contentTop() + cursor, buttonWidth,
                text, action, DaedalonConfigUi.Button.Style.NEUTRAL));
        controls.add(new Control(button, cursor));
        return button;
    }

    protected final DaedalonConfigUi.Button footerButton(int x, int buttonWidth, Text text,
                                                         net.minecraft.client.gui.widget.ButtonWidget.PressAction action,
                                                         DaedalonConfigUi.Button.Style style) {
        return addDrawableChild(new DaedalonConfigUi.Button(x, panel.footerTop(), buttonWidth, text, action, style));
    }

    protected final void rebuildPage() {
        scroll = 0;
        setFocused(null);
        clearAndInit();
    }

    private int maxScroll() { return DaedalonConfigLayout.maxScroll(cursor, panel.contentHeight()); }

    private void setScroll(int value) {
        scroll = DaedalonConfigLayout.clampScroll(value, cursor, panel.contentHeight());
        // Geometry depends only on layout and scroll state, so drawing and pointer input share one snapshot.
        scrollBar = DaedalonConfigLayout.scrollBar(panel, scroll, maxScroll());
        for (Control control : controls) {
            boolean visible = DaedalonConfigLayout.controlVisible(control.top, control.button.getHeight(),
                    scroll, panel.contentHeight());
            control.button.setY(panel.contentTop() + control.top - scroll);
            control.button.visible = visible;
            control.button.active = visible && control.enabled;
            if (!visible && getFocused() == control.button) setFocused(null);
        }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        DaedalonConfigUi.drawPage(context, width, height, panel, title);
        context.enableScissor(panel.contentLeft(), panel.contentTop(),
                panel.contentLeft() + panel.contentWidth(), panel.contentBottom());
        int lineHeight = DaedalonConfigUi.lineHeight(textRenderer);
        for (Paragraph paragraph : paragraphs) {
            int y = panel.contentTop() + paragraph.top() - scroll;
            for (OrderedText line : paragraph.lines()) {
                if (y + lineHeight >= panel.contentTop() && y < panel.contentBottom())
                    context.drawText(textRenderer, line, panel.contentLeft(), y, paragraph.color(), false);
                y += lineHeight;
            }
        }
        context.disableScissor();
        DaedalonConfigUi.drawScrollBar(context, scrollBar, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x >= panel.left() && x < panel.right() && y >= panel.contentTop() && y < panel.contentBottom()) {
            setScroll(scroll - (int) Math.round(amount * DaedalonConfigUi.lineHeight(textRenderer) * 3));
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    private boolean overScrollBar(double x, double y) {
        return scrollBar.maxScroll() > 0 && scrollBar.contains(x, y);
    }

    private void scrollToPointer(double y) {
        setScroll(scrollBar.valueAt(y));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && overScrollBar(x, y)) {
            draggingScroll = true;
            scrollToPointer(y);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScroll) {
            scrollToPointer(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        int target;
        switch (key) {
            case GLFW.GLFW_KEY_PAGE_UP -> target = scroll - DaedalonConfigLayout.pageStep(panel.contentHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> target = scroll + DaedalonConfigLayout.pageStep(panel.contentHeight());
            case GLFW.GLFW_KEY_HOME -> target = 0;
            case GLFW.GLFW_KEY_END -> target = maxScroll();
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
        setScroll(target);
        return true;
    }

    @Override public Text getNarratedTitle() {
        var narration = title.copy();
        for (Paragraph paragraph : paragraphs) narration.append(". ").append(paragraph.text());
        return narration;
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }

    private record Paragraph(Text text, List<OrderedText> lines, int top, int color) { }
    private static final class Control {
        private final DaedalonConfigUi.Button button;
        private final int top;
        private boolean enabled;
        private Control(DaedalonConfigUi.Button button, int top) { this.button = button; this.top = top; }
    }
}
