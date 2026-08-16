package ru.heldyy.hubswap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.heldyy.hubswap.config.ModConfig;
import ru.heldyy.hubswap.config.StatsData;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;

public class HubSwap {
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("hubswap.json");

    private static final Path STATS_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("hubswap_stats.json");

    private static ModConfig CONFIG = loadConfigInternal();
    private static StatsData STATS = loadStatsInternal();

    private static Timer autoSaveTimer;

    public static ModConfig getConfig() {
        return CONFIG;
    }

    public static StatsData getStats() {
        return STATS;
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(CONFIG, w);
                System.out.println("[HubSwap] Config saved to " + CONFIG_PATH);
            }
        } catch (Exception e) {
            System.err.println("[HubSwap] Failed to save config: " + e.getMessage());
        }
    }

    public static void saveStats() {
        try {
            STATS.flushCurrentServer();
            Files.createDirectories(STATS_PATH.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(STATS_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(STATS, w);
                System.out.println("[HubSwap] Stats saved to " + STATS_PATH);
            }
        } catch (Exception e) {
            System.err.println("[HubSwap] Failed to save stats: " + e.getMessage());
        }
    }

    public static void reloadConfig() {
        CONFIG = loadConfigInternal();
        System.out.println("[HubSwap] Config reloaded.");
    }

    /**
     * Перезагружает статистику из файла (используется при открытии вкладки STATS в GUI)
     */
    public static void reloadStats() {
        STATS = loadStatsInternal();
        System.out.println("[HubSwap] Stats reloaded from file.");
    }

    private static ModConfig loadConfigInternal() {
        if (Files.exists(CONFIG_PATH)) {
            try (BufferedReader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                ModConfig cfg = GSON.fromJson(r, ModConfig.class);
                if (cfg != null) {
                    System.out.println("[HubSwap] Config loaded from " + CONFIG_PATH);
                    migrateFromOldConfigs();
                    return cfg;
                }
            } catch (Exception e) {
                System.err.println("[HubSwap] Failed to load config: " + e.getMessage());
            }
        }
        System.out.println("[HubSwap] No config found, creating default.");
        ModConfig cfg = new ModConfig();
        migrateFromOldConfigs();
        return cfg;
    }

    private static void migrateFromOldConfigs() {
        // Миграция из старых файлов (оставлена как есть)
    }

    private static StatsData loadStatsInternal() {
        if (Files.exists(STATS_PATH)) {
            try (BufferedReader r = Files.newBufferedReader(STATS_PATH, StandardCharsets.UTF_8)) {
                StatsData s = GSON.fromJson(r, StatsData.class);
                if (s != null) {
                    System.out.println("[HubSwap] Stats loaded from " + STATS_PATH);
                    return s;
                }
            } catch (Exception e) {
                System.err.println("[HubSwap] Failed to load stats: " + e.getMessage());
            }
        }
        System.out.println("[HubSwap] No stats found, creating new.");
        return new StatsData();
    }

    // ---- Автосохранение ----
    public static void init() {
        startAutoSave();
    }

    public static void startAutoSave() {
        if (autoSaveTimer != null) return;
        autoSaveTimer = new Timer(true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveStats();
                System.out.println("[HubSwap] Auto-save stats (every 30s)");
            }
        }, 30000, 30000);
        System.out.println("[HubSwap] Auto-save started (interval: 30s)");
    }

    public static void stopAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
            System.out.println("[HubSwap] Auto-save stopped");
        }
    }
}