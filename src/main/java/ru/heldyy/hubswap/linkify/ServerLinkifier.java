package ru.heldyy.hubswap.linkify;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.heldyy.hubswap.config.ModConfig;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerLinkifier {

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)" +
                    "(?<liteNew>(?:лайт|lite)\\s*#?\\s*(?<liteNum>\\d+))" +
                    "|" +
                    "(?<lite120New>(?:лайт\\s*1\\.20|лайт120|lite\\s*1\\.20|lite120)\\s*#?\\s*(?<lite120Num>\\d+))" +
                    "|" +
                    "(?<classicNew>(?:классик|classic|classik|clasik|clasic)\\s*#?\\s*(?<classicNum>\\d+))" +
                    "|" +
                    "(?<primeNew>(?:прайм|prime)\\s*#?\\s*(?<primeNum>\\d+))" +
                    "|" +
                    "(?<liteN>\\bLite-Anarchy-(?<liteNumOld>\\d+)\\b)" +
                    "|(?<liteShort>\\bLite-(?<liteShortNum>\\d+)\\b)" +
                    "|(?<lite120>\\b1-20L-(?<lite120NumOld>[1-3])\\b)" +
                    "|(?<l2a3>\\bl2anarchy3\\b)" +
                    "|(?<l2a2>\\bl2anarchy2\\b)" +
                    "|(?<l2a1>\\bl2anarchy\\b)" +
                    "|(?<lanN>\\blanarchy(?<lanNum>\\d+)\\b)" +
                    "|(?<lanBare>\\blanarchy\\b)" +
                    "|(?<clDash>(?<![a-zA-Z])Anarchy-(?<clDashNum>[1-5])\\b)" +
                    "|(?<clN>(?<![a-zA-Z])anarchy(?<clNum>[1-5])\\b)"
    );

    public static Text linkify(Text original, ModConfig cfg) {
        if (original == null || cfg == null) return original;

        String rawAll = original.getString();
        if (rawAll == null || rawAll.isEmpty()) return original;
        if (!PATTERN.matcher(rawAll).find()) return original;

        MutableText out = Text.empty();
        final boolean[] changed = new boolean[]{false};

        original.visit((style, part) -> {
            if (part == null || part.isEmpty()) return Optional.empty();
            boolean segmentChanged = appendLinkifiedPart(out, style, part, cfg);
            if (segmentChanged) changed[0] = true;
            return Optional.empty();
        }, Style.EMPTY);

        return changed[0] ? out : original;
    }

    private static boolean appendLinkifiedPart(MutableText out, Style baseStyle, String segment, ModConfig cfg) {
        Matcher m = PATTERN.matcher(segment);
        if (!m.find()) {
            out.append(Text.literal(segment).setStyle(baseStyle));
            return false;
        }

        m.reset();
        int last = 0;

        while (m.find()) {
            if (m.start() > last) {
                out.append(Text.literal(segment.substring(last, m.start())).setStyle(baseStyle));
            }

            String matchedText = segment.substring(m.start(), m.end());
            String mode = null;
            int serverNum = 1;

            if (m.group("liteNew") != null) {
                mode = "lite";
                serverNum = parseIntSafe(m.group("liteNum"), 1);
            } else if (m.group("lite120New") != null) {
                mode = "lite120";
                serverNum = parseIntSafe(m.group("lite120Num"), 1);
            } else if (m.group("classicNew") != null) {
                mode = "classic";
                serverNum = parseIntSafe(m.group("classicNum"), 1);
            } else if (m.group("primeNew") != null) {
                mode = "prime";
                serverNum = parseIntSafe(m.group("primeNum"), 1);
            } else if (m.group("liteN") != null) {
                mode = "lite";
                serverNum = parseIntSafe(m.group("liteNumOld"), 1);
            } else if (m.group("liteShort") != null) {
                mode = "lite";
                serverNum = parseIntSafe(m.group("liteShortNum"), 1);
            } else if (m.group("lite120") != null) {
                mode = "lite120";
                serverNum = parseIntSafe(m.group("lite120NumOld"), 1);
            } else if (m.group("l2a3") != null) {
                mode = "lite120";
                serverNum = 3;
            } else if (m.group("l2a2") != null) {
                mode = "lite120";
                serverNum = 2;
            } else if (m.group("l2a1") != null) {
                mode = "lite120";
                serverNum = 1;
            } else if (m.group("lanN") != null) {
                mode = "lite";
                serverNum = parseIntSafe(m.group("lanNum"), 1);
            } else if (m.group("lanBare") != null) {
                mode = "lite";
                serverNum = 1;
            } else if (m.group("clDash") != null) {
                mode = "classic";
                serverNum = parseIntSafe(m.group("clDashNum"), 1);
            } else if (m.group("clN") != null) {
                mode = "classic";
                serverNum = parseIntSafe(m.group("clNum"), 1);
            }

            if (mode == null) {
                out.append(Text.literal(matchedText).setStyle(baseStyle));
                last = m.end();
                continue;
            }

            String baseCmd = switch (mode) {
                case "classic" -> "cn";
                case "prime" -> "pm";
                case "lite120" -> "ln120";
                default -> "ln";
            };
            String command = "/" + baseCmd + " " + serverNum;

            Formatting linkColor = cfg.getLinkColor();
            Style linkStyle = Style.EMPTY
                    .withBold(baseStyle.isBold())
                    .withItalic(baseStyle.isItalic())
                    .withUnderline(true)
                    .withColor(linkColor)
                    .withClickEvent(new ClickEvent.RunCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Text.literal("Нажмите: ").formatted(Formatting.GRAY)
                                    .append(Text.literal(command).formatted(linkColor))
                    ));

            out.append(Text.literal(matchedText).setStyle(linkStyle));
            last = m.end();
        }

        if (last < segment.length()) {
            out.append(Text.literal(segment.substring(last)).setStyle(baseStyle));
        }

        return true;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }
}