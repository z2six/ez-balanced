package net.z2six.ezbalance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class EzBalanceUi {
    private static final int SCROLLBAR_PATTERN_COLOR = 0xFFB6B6B6;

    private EzBalanceUi() {}

    static Button createButton(Component text, int x, int y, int width, int height, Button.OnPress onPress) {
        return EzBalanceButton.create(text, x, y, width, height, onPress);
    }

    static void drawInlineButton(GuiGraphics graphics, int x, int y, int width, int height, String text, boolean accent, boolean hovered) {
        int fill = hovered ? 0xFF242424 : AbstractEzBalanceScreen.COLOR_BACKGROUND;
        int border = accent || hovered ? AbstractEzBalanceScreen.COLOR_ACCENT : AbstractEzBalanceScreen.COLOR_BORDER;
        int textColor = hovered ? 0xFFFFFFFF : AbstractEzBalanceScreen.COLOR_TEXT;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, text, x + (width - minecraft.font.width(text)) / 2, y + 6, textColor, false);
    }

    static void drawVerticalScrollbar(GuiGraphics graphics, int trackX, int trackTop, int trackBottom, int trackWidth, int thumbTop, int thumbHeight) {
        drawVerticalScrollbar(graphics, trackX, trackTop, trackBottom, trackWidth, thumbTop, thumbHeight, isMouseOver(trackX, trackTop, trackX + trackWidth, trackBottom));
    }

    static void drawHorizontalScrollbar(GuiGraphics graphics, int trackLeft, int trackRight, int trackY, int trackHeight, int thumbLeft, int thumbWidth) {
        drawHorizontalScrollbar(graphics, trackLeft, trackRight, trackY, trackHeight, thumbLeft, thumbWidth, isMouseOver(trackLeft, trackY, trackRight, trackY + trackHeight));
    }

    static void drawVerticalScrollbar(GuiGraphics graphics, int trackX, int trackTop, int trackBottom, int trackWidth, int thumbTop, int thumbHeight, boolean hovered) {
        int extra = hovered ? 2 : 0;
        int x1 = trackX - extra / 2;
        int x2 = trackX + trackWidth + (extra - extra / 2);
        graphics.fill(x1, trackTop, x2, trackBottom, AbstractEzBalanceScreen.COLOR_BORDER);
        drawScrollbarThumb(graphics, trackX, thumbTop, trackWidth, thumbHeight, hovered);
    }

    static void drawHorizontalScrollbar(GuiGraphics graphics, int trackLeft, int trackRight, int trackY, int trackHeight, int thumbLeft, int thumbWidth, boolean hovered) {
        int extra = hovered ? 2 : 0;
        int y1 = trackY - extra / 2;
        int y2 = trackY + trackHeight + (extra - extra / 2);
        graphics.fill(trackLeft, y1, trackRight, y2, AbstractEzBalanceScreen.COLOR_BORDER);
        drawScrollbarThumb(graphics, thumbLeft, trackY, thumbWidth, trackHeight, hovered);
    }

    private static void drawScrollbarThumb(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        int extra = hovered ? 2 : 0;
        int x1 = x - extra / 2;
        int y1 = y - extra / 2;
        int x2 = x + width + (extra - extra / 2);
        int y2 = y + height + (extra - extra / 2);
        graphics.fill(x1, y1, x2, y2, AbstractEzBalanceScreen.COLOR_BACKGROUND);
        drawDiagonalAccent(graphics, x1, y1, x2, y2);
    }

    private static void drawDiagonalAccent(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                if (((x - x1) + (y - y1)) % 3 == 0) {
                    graphics.fill(x, y, x + 1, y + 1, SCROLLBAR_PATTERN_COLOR);
                }
            }
        }
    }

    private static boolean isMouseOver(int x1, int y1, int x2, int y2) {
        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.getWindow().getGuiScale();
        double mouseX = minecraft.mouseHandler.xpos() / scale;
        double mouseY = minecraft.mouseHandler.ypos() / scale;
        return !minecraft.mouseHandler.isLeftPressed()
                && mouseX >= x1
                && mouseX <= x2
                && mouseY >= y1
                && mouseY <= y2;
    }
}
