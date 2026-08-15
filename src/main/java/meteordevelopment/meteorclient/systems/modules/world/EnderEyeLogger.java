/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class EnderEyeLogger extends Module {
    private final Map<Integer, Vec3> startPositions = new HashMap<>();

    public EnderEyeLogger() {
        super(Categories.World, "ender-eye-logger", "Reports the direction and path of thrown Eyes of Ender.");
    }

    @Override
    public void onActivate() {
        startPositions.clear();
    }

    @Override
    public void onDeactivate() {
        startPositions.clear();
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity instanceof EyeOfEnder) {
            startPositions.put(event.entity.getId(), event.entity.position());
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!(event.entity instanceof EyeOfEnder)) return;

        Vec3 start = startPositions.remove(event.entity.getId());
        if (start == null) return;

        Vec3 end = event.entity.position();
        Vec3 path = end.subtract(start);
        double horizontalDistance = Math.hypot(path.x(), path.z());
        if (horizontalDistance < 0.01) return;

        double heading = Math.toDegrees(Math.atan2(-path.x(), path.z()));
        if (heading < 0) heading += 360;

        info(
            "Eye heading %.1f degrees, ended at %.1f, %.1f, %.1f (%.1f blocks).",
            heading,
            end.x(),
            end.y(),
            end.z(),
            horizontalDistance
        );
    }
}
