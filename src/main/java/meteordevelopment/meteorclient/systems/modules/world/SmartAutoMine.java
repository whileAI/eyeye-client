/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class SmartAutoMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("target-blocks")
        .description("Blocks mined by Baritone.")
        .defaultValue(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)
        .build()
    );

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("Number of blocks to mine. Set to 0 for no limit.")
        .defaultValue(0)
        .range(0, 2304)
        .sliderRange(0, 256)
        .build()
    );

    private final Setting<Boolean> lowHealth = sgSafety.add(new BoolSetting.Builder()
        .name("low-health")
        .description("Stops mining when health reaches the threshold.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> health = sgSafety.add(new IntSetting.Builder()
        .name("health")
        .description("Health threshold, including absorption hearts.")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 20)
        .visible(lowHealth::get)
        .build()
    );

    private final Setting<Boolean> lowHunger = sgSafety.add(new BoolSetting.Builder()
        .name("low-hunger")
        .description("Stops mining when hunger reaches the threshold.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hunger = sgSafety.add(new IntSetting.Builder()
        .name("hunger")
        .description("Hunger threshold.")
        .defaultValue(6)
        .range(0, 20)
        .sliderRange(0, 20)
        .visible(lowHunger::get)
        .build()
    );

    private final Setting<Boolean> fullInventory = sgSafety.add(new BoolSetting.Builder()
        .name("full-inventory")
        .description("Stops mining when too few inventory slots remain.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> freeSlots = sgSafety.add(new IntSetting.Builder()
        .name("free-slots")
        .description("Minimum number of empty inventory slots to keep.")
        .defaultValue(2)
        .range(0, 10)
        .sliderRange(0, 10)
        .visible(fullInventory::get)
        .build()
    );

    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
    private final Settings baritoneSettings = BaritoneAPI.getSettings();
    private boolean previousMineScanDroppedItems;

    public SmartAutoMine() {
        super(Categories.World, "smart-auto-mine", "Mines selected blocks with Baritone and safety limits.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null || targetBlocks.get().isEmpty()) {
            error("Join a world and select target blocks first.");
            toggle();
            return;
        }

        previousMineScanDroppedItems = baritoneSettings.mineScanDroppedItems.value;
        baritoneSettings.mineScanDroppedItems.value = true;

        Block[] blocks = targetBlocks.get().toArray(Block[]::new);
        baritone.getPathingBehavior().cancelEverything();
        if (amount.get() > 0) baritone.getMineProcess().mine(amount.get(), blocks);
        else baritone.getMineProcess().mine(blocks);

        info("Mining %s.", amount.get() > 0 ? amount.get() + " blocks" : "without a limit");
    }

    @Override
    public void onDeactivate() {
        baritone.getPathingBehavior().cancelEverything();
        baritoneSettings.mineScanDroppedItems.value = previousMineScanDroppedItems;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        String reason = getSafetyReason();
        if (reason.isEmpty()) return;

        error("Stopped: %s.", reason);
        toggle();
    }

    private String getSafetyReason() {
        if (lowHealth.get() && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= health.get()) return "low health";
        if (lowHunger.get() && mc.player.getFoodData().getFoodLevel() <= hunger.get()) return "low hunger";
        if (fullInventory.get() && getFreeSlots() <= freeSlots.get()) return "inventory is nearly full";
        return "";
    }

    private int getFreeSlots() {
        int slots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) slots++;
        }
        return slots;
    }
}
