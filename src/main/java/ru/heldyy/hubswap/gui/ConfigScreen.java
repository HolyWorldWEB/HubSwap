package ru.heldyy.hubswap.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.heldyy.hubswap.HubSwap;
import ru.heldyy.hubswap.client.HubSwapClient;
import ru.heldyy.hubswap.config.HotkeySlot;
import ru.heldyy.hubswap.config.ModConfig;
import ru.heldyy.hubswap.config.StatsData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigScreen extends Screen {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final Screen parent;
    private final ModConfig config;

    private enum Tab { GENERAL, MODES, RANGES, HOTKEYS, STATS }
    private Tab currentTab = Tab.GENERAL;
    private ButtonWidget tabGeneral, tabModes, tabRanges, tabHotkeys, tabStats;

    // Общие настройки
    private boolean notificationsEnabledTmp;
    private ModConfig.ColorTheme currentTheme;
    private Formatting currentLinkColor;
    private int timeoutTicksTmp;
    private TextFieldWidget timeoutField;

    // Настройки режимов (алиасы)
    private TextFieldWidget liteAliasesField;
    private TextFieldWidget lite120AliasesField;
    private TextFieldWidget classicAliasesField;
    private TextFieldWidget primeAliasesField;

    // Настройки диапазонов
    private TextFieldWidget liteTotalField;
    private TextFieldWidget liteSoloMin, liteSoloMax;
    private TextFieldWidget liteDuoMin, liteDuoMax;
    private TextFieldWidget liteTrioMin, liteTrioMax;
    private TextFieldWidget liteClanMin, liteClanMax;
    private TextFieldWidget lite120TotalField;
    private TextFieldWidget classicTotalField;
    private TextFieldWidget primeTotalField;

    // Хоткеи
    private List<HotkeySlot> hotkeyTmp;
    private int listeningSlot = -1;
    private final List<ButtonWidget> hotkeyKeyBtns = new ArrayList<>();
    private final List<ButtonWidget> hotkeyModeBtns = new ArrayList<>();
    private final List<TextFieldWidget> hotkeyNumFields = new ArrayList<>();
    private final List<ButtonWidget> hotkeyToggleBtns = new ArrayList<>();

    // Кнопки
    private ButtonWidget notificationsToggleButton;
    private ButtonWidget themeToggleButton;
    private ButtonWidget linkColorToggleButton;
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;

    // Анимация
    private float backgroundAlpha = 0.0f;
    private float contentOffset = 20.0f;

    // Layout
    private int margin, panelW, lx, rx, colW, contentY, footerY;

    // Группы виджетов
    private List<WidgetGroup> groups = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Text.literal("Настройки HubSwap"));
        this.parent = parent;
        this.config = HubSwap.getConfig();
        this.currentTheme = config.getColorTheme();
        this.currentLinkColor = config.getLinkColor();
        this.notificationsEnabledTmp = config.isNotificationsEnabled();
        this.timeoutTicksTmp = config.getTimeoutTicks();
        this.hotkeyTmp = new ArrayList<>();
        for (HotkeySlot s : config.getHotkeySlots())
            hotkeyTmp.add(new HotkeySlot(s.getKeyCode(), s.getMode(), s.getServerNumber(), s.isEnabled()));
    }

    private void recalcLayout() {
        margin   = Math.max(12, this.width / 14);
        panelW   = this.width - margin * 2;
        lx       = margin;
        rx       = this.width / 2 + 6;
        colW     = this.width / 2 - margin - 6;
        contentY = 100;
        footerY  = this.height - 50;
    }

    @Override
    protected void init() {
        recalcLayout();
        buildAllWidgets();
        showTab(Tab.GENERAL);
    }

    private void buildAllWidgets() {
        clearChildren();
        hotkeyKeyBtns.clear();
        hotkeyModeBtns.clear();
        hotkeyNumFields.clear();
        hotkeyToggleBtns.clear();
        groups.clear();

        buildPersistentWidgets();

        // ---- Вкладка GENERAL ----
        {
            List<ClickableWidget> widgets = new ArrayList<>();
            int sp = Math.min(52, (footerY - contentY) / 4);
            int fh = 20;
            timeoutField = addField(lx, contentY + 0, colW, fh, String.valueOf(timeoutTicksTmp), 4);
            widgets.add(timeoutField);

            notificationsToggleButton = addDrawableChild(ButtonWidget.builder(getNotificationButtonText(),
                            btn -> { notificationsEnabledTmp = !notificationsEnabledTmp; btn.setMessage(getNotificationButtonText()); })
                    .dimensions(rx, contentY + 0, colW, fh).build());
            widgets.add(notificationsToggleButton);

            themeToggleButton = addDrawableChild(ButtonWidget.builder(getThemeButtonText(),
                            btn -> { currentTheme = currentTheme.next(); btn.setMessage(getThemeButtonText()); })
                    .dimensions(rx, contentY + sp * 1, colW, fh).build());
            widgets.add(themeToggleButton);

            linkColorToggleButton = addDrawableChild(ButtonWidget.builder(getLinkColorButtonText(),
                            btn -> { currentLinkColor = nextLinkColor(currentLinkColor); btn.setMessage(getLinkColorButtonText()); })
                    .dimensions(rx, contentY + sp * 2, colW, fh).build());
            widgets.add(linkColorToggleButton);

            groups.add(new WidgetGroup(widgets));
        }

        // ---- Вкладка MODES ----
        {
            List<ClickableWidget> widgets = new ArrayList<>();
            int sp = Math.min(40, (footerY - contentY) / 6);
            int fh = 20;

            ButtonWidget label1 = addStaticLabel(contentY + 0 - 22, "Lite алиасы (через запятую):", lx);
            widgets.add(label1);
            liteAliasesField = addField(lx, contentY + 0, panelW, fh, String.join(", ", config.getLite().getAliases()), 100);
            widgets.add(liteAliasesField);

            ButtonWidget label2 = addStaticLabel(contentY + sp * 1 + 4 - 22, "Lite120 алиасы:", lx);
            widgets.add(label2);
            lite120AliasesField = addField(lx, contentY + sp * 1 + 4, panelW, fh, String.join(", ", config.getLite120().getAliases()), 100);
            widgets.add(lite120AliasesField);

            ButtonWidget label3 = addStaticLabel(contentY + sp * 2 + 8 - 22, "Classic алиасы:", lx);
            widgets.add(label3);
            classicAliasesField = addField(lx, contentY + sp * 2 + 8, panelW, fh, String.join(", ", config.getClassic().getAliases()), 100);
            widgets.add(classicAliasesField);

            ButtonWidget label4 = addStaticLabel(contentY + sp * 3 + 12 - 22, "Prime алиасы:", lx);
            widgets.add(label4);
            primeAliasesField = addField(lx, contentY + sp * 3 + 12, panelW, fh, String.join(", ", config.getPrime().getAliases()), 100);
            widgets.add(primeAliasesField);

            groups.add(new WidgetGroup(widgets));
        }

        // ---- Вкладка RANGES ----
        {
            List<ClickableWidget> widgets = new ArrayList<>();
            int fh = 20;
            int y = contentY;
            int labelX = lx;
            int fieldX = lx + 220;
            int fieldWidth = 60;
            int gapBetweenMinMax = 20;

            ButtonWidget labelLiteTotal = addStaticLabel(y, "Lite всего:", labelX);
            widgets.add(labelLiteTotal);
            liteTotalField = addField(fieldX, y, fieldWidth, fh, String.valueOf(config.getLite().getRanges().getTotal()), 3);
            widgets.add(liteTotalField);
            y += fh + 16;

            ButtonWidget hdrRange = addStaticLabel(y, "Диапазон", labelX);
            widgets.add(hdrRange);
            ButtonWidget hdrMin = addStaticLabel(y, "Min", fieldX - 90);
            widgets.add(hdrMin);
            ButtonWidget hdrMax = addStaticLabel(y, "Max", fieldX + fieldWidth + gapBetweenMinMax - 90);
            widgets.add(hdrMax);
            y += 18;

            List<ModConfig.RangeEntry> entries = config.getLite().getRanges().getEntries();
            String[] keys = {"solo", "duo", "trio", "clans"};
            String[] names = {"Solo", "Duo", "Trio", "Clan"};

            TextFieldWidget[] minFields = new TextFieldWidget[4];
            TextFieldWidget[] maxFields = new TextFieldWidget[4];

            for (int i = 0; i < 4; i++) {
                ModConfig.RangeEntry entry = null;
                for (ModConfig.RangeEntry e : entries) {
                    if (e.key.equals(keys[i])) {
                        entry = e;
                        break;
                    }
                }
                if (entry == null) entry = new ModConfig.RangeEntry(keys[i], names[i], 1, 1);

                int rowY = y + i * (fh + 4);

                ButtonWidget labelName = addStaticLabel(rowY, names[i], labelX);
                widgets.add(labelName);
                TextFieldWidget minF = addField(fieldX, rowY, fieldWidth, fh, String.valueOf(entry.min), 3);
                widgets.add(minF);
                TextFieldWidget maxF = addField(fieldX + fieldWidth + gapBetweenMinMax, rowY, fieldWidth, fh, String.valueOf(entry.max), 3);
                widgets.add(maxF);

                minFields[i] = minF;
                maxFields[i] = maxF;
            }

            liteSoloMin = minFields[0]; liteSoloMax = maxFields[0];
            liteDuoMin  = minFields[1]; liteDuoMax  = maxFields[1];
            liteTrioMin = minFields[2]; liteTrioMax = maxFields[2];
            liteClanMin = minFields[3]; liteClanMax = maxFields[3];

            y += 4 * (fh + 4) + 20;

            ButtonWidget lblLite120 = addStaticLabel(y, "Lite 1.20 всего:", labelX);
            widgets.add(lblLite120);
            lite120TotalField = addField(fieldX, y, fieldWidth, fh, String.valueOf(config.getLite120().getRanges().getTotal()), 3);
            widgets.add(lite120TotalField);
            y += fh + 16;

            ButtonWidget lblClassic = addStaticLabel(y, "Classic всего:", labelX);
            widgets.add(lblClassic);
            classicTotalField = addField(fieldX, y, fieldWidth, fh, String.valueOf(config.getClassic().getRanges().getTotal()), 3);
            widgets.add(classicTotalField);
            y += fh + 16;

            ButtonWidget lblPrime = addStaticLabel(y, "Prime всего:", labelX);
            widgets.add(lblPrime);
            primeTotalField = addField(fieldX, y, fieldWidth, fh, String.valueOf(config.getPrime().getRanges().getTotal()), 3);
            widgets.add(primeTotalField);
            y += fh + 16;

            groups.add(new WidgetGroup(widgets));
        }

        // ---- Вкладка HOTKEYS ----
        {
            List<ClickableWidget> widgets = new ArrayList<>();
            int rowH   = Math.min(20, (footerY - contentY - 30) / 9);
            int cardH  = rowH + 2;
            int startY = contentY + 24;

            int totalW = panelW;
            int keyW  = (int)(totalW * 0.25);
            int modeW = (int)(totalW * 0.25);
            int numW  = (int)(totalW * 0.12);
            int togW  = (int)(totalW * 0.13);
            int gap   = (totalW - keyW - modeW - numW - togW) / 3;

            int kx = lx;
            int mx = kx + keyW + gap;
            int nx = mx + modeW + gap;
            int tx = nx + numW + gap;

            for (int i = 0; i < 8; i++) {
                HotkeySlot slot = hotkeyTmp.get(i);
                int y = startY + cardH * i;
                final int idx = i;

                ButtonWidget keyBtn = addDrawableChild(ButtonWidget.builder(
                                Text.literal(slot.getKeyCode() < 0 ? "[ --- ]" : "[ " + getKeyName(slot.getKeyCode()) + " ]"),
                                btn -> { listeningSlot = idx; btn.setMessage(Text.literal("[ нажми... ]")); })
                        .dimensions(kx, y, keyW, rowH).build());
                widgets.add(keyBtn);
                hotkeyKeyBtns.add(keyBtn);

                ButtonWidget modeBtn = addDrawableChild(ButtonWidget.builder(
                                Text.literal(getModeDisplayName(slot.getMode())),
                                btn -> {
                                    String next = nextMode(hotkeyTmp.get(idx).getMode());
                                    hotkeyTmp.get(idx).setMode(next);
                                    btn.setMessage(Text.literal(getModeDisplayName(next)));
                                })
                        .dimensions(mx, y, modeW, rowH).build());
                widgets.add(modeBtn);
                hotkeyModeBtns.add(modeBtn);

                TextFieldWidget numField = new TextFieldWidget(textRenderer, nx, y, numW, rowH, Text.literal(""));
                numField.setText(String.valueOf(slot.getServerNumber()));
                numField.setMaxLength(3);
                addDrawableChild(numField);
                widgets.add(numField);
                hotkeyNumFields.add(numField);

                ButtonWidget toggleBtn = addDrawableChild(ButtonWidget.builder(slotToggleText(slot.isEnabled()),
                                btn -> {
                                    boolean cur = hotkeyTmp.get(idx).isEnabled();
                                    hotkeyTmp.get(idx).setEnabled(!cur);
                                    btn.setMessage(slotToggleText(!cur));
                                })
                        .dimensions(tx, y, togW, rowH).build());
                widgets.add(toggleBtn);
                hotkeyToggleBtns.add(toggleBtn);
            }

            groups.add(new WidgetGroup(widgets));
        }

        // ---- Вкладка STATS ----
        groups.add(new WidgetGroup(new ArrayList<>()));

        for (int i = 0; i < groups.size(); i++) {
            groups.get(i).setVisible(i == 0);
        }
    }

    private void buildPersistentWidgets() {
        int cx   = this.width / 2;
        int tabY = 36;
        int tabW = Math.min(80, panelW / 5 - 4);

        tabGeneral = addDrawableChild(ButtonWidget.builder(Text.literal("⚙ Основные"), b -> switchTab(Tab.GENERAL))
                .dimensions(cx - tabW * 2 - 6 - 2, tabY, tabW, 18).build());
        tabModes   = addDrawableChild(ButtonWidget.builder(Text.literal("📌 Режимы"),   b -> switchTab(Tab.MODES))
                .dimensions(cx - tabW - 3,          tabY, tabW, 18).build());
        tabRanges  = addDrawableChild(ButtonWidget.builder(Text.literal("📊 Диапазоны"), b -> switchTab(Tab.RANGES))
                .dimensions(cx + 3,                  tabY, tabW, 18).build());
        tabHotkeys = addDrawableChild(ButtonWidget.builder(Text.literal("⌨ Хоткеи"),    b -> switchTab(Tab.HOTKEYS))
                .dimensions(cx + tabW + 6,           tabY, tabW, 18).build());
        tabStats   = addDrawableChild(ButtonWidget.builder(Text.literal("📊 Стат"),     b -> switchTab(Tab.STATS))
                .dimensions(cx + tabW * 2 + 9,       tabY, tabW, 18).build());

        int btnW = Math.min(150, panelW / 3);
        saveButton   = addDrawableChild(ButtonWidget.builder(Text.literal("✓ Сохранить"), btn -> onSave())
                .dimensions(cx - btnW - 4, footerY + 10, btnW, 20).build());
        cancelButton = addDrawableChild(ButtonWidget.builder(Text.literal("✕ Отмена"), btn -> close())
                .dimensions(cx + 4, footerY + 10, btnW, 20).build());
    }

    private ButtonWidget addStaticLabel(int y, String text, int x) {
        ButtonWidget label = ButtonWidget.builder(Text.literal(text), b -> {})
                .dimensions(x, y, 200, 14)
                .build();
        label.active = false;
        return addDrawableChild(label);
    }

    private void showTab(Tab tab) {
        for (WidgetGroup group : groups) {
            group.setVisible(false);
        }
        int idx = tab.ordinal();
        if (idx >= 0 && idx < groups.size()) {
            groups.get(idx).setVisible(true);
        }

        if (tab == Tab.STATS) {
            HubSwap.reloadStats();
        }

        currentTab = tab;
    }

    private void switchTab(Tab tab) {
        showTab(tab);
        listeningSlot = -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        backgroundAlpha = Math.min(1.0f, backgroundAlpha + delta * 2.0f);
        contentOffset   = Math.max(0.0f, contentOffset   - delta * 60.0f);

        renderGradientBackground(context);
        renderHeaderPanel(context);
        renderFooterPanel(context);

        super.render(context, mouseX, mouseY, delta);

        if (currentTab == Tab.STATS) {
            renderStatsTab(context);
        }

        renderActiveTabUnderline(context);
    }

    // ---- Статистика (взято из нового ConfigScreen и адаптировано) ----
    private void renderStatsTab(DrawContext context) {
        StatsData stats = HubSwap.getStats();
        if (stats == null) return;

        int themeRgb = currentTheme.getRgbColor();
        int y = contentY;

        renderSectionHeader(context, lx, y, panelW, "📊 Переходы", themeRgb);
        y += 16;
        context.drawText(textRenderer, Text.literal("Всего: " + stats.getTotalSwitches()), lx + 8, y, textArgb(0xFFFFFF), true);
        context.drawText(textRenderer, Text.literal("За сессию: " + stats.getSessionSwitches()), rx, y, textArgb(0xFFFFFF), true);
        y += 22;

        renderSectionHeader(context, lx, y, panelW, "🏆 Любимый сервер", themeRgb);
        y += 16;
        String fav = stats.getFavoriteKey();
        if (fav != null) {
            context.drawText(textRenderer,
                    Text.literal(StatsData.formatKey(fav) + "  —  " + stats.getCountForKey(fav) + " раз").formatted(currentTheme.getFormatting()),
                    lx + 8, y, textArgb(themeRgb), true);
        } else {
            context.drawText(textRenderer, Text.literal("Пока нет данных"), lx + 8, y, textArgb(0x666666), false);
        }
        y += 22;

        renderSectionHeader(context, lx, y, panelW, "📋 Топ серверов", themeRgb);
        y += 16;
        List<Map.Entry<String, Long>> sorted = stats.getServerCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).toList();
        if (sorted.isEmpty()) {
            context.drawText(textRenderer, Text.literal("Пока нет данных"), lx + 8, y, textArgb(0x666666), false);
            y += 14;
        } else {
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Long> e = sorted.get(i);
                String medal = switch (i) { case 0 -> "🥇"; case 1 -> "🥈"; case 2 -> "🥉"; default -> (i+1)+"."; };
                context.drawText(textRenderer,
                        Text.literal(medal + " " + StatsData.formatKey(e.getKey()) + " — " + e.getValue() + " раз")
                                .formatted(i == 0 ? currentTheme.getFormatting() : Formatting.WHITE),
                        lx + 8, y + i * 14, textArgb(i == 0 ? themeRgb : 0xCCCCCC), i == 0);
            }
            y += sorted.size() * 14 + 8;
        }

        renderSectionHeader(context, lx, y, panelW, "⏱ Время на серверах", themeRgb);
        y += 16;
        // Используем ключи, соответствующие вашим данным
        String[][] rows = {
                {"Lite", "lite"},
                {"Lite 1.20", "lite120"},
                {"Classic", "classic"},
                {"Prime", "prime"}
        };
        long maxMs = 200L * 60 * 60 * 1000; // 200 часов
        int barX = lx + 140;
        int barW = this.width - margin - barX - 4;
        for (int i = 0; i < rows.length; i++) {
            int rowY = y + i * 24;
            long ms = stats.getTimeSpentMs(rows[i][1]);
            context.drawText(textRenderer, Text.literal(rows[i][0]), lx + 8, rowY + 4, textArgb(0xFFFFFF), false);
            context.drawText(textRenderer,
                    Text.literal(StatsData.formatTime(ms)).formatted(currentTheme.getFormatting()),
                    lx + 68, rowY + 4, textArgb(themeRgb), true);
            float ratio = (float) ms / maxMs;
            if (ratio > 1.0f) ratio = 1.0f;
            int filled = (int)(barW * ratio);
            int bgA = (int)(backgroundAlpha * 120);
            context.fill(barX, rowY + 2, barX + barW, rowY + 12, bgA << 24 | 0x1a1f3a);
            if (filled > 0)
                context.fill(barX, rowY + 2, barX + filled, rowY + 12, (int)(backgroundAlpha * 200) << 24 | themeRgb);
        }
    }

    private void renderSectionHeader(DrawContext context, int x, int y, int width, String title, int themeRgb) {
        int alpha = (int)(backgroundAlpha * 160);
        context.fill(x, y, x + width, y + 13, alpha << 24 | 0x16213e);
        context.fill(x, y, x + 3, y + 13, (int)(backgroundAlpha * 255) << 24 | themeRgb);
        context.drawText(textRenderer, Text.literal(title).formatted(currentTheme.getFormatting()), x + 7, y + 3, textArgb(themeRgb), false);
    }

    // ---- Вспомогательный метод для цвета с прозрачностью ----
    private int textArgb(int rgb) {
        int alpha = (int)(backgroundAlpha * 255.0f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    // ---- Остальные методы (рендеринг фона, заголовков, сохранение и т.д.) ----
    private void renderGradientBackground(DrawContext context) {
        context.fillGradient(0, 0, this.width, this.height,
                ((int)(backgroundAlpha * 200) << 24) | 0x0a0e27,
                ((int)(backgroundAlpha * 220) << 24) | 0x1a1f3a);
    }

    private void renderHeaderPanel(DrawContext context) {
        int panelH = 60;
        int alpha  = (int)(backgroundAlpha * 180);
        context.fillGradient(0, 0, this.width, panelH, alpha << 24 | 0x16213e, alpha << 24 | 0x0f1728);
        context.fill(0, panelH - 2, this.width, panelH, alpha << 24 | currentTheme.getRgbColor());
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("HubSwap").formatted(currentTheme.getFormatting(), Formatting.BOLD),
                this.width / 2, 17, 0xFFFFFF);
    }

    private void renderFooterPanel(DrawContext context) {
        int alpha = (int)(backgroundAlpha * 200);
        context.fill(0, footerY, this.width, footerY + 2, alpha << 24 | currentTheme.getRgbColor());
        context.fill(0, footerY + 2, this.width, this.height, (int)(backgroundAlpha * 160) << 24 | 0x0a0e27);
    }

    private void renderActiveTabUnderline(DrawContext context) {
        ButtonWidget btn;
        switch (currentTab) {
            case GENERAL -> btn = tabGeneral;
            case MODES -> btn = tabModes;
            case RANGES -> btn = tabRanges;
            case HOTKEYS -> btn = tabHotkeys;
            case STATS -> btn = tabStats;
            default -> btn = null;
        }
        if (btn == null) return;
        int alpha = (int)(backgroundAlpha * 240);
        int color = alpha << 24 | currentTheme.getRgbColor();
        context.fill(btn.getX(), btn.getY() + btn.getHeight() - 2,
                btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), color);
    }

    // ---- Сохранение ----
    private void onSave() {
        try {
            timeoutTicksTmp = Integer.parseInt(timeoutField.getText().trim());
            config.setTimeoutTicks(timeoutTicksTmp);
        } catch (NumberFormatException ignored) {}

        config.setNotificationsEnabled(notificationsEnabledTmp);
        config.setColorTheme(currentTheme);
        config.setLinkColor(currentLinkColor);

        config.getLite().setAliases(parseAliases(liteAliasesField.getText()));
        config.getLite120().setAliases(parseAliases(lite120AliasesField.getText()));
        config.getClassic().setAliases(parseAliases(classicAliasesField.getText()));
        config.getPrime().setAliases(parseAliases(primeAliasesField.getText()));

        try {
            int total = Integer.parseInt(liteTotalField.getText().trim());
            config.getLite().getRanges().setTotal(total);
        } catch (NumberFormatException ignored) {}

        List<ModConfig.RangeEntry> newEntries = new ArrayList<>();
        try {
            int min = Integer.parseInt(liteSoloMin.getText().trim());
            int max = Integer.parseInt(liteSoloMax.getText().trim());
            newEntries.add(new ModConfig.RangeEntry("solo", "Соло", min, max));
        } catch (NumberFormatException ignored) {}
        try {
            int min = Integer.parseInt(liteDuoMin.getText().trim());
            int max = Integer.parseInt(liteDuoMax.getText().trim());
            newEntries.add(new ModConfig.RangeEntry("duo", "Дуо", min, max));
        } catch (NumberFormatException ignored) {}
        try {
            int min = Integer.parseInt(liteTrioMin.getText().trim());
            int max = Integer.parseInt(liteTrioMax.getText().trim());
            newEntries.add(new ModConfig.RangeEntry("trio", "Трио", min, max));
        } catch (NumberFormatException ignored) {}
        try {
            int min = Integer.parseInt(liteClanMin.getText().trim());
            int max = Integer.parseInt(liteClanMax.getText().trim());
            newEntries.add(new ModConfig.RangeEntry("clans", "Кланы", min, max));
        } catch (NumberFormatException ignored) {}

        if (!newEntries.isEmpty()) {
            config.getLite().getRanges().setEntries(newEntries);
        }

        try {
            int total = Integer.parseInt(lite120TotalField.getText().trim());
            config.getLite120().getRanges().setTotal(total);
        } catch (NumberFormatException ignored) {}

        try {
            int total = Integer.parseInt(classicTotalField.getText().trim());
            config.getClassic().getRanges().setTotal(total);
        } catch (NumberFormatException ignored) {}

        try {
            int total = Integer.parseInt(primeTotalField.getText().trim());
            config.getPrime().getRanges().setTotal(total);
        } catch (NumberFormatException ignored) {}

        for (int i = 0; i < 8 && i < hotkeyNumFields.size(); i++) {
            try {
                int num = Integer.parseInt(hotkeyNumFields.get(i).getText().trim());
                hotkeyTmp.get(i).setServerNumber(Math.max(1, num));
            } catch (NumberFormatException ignored) {}
        }
        List<HotkeySlot> dst = config.getHotkeySlots();
        for (int i = 0; i < 8; i++) {
            HotkeySlot s = hotkeyTmp.get(i), d = dst.get(i);
            d.setKeyCode(s.getKeyCode());
            d.setMode(s.getMode());
            d.setServerNumber(s.getServerNumber());
            d.setEnabled(s.isEnabled());
        }

        HubSwap.saveConfig();
        HubSwap.reloadConfig();
        HubSwapClient.registerConfiguredCommands();

        if (client != null && client.player != null) {
            client.player.sendMessage(
                    Text.literal("[HubSwap] ").formatted(currentTheme.getFormatting())
                            .append(Text.literal("✓ Настройки сохранены!").formatted(Formatting.GREEN)), false);
            System.out.println("[HubSwap] Config saved successfully.");
        }
        close();
    }

    private List<String> parseAliases(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ---- Хелперы для хоткеев ----
    private String getModeDisplayName(String mode) {
        return switch (mode) {
            case "lite" -> "Lite";
            case "lite120" -> "Lite120";
            case "classic" -> "Classic";
            case "prime" -> "Prime";
            default -> mode;
        };
    }

    private String nextMode(String mode) {
        return switch (mode) {
            case "lite" -> "lite120";
            case "lite120" -> "classic";
            case "classic" -> "prime";
            case "prime" -> "lite";
            default -> "lite";
        };
    }

    private String getKeyName(int keyCode) {
        if (keyCode >= 65 && keyCode <= 90) return String.valueOf((char) keyCode);
        if (keyCode >= 48 && keyCode <= 57) return String.valueOf((char) keyCode);
        return "Key" + keyCode;
    }

    private Text slotToggleText(boolean on) {
        return on ? Text.literal("✓ Вкл").formatted(Formatting.GREEN)
                : Text.literal("✗ Выкл").formatted(Formatting.RED);
    }

    private Text getNotificationButtonText() {
        return notificationsEnabledTmp
                ? Text.literal("🔔 Уведомления: ВКЛ").formatted(Formatting.GREEN)
                : Text.literal("🔕 Уведомления: ВЫКЛ").formatted(Formatting.RED);
    }

    private Text getThemeButtonText() {
        return Text.literal("🎨 Тема: " + currentTheme.getDisplayName()).formatted(currentTheme.getFormatting());
    }

    private Text getLinkColorButtonText() {
        return Text.literal("🔗 Цвет ссылок: " + getLinkColorName(currentLinkColor)).formatted(currentLinkColor);
    }

    private String getLinkColorName(Formatting color) {
        if (color == Formatting.GOLD)         return "Золотой";
        if (color == Formatting.GREEN)        return "Зелёный";
        if (color == Formatting.YELLOW)       return "Жёлтый";
        if (color == Formatting.AQUA)         return "Синий";
        if (color == Formatting.LIGHT_PURPLE) return "Фиолетовый";
        if (color == Formatting.RED)          return "Красный";
        return "Золотой";
    }

    private Formatting nextLinkColor(Formatting current) {
        Formatting[] colors = { Formatting.GOLD, Formatting.GREEN, Formatting.YELLOW,
                Formatting.AQUA, Formatting.LIGHT_PURPLE, Formatting.RED };
        for (int i = 0; i < colors.length; i++)
            if (colors[i] == current) return colors[(i + 1) % colors.length];
        return Formatting.GOLD;
    }

    private TextFieldWidget addField(int x, int y, int w, int h, String text, int maxLen) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, h, Text.literal(""));
        f.setText(text);
        f.setMaxLength(maxLen);
        return addDrawableChild(f);
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (listeningSlot >= 0) {
            int keyCode = key.key();
            if (keyCode == 256) {
                hotkeyTmp.get(listeningSlot).setKeyCode(-1);
                hotkeyKeyBtns.get(listeningSlot).setMessage(Text.literal("[ --- ]"));
            } else {
                hotkeyTmp.get(listeningSlot).setKeyCode(keyCode);
                hotkeyKeyBtns.get(listeningSlot).setMessage(Text.literal("[ " + getKeyName(keyCode) + " ]"));
            }
            listeningSlot = -1;
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private static class WidgetGroup {
        private final List<ClickableWidget> widgets;

        WidgetGroup(List<ClickableWidget> widgets) {
            this.widgets = widgets;
        }

        void setVisible(boolean visible) {
            for (ClickableWidget w : widgets) {
                w.visible = visible;
            }
        }
    }
}