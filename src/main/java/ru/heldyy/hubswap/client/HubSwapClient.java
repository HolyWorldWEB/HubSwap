package ru.heldyy.hubswap.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.heldyy.hubswap.HubSwap;
import ru.heldyy.hubswap.config.HotkeySlot;
import ru.heldyy.hubswap.config.ModConfig;
import ru.heldyy.hubswap.executor.AnarchyExecutor;
import ru.heldyy.hubswap.gui.ConfigScreen;
import ru.heldyy.hubswap.gui.NotificationRenderer;
import ru.heldyy.hubswap.gui.TransitionDetector;
import ru.heldyy.hubswap.updater.UpdateChecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HubSwapClient implements ClientModInitializer {
    private static KeyBinding configMenuKey;
    private static CommandDispatcher<FabricClientCommandSource> DISPATCHER;

    private static final Map<Integer, Boolean> hotkeyPressed = new HashMap<>();

    private static final Map<Character, Character> EN_TO_RU = new HashMap<>();

    static {
        EN_TO_RU.put('q', 'й'); EN_TO_RU.put('w', 'ц'); EN_TO_RU.put('e', 'у');
        EN_TO_RU.put('r', 'к'); EN_TO_RU.put('t', 'е'); EN_TO_RU.put('y', 'н');
        EN_TO_RU.put('u', 'г'); EN_TO_RU.put('i', 'ш'); EN_TO_RU.put('o', 'щ');
        EN_TO_RU.put('p', 'з'); EN_TO_RU.put('a', 'ф'); EN_TO_RU.put('s', 'ы');
        EN_TO_RU.put('d', 'в'); EN_TO_RU.put('f', 'а'); EN_TO_RU.put('g', 'п');
        EN_TO_RU.put('h', 'р'); EN_TO_RU.put('j', 'о'); EN_TO_RU.put('k', 'л');
        EN_TO_RU.put('l', 'д'); EN_TO_RU.put('z', 'я'); EN_TO_RU.put('x', 'ч');
        EN_TO_RU.put('c', 'с'); EN_TO_RU.put('v', 'м'); EN_TO_RU.put('b', 'и');
        EN_TO_RU.put('n', 'т'); EN_TO_RU.put('m', 'ь');
    }

    @Override
    public void onInitializeClient() {
        HubSwap.init();

        registerKeybinds();
        registerCommands();
        registerTickHandler();
        registerLifecycleEvents();
        NotificationRenderer.register();
    }

    private void registerKeybinds() {
        configMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hubswap.config",
                295,
                "category.hubswap.main"
        ));
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            DISPATCHER = dispatcher;
            registerConfiguredCommands();
        });
    }

    public static void registerConfiguredCommands() {
        if (DISPATCHER == null) return;

        ModConfig config = HubSwap.getConfig();

        // Основные жёсткие команды
        registerLiteral("ln", "lite", true);
        registerLiteral("ln120", "lite120", true);
        registerLiteral("cn", "classic", true);
        registerLiteral("pm", "prime", true);

        // Алиасы с top/down для каждого алиаса отдельно
        registerAliases(config.getLite().getAliases(), "lite");
        registerAliases(config.getLite120().getAliases(), "lite120");
        registerAliases(config.getClassic().getAliases(), "classic");
        registerAliases(config.getPrime().getAliases(), "prime");
    }

    private static void registerAliases(List<String> aliases, String mode) {
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) continue;
            String trimmed = alias.trim();
            registerLiteral(trimmed, mode, true);
            String ru = toRussianLayout(trimmed);
            if (!ru.equals(trimmed)) {
                registerLiteral(ru, mode, true);
            }
        }
    }

    private static void registerLiteral(String literal, String mode, boolean enableTopDown) {
        if (literal == null || literal.isBlank() || DISPATCHER == null) return;
        if (DISPATCHER.getRoot().getChild(literal) != null) {
            System.out.println("[HubSwap] Alias conflict: /" + literal + " already registered. Skipping.");
            return;
        }

        ModConfig config = HubSwap.getConfig();
        int max = config.getMode(mode).getRanges().getMax();

        var cmd = ClientCommandManager.literal(literal)
                .then(ClientCommandManager.argument("number", IntegerArgumentType.integer(1, max))
                        .executes(ctx -> {
                            int number = IntegerArgumentType.getInteger(ctx, "number");
                            AnarchyExecutor.start(mode, number);
                            return 1;
                        }));

        if (enableTopDown) {
            // top
            cmd.then(ClientCommandManager.literal("top")
                    .then(ClientCommandManager.argument("step", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int step = IntegerArgumentType.getInteger(ctx, "step");
                                AnarchyExecutor.top(mode, step);
                                return 1;
                            }))
                    .executes(ctx -> {
                        AnarchyExecutor.top(mode, 1);
                        return 1;
                    }));

            // down
            cmd.then(ClientCommandManager.literal("down")
                    .then(ClientCommandManager.argument("step", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int step = IntegerArgumentType.getInteger(ctx, "step");
                                AnarchyExecutor.down(mode, step);
                                return 1;
                            }))
                    .executes(ctx -> {
                        AnarchyExecutor.down(mode, 1);
                        return 1;
                    }));
        }

        cmd.executes(ctx -> {
            ctx.getSource().sendFeedback(Text.literal("Использование: /" + literal + " <номер>  или  /" + literal + " top [шаг]  или  /" + literal + " down [шаг]"));
            return 0;
        });

        DISPATCHER.register(cmd);
    }

    private static String toRussianLayout(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            char lower = Character.toLowerCase(ch);
            Character mapped = EN_TO_RU.get(lower);
            out.append(mapped != null ? mapped : ch);
        }
        return out.toString();
    }

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AnarchyExecutor.tick();

            if (configMenuKey.wasPressed()) {
                client.setScreen(new ConfigScreen(null));
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("[HubSwap] ").formatted(HubSwap.getConfig().getColorTheme().getFormatting())
                                    .append(Text.literal("Открыто меню настроек").formatted(Formatting.WHITE)),
                            false
                    );
                }
            }

            if (client.currentScreen == null && client.player != null) {
                List<HotkeySlot> slots = HubSwap.getConfig().getHotkeySlots();
                for (HotkeySlot slot : slots) {
                    if (!slot.isEnabled() || slot.getKeyCode() < 0) continue;
                    int code = slot.getKeyCode();
                    boolean nowDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), code);
                    boolean wasDown = hotkeyPressed.getOrDefault(code, false);

                    if (nowDown && !wasDown) {
                        AnarchyExecutor.start(slot.getMode(), slot.getServerNumber());
                    }
                    hotkeyPressed.put(code, nowDown);
                }
            }
        });
    }

    private void registerLifecycleEvents() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HubSwap.saveStats();
            HubSwap.stopAutoSave();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            UpdateChecker.checkAfterJoin();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TransitionDetector.onDisconnect();
        });
    }
}