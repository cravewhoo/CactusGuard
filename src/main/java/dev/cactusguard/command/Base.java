package dev.cactusguard.command;

import dev.cactusguard.Services;
import dev.cactusguard.gui.Screens;
import dev.cactusguard.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Small shared helpers so each command file only holds its own logic. */
abstract class Base implements CommandExecutor, TabCompleter {

    protected final Services svc;

    Base(Services svc) { this.svc = svc; }

    /** @return true (and tells the sender) when they lack the permission. */
    protected boolean denied(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return false;
        s.sendMessage(svc.msg().msg("general.no-permission"));
        return true;
    }

    protected void usage(CommandSender s, String text) {
        s.sendMessage(svc.msg().msg("general.usage", "usage", text));
    }

    /** Resolve a player or tell the sender why not. */
    protected Screens.Who target(CommandSender s, String name) {
        Screens.Who w = Players.resolve(name);
        if (w == null) s.sendMessage(svc.msg().msg("general.unknown-player", "name", name));
        return w;
    }

    protected static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    protected static UUID idOf(CommandSender s) { return s instanceof Player p ? p.getUniqueId() : null; }

    protected static String nameOf(CommandSender s) { return s instanceof Player ? s.getName() : "Console"; }

    protected static List<String> online(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
        }
        return out;
    }

    protected static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? online(args[0]) : List.of();
    }
}
