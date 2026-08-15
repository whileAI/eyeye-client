/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public class Grim extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> fakeGround = sgGeneral.add(new BoolSetting.Builder()
        .name("fake-ground")
        .description("Reports an on-ground state while the position is frozen.")
        .defaultValue(false)
        .build()
    );

    private Vec3 lockedPosition;

    public Grim() {
        super(Categories.Movement, "grim", "Freezes your server position while keeping camera rotation synchronized.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) lockedPosition = mc.player.position();
    }

    @Override
    public void onDeactivate() {
        lockedPosition = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (lockedPosition == null) lockedPosition = mc.player.position();

        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setPos(lockedPosition);
    }

    @EventHandler(priority = -999)
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || !(event.packet instanceof ServerboundMovePlayerPacket packet)) return;
        if (!(packet instanceof ServerboundMovePlayerPacket.Pos) && !(packet instanceof ServerboundMovePlayerPacket.PosRot)) return;

        event.cancel();
        event.sendSilently(new ServerboundMovePlayerPacket.Rot(
            mc.player.getYRot(),
            mc.player.getXRot(),
            fakeGround.get() || mc.player.onGround(),
            mc.player.horizontalCollision
        ));
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || !(event.packet instanceof ClientboundPlayerPositionPacket packet)) return;

        lockedPosition = PositionMoveRotation.calculateAbsolute(
            PositionMoveRotation.of(mc.player),
            packet.change(),
            packet.relatives()
        ).position();
    }
}
