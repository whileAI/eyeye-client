/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.screens.settings.EnchantmentListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnchantmentListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;

import java.util.UUID;
import java.util.Set;

public class VillagerAutoEnchantment extends Module {
    private static final int ACTION_DELAY = 6;

    private final SettingGroup sgData = settings.getDefaultGroup();

    private final Setting<String> villagerId = sgData.add(new StringSetting.Builder()
        .name("villager-id")
        .description("Selected villager UUID.")
        .visible(() -> false)
        .build()
    );

    private final Setting<BlockPos> lecternPos = sgData.add(new BlockPosSetting.Builder()
        .name("lectern-position")
        .description("Selected lectern position.")
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> lecternSelected = sgData.add(new BoolSetting.Builder()
        .name("lectern-selected")
        .description("Whether a lectern position has been selected.")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    private final Setting<Set<ResourceKey<Enchantment>>> enchantment = sgData.add(new EnchantmentListSetting.Builder()
        .name("enchantment")
        .description("Enchantment to find from the librarian.")
        .defaultValue(Enchantments.MENDING)
        .visible(() -> false)
        .build()
    );

    private final Setting<Integer> level = sgData.add(new IntSetting.Builder()
        .name("enchantment-level")
        .description("Enchantment level to find.")
        .defaultValue(1)
        .min(1)
        .visible(() -> false)
        .build()
    );

    private Selection selection = Selection.None;
    private Stage stage = Stage.Idle;
    private Screen previousScreen;
    private ResourceKey<Enchantment> targetEnchantment;
    private int targetLevel;
    private int timer;
    private int lecternPickupTicks;
    private int interactionGuardTicks;
    private int attempts;

    public VillagerAutoEnchantment() {
        super(Categories.World, "auto-librarian", "Rerolls a librarian until it offers the selected enchanted book.");

        autoSubscribe = false;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        normalizeLevel();
        WTable table = theme.table();

        table.add(theme.label("Villager:"));
        table.add(theme.label(villagerId.get().isBlank() ? "Not selected" : "Selected"));
        WButton selectVillager = table.add(theme.button("Select")).widget();
        selectVillager.action = () -> startSelection(Selection.Villager);
        table.row();

        table.add(theme.label("Lectern:"));
        table.add(theme.label(lecternSelected.get() ? formatPos(lecternPos.get()) : "Not selected"));
        WButton selectLectern = table.add(theme.button("Select")).widget();
        selectLectern.action = () -> startSelection(Selection.Lectern);
        table.row();

        ResourceKey<Enchantment> selected = getSelectedEnchantment();
        table.add(theme.label("Book:"));
        table.add(theme.label(selected == null ? "Not selected" : Names.get(selected)));
        WButton selectBook = table.add(theme.button("Select")).widget();
        selectBook.action = () -> {
            WidgetScreen parent = mc.gui.screen() instanceof WidgetScreen screen ? screen : null;
            TradeableEnchantmentScreen screen = new TradeableEnchantmentScreen(theme, enchantment);
            screen.onClosed(() -> {
                normalizeLevel();
                if (parent != null) parent.reload();
            });
            mc.gui.setScreen(screen);
        };
        table.row();

        table.add(theme.label("Level:"));
        WDropdown<String> levels = table.add(theme.dropdown(getAvailableLevels(), toRoman(level.get()))).widget();
        levels.action = () -> level.set(fromRoman(levels.get()));

        return table;
    }

    @Override
    public void onActivate() {
        targetEnchantment = resolveTargetEnchantment();
        Villager villager = getVillager();

        if (villager == null || !lecternSelected.get() || targetEnchantment == null) {
            error("Select a villager, lectern and book first.");
            toggle();
            return;
        }
        if (villager.isBaby()) {
            error("The selected villager is a child.");
            toggle();
            return;
        }
        if (villager.getVillagerXp() > 0 || villager.getVillagerData().level() > 1) {
            error("The selected villager has locked trades and cannot be rerolled.");
            toggle();
            return;
        }
        if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)
            && !villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            error("The selected villager must be unemployed or a librarian.");
            toggle();
            return;
        }
        if (!isWithinReach(villager)) {
            error("The villager or lectern is out of reach.");
            toggle();
            return;
        }

        stage = getInitialStage(villager);
        if (stage == Stage.Place) lecternPickupTicks = 100;
        timer = 0;
        attempts = 0;
        info("Searching for %s.", formatBook(targetEnchantment, targetLevel));
    }

    @Override
    public void onDeactivate() {
        stage = Stage.Idle;
        InvUtils.swapBack();
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (interactionGuardTicks > 0) {
            event.cancel();
            return;
        }
        if (selection != Selection.Villager) return;
        event.cancel();

        if (!(event.entity instanceof Villager villager)) {
            showSubtitle("Right-click a villager");
            return;
        }

        villagerId.set(villager.getUUID().toString());
        finishSelection();
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (interactionGuardTicks > 0) {
            event.cancel();
            return;
        }
        if (selection != Selection.Lectern) return;
        event.cancel();

        if (!mc.level.getBlockState(event.result.getBlockPos()).is(Blocks.LECTERN)) {
            showSubtitle("Right-click a lectern");
            return;
        }

        lecternPos.set(event.result.getBlockPos());
        lecternSelected.set(true);
        finishSelection();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (selection == Selection.None) return;

        event.cancel();
        finishSelection();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (interactionGuardTicks > 0) interactionGuardTicks--;
        if (!isActive() || mc.player == null || mc.level == null) return;

        Villager villager = getVillager();
        if (villager == null) {
            stopWithError("Selected villager is no longer loaded.");
            return;
        }
        if (!isWithinReach(villager)) {
            stopWithError("The villager or lectern moved out of reach.");
            return;
        }
        if (timer-- > 0) return;

        switch (stage) {
            case WaitUnemployed -> {
                if (villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
                    lecternPickupTicks = 100;
                    stage = Stage.WaitLectern;
                }
            }
            case WaitLectern -> waitForLectern();
            case Place -> placeLectern();
            case WaitLibrarian -> {
                if (!mc.level.getBlockState(lecternPos.get()).is(Blocks.LECTERN)) stage = Stage.Place;
                else if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) stage = Stage.OpenTrades;
            }
            case OpenTrades -> openTrades(villager);
            case WaitTrades -> checkTrades();
            case Break -> breakLectern();
            case Idle -> {
            }
        }
    }

    private void placeLectern() {
        if (mc.level.getBlockState(lecternPos.get()).is(Blocks.LECTERN)) {
            stage = Stage.WaitLibrarian;
            return;
        }

        FindItemResult lectern = InvUtils.findInHotbar(Items.LECTERN);
        if (!lectern.found()) {
            lectern = InvUtils.find(Items.LECTERN);
            if (!lectern.found()) {
                lecternPickupTicks = 100;
                stage = Stage.WaitLectern;
                return;
            }

            InvUtils.move().from(lectern.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
            timer = ACTION_DELAY;
            return;
        }

        if (!BlockUtils.place(lecternPos.get(), lectern, true, 0, true)) {
            stopWithError("The selected lectern position cannot be used.");
            return;
        }

        stage = Stage.WaitLibrarian;
        timer = ACTION_DELAY;
    }

    private void openTrades(Villager villager) {
        InteractionResult result = mc.gameMode.interact(mc.player, villager, new EntityHitResult(villager), InteractionHand.MAIN_HAND);
        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);

        stage = Stage.WaitTrades;
        timer = ACTION_DELAY;
    }

    private void checkTrades() {
        if (!(mc.gui.screen() instanceof MerchantScreen screen)) {
            stage = Stage.OpenTrades;
            timer = ACTION_DELAY;
            return;
        }
        if (screen.getMenu().getOffers().isEmpty()) {
            timer = ACTION_DELAY;
            return;
        }

        MerchantOffers offers = screen.getMenu().getOffers();
        info("Attempt %s: %s.", ++attempts, describeBooks(offers));

        boolean found = offers.stream().anyMatch(offer -> hasTargetEnchantment(offer.getResult()));
        if (found) {
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            info("Found %s. The villager and lectern were left untouched.", formatBook(targetEnchantment, targetLevel));
            toggle();
            return;
        }

        mc.player.closeContainer();
        stage = Stage.Break;
        timer = ACTION_DELAY;
    }

    private void breakLectern() {
        if (!mc.level.getBlockState(lecternPos.get()).is(Blocks.LECTERN)) {
            InvUtils.swapBack();
            stage = Stage.WaitUnemployed;
            timer = ACTION_DELAY;
            return;
        }

        FindItemResult tool = InvUtils.findFastestTool(mc.level.getBlockState(lecternPos.get()));
        if (tool.found()) InvUtils.swap(tool.slot(), true);
        BlockUtils.breakBlock(lecternPos.get(), true);
    }

    private void waitForLectern() {
        if (InvUtils.find(Items.LECTERN).found()) {
            stage = Stage.Place;
            return;
        }
        if (lecternPickupTicks-- <= 0) stopWithError("No lectern found in the inventory after 5 seconds.");
    }

    private boolean hasTargetEnchantment(net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        return enchantments.keySet().stream().anyMatch(holder -> holder.is(targetEnchantment) && enchantments.getLevel(holder) == targetLevel);
    }

    private String describeBooks(MerchantOffers offers) {
        StringBuilder result = new StringBuilder();

        for (var offer : offers) {
            var stack = offer.getResult();
            if (!stack.is(Items.ENCHANTED_BOOK)) continue;

            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var holder : enchantments.keySet()) {
                if (!result.isEmpty()) result.append(", ");
                result.append(Names.get(holder)).append(' ').append(toRoman(enchantments.getLevel(holder)));
            }
        }

        return result.isEmpty() ? "No enchanted book" : result.toString();
    }

    private Stage getInitialStage(Villager villager) {
        boolean hasLectern = mc.level.getBlockState(lecternPos.get()).is(Blocks.LECTERN);
        if (!hasLectern) return villager.getVillagerData().profession().is(VillagerProfession.NONE) ? Stage.Place : Stage.WaitUnemployed;
        if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) return Stage.OpenTrades;
        return Stage.WaitLibrarian;
    }

    private Villager getVillager() {
        if (mc.level == null || villagerId.get().isBlank()) return null;

        try {
            Entity entity = mc.level.getEntity(UUID.fromString(villagerId.get()));
            return entity instanceof Villager villager ? villager : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isWithinReach(Villager villager) {
        return villager.distanceToSqr(mc.player) <= 36 && PlayerUtils.isWithinReach(lecternPos.get());
    }

    private ResourceKey<Enchantment> resolveTargetEnchantment() {
        Registry<Enchantment> registry = getEnchantmentRegistry();
        ResourceKey<Enchantment> selected = getSelectedEnchantment();
        if (registry == null || selected == null) return null;
        if (!registry.get(selected).map(holder -> holder.is(EnchantmentTags.TRADEABLE)).orElse(false)) return null;

        normalizeLevel();
        targetLevel = level.get();
        return selected;
    }

    private ResourceKey<Enchantment> getSelectedEnchantment() {
        return enchantment.get().stream().findFirst().orElse(null);
    }

    private void normalizeLevel() {
        Registry<Enchantment> registry = getEnchantmentRegistry();
        ResourceKey<Enchantment> selected = getSelectedEnchantment();
        if (registry == null || selected == null || !registry.containsKey(selected)) return;

        Enchantment value = registry.getValueOrThrow(selected);
        level.set(Math.max(value.getMinLevel(), Math.min(level.get(), value.getMaxLevel())));
    }

    private String[] getAvailableLevels() {
        Registry<Enchantment> registry = getEnchantmentRegistry();
        ResourceKey<Enchantment> selected = getSelectedEnchantment();
        if (registry == null || selected == null || !registry.containsKey(selected)) return new String[]{"I"};

        Enchantment value = registry.getValueOrThrow(selected);
        String[] levels = new String[value.getMaxLevel() - value.getMinLevel() + 1];
        for (int i = 0; i < levels.length; i++) levels[i] = toRoman(value.getMinLevel() + i);
        return levels;
    }

    private Registry<Enchantment> getEnchantmentRegistry() {
        if (mc.getConnection() == null) return null;
        return mc.getConnection().registryAccess().lookup(Registries.ENCHANTMENT).orElse(null);
    }

    private void startSelection(Selection selection) {
        if (mc.player == null || mc.level == null) {
            error("Join a world before selecting targets.");
            return;
        }
        if (isActive()) {
            error("Disable the module before changing targets.");
            return;
        }

        this.selection = selection;
        previousScreen = mc.gui.screen();
        showSubtitle(selection == Selection.Villager ? "Right-click a villager" : "Right-click a lectern");
        mc.gui.setScreen(null);
    }

    private void finishSelection() {
        selection = Selection.None;
        interactionGuardTicks = 2;
        showSubtitle("Selection saved");
        mc.gui.setScreen(previousScreen);
        previousScreen = null;
    }

    private void showSubtitle(String text) {
        if (mc.getConnection() == null) return;

        mc.getConnection().setTitlesAnimation(new ClientboundSetTitlesAnimationPacket(3, 30, 6));
        mc.getConnection().setSubtitleText(new ClientboundSetSubtitleTextPacket(Component.literal(text)));
        mc.getConnection().setTitleText(new ClientboundSetTitleTextPacket(Component.literal(" ")));
    }

    private void stopWithError(String message) {
        error(message);
        toggle();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String formatBook(ResourceKey<Enchantment> enchantment, int level) {
        return Names.get(enchantment) + " " + toRoman(level);
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private static int fromRoman(String level) {
        return switch (level) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> Integer.parseInt(level);
        };
    }

    private class TradeableEnchantmentScreen extends EnchantmentListSettingScreen {
        public TradeableEnchantmentScreen(GuiTheme theme, Setting<Set<ResourceKey<Enchantment>>> setting) {
            super(theme, setting);
        }

        @Override
        protected boolean includeValue(ResourceKey<Enchantment> value) {
            if (mc.getConnection() == null) return false;
            return mc.getConnection().registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.get(value))
                .map(holder -> holder.is(EnchantmentTags.TRADEABLE))
                .orElse(false);
        }

        @Override
        protected void addValue(ResourceKey<Enchantment> value) {
            collection.clear();
            super.addValue(value);
        }
    }

    private enum Selection {
        None,
        Villager,
        Lectern
    }

    private enum Stage {
        Idle,
        WaitUnemployed,
        WaitLectern,
        Place,
        WaitLibrarian,
        OpenTrades,
        WaitTrades,
        Break
    }
}
