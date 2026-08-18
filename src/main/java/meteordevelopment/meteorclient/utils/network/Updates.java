package meteordevelopment.meteorclient.utils.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.misc.Version;
import meteordevelopment.meteorclient.utils.render.prompts.YesNoPrompt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Updates {
    private static final String API_URL = "https://updates.eyeyeclient.site/api/versions";
    private static final String RELEASE_URL = "https://updates.eyeyeclient.site/releases/";
    private static final AtomicBoolean checked = new AtomicBoolean();
    private static final AtomicBoolean downloading = new AtomicBoolean();

    private static volatile Release available;
    private static boolean promptShown;

    private Updates() {
    }

    public static void check() {
        if (!checked.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                String body = Http.get(API_URL).ignoreExceptions().sendString();
                if (body == null) return;

                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                for (JsonElement element : root.getAsJsonArray("releases")) {
                    JsonObject release = element.getAsJsonObject();
                    if (!release.has("latest") || !release.get("latest").getAsBoolean()) continue;

                    String version = release.get("version").getAsString();
                    if (!new Version(version).isHigherThan(MeteorClient.VERSION)) return;

                    String details = Http.get(API_URL + "/" + version).ignoreExceptions().sendString();
                    if (details == null) return;

                    JsonObject detail = JsonParser.parseString(details).getAsJsonObject();
                    String file = detail.get("file").getAsString();
                    String sha256 = detail.get("sha256").getAsString();
                    if (!file.equals("eyeye-client-" + version + ".jar") || !sha256.matches("[a-fA-F0-9]{64}")) return;

                    available = new Release(version, file, sha256);
                    return;
                }
            } catch (Exception e) {
                MeteorClient.LOG.debug("Could not check for EyEye updates.", e);
            }
        });
    }

    public static void showPrompt() {
        Release release = available;
        if (release == null || promptShown || downloading.get() || !Config.get().updateNotifications.get()) return;

        promptShown = true;
        YesNoPrompt.create(GuiThemes.get(), MeteorClient.mc.gui.screen())
            .title("EyEye update available")
            .message("Version %s is available. You are using %s.", release.version, MeteorClient.VERSION)
            .message("The client will close, replace its JAR and can then be launched again.")
            .yesText("Update and restart")
            .noText("Not now")
            .dontShowAgainCheckboxVisible(false)
            .onYes(() -> download(release))
            .show();
    }

    private static void download(Release release) {
        if (!downloading.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                Files.createDirectories(mods);

                Path pending = mods.resolve(".eyeye-client-" + release.version + ".jar.pending");
                try (InputStream input = Http.get(RELEASE_URL + release.file).ignoreExceptions().sendInputStream()) {
                    if (input == null) throw new IOException("Release download failed.");
                    Files.copy(input, pending, StandardCopyOption.REPLACE_EXISTING);
                }

                if (!sha256(pending).equalsIgnoreCase(release.sha256)) {
                    Files.deleteIfExists(pending);
                    throw new IOException("Release checksum mismatch.");
                }

                startUpdater(mods, pending, release.file, ProcessHandle.current().info().commandLine().orElse(""));
                MeteorClient.mc.execute(MeteorClient.mc::stop);
            } catch (Exception e) {
                downloading.set(false);
                promptShown = false;
                MeteorClient.LOG.warn("EyEye update failed.", e);
            }
        });
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }

        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(String.format("%02x", value));
        return hash.toString();
    }

    private static void startUpdater(Path mods, Path pending, String file, String launchCommand) throws IOException {
        Path script = FabricLoader.getInstance().getGameDir().resolve("eyeye-client-update.ps1");
        String encodedMods = Base64.getEncoder().encodeToString(mods.toString().getBytes(StandardCharsets.UTF_8));
        String encodedPending = Base64.getEncoder().encodeToString(pending.toString().getBytes(StandardCharsets.UTF_8));
        String encodedLaunchCommand = Base64.getEncoder().encodeToString(launchCommand.getBytes(StandardCharsets.UTF_8));
        String scriptText = """
            $ErrorActionPreference = 'Stop'
            $mods = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s'))
            $pending = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s'))
            $launchCommand = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s'))
            Start-Sleep -Seconds 3
            Get-ChildItem -LiteralPath $mods -Filter 'eyeye-client-*.jar' -File | Remove-Item -Force
            Move-Item -LiteralPath $pending -Destination (Join-Path $mods '%s') -Force
            Remove-Item -LiteralPath $PSCommandPath -Force
            if (-not [string]::IsNullOrWhiteSpace($launchCommand)) {
                Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/s', '/c', $launchCommand)
            }
            """.formatted(encodedMods, encodedPending, encodedLaunchCommand, file);

        Files.writeString(script, scriptText, StandardCharsets.UTF_8);
        new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
    }

    private record Release(String version, String file, String sha256) {
    }
}
