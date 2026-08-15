/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class Grim extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> fakeGround = sgGeneral.add(new BoolSetting.Builder()
        .name("fake-ground")
        .description("Reports an on-ground state while floating.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useSnapPacket = sgGeneral.add(new BoolSetting.Builder()
        .name("use-snap-packet")
        .description("Uses a full position and rotation packet for legacy protocol translation.")
        .defaultValue(false)
        .build()
    );

    private Vec3 tickStartPosition;
    private boolean sendingPacket;

    public Grim() {
        super(Categories.Movement, "grim", "Freezes movement using the Grim floating packet sequence.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) tickStartPosition = mc.player.position();
    }

    @Override
    public void onDeactivate() {
        tickStartPosition = null;
        sendingPacket = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null) tickStartPosition = mc.player.position();
    }

    @EventHandler(priority = 1000)
    private void onMovementPackets(SendMovementPacketsEvent.Pre event) {
        if (mc.player == null || tickStartPosition == null) return;

        mc.player.setPos(tickStartPosition);
        boolean onGround = fakeGround.get() || mc.player.onGround();
        if (fakeGround.get()) mc.player.setOnGround(true);

        ServerboundMovePlayerPacket packet = useSnapPacket.get() || fakeGround.get()
            ? new ServerboundMovePlayerPacket.PosRot(
                tickStartPosition.x(),
                tickStartPosition.y(),
                tickStartPosition.z(),
                mc.player.getYRot(),
                mc.player.getXRot(),
                onGround,
                mc.player.horizontalCollision
            )
            : new ServerboundMovePlayerPacket.Rot(
                mc.player.getYRot(),
                mc.player.getXRot(),
                onGround,
                mc.player.horizontalCollision
            );

        sendingPacket = true;
        try {
            mc.player.connection.send(packet);
        } finally {
            sendingPacket = false;
        }
    }

    @EventHandler(priority = -1000)
    private void onPacketSend(PacketEvent.Send event) {
        if (!sendingPacket && event.packet instanceof ServerboundMovePlayerPacket) event.cancel();
    }
}
