package com.oliver.daedalon.client.glaux.interaction;

/** Keep a visible owl beside the conversation at ordinary Minecraft GUI scales. */
final class GlauxHudLayout {
    private GlauxHudLayout() { }
    static int width(int screenWidth) { return Math.min(260, Math.max(120, screenWidth / 2 - 52)); }
    static int x(int anchor, int screenWidth, int panelWidth) {
        int preferred = anchor <= screenWidth / 2 ? anchor + 36 : anchor - panelWidth - 36;
        return Math.max(8, Math.min(screenWidth - panelWidth - 8, preferred));
    }
}
