package ru.heldyy.hubswap.executor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.world.World;
import ru.heldyy.hubswap.HubSwap;
import ru.heldyy.hubswap.config.ModConfig;
import ru.heldyy.hubswap.gui.NotificationRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnarchyExecutor {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    private enum State {
        IDLE,
        WAITING_HUB_WORLD,
        WAITING_MENU,
        WAITING_MENU1,
        WAITING_MENU2,
        HUB_ARRIVED
    }

    private static State state = State.IDLE;
    private static String mode = "lite";
    private static int targetNumber = -1;
    private static String categoryKey = null;
    private static String serverKey = null;
    private static World prevWorld = null;
    private static int ticks = 0;
    private static int timeoutTicks = 400;
    private static int arrivalTicks = 0;

    private static final Map<String, Integer> currentNumbers = new HashMap<>();

    private static final Pattern LITE120_PATTERN = Pattern.compile("(?i)Лайт\\s*#?\\s*(\\d+)");
    private static final Pattern CLASSIC_PATTERN = Pattern.compile("(?i)Классик\\s*#?\\s*(\\d+)");
    private static final Pattern PRIME_PATTERN = Pattern.compile("(?i)Прайм\\s*#?\\s*(\\d+)");

    private static final double HUB_X = 317.5;
    private static final double HUB_Y = 28.0;
    private static final double HUB_Z = 302.5;
    private static final double HUB_RADIUS = 20.0;
    private static final double HUB_Y_TOLERANCE = 10.0;

    // ---- TOP/DOWN ----
    public static void top(String modeName, int step) {
        if (step < 1) step = 1;
        int max = HubSwap.getConfig().getMode(modeName).getRanges().getTotal();
        int current = currentNumbers.getOrDefault(modeName, 1);
        int newNumber = ((current - 1 + step) % max) + 1;
        start(modeName, newNumber);
    }

    public static void down(String modeName, int step) {
        if (step < 1) step = 1;
        int max = HubSwap.getConfig().getMode(modeName).getRanges().getTotal();
        int current = currentNumbers.getOrDefault(modeName, 1);
        int newNumber = ((current - 1 - step % max + max) % max) + 1;
        start(modeName, newNumber);
    }

    public static void start(String modeName, int number) {
        if (client.player == null || client.getNetworkHandler() == null) {
            sendError("Вы не подключены к серверу");
            return;
        }

        ModConfig config = HubSwap.getConfig();
        ModConfig.ModeConfig modeCfg = config.getMode(modeName);
        if (!modeCfg.getRanges().isValid(number)) {
            sendError("Номер вне допустимого диапазона (1-" + modeCfg.getRanges().getMax() + ")");
            return;
        }

        reset();

        mode = modeName;
        targetNumber = number;
        timeoutTicks = config.getTimeoutTicks();
        prevWorld = client.world;
        ticks = 0;
        arrivalTicks = 0;

        currentNumbers.put(mode, number);

        if ("lite".equals(mode)) {
            ModConfig.RangeEntry entry = modeCfg.getRanges().find(number);
            if (entry == null) {
                sendError("Не удалось определить категорию для #" + number);
                reset();
                return;
            }
            categoryKey = entry.key;
            serverKey = number == 1 ? "lanarchy" : "lanarchy" + number;
        }

        // Проверка уже открытого меню
        Screen screen = client.currentScreen;
        if (screen instanceof HandledScreen<?> handledScreen) {
            if ("lite".equals(mode)) {
                var handler = handledScreen.getScreenHandler();
                int containerSlots = handler.slots.size() - 36;
                boolean foundType = false;
                boolean foundServer = false;
                for (int i = 0; i < containerSlots; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty()) {
                        String typeVal = readNbt(stack, "advancedserverselecter:server-type");
                        String serverVal = readNbt(stack, "advancedserverselecter:server");
                        if (categoryKey.equals(typeVal)) foundType = true;
                        if (serverKey.equals(serverVal)) foundServer = true;
                    }
                }
                if (foundType && foundServer) {
                    state = State.WAITING_MENU2;
                    System.out.println("[HubSwap] Уже открыто меню Lite, сразу переходим к выбору сервера");
                    return;
                } else if (foundType) {
                    state = State.WAITING_MENU1;
                    System.out.println("[HubSwap] Уже открыто меню Lite, выбираем категорию");
                    return;
                }
            } else {
                Pattern pattern = getPatternForMode(mode);
                if (pattern != null) {
                    int slot = findSlotByLore(handledScreen, pattern, targetNumber);
                    if (slot != -1) {
                        clickSlot(handledScreen, slot);
                        finishSuccess();
                        return;
                    }
                }
            }
        }

        // Отправляем /hub
        System.out.println("[HubSwap] Отправляем команду /hub");
        client.getNetworkHandler().sendChatCommand("hub");
        state = State.WAITING_HUB_WORLD;
        ticks = 0;
        arrivalTicks = 0;
        System.out.println("[HubSwap] Отправлена команда /hub, состояние WAITING_HUB_WORLD");
    }

    public static void onChatMessage(String msg) {
        // Не используем сообщения
    }

    public static void tick() {
        // Если состояние IDLE, ничего не делаем
        if (state == State.IDLE) return;

        // Если игрок временно отсутствует (например, во время телепортации), просто пропускаем тик
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        ticks++;
        if (ticks > timeoutTicks) {
            sendError("Таймаут при переходе на #" + targetNumber + " (режим " + mode + ")");
            reset();
            return;
        }

        switch (state) {
            case WAITING_HUB_WORLD -> {
                arrivalTicks++;
                // Ждём 20 тиков (≈1 секунда) для стабилизации после телепортации
                if (arrivalTicks >= 1) {
                    System.out.println("[HubSwap] Задержка 1 тиков прошла, переходим в HUB_ARRIVED");
                    state = State.HUB_ARRIVED;
                    arrivalTicks = 0;
                }
            }

            case HUB_ARRIVED -> {
                arrivalTicks++;
                if (arrivalTicks >= 5) {
                    System.out.println("[HubSwap] Отправляем команду меню после задержки");
                    sendMenuCommand();
                }
            }

            case WAITING_MENU1 -> scanMenu1();
            case WAITING_MENU2 -> scanMenu2();
            case WAITING_MENU -> scanMenu();
        }
    }

    private static boolean isInHub() {
        if (client.player == null) return false;
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        return Math.abs(x - HUB_X) <= HUB_RADIUS
                && Math.abs(y - HUB_Y) <= HUB_Y_TOLERANCE
                && Math.abs(z - HUB_Z) <= HUB_RADIUS;
    }

    private static void sendMenuCommand() {
        String menuCmd = switch (mode) {
            case "lite" -> "lite";
            case "lite120" -> "lite120";
            case "classic" -> "anarchy";
            case "prime" -> "prime";
            default -> "";
        };
        if (menuCmd.isEmpty()) {
            reset();
            return;
        }
        System.out.println("[HubSwap] Отправляем команду меню: " + menuCmd);
        client.getNetworkHandler().sendChatCommand(menuCmd);

        if ("lite".equals(mode)) {
            state = State.WAITING_MENU1;
        } else {
            state = State.WAITING_MENU;
        }
        ticks = 0;
        arrivalTicks = 0;
    }

    private static void scanMenu1() {
        scanMenuByNbt("advancedserverselecter:server-type", categoryKey, true);
    }

    private static void scanMenu2() {
        scanMenuByNbt("advancedserverselecter:server", serverKey, false);
    }

    private static void scanMenu() {
        Screen screen = client.currentScreen;
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return;
        }

        Pattern pattern = getPatternForMode(mode);
        if (pattern == null) {
            reset();
            return;
        }

        int slot = findSlotByLore(handledScreen, pattern, targetNumber);
        if (slot != -1) {
            System.out.println("[HubSwap] Найден слот по лору: " + slot);
            clickSlot(handledScreen, slot);
            finishSuccess();
            return;
        }

        slot = findSlotByItemCount(handledScreen, targetNumber);
        if (slot != -1) {
            System.out.println("[HubSwap] Найден слот по количеству: " + slot);
            clickSlot(handledScreen, slot);
            finishSuccess();
        } else {
            System.out.println("[HubSwap] Не найден слот для " + mode + " #" + targetNumber);
        }
    }

    private static void scanMenuByNbt(String nbtKey, String expectedValue, boolean firstMenu) {
        Screen screen = client.currentScreen;
        if (screen instanceof HandledScreen<?> handledScreen) {
            var handler = handledScreen.getScreenHandler();
            int containerSlots = handler.slots.size() - 36;
            for (int i = 0; i < containerSlots; i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (!stack.isEmpty()) {
                    String value = readNbt(stack, nbtKey);
                    if (expectedValue.equals(value)) {
                        clickSlot(handledScreen, i);
                        if (firstMenu) {
                            state = State.WAITING_MENU2;
                        } else {
                            finishSuccess();
                        }
                        ticks = 0;
                        return;
                    }
                }
            }
        }
    }

    private static int findSlotByLore(HandledScreen<?> screen, Pattern pattern, int number) {
        var handler = screen.getScreenHandler();
        int containerSlots = handler.slots.size() - 36;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                String loreText = getLoreText(stack);
                if (loreText != null && !loreText.isEmpty()) {
                    Matcher m = pattern.matcher(loreText);
                    if (m.find()) {
                        try {
                            int num = Integer.parseInt(m.group(1));
                            if (num == number) {
                                return i;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return -1;
    }

    private static int findSlotByItemCount(HandledScreen<?> screen, int number) {
        var handler = screen.getScreenHandler();
        int containerSlots = handler.slots.size() - 36;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.PLAYER_HEAD) {
                if (stack.getCount() == number) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void clickSlot(HandledScreen<?> screen, int slot) {
        client.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                client.player
        );
    }

    private static Pattern getPatternForMode(String mode) {
        return switch (mode) {
            case "lite120" -> LITE120_PATTERN;
            case "classic" -> CLASSIC_PATTERN;
            case "prime" -> PRIME_PATTERN;
            default -> null;
        };
    }

    private static String readNbt(ItemStack stack, String key) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;
        NbtCompound root = customData.copyNbt();
        if (root == null || !root.contains("PublicBukkitValues")) return null;
        NbtCompound values = root.getCompound("PublicBukkitValues").orElse(null);
        if (values == null) return null;
        return values.getString(key).orElse(null);
    }

    private static String getLoreText(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;
        NbtCompound root = customData.copyNbt();
        if (root == null) return null;

        if (!root.contains("display")) return null;
        NbtCompound display = root.getCompound("display").orElse(null);
        if (display == null) return null;

        if (display.contains("Lore")) {
            NbtList loreList = display.getList("Lore").orElse(null);
            if (loreList != null && !loreList.isEmpty()) {
                StringBuilder fullText = new StringBuilder();
                for (int i = 0; i < loreList.size(); i++) {
                    String raw = loreList.getString(i).orElse(null);
                    if (raw == null) continue;
                    try {
                        var jsonElement = JsonParser.parseString(raw);
                        DataResult<Text> result = TextCodecs.CODEC.parse(JsonOps.INSTANCE, jsonElement);
                        if (result.result().isPresent()) {
                            Text parsed = result.result().get();
                            fullText.append(parsed.getString());
                        } else {
                            fullText.append(raw);
                        }
                    } catch (Exception e) {
                        fullText.append(raw);
                    }
                    if (i < loreList.size() - 1) fullText.append(" ");
                }
                String result = fullText.toString().trim();
                if (!result.isEmpty()) {
                    System.out.println("[HubSwap] Извлечён лор: \"" + result + "\"");
                    return result;
                }
            }
        }

        if (display.contains("Name")) {
            String nameRaw = display.getString("Name").orElse(null);
            if (nameRaw != null) {
                try {
                    var jsonElement = JsonParser.parseString(nameRaw);
                    DataResult<Text> result = TextCodecs.CODEC.parse(JsonOps.INSTANCE, jsonElement);
                    if (result.result().isPresent()) {
                        Text parsed = result.result().get();
                        String nameText = parsed.getString();
                        if (!nameText.isEmpty()) {
                            System.out.println("[HubSwap] Извлечено имя: \"" + nameText + "\"");
                            return nameText;
                        }
                    }
                } catch (Exception ignored) {}
                return nameRaw;
            }
        }

        return null;
    }

    private static void finishSuccess() {
        HubSwap.getStats().recordSwitch(mode, targetNumber);
        HubSwap.saveStats();
        NotificationRenderer.showNotification("Успешный переход на " + mode + " #" + targetNumber);
        System.out.println("[HubSwap] Переход успешно завершён на " + mode + " #" + targetNumber);
        reset();
    }

    private static void sendError(String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[HubSwap] Ошибка: " + msg), false);
        }
        reset();
    }

    private static void reset() {
        state = State.IDLE;
        mode = "lite";
        targetNumber = -1;
        categoryKey = null;
        serverKey = null;
        prevWorld = null;
        ticks = 0;
        arrivalTicks = 0;
    }

    public static boolean isBusy() {
        return state != State.IDLE;
    }
}