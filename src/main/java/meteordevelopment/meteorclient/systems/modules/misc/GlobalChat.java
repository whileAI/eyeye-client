/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GlobalChat extends Module {
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MAX_PENDING_MESSAGES = 20;
    private static final long RECONNECT_DELAY_MS = 3_000;
    private static final String ENDPOINT = "wss://eyeye-global-chat.eyeye-local-chat.workers.dev/chat";

    private final HttpClient client = HttpClient.newHttpClient();
    private final StringBuilder received = new StringBuilder();
    private final Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();
    private final Set<String> eyeyeUsers = ConcurrentHashMap.newKeySet();
    private volatile WebSocket socket;
    private volatile String connectedServer = "";
    private volatile boolean connecting;
    private volatile long connectionAttempt;
    private volatile long nextConnectionAttempt;

    public GlobalChat() {
        super(Categories.Misc, "global-chat", "Shares messages with EyEye users on the same server.");
    }

    @Override
    public void onActivate() {
        nextConnectionAttempt = 0;
        ensureConnected();
    }

    @Override
    public void onDeactivate() {
        disconnect(true);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;
        disconnect(true);
        nextConnectionAttempt = 0;
        ensureConnected();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ensureConnected();
        flushMessages();
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

        if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
            error("EyEye Chat queue is full. Try again in a moment.");
            return false;
        }

        pendingMessages.offer(content);
        ensureConnected();
        flushMessages();
        return true;
    }

    public boolean isEyEyeUser(String name) {
        return eyeyeUsers.contains(normalizeName(name));
    }

    private void ensureConnected() {
        String server = getServer();
        if (!isActive() || server.isEmpty() || connecting || System.currentTimeMillis() < nextConnectionAttempt) return;
        if (socket != null && server.equals(connectedServer)) return;

        disconnect(false);
        connect(server);
    }

    private void connect(String server) {
        long attempt = ++connectionAttempt;
        connecting = true;

        try {
            URI endpoint = URI.create(ENDPOINT + "?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8) + "&name=" + URLEncoder.encode(getNickname(), StandardCharsets.UTF_8));
            client.newWebSocketBuilder().buildAsync(endpoint, new ChatListener(attempt)).whenComplete((connected, error) -> {
                if (attempt != connectionAttempt) {
                    if (connected != null) connected.sendClose(WebSocket.NORMAL_CLOSURE, "Replaced");
                    return;
                }

                if (error != null) {
                    connectionFailed(attempt, error);
                    return;
                }

                if (!isActive() || !server.equals(getServer())) {
                    connected.sendClose(WebSocket.NORMAL_CLOSURE, "Disabled");
                    return;
                }

                socket = connected;
                connectedServer = server;
                connecting = false;
                nextConnectionAttempt = 0;
                flushMessages();
                mc.execute(() -> info("EyEye Chat enabled. Use ;chat <message>."));
            });
        } catch (IllegalArgumentException error) {
            connectionFailed(attempt, error);
        }
    }

    private void connectionFailed(long attempt, Throwable error) {
        if (attempt != connectionAttempt) return;
        socket = null;
        connectedServer = "";
        connecting = false;
        nextConnectionAttempt = System.currentTimeMillis() + RECONNECT_DELAY_MS;
        mc.execute(() -> warning("EyEye Chat connection failed: %s", error.getMessage()));
    }

    private void disconnect(boolean clearPendingMessages) {
        connectionAttempt++;
        connecting = false;
        WebSocket current = socket;
        socket = null;
        connectedServer = "";
        eyeyeUsers.clear();
        if (clearPendingMessages) pendingMessages.clear();
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "Disabled");
    }

    private void flushMessages() {
        WebSocket current = socket;
        if (current == null || !isActive()) return;

        String message;
        while ((message = pendingMessages.poll()) != null) current.sendText("M\t" + message, true);
    }

    private String getNickname() {
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

    private static String normalizeName(String value) {
        return sanitize(value).toLowerCase(Locale.ROOT);
    }

    private void receive(String payload) {
        String[] parts = payload.split("\\t", 3);
        if (parts.length == 2 && "P".equals(parts[0])) {
            eyeyeUsers.add(normalizeName(parts[1]));
            return;
        }
        if (parts.length == 2 && "L".equals(parts[0])) {
            eyeyeUsers.remove(normalizeName(parts[1]));
            return;
        }

        String author;
        String message;
        if (parts.length == 3 && "M".equals(parts[0])) {
            author = sanitize(parts[1]);
            message = sanitize(parts[2]);
        } else if (parts.length == 2) {
            author = sanitize(parts[0]);
            message = sanitize(parts[1]);
        } else return;
        if (author.isEmpty() || message.isEmpty()) return;

        ChatUtils.sendMsg(Component.literal("[EyEye Chat] <").append(author).append("> ").append(message));
    }

    private class ChatListener implements WebSocket.Listener {
        private final long attempt;

        private ChatListener(long attempt) {
            this.attempt = attempt;
        }

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
            if (forget(webSocket)) connectionFailed(attempt, error);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (forget(webSocket) && isActive()) {
                nextConnectionAttempt = System.currentTimeMillis() + RECONNECT_DELAY_MS;
            }
            return CompletableFuture.completedFuture(null);
        }

        private boolean forget(WebSocket webSocket) {
            if (socket != webSocket || attempt != connectionAttempt) return false;
            socket = null;
            connectedServer = "";
            connecting = false;
            return true;
        }
    }
}
