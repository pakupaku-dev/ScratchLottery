package jp.pakupaku.scratchlottery.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TextUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextUtil() {
    }

    public static Component miniMessage(String text) {
        return MINI_MESSAGE.deserialize(text == null ? "" : text);
    }

    public static Component miniMessage(String text, Map<String, ?> placeholders) {
        return miniMessage(applyPlaceholders(text, placeholders));
    }

    public static List<Component> miniMessageList(List<String> lines, Map<String, ?> placeholders) {
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(miniMessage(line, placeholders));
        }
        return components;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String applyPlaceholders(String text, Map<String, ?> placeholders) {
        String applied = text == null ? "" : text;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            applied = applied.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return applied;
    }
}
