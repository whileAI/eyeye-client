/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector2d;

public class Strafe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Minimum horizontal speed while strafing.")
        .defaultValue(0.2873)
        .range(0.01, 2)
        .sliderRange(0.01, 1)
        .build()
    );

    public Strafe() {
        super(Categories.Movement, "strafe", "Improves directional control while moving through the air.");
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null
            || mc.player.onGround()
            || mc.player.isFallFlying()
            || mc.player.isInWater()
            || mc.player.isInLava()
            || !PlayerUtils.isMoving()) return;

        double horizontalSpeed = Math.max(Math.hypot(event.movement.x(), event.movement.z()), speed.get());
        Vector2d movement = meteordevelopment.meteorclient.systems.modules.movement.speed.modes.Strafe.transformStrafe(horizontalSpeed);
        ((IVec3) event.movement).meteor$setXZ(movement.x, movement.y);
    }
}
