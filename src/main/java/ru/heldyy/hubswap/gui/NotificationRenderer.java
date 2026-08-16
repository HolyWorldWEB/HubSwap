package ru.heldyy.hubswap.gui;

import ru.heldyy.hubswap.HubSwap;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class NotificationRenderer {
    private static Text message = null;
    private static long showTime = 0L;
    private static final long TOTAL_DURATION = 4000L;
    private static final long FADE_IN = 400L;
    private static final long FADE_OUT = 400L;
    private static final float BASE_Y = 25.0f;
    private static final AnimationState anim = new AnimationState();

    public static void showNotification(String msg) {
        if (!HubSwap.getConfig().isNotificationsEnabled()) return;
        message = Text.literal(msg);
        showTime = System.currentTimeMillis();
        anim.alpha = 0.0f;
        anim.offsetY = -20.0f;
        anim.scale = 0.9f;
        anim.lastUpdate = showTime;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!HubSwap.getConfig().isNotificationsEnabled()) return;
            if (message == null || MinecraftClient.getInstance().world == null) return;

            long now = System.currentTimeMillis();
            long elapsed = now - showTime;
            if (elapsed > TOTAL_DURATION) {
                message = null;
                return;
            }

            float targetAlpha;
            float targetScale;
            if (elapsed < FADE_IN) {
                float progress = (float) elapsed / (float) FADE_IN;
                targetAlpha = easeOutQuart(progress);
                targetScale = 0.9f + (0.1f * easeOutBack(progress));
            } else if (elapsed > (TOTAL_DURATION - FADE_OUT)) {
                float progress = 1.0f - (float) (elapsed - (TOTAL_DURATION - FADE_OUT)) / (float) FADE_OUT;
                targetAlpha = easeInQuart(progress);
                targetScale = 1.0f;
            } else {
                targetAlpha = 1.0f;
                targetScale = 1.0f;
            }

            float targetYOffset = 0.0f;
            float deltaTime = (float) (now - anim.lastUpdate) / 1000.0f;
            anim.lastUpdate = now;

            float smoothing = 8.0f * deltaTime;
            anim.alpha = smoothLerp(anim.alpha, targetAlpha, smoothing);
            anim.offsetY = smoothLerp(anim.offsetY, targetYOffset, smoothing);
            anim.scale = smoothLerp(anim.scale, targetScale, smoothing);

            MinecraftClient client = MinecraftClient.getInstance();
            int screenWidth = context.getScaledWindowWidth();
            int textWidth = client.textRenderer.getWidth(message);

            int panelWidth = textWidth + 20;
            int panelHeight = 16;

            int x = screenWidth - panelWidth - 10;
            int y = (int) (BASE_Y + anim.offsetY);

            // Матричные трансформации убраны для совместимости с 1.21.11
            // Вместо scale используем прямой рендеринг с параметрами

            int alphaInt = (int) (anim.alpha * 255.0f);

            int bgColorTop = (int) (anim.alpha * 180.0f) << 24 | 0x1a1a2e;
            int bgColorBottom = (int) (anim.alpha * 200.0f) << 24 | 0x16213e;
            context.fillGradient(x, y, x + panelWidth, y + panelHeight, bgColorTop, bgColorBottom);

            int borderColor = alphaInt << 24 | HubSwap.getConfig().getColorTheme().getRgbColor();
            context.fill(x - 1, y - 1, x + panelWidth + 1, y, borderColor);
            context.fill(x - 1, y + panelHeight, x + panelWidth + 1, y + panelHeight + 1, borderColor);
            context.fill(x - 1, y - 1, x, y + panelHeight + 1, borderColor);
            context.fill(x + panelWidth, y - 1, x + panelWidth + 1, y + panelHeight + 1, borderColor);

            int iconColor = alphaInt << 24 | HubSwap.getConfig().getColorTheme().getRgbColor();
            context.fill(x + 6, y + 5, x + 8, y + 11, iconColor);
            context.fill(x + 8, y + 6, x + 10, y + 10, iconColor);

            int textColor = alphaInt << 24 | 0xFFFFFF;
            context.drawText(client.textRenderer, message, x + 14, y + 4, textColor, true);
        });
    }

    private static float smoothLerp(float current, float target, float smoothing) {
        return current + (target - current) * Math.min(smoothing, 1.0f);
    }

    private static float easeOutQuart(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 4.0);
    }

    private static float easeInQuart(float t) {
        return (float) Math.pow(t, 4.0);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3.0) + c1 * (float) Math.pow(t - 1.0f, 2.0);
    }

    private static class AnimationState {
        float alpha = 0.0f;
        float offsetY = -20.0f;
        float scale = 0.9f;
        long lastUpdate = 0L;
    }
}