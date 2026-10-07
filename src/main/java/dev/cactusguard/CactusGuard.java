package dev.cactusguard;

import dev.cactusguard.command.GuardCommand;
import dev.cactusguard.command.PunishCommands;
import dev.cactusguard.command.ReportCommand;
import dev.cactusguard.discord.DiscordBridge;
import dev.cactusguard.gui.ChatPrompt;
import dev.cactusguard.gui.MenuListener;
import dev.cactusguard.gui.Screens;
import dev.cactusguard.listener.PlayerListener;
import dev.cactusguard.storage.Database;
import dev.cactusguard.util.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** CactusGuard: punishments, reports, staff notes and a Discord log bridge, driven by a green GUI. */
public final class CactusGuard extends JavaPlugin {

    private Database db;
    private DiscordBridge discord;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);

        try {
            db = new Database(new File(getDataFolder(), "cactusguard.db"));
        } catch (SQLException e) {
            getLogger().severe("Could not open the database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        discord = new DiscordBridge(getLogger());
        applyDiscordConfig();

        Services services = new Services(this, db, discord, messages);
        ChatPrompt prompt = new ChatPrompt(services);
        Screens screens = new Screens(services, prompt);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(services), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(prompt, this);

        PunishCommands punish = new PunishCommands(services);
        for (String c : new String[]{"ban", "tempban", "unban", "mute", "tempmute", "unmute", "warn", "kick"}) {
            register(c, punish);
        }
        register("report", new ReportCommand(services));
        register("cactusguard", new GuardCommand(this, services, screens));

        discord.send(DiscordBridge.Kind.SYSTEM, "Server online",
                "CactusGuard " + getPluginMeta().getVersion() + " is running.");
        getLogger().info("CactusGuard enabled. Stay prickly.");
    }

    @Override
    public void onDisable() {
        if (discord != null) {
            discord.send(DiscordBridge.Kind.SYSTEM, "Server offline", "CactusGuard is shutting down.");
            discord.shutdown();
        }
        if (db != null) db.close();
    }

    /** Re-read config.yml and messages.yml. */
    public void reloadAll() {
        reloadConfig();
        messages.reload();
        applyDiscordConfig();
    }

    private void applyDiscordConfig() {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        for (String k : new String[]{"punishments", "reports", "notes", "system"}) {
            toggles.put(k, getConfig().getBoolean("discord.log." + k, true));
        }
        discord.configure(getConfig().getBoolean("discord.enabled", false),
                getConfig().getString("discord.webhook-url", ""),
                getConfig().getString("discord.username", "CactusGuard"),
                getConfig().getString("discord.avatar-url", ""), toggles);
    }

    private void register(String name, CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        if (exec instanceof TabCompleter tc) cmd.setTabCompleter(tc);
    }
}
