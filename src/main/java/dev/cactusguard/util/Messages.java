package dev.cactusguard.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads messages.yml and renders entries with placeholders.
 * Missing keys fall back to the bundled default, so an old messages.yml never breaks the plugin.
 * Placeholder values are inserted as plain text (never parsed), so player input cannot inject formatting.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private FileConfiguration file;
    private FileConfiguration defaults;
    private String prefix = "";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        file = YamlConfiguration.loadConfiguration(f);
        var in = plugin.getResource("messages.yml");
        defaults = in == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        prefix = raw("prefix");
    }

    /** Raw MiniMessage string for a key. */
    public String raw(String key) {
        String s = file.getString(key);
        if (s == null) s = defaults.getString(key);
        return s == null ? key : s;
    }

    /** Message without prefix. Args are alternating name, value pairs: {@code get("x", "name", "Steve")}. */
    public Component get(String key, String... args) {
        return MM.deserialize(raw(key), resolvers(args));
    }

    /** Message with the plugin prefix. */
    public Component msg(String key, String... args) {
        return MM.deserialize(prefix + raw(key), resolvers(args));
    }

    /** Plain string with placeholders replaced (for GUI titles and item names). */
    public String text(String key, String... args) {
        String s = raw(key);
        for (int i = 0; i + 1 < args.length; i += 2) s = s.replace("<" + args[i] + ">", args[i + 1]);
        return s;
    }

    /** Prefixed free-form MiniMessage built by code (used for rare dynamic lines). */
    public Component prefixed(Component body) {
        return MM.deserialize(prefix).append(body);
    }

    private static TagResolver[] resolvers(String[] args) {
        int n = args.length / 2;
        TagResolver[] out = new TagResolver[n];
        for (int i = 0; i < n; i++) out[i] = Placeholder.unparsed(args[i * 2], args[i * 2 + 1]);
        return out;
    }
}
