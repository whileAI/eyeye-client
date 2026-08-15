/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class BowTP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to search for a target.")
        .defaultValue(80)
        .range(1, 200)
        .sliderRange(1, 120)
        .build()
    );

    private final Setting<Double> verticalDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-distance")
        .description("Vertical simulation distance used before releasing the bow.")
        .defaultValue(80)
        .range(0, 200)
        .sliderRange(0, 120)
        .build()
    );

    private final Setting<Double> packetDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("packet-distance")
        .description("Maximum distance between movement packets.")
        .defaultValue(10)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities that can be targeted.")
        .defaultValue(EntityTypes.PLAYER)
        .onlyAttackable()
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to select a target.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private Entity target;

    public BowTP() {
        super(Categories.Combat, "bow-tp", "Teleports bow release packets to a target and returns immediately.");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler(priority = -999)
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundPlayerActionPacket packet)
            || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
            || mc.player == null
            || !(mc.player.getUseItem().getItem() instanceof BowItem)) return;

        target = TargetUtils.get(this::isValidTarget, priority.get());
        if (target == null) return;

        Vec3 origin = mc.player.position();
        double targetHeight = target.getBbHeight() - mc.player.getEyeHeight() + 0.11;
        Vec3 attackPosition = target.position().add(0, targetHeight, 0);

        event.cancel();
        sendPath(event, origin, attackPosition);

        if (verticalDistance.get() > 0) {
            Vec3 top = attackPosition.add(0, verticalDistance.get(), 0);
            sendPath(event, attackPosition, top);
            sendPath(event, top, attackPosition);
        }

        event.sendSilently(packet);
        sendPath(event, attackPosition, origin);
        info("Released bow at %s and returned.", EntityUtils.getName(target));
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player || !entity.isAlive() || !entities.get().contains(entity.getType())) return false;
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) return false;
        if (entity instanceof Player player && (player.isCreative() || !Friends.get().shouldAttack(player))) return false;
        return entity.distanceTo(mc.player) <= targetRange.get();
    }

    private void sendPath(PacketEvent.Send event, Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / packetDistance.get()));
        for (int step = 1; step <= steps; step++) {
            double progress = (double) step / steps;
            Vec3 position = from.lerp(to, progress);
            event.sendSilently(new ServerboundMovePlayerPacket.Pos(
                position.x(),
                position.y(),
                position.z(),
                false,
                mc.player.horizontalCollision
            ));
        }
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
