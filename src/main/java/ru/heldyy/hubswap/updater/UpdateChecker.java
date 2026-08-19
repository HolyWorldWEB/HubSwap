package ru.heldyy.hubswap.updater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateChecker {
    private static final String MOD_ID = "hubswap";

    private static final String GITHUB_API =
            "https://api.github.com/repos/Heldyy90/HubSwap/releases/latest";

    private static final String REPO_URL =
            "https://github.com/Heldyy90/HubSwap/releases/latest";

    private static boolean checked = false;

    public static void checkAfterJoin() {
        if (checked) return;
        checked = true;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(3500);

                String currentVersion = getCurrentVersion();
                ReleaseInfo latest = getLatestRelease();

                if (latest == null || latest.tagName == null) return;

                String latestVersion = normalizeVersion(latest.tagName);
                String current = normalizeVersion(currentVersion);

                if (isNewerVersion(latestVersion, current)) {
                    sendUpdateMessage(latest.tagName, latest.htmlUrl);
                }

            } catch (Exception e) {
                System.err.println("[HubSwap] Update check failed: " + e.getMessage());
            }
        }, "HubSwap Update Checker");
        thread.setDaemon(true);
        thread.start();
    }

    private static String getCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    private static ReleaseInfo getLatestRelease() throws Exception {
        URL url = new URL(GITHUB_API);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "HubSwap-UpdateChecker");

            if (connection.getResponseCode() != 200) {
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            String tagName = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
            String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : REPO_URL;
            return new ReleaseInfo(tagName, htmlUrl);
        } finally {
            connection.disconnect();
        }
    }

    private static void sendUpdateMessage(String latestVersion, String url) {
        MinecraftClient client = MinecraftClient.getInstance();

        client.execute(() -> {
            if (client.player == null) return;

            Text prefix = Text.literal("[HubSwap] ")
                    .formatted(Formatting.AQUA, Formatting.BOLD);

            Text message = Text.literal("Вышла новая версия мода: ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(latestVersion).formatted(Formatting.GREEN))
                    .append(Text.literal("  "));

            String safeUrl = (url != null && url.startsWith("https://github.com/")) ? url : REPO_URL;
            Text link = Text.literal("[СКАЧАТЬ]")
                    .styled(style -> style
                            .withColor(Formatting.AQUA)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, safeUrl))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Открыть страницу релиза HubSwap")
                            ))
                    );

            client.player.sendMessage(prefix.copy().append(message).append(link), false);
        });
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "0.0.0";

        // Обрезаем метаданные (например, +mc1.20.1)
        String base = version.split("[+-]")[0];

        String clean = base.toLowerCase()
                .replace("version", "")
                .replaceAll("[^0-9.]", "")
                .replaceAll("^\\.+", "")
                .replaceAll("\\.+$", "")
                .trim();

        return clean.isEmpty() ? "0.0.0" : clean;
    }

    private static boolean isNewerVersion(String latest, String current) {
        int[] latestParts = parseVersion(latest);
        int[] currentParts = parseVersion(current);

        for (int i = 0; i < 3; i++) {
            if (latestParts[i] > currentParts[i]) return true;
            if (latestParts[i] < currentParts[i]) return false;
        }

        return false;
    }

    private static int[] parseVersion(String version) {
        int[] result = new int[]{0, 0, 0};

        try {
            String cleanVersion = normalizeVersion(version);
            String[] parts = cleanVersion.split("\\.");

            int index = 0;
            for (String part : parts) {
                if (index >= 3) break;
                if (part == null || part.isBlank()) continue;

                String clean = part.replaceAll("[^0-9]", "");
                if (!clean.isEmpty()) {
                    result[index] = Integer.parseInt(clean);
                    index++;
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private record ReleaseInfo(String tagName, String htmlUrl) {
    }
}