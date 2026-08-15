/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface MeteorWidget extends BaseWidget {
    default MeteorGuiTheme theme() {
        return (MeteorGuiTheme) getTheme();
    }

    default void roundedQuad(GuiRenderer renderer, double x, double y, double width, double height, double radius, Color color) {
        double r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            renderer.quad(x, y, width, height, color);
            return;
        }

        renderer.quad(x + r, y, width - r * 2, height, color);
        renderer.quad(x, y + r, width, height - r * 2, color);
        renderer.triangle(x + r, y, x, y + r, x + r, y + r, color);
        renderer.triangle(x + width - r, y, x + width - r, y + r, x + width, y + r, color);
        renderer.triangle(x, y + height - r, x + r, y + height - r, x + r, y + height, color);
        renderer.triangle(x + width - r, y + height - r, x + width, y + height - r, x + width - r, y + height, color);
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
        MeteorGuiTheme theme = theme();
        double border = theme.scale(1);
        double radius = theme.scale(2);

        roundedQuad(renderer, widget.x, widget.y, widget.width, widget.height, radius, outlineColor);
        roundedQuad(renderer, widget.x + border, widget.y + border, widget.width - border * 2, widget.height - border * 2, radius - border, backgroundColor);
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        MeteorGuiTheme theme = theme();
        renderBackground(renderer, widget, theme.outlineColor.get(pressed, mouseOver), theme.backgroundColor.get(pressed, mouseOver));
    }
}
