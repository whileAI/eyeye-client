/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnderEyeLogger extends Module {
    private static final int MAX_STRONGHOLD_COORDINATE = 40_000;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxResults = sgGeneral.add(new IntSetting.Builder()
        .name("max-results")
        .description("Maximum number of possible stronghold chunks to show.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 16)
        .build()
    );

    private final Map<Integer, Vec3> startPositions = new HashMap<>();

    public EnderEyeLogger() {
        super(Categories.World, "ender-eye-logger", "Estimates stronghold chunks from thrown Eyes of Ender.");
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

        List<StrongholdCandidate> candidates = findCandidates(start, path);
        if (candidates.isEmpty()) {
            warning("No stronghold candidates found. Throw another eye from a different position.");
            return;
        }

        info("Eye heading %.1f degrees. Possible stronghold chunks:", heading);
        for (int i = 0; i < Math.min(maxResults.get(), candidates.size()); i++) {
            StrongholdCandidate candidate = candidates.get(i);
            info("Ring %d: approximately %d, %d.", candidate.ring(), candidate.x(), candidate.z());
        }
    }

    private static List<StrongholdCandidate> findCandidates(Vec3 start, Vec3 path) {
        boolean useX = Math.abs(path.x()) >= Math.abs(path.z());
        double primaryStart = useX ? start.x() : start.z();
        double primaryDirection = useX ? path.x() : path.z();
        double secondaryStart = useX ? start.z() : start.x();
        double secondaryDirection = useX ? path.z() : path.x();
        if (Math.abs(primaryDirection) < 1.0E-8) return List.of();

        int step = primaryDirection > 0 ? 16 : -16;
        int primary = primaryDirection > 0
            ? (int) Math.ceil(primaryStart / 16.0) * 16
            : (int) Math.floor(primaryStart / 16.0) * 16;
        if (Math.abs(primary - primaryStart) < 1.0E-8) primary += step;

        List<StrongholdCandidate> candidates = new ArrayList<>();
        while (Math.abs(primary) <= MAX_STRONGHOLD_COORDINATE) {
            double time = (primary - primaryStart) / primaryDirection;
            if (time <= 0) {
                primary += step;
                continue;
            }

            double secondary = secondaryStart + secondaryDirection * time;
            int secondaryChunk = (int) Math.round(secondary / 16.0) * 16;
            if (Math.abs(secondary - secondaryChunk) <= 0.1) {
                int x = useX ? primary : secondaryChunk;
                int z = useX ? secondaryChunk : primary;
                int ring = getStrongholdRing(x, z);
                if (ring > 0) candidates.add(new StrongholdCandidate(x, z, ring));
            }

            primary += step;
        }

        candidates.sort(Comparator.comparingDouble(candidate -> Math.hypot(candidate.x(), candidate.z())));
        return candidates;
    }

    private static int getStrongholdRing(int x, int z) {
        double distance = Math.hypot(x, z);
        for (int ring = 0; ring < 8; ring++) {
            int min = (int) (32 * (2.75 + 6 * ring)) * 16 - 128;
            int max = (int) (32 * (5.25 + 6 * ring)) * 16 + 128;
            if (distance >= min && distance <= max) return ring + 1;
        }
        return -1;
    }

    private record StrongholdCandidate(int x, int z, int ring) {}
}
