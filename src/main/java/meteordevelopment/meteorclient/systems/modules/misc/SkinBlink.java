/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.EnumMap;
import java.util.Map;

public class SkinBlink extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between skin changes in ticks.")
        .defaultValue(20)
        .range(1, 200)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> modelParts = sgGeneral.add(new BoolSetting.Builder()
        .name("model-parts")
        .description("Toggles all outer skin layers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mainHand = sgGeneral.add(new BoolSetting.Builder()
        .name("main-hand")
        .description("Alternates the main hand.")
        .defaultValue(false)
        .build()
    );

    private final Map<PlayerModelPart, Boolean> previousParts = new EnumMap<>(PlayerModelPart.class);
    private HumanoidArm previousArm;
    private boolean changed;
    private int timer;

    public SkinBlink() {
        super(Categories.Misc, "skin-blink", "Repeatedly toggles skin layers and the main hand.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        changed = false;
        previousParts.clear();
        previousArm = null;
    }

    @Override
    public void onDeactivate() {
        restore();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++timer < delay.get()) return;

        timer = 0;
        if (changed) restore();
        else blink();
        mc.options.broadcastOptions();
    }

    private void blink() {
        changed = true;

        if (modelParts.get()) {
            previousParts.clear();
            for (PlayerModelPart part : PlayerModelPart.values()) {
                boolean enabled = mc.options.isModelPartEnabled(part);
                previousParts.put(part, enabled);
                mc.options.setModelPart(part, !enabled);
            }
        }

        if (mainHand.get()) {
            previousArm = mc.options.mainHand().get();
            mc.options.mainHand().set(previousArm.getOpposite());
        }
    }

    private void restore() {
        if (!changed) return;

        previousParts.forEach(mc.options::setModelPart);
        previousParts.clear();

        if (previousArm != null) {
            mc.options.mainHand().set(previousArm);
            previousArm = null;
        }

        changed = false;
        mc.options.broadcastOptions();
    }
}
