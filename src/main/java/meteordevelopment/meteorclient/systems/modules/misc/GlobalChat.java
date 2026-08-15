/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class GlobalChat extends Module {
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final String ENDPOINT = "wss://eyeye-global-chat.eyeye-local-chat.workers.dev/chat";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> nickname = sgGeneral.add(new StringSetting.Builder()
        .name("nickname")
        .description("Name shown in EyEye Chat. Uses your Minecraft name when empty.")
        .defaultValue("")
        .build()
    );

    private final HttpClient client = HttpClient.newHttpClient();
    private final StringBuilder received = new StringBuilder();
    private volatile WebSocket socket;
    private volatile String connectedServer = "";

    public GlobalChat() {
        super(Categories.Misc, "global-chat", "Shares messages with EyEye users on the same server.");
    }

    @Override
    public void onActivate() {
        connect();
    }

    @Override
    public void onDeactivate() {
        disconnect();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;
        disconnect();
        connect();
    }

    public boolean send(String message) {
        if (!isActive()) {
            error("EyEye Chat is disabled. Run ;chat-status true first.");
            return false;
        }

        String server = getServer();
        if (server.isEmpty()) {
            error("EyEye Chat only works on multiplayer servers.");
            return false;
        }

        String content = sanitize(message);
        if (content.isEmpty()) return false;

        WebSocket current = socket;
        if (current == null || !server.equals(connectedServer)) {
            disconnect();
            connect();
            warning("EyEye Chat is connecting. Try again in a moment.");
            return false;
        }

        current.sendText(getNickname() + "\t" + content, true);
        return true;
    }

    private void connect() {
        String server = getServer();
        if (server.isEmpty()) return;

        try {
            URI endpoint = URI.create(ENDPOINT + "?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8));
            client.newWebSocketBuilder().buildAsync(endpoint, new ChatListener()).whenComplete((connected, error) -> {
                if (error != null) {
                    mc.execute(() -> warning("EyEye Chat connection failed: %s", error.getMessage()));
                    return;
                }

                if (!isActive() || !server.equals(getServer())) {
                    connected.sendClose(WebSocket.NORMAL_CLOSURE, "Disabled");
                    return;
                }

                socket = connected;
                connectedServer = server;
                mc.execute(() -> info("EyEye Chat enabled. Use ;chat <message>."));
            });
        } catch (IllegalArgumentException error) {
            error("EyEye Chat endpoint is invalid.");
        }
    }

    private void disconnect() {
        WebSocket current = socket;
        socket = null;
        connectedServer = "";
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "Disabled");
    }

    private String getNickname() {
        String value = sanitize(nickname.get());
        if (!value.isEmpty()) return value;
        return mc.player != null ? sanitize(mc.player.getName().getString()) : "EyEye User";
    }

    private String getServer() {
        if (mc.isLocalServer() || mc.getCurrentServer() == null) return "";
        String server = sanitize(mc.getCurrentServer().ip).toLowerCase(Locale.ROOT);
        if (server.endsWith(".")) server = server.substring(0, server.length() - 1);
        if (server.endsWith(":25565")) server = server.substring(0, server.length() - 6);
        return server;
    }

    private static String sanitize(String value) {
        String normalized = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.substring(0, Math.min(normalized.length(), MAX_MESSAGE_LENGTH));
    }

    private void receive(String payload) {
        int separator = payload.indexOf('\t');
        if (separator <= 0 || separator == payload.length() - 1) return;

        String author = sanitize(payload.substring(0, separator));
        String message = sanitize(payload.substring(separator + 1));
        if (author.isEmpty() || message.isEmpty()) return;

        ChatUtils.sendMsg(Component.literal("[EyEye Chat] <").append(author).append("> ").append(message));
    }

    private class ChatListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (received) {
                received.append(data);
                if (last) {
                    String payload = received.toString();
                    received.setLength(0);
                    mc.execute(() -> receive(payload));
                }
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            forget(webSocket);
            mc.execute(() -> warning("EyEye Chat connection lost: %s", error.getMessage()));
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            forget(webSocket);
            return CompletableFuture.completedFuture(null);
        }

        private void forget(WebSocket webSocket) {
            if (socket != webSocket) return;
            socket = null;
            connectedServer = "";
        }
    }
}
