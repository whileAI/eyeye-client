/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;

public class GlobalChat extends Module {
    private static final int MAX_MESSAGE_LENGTH = 256;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> endpoint = sgGeneral.add(new StringSetting.Builder()
        .name("endpoint")
        .description("Cloudflare Worker WebSocket address.")
        .placeholder("wss://your-worker.workers.dev/chat")
        .defaultValue("")
        .wide()
        .build()
    );

    private final Setting<String> nickname = sgGeneral.add(new StringSetting.Builder()
        .name("nickname")
        .description("Name shown in Global Chat. Uses your Minecraft name when empty.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Messages starting with this prefix are sent to Global Chat.")
        .defaultValue("!")
        .build()
    );

    private final HttpClient client = HttpClient.newHttpClient();
    private final StringBuilder received = new StringBuilder();
    private volatile WebSocket socket;

    public GlobalChat() {
        super(Categories.Misc, "global-chat", "Sends messages to other EyEye users through Cloudflare.");
    }

    @Override
    public void onActivate() {
        connect();
    }

    @Override
    public void onDeactivate() {
        WebSocket current = socket;
        socket = null;
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "Disabled");
    }

    @EventHandler
    private void onMessageSend(SendMessageEvent event) {
        String marker = prefix.get();
        if (marker.isEmpty() || !event.message.startsWith(marker)) return;

        event.cancel();
        String message = sanitize(event.message.substring(marker.length()));
        if (message.isEmpty()) return;

        WebSocket current = socket;
        if (current == null) {
            warning("Global Chat is not connected.");
            return;
        }

        current.sendText(getNickname() + "\t" + message, true);
    }

    private void connect() {
        if (endpoint.get().isBlank()) {
            warning("Set a Cloudflare Worker endpoint first.");
            return;
        }

        try {
            URI address = URI.create(endpoint.get());
            if (!"ws".equalsIgnoreCase(address.getScheme()) && !"wss".equalsIgnoreCase(address.getScheme())) {
                warning("Endpoint must start with ws:// or wss://.");
                return;
            }

            client.newWebSocketBuilder().buildAsync(address, new ChatListener()).whenComplete((connected, error) -> {
                if (error != null) {
                    mc.execute(() -> warning("Connection failed: %s", error.getMessage()));
                    return;
                }

                if (!isActive()) {
                    connected.sendClose(WebSocket.NORMAL_CLOSURE, "Disabled");
                    return;
                }

                socket = connected;
                mc.execute(() -> info("Connected. Use %smessage to send Global Chat messages.", prefix.get()));
            });
        } catch (IllegalArgumentException error) {
            warning("Invalid Cloudflare Worker endpoint.");
        }
    }

    private String getNickname() {
        String value = sanitize(nickname.get());
        if (!value.isEmpty()) return value;
        return mc.player != null ? sanitize(mc.player.getName().getString()) : "EyEye User";
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

        ChatUtils.sendMsg(Component.literal("[Global] ").append(author).append(" » ").append(message));
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
            socket = null;
            mc.execute(() -> warning("Connection lost: %s", error.getMessage()));
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (socket == webSocket) socket = null;
            return CompletableFuture.completedFuture(null);
        }
    }
}
