/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;

public class DiscordPresence extends Module {
    private static final long APPLICATION_ID = 1538909961646641302L;
    private static final RichPresence rpc = new RichPresence();

    private String lastState = "";

    public DiscordPresence() {
        super(Categories.Misc, "discord-presence", "Shows your EyEye Client activity on Discord.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        DiscordIPC.start(APPLICATION_ID, null);

        rpc.setStart(System.currentTimeMillis() / 1000L);
        rpc.setDetails("Best free client");
        rpc.setLargeImage("eyeye", "EyEye Client");
        rpc.setSmallImage("minecraft", "Minecraft " + SharedConstants.getCurrentVersion().name());

        lastState = "";
        updatePresence();
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        updatePresence();
    }

    private void updatePresence() {
        String state = getState();
        if (state.equals(lastState)) return;

        rpc.setState(state);
        DiscordIPC.setActivity(rpc);
        lastState = state;
    }

    private String getState() {
        ServerData server = mc.getCurrentServer();
        if (server != null) return "Server: " + server.ip;

        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return "World: " + mc.getSingleplayerServer().getWorldData().getLevelName();
        }

        return "Main Menu";
    }
}
