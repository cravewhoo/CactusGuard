package dev.cactusguard.command;

import dev.cactusguard.CactusGuard;
import dev.cactusguard.Services;
import dev.cactusguard.gui.Screens;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /cg is the front door. With no arguments it opens the hub GUI.
 * Sub-commands exist for console use and for staff who prefer typing:
 * players, reports, history <p>, notes <p>, reload, stats.
 */
public final class GuardCommand extends Base {

    private final CactusGuard plugin;
    private final Screens screens;

    public GuardCommand(CactusGuard plugin, Services svc, Screens screens) {
        super(svc);
        this.plugin = plugin;
        this.screens = screens;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (denied(sender, "cactusguard.admin")) return true;
            plugin.reloadAll();
            sender.sendMessage(svc.msg().msg("general.reloaded"));
            return true;
        }
        if (sub.equals("stats")) {
            if (denied(sender, "cactusguard.admin")) return true;
            stats(sender);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(svc.msg().msg("general.players-only"));
            return true;
        }
        if (denied(sender, "cactusguard.use")) return true;

        switch (sub) {
            case "players" -> screens.players(p, 0);
            case "reports" -> {
                if (denied(sender, "cactusguard.reports.manage")) return true;
                screens.reports(p);
            }
            case "history", "notes" -> {
                String perm = sub.equals("history") ? "cactusguard.history" : "cactusguard.notes";
                if (denied(sender, perm)) return true;
                if (args.length < 2) {
                    usage(sender, "/cg " + sub + " <player>");
                    return true;
                }
                Screens.Who t = target(sender, args[1]);
                if (t == null) return true;
                if (sub.equals("history")) screens.history(p, t); else screens.notes(p, t);
            }
            case "" -> screens.hub(p);
            default -> {
                Screens.Who t = target(sender, args[0]); // /cg <player> opens that player's menu
                if (t != null) screens.player(p, t);
            }
        }
        return true;
    }

    private void stats(CommandSender sender) {
        svc.async(() -> new int[]{svc.db().totalPunishments(), svc.db().totalReports(),
                        svc.db().countPendingReports(), svc.db().totalNotes()},
                n -> sender.sendMessage(Component.text("Punishments " + n[0] + " | Reports " + n[1]
                        + " (" + n[2] + " pending) | Notes " + n[3] + " | Discord "
                        + (svc.discord().isEnabled() ? "on" : "off"), dev.cactusguard.util.Theme.GREEN)),
                err -> sender.sendMessage(Component.text(err)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new java.util.ArrayList<>(List.of("players", "reports", "history", "notes", "reload", "stats"));
            opts.addAll(online(args[0]));
            return filter(opts, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("notes"))) {
            return online(args[1]);
        }
        return List.of();
    }
}
