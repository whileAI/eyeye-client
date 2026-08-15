/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public class SetbackLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> minimumDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-distance")
        .description("Minimum correction distance to report.")
        .defaultValue(0.1)
        .range(0, 100)
        .sliderRange(0, 10)
        .build()
    );

    public SetbackLogger() {
        super(Categories.Movement, "setback-logger", "Reports server position corrections and their distance.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPlayerPositionPacket packet) || mc.player == null) return;

        Vec3 current = mc.player.position();
        Vec3 target = PositionMoveRotation.calculateAbsolute(
            PositionMoveRotation.of(mc.player),
            packet.change(),
            packet.relatives()
        ).position();
        double distance = current.distanceTo(target);

        if (distance < minimumDistance.get()) return;
        info("Setback #%d to %.1f, %.1f, %.1f (%.2f blocks).", packet.id(), target.x(), target.y(), target.z(), distance);
    }
}
