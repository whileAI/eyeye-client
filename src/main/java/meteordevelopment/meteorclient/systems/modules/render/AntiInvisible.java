/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class AntiInvisible extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> transparency = sgGeneral.add(new IntSetting.Builder()
        .name("transparency")
        .description("Opacity of invisible entities.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    public AntiInvisible() {
        super(Categories.Render, "anti-invisible", "Shows invisible entities.");
    }
}
