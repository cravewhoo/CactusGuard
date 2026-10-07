package dev.cactusguard.command;

import dev.cactusguard.Services;
import dev.cactusguard.gui.Screens;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** /report <player> <reason>, the only command regular players get. Staff review reports in the GUI. */
public final class ReportCommand extends Base {

    public ReportCommand(Services svc) { super(svc); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player me)) {
            sender.sendMessage(svc.msg().msg("general.players-only"));
            return true;
        }
        if (denied(sender, "cactusguard.report")) return true;
        if (args.length < 2) {
            usage(sender, svc.msg().text("report.usage"));
            return true;
        }
        Screens.Who t = target(sender, args[0]);
        if (t == null) return true;

        var cfg = svc.config();
        if (t.id().equals(me.getUniqueId()) && !cfg.getBoolean("reports.allow-self-report", false)) {
            sender.sendMessage(svc.msg().msg("report.self"));
            return true;
        }
        String reason = join(args, 1).trim();
        int min = cfg.getInt("reports.min-reason-length", 5);
        if (reason.length() < min) {
            sender.sendMessage(svc.msg().msg("report.short", "min", String.valueOf(min)));
            return true;
        }
        if (reason.length() > 200) reason = reason.substring(0, 200);

        long left = svc.reportCooldownLeft(me.getUniqueId());
        if (left > 0) {
            sender.sendMessage(svc.msg().msg("report.cooldown", "seconds", String.valueOf(left)));
            return true;
        }

        int cap = cfg.getInt("reports.max-open-per-player", 3);
        String world = me.getWorld().getName();
        String text = reason;
        svc.markReported(me.getUniqueId());
        svc.async(() -> svc.db().pendingReportsBy(me.getUniqueId()) >= cap ? null
                        : svc.db().addReport(me.getUniqueId(), me.getName(), t.id(), t.name(), text, world),
                r -> {
                    if (r == null) {
                        svc.clearCooldown(me.getUniqueId());
                        me.sendMessage(svc.msg().msg("report.cap", "cap", String.valueOf(cap)));
                        return;
                    }
                    me.sendMessage(svc.msg().msg("report.sent", "id", String.valueOf(r.id())));
                    svc.announceReport(r);
                }, err -> {
                    svc.clearCooldown(me.getUniqueId());
                    me.sendMessage(Component.text(err));
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? online(args[0]) : List.of();
    }
}
