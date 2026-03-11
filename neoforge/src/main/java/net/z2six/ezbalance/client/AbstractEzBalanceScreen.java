package net.z2six.ezbalance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

abstract class AbstractEzBalanceScreen extends Screen {
    protected static final int COLOR_BACKGROUND = 0xFF050505;
    protected static final int COLOR_SURFACE = 0xFF121212;
    protected static final int COLOR_SURFACE_ALT = 0xFF1A1A1A;
    protected static final int COLOR_BORDER = 0xFF262626;
    protected static final int COLOR_TEXT = 0xFFE8E8E8;
    protected static final int COLOR_MUTED = 0xFF8E8E8E;
    protected static final int COLOR_ACCENT = 0xFFFC0553;

    protected AbstractEzBalanceScreen(Component title) {
        super(title);
    }

    protected void fillBackground(GuiGraphics graphics) {
        this.renderTransparentBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0xC0050505);
    }

    protected void drawPanel(GuiGraphics graphics, int x1, int y1, int x2, int y2, boolean accent) {
        graphics.fill(x1, y1, x2, y2, accent ? COLOR_SURFACE_ALT : COLOR_SURFACE);
        graphics.fill(x1, y1, x2, y1 + 1, accent ? COLOR_ACCENT : COLOR_BORDER);
        graphics.fill(x1, y2 - 1, x2, y2, COLOR_BORDER);
        graphics.fill(x1, y1, x1 + 1, y2, COLOR_BORDER);
        graphics.fill(x2 - 1, y1, x2, y2, COLOR_BORDER);
    }

    protected void drawLabel(GuiGraphics graphics, String text, int x, int y, boolean accent) {
        graphics.drawString(this.font, text, x, y, accent ? COLOR_ACCENT : COLOR_TEXT, false);
    }

    protected void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    protected Button customButton(String text, int x, int y, int width, int height, Button.OnPress onPress) {
        return EzBalanceUi.createButton(Component.literal(text), x, y, width, height, onPress);
    }

    protected void drawInlineButton(GuiGraphics graphics, int x, int y, int width, int height, String text, boolean accent, boolean hovered) {
        EzBalanceUi.drawInlineButton(graphics, x, y, width, height, text, accent, hovered);
    }

    protected static Set<String> parseCsvSet(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashSet<>();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    protected static String toCsv(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    protected static String sanitizeId(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    protected static Double parseNullableDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
