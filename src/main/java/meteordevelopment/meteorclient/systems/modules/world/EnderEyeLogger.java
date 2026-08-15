/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

public class EnderEyeLogger extends Module {
    private static final int MAX_STRONGHOLD_COORD = 40_000;
    private static final IntList STRONGHOLD_DISTANCE_RANGES = new IntArrayList();

    static {
        int distance = 32;
        for (int ring = 0; ring < 8; ring++) {
            STRONGHOLD_DISTANCE_RANGES.add((int) (distance * (2.75 + 6 * ring)) * 16 - 128);
            STRONGHOLD_DISTANCE_RANGES.add((int) (distance * (5.25 + 6 * ring)) * 16 + 128);
        }
    }

    private final Int2ObjectMap<Vec3> tracked = new Int2ObjectOpenHashMap<>();

    public EnderEyeLogger() {
        super(Categories.World, "ender-eye-logger", "Calculates possible stronghold positions from an Eye of Ender.");
    }

    @Override
    public void onActivate() {
        tracked.clear();
    }

    @Override
    public void onDeactivate() {
        tracked.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundAddEntityPacket packet && packet.getType() == EntityTypes.EYE_OF_ENDER) {
            tracked.put(packet.getId(), new Vec3(packet.getX(), packet.getY(), packet.getZ()));
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!(event.entity instanceof EyeOfEnder eye)) return;

        Vec3 start = tracked.remove(eye.getId());
        if (start == null) return;

        Vec3 velocity = eye.position().subtract(start).normalize();
        int startX = (int) start.x();
        info("Start calculating Eye of Ender...");

        if (velocity.x() == 0) {
            info("Pointing at Z%s.", velocity.z() > 0 ? "+" : "-");
            return;
        }
        if (velocity.z() == 0) {
            info("Pointing at X%s.", velocity.x() > 0 ? "+" : "-");
            return;
        }

        int deltaX = velocity.x() > 0 ? 1 : -1;
        double slope = velocity.z() / velocity.x();
        double intercept = start.z() - slope * start.x();

        CompletableFuture.supplyAsync(() -> calculateCandidates(startX, deltaX, slope, intercept))
            .thenAcceptAsync(candidates -> showCandidates(candidates, slope, intercept), mc);
    }

    private static IntList calculateCandidates(int startX, int deltaX, double slope, double intercept) {
        IntList testPoints = new IntArrayList();
        int maxThresholdZ = (int) ((long) (MAX_STRONGHOLD_COORD - intercept - slope * startX) / (deltaX * slope));
        int minThresholdZ = (int) ((long) (-MAX_STRONGHOLD_COORD - intercept - slope * startX) / (deltaX * slope));
        int maxThresholdX = (MAX_STRONGHOLD_COORD - startX) / deltaX;
        int minThresholdX = (-MAX_STRONGHOLD_COORD - startX) / deltaX;
        int minThreshold = Math.max(0, Math.min(Math.min(maxThresholdZ, minThresholdZ), Math.min(maxThresholdX, minThresholdX)));
        int maxThreshold = Math.max(0, Math.min(Math.max(maxThresholdZ, minThresholdZ), Math.max(maxThresholdX, minThresholdX)));

        for (int step = minThreshold; step < maxThreshold; step++) {
            int x = startX + deltaX * step;
            if (x % 16 == 0) testPoints.add(x);
        }

        IntList candidates = new IntArrayList();
        for (int x : testPoints) {
            double z = slope * x + intercept;
            long roundedZ = Math.round(z);
            if (roundedZ % 16 == 0 && Math.abs(roundedZ - z) < 1E-1) candidates.add(x);
        }
        return candidates;
    }

    private void showCandidates(IntList candidates, double slope, double intercept) {
        if (candidates.isEmpty()) {
            warning("Calculation failure.");
            return;
        }

        info("Potential positions:");
        for (int x : candidates) {
            int z = (int) Math.round(slope * x + intercept);
            info(ChatUtils.formatCoords(new Vec3(x, 0, z)));
        }

        int foundRingIndex = -1;
        outer:
        for (int x : candidates) {
            int z = (int) Math.round(slope * x + intercept);
            int distance = (int) Math.sqrt((long) x * x + (long) z * z);
            for (int index = 0; index < STRONGHOLD_DISTANCE_RANGES.size() - 1; index += 2) {
                int min = STRONGHOLD_DISTANCE_RANGES.getInt(index);
                int max = STRONGHOLD_DISTANCE_RANGES.getInt(index + 1);
                if (distance < min || distance > max) continue;

                if (foundRingIndex == -1 || foundRingIndex == index) {
                    foundRingIndex = index;
                    Component message = Component.literal("Most probably at: ")
                        .append(ChatUtils.formatCoords(new Vec3(x, 0, z)))
                        .append(Component.literal(", in ring " + (foundRingIndex / 2 + 1) + "."));
                    info(message);
                } else {
                    break outer;
                }
            }
        }
    }
}
