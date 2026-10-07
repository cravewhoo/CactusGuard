package dev.cactusguard.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Colours and tiny text helpers. Bright green, minimal. */
public final class Theme {

    public static final TextColor GREEN = TextColor.color(0x39FF14);
    public static final TextColor LIME = TextColor.color(0xA8FF78);
    public static final TextColor GRAY = NamedTextColor.GRAY;
    public static final TextColor DARK = NamedTextColor.DARK_GRAY;
    public static final TextColor RED = TextColor.color(0xFF5555);

    public static final int EMBED_GREEN = 0x39FF14;
    public static final int EMBED_RED = 0xFF5555;
    public static final int EMBED_YELLOW = 0xFFD93D;

    private Theme() {}

    /** Item/GUI text: no italics, optional bold. */
    public static Component item(String text, TextColor color, boolean bold) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, bold);
    }

    public static Component item(String text, TextColor color) { return item(text, color, false); }
}
