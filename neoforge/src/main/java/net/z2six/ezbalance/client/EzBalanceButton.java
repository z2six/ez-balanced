package net.z2six.ezbalance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class EzBalanceButton extends Button {
    private EzBalanceButton(Button.Builder builder) {
        super(builder);
    }

    public static EzBalanceButton create(Component text, int x, int y, int width, int height, OnPress onPress) {
        return new EzBalanceButton(
                Button.builder(text, onPress)
                        .bounds(x, y, width, height)
        );
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        boolean hovered = this.isHovered();
        boolean active = this.active;

        int fill = active ? (hovered ? 0xFF262626 : 0xFF141414) : 0xFF0A0A0A;
        int border = hovered ? AbstractEzBalanceScreen.COLOR_ACCENT : 0xFF2B2B2B;
        int text = active ? (hovered ? 0xFFFFFFFF : AbstractEzBalanceScreen.COLOR_TEXT) : 0xFF666666;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);

        Minecraft minecraft = Minecraft.getInstance();
        int textX = x + (width - minecraft.font.width(this.getMessage())) / 2;
        int textY = y + (height - 8) / 2;
        graphics.drawString(minecraft.font, this.getMessage(), textX, textY, text, false);
    }
}
