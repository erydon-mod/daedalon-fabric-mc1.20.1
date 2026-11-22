package com.oliver.daedalon.client.config;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * Bounded family styling: shared GUI textures and Cinzel display text.
 * Body copy uses Minecraft's default font at 1:1 scale. No ERYDON dependency.
 */
final class DaedalonConfigUi {
    static final int TEXT_COLOR = 0xFF050505;
    static final int MUTED_TEXT_COLOR = 0xFF1F1F1F;
    static final int ERROR_COLOR = 0xFF8B1111;
    static final int SUCCESS_COLOR = 0xFF245B32;
    private static final int BRONZE = 0xFFC9AB70;
    private static final int BUTTON_TEXT = 0xFFF8F3E8;
    private static final int DISABLED_TEXT = 0xFFB8B0A2;
    private static final Identifier PANEL = texture("glacium_config_background.png");
    private static final Identifier BACKDROP = texture("nerium_control_background.png");
    private static final Identifier CONTROL = texture("portorium_control_background.png");
    private static final Identifier FRAME = texture("portorium_control_frame.png");
    private static final Identifier SCROLLBAR_HANDLE = texture("slider_handle_slim.png");
    private static final Identifier CINZEL = new Identifier("daedalon", "cinzel");

    private DaedalonConfigUi() { }

    private static Identifier texture(String filename) {
        return new Identifier("daedalon", "textures/gui/" + filename);
    }

    static int lineHeight(TextRenderer renderer) { return renderer.fontHeight + 3; }

    static void drawPage(DrawContext context, int screenWidth, int screenHeight,
                         DaedalonConfigLayout.Frame panel, Text title) {
        int sampleWidth = screenWidth >= screenHeight ? 1024
                : Math.max(1, Math.round(1024F * screenWidth / Math.max(1, screenHeight)));
        int sampleHeight = screenHeight >= screenWidth ? 1024
                : Math.max(1, Math.round(1024F * screenHeight / Math.max(1, screenWidth)));
        context.drawTexture(BACKDROP, 0, 0, screenWidth, screenHeight, (1024 - sampleWidth) / 2F,
                (1024 - sampleHeight) / 2F, sampleWidth, sampleHeight, 1024, 1024);
        context.fill(0, 0, screenWidth, screenHeight, 0xE6000000);
        drawPanel(context, panel);
        drawDisplay(context, Text.literal("ERYDON Daedalon"), panel.left() + panel.width() / 2,
                panel.top() + 17, panel.width() - 36, 0.9F, MUTED_TEXT_COLOR);
        drawDisplay(context, title, panel.left() + panel.width() / 2,
                panel.top() + 43, panel.width() - 36, 1.2F, TEXT_COLOR);
    }

    private static void drawPanel(DrawContext context, DaedalonConfigLayout.Frame panel) {
        int leftWidth = Math.min(100, panel.width() / 2);
        int rightWidth = leftWidth;
        int centerWidth = Math.max(0, panel.width() - leftWidth - rightWidth);
        int headerHeight = Math.min(60, panel.height());
        int footerHeight = Math.min(8, Math.max(0, panel.height() - headerHeight));
        int middleHeight = Math.max(0, panel.height() - headerHeight - footerHeight);
        context.fill(panel.left() - 3, panel.top() + 2, panel.right() + 3, panel.bottom() + 4, 0x68000000);
        panelRow(context, panel.left(), panel.top(), leftWidth, centerWidth, rightWidth, headerHeight, 0, 120);
        panelRow(context, panel.left(), panel.top() + headerHeight, leftWidth, centerWidth, rightWidth,
                middleHeight, 120, 240);
        panelRow(context, panel.left(), panel.bottom() - footerHeight, leftWidth, centerWidth, rightWidth,
                footerHeight, 360, 16);
    }

    private static void panelRow(DrawContext context, int x, int y, int leftWidth, int middleWidth,
                                 int rightWidth, int height, int sourceY, int sourceHeight) {
        if (height <= 0) return;
        context.drawTexture(PANEL, x, y, leftWidth, height, 0F, sourceY, 200, sourceHeight, 720, 376);
        if (middleWidth > 0)
            context.drawTexture(PANEL, x + leftWidth, y, middleWidth, height,
                    200F, sourceY, 320, sourceHeight, 720, 376);
        context.drawTexture(PANEL, x + leftWidth + middleWidth, y, rightWidth, height,
                520F, sourceY, 200, sourceHeight, 720, 376);
    }

    private static Text display(Text text) {
        return text.copy().styled(style -> style.withFont(CINZEL));
    }

    private static void drawDisplay(DrawContext context, Text text, int centerX, int y,
                                    int availableWidth, float preferredScale, int color) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text styled = display(text);
        float scale = Math.min(preferredScale, availableWidth / (float) Math.max(1, renderer.getWidth(styled)));
        context.getMatrices().push();
        context.getMatrices().translate(centerX, y, 0F);
        context.getMatrices().scale(scale, scale, 1F);
        context.drawText(renderer, styled, -renderer.getWidth(styled) / 2, 0, color, false);
        context.getMatrices().pop();
    }

    static void drawScrollBar(DrawContext context, DaedalonConfigLayout.ScrollBar bar, int mouseX, int mouseY) {
        if (bar.maxScroll() <= 0) return;
        int trackX = bar.left() + bar.width() / 2;
        boolean highlighted = bar.contains(mouseX, mouseY);
        context.fill(trackX - 1, bar.top(), trackX + 1, bar.bottom(), 0x66000000);
        if (highlighted) context.fill(trackX, bar.top(), trackX + 1, bar.bottom(), BRONZE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            context.drawTexture(SCROLLBAR_HANDLE, bar.left(), bar.handleTop(), bar.width(), bar.handleHeight(),
                    0F, 0F, 18, 40, 18, 40);
        } finally {
            // Match DrawContext's colored textured-quad cleanup instead of leaking blend state to later controls.
            RenderSystem.disableBlend();
        }
        if (highlighted) {
            // Match the family handle's half-pixel inset focus light without changing its texture.
            context.getMatrices().push();
            context.getMatrices().scale(0.5F, 0.5F, 1F);
            int left = (bar.left() + 1) * 2, right = (bar.right() - 1) * 2;
            int top = (bar.handleTop() + 2) * 2, bottom = (bar.handleBottom() - 2) * 2;
            context.fill(left, top, right, top + 1, 0xFFFFE8A8);
            context.fill(left, bottom - 1, right, bottom, 0xFFFFE8A8);
            context.fill(left, top, left + 1, bottom, 0xFFFFE8A8);
            context.fill(right - 1, top, right, bottom, 0xFFFFE8A8);
            context.getMatrices().pop();
        }
    }

    private static void drawControl(DrawContext context, int left, int top, int width, int height,
                                    DaedalonConfigLayout.ControlSample sample,
                                    Button.Style style, boolean highlighted, boolean pressed) {
        context.drawTexture(CONTROL, left, top, width, height, (float) sample.sourceX(), (float) sample.sourceY(),
                sample.sourceWidth(), sample.sourceHeight(), 1024, 1024);
        int overlay = style == Button.Style.PRIMARY ? highlighted ? 0x46000000 : 0x60000000
                : style == Button.Style.QUIET ? highlighted ? 0x70000000 : 0x8A000000
                : highlighted ? 0x56000000 : 0x74000000;
        context.fill(left, top, left + width, top + height, overlay);
        if (style == Button.Style.PRIMARY)
            context.fill(left + 2, top + 2, left + width - 2, top + height - 2,
                    highlighted ? 0x20E5A01D : 0x12E5A01D);
        if (pressed) context.fill(left, top, left + width, top + height, 0x36000000);
        drawControlFrame(context, left, top, width, height);
    }

    private static void drawControlFrame(DrawContext context, int left, int top, int width, int height) {
        int borderX = Math.min(4, width / 2), borderY = Math.min(4, height / 2);
        for (int row = 0; row < 3; row++) {
            int y = row == 0 ? top : row == 1 ? top + borderY : top + height - borderY;
            int h = row == 1 ? height - 2 * borderY : borderY;
            int v = row == 0 ? 0 : row == 1 ? 16 : 64;
            int sourceH = row == 1 ? 48 : 16;
            for (int column = 0; column < 3; column++) {
                if (row == 1 && column == 1) continue;
                int x = column == 0 ? left : column == 1 ? left + borderX : left + width - borderX;
                int w = column == 1 ? width - 2 * borderX : borderX;
                int u = column == 0 ? 0 : column == 1 ? 16 : 112;
                int sourceW = column == 1 ? 96 : 16;
                if (w > 0 && h > 0)
                    context.drawTexture(FRAME, x, y, w, h, (float) u, (float) v, sourceW, sourceH, 128, 80);
            }
        }
    }

    static final class Button extends ButtonWidget {
        enum Style { NEUTRAL, PRIMARY, QUIET }
        private final Style style;
        private final DaedalonConfigLayout.ControlSample controlSample;
        private long pressedUntil;
        private String fittedSource;
        private int fittedWidth = -1;
        private Text fitted;
        private boolean overflow;

        Button(int x, int y, int width, Text message, PressAction action, Style style) {
            super(x, y, width, DaedalonConfigLayout.BUTTON_HEIGHT, message, action, DEFAULT_NARRATION_SUPPLIER);
            this.style = style;
            this.controlSample = DaedalonConfigLayout.controlSample(x, y, width, DaedalonConfigLayout.BUTTON_HEIGHT);
        }

        DaedalonConfigLayout.ControlSample controlSample() { return controlSample; }

        @Override public void onPress() {
            pressedUntil = Util.getMeasuringTimeMs() + 120L;
            super.onPress();
        }

        private Text fittedLabel(TextRenderer renderer) {
            String source = getMessage().getString();
            int available = Math.max(1, width - 10);
            if (!source.equals(fittedSource) || fittedWidth != available) {
                fittedSource = source;
                fittedWidth = available;
                fitted = display(getMessage());
                overflow = renderer.getWidth(fitted) > available;
                if (overflow) {
                    int end = source.length();
                    do {
                        end = source.offsetByCodePoints(end, -1);
                        fitted = display(Text.literal(source.substring(0, end) + "..."));
                    } while (end > 0 && renderer.getWidth(fitted) > available);
                }
                if (overflow && getTooltip() == null) setTooltip(Tooltip.of(getMessage()));
            }
            return fitted;
        }

        @Override protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            boolean highlighted = isMouseOver(mouseX, mouseY) || isFocused();
            boolean pressed = active && Util.getMeasuringTimeMs() < pressedUntil;
            drawControl(context, getX(), getY(), width, height, controlSample, style, highlighted, pressed);
            if (!active) context.fill(getX(), getY(), getX() + width, getY() + height, 0x88000000);
            Text label = fittedLabel(renderer);
            int x = getX() + (width - renderer.getWidth(label)) / 2;
            int y = getY() + 9 + (pressed ? 1 : 0);
            context.getMatrices().push();
            context.getMatrices().translate(-0.5F, 0.5F, 0F);
            context.drawText(renderer, label, x, y, 0xA0000000, false);
            context.getMatrices().pop();
            context.drawText(renderer, label, x, y, active ? BUTTON_TEXT : DISABLED_TEXT, false);
            if (isFocused()) {
                context.drawBorder(getX(), getY(), width, height, 0xFFFFE8A8);
            }
        }
    }
}
