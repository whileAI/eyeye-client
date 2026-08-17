/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;

public class Watermark extends Module {
    private static final Color BACKGROUND = new Color(30, 30, 46, 225);
    private static final Color ACCENT = new Color(137, 180, 250);
    private static final Color TEXT = new Color(245, 245, 247);

    public Watermark() {
        super(Categories.Render, "watermark", "Displays the EyEye Client watermark.");
        runInMainMenu = true;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(event.graphics);

        double scale = Hud.get().getTextScale();
        String text = "EyEye Client | v" + MeteorClient.VERSION;
        double padding = 7;
        double height = renderer.textHeight(true, scale) + padding * 2;
        double width = renderer.textWidth(text, true, scale) + padding * 2 + 2;

        renderer.quad(4, 4, width, height, BACKGROUND);
        renderer.quad(4, 4, 2, height, ACCENT);
        renderer.quad(4, 4, width, 1, ACCENT);
        renderer.text(text, 4 + padding + 2, 4 + padding, TEXT, true, scale);
        renderer.end();
    }
}
