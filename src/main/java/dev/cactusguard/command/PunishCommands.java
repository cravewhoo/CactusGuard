package dev.cactusguard.command;

import dev.cactusguard.Services;
import dev.cactusguard.gui.Screens;
import dev.cactusguard.model.PunishType;
import dev.cactusguard.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** Quick commands: ban, tempban, mute, tempmute, warn, kick, unban, unmute. The GUI is the main way in. */
public final class PunishCommands extends Base {

    public PunishCommands(Services svc) { super(svc); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        boolean undo = name.equals("unban") || name.equals("unmute");
        if (denied(sender, undo ? "cactusguard.unpunish" : "cactusguard.punish")) return true;

        boolean timed = name.equals("tempban") || name.equals("tempmute");
        if (args.length < (timed ? 2 : 1)) {
            usage(sender, "/" + name + " <player>" + (timed ? " <duration>" : "") + (undo ? "" : " [reason]"));
            return true;
        }
        Screens.Who t = target(sender, args[0]);
        if (t == null) return true;

        if (undo) {
            PunishType type = name.equals("unban") ? PunishType.BAN : PunishType.MUTE;
            svc.pardon(nameOf(sender), t.id(), t.name(), type, removed -> sender.sendMessage(removed == 0
                            ? svc.msg().msg("punish.nothing-active", "name", t.name(), "what", type.label().toLowerCase(Locale.ROOT))
                            : svc.msg().msg("punish.pardon-done", "name", t.name(),
                            "state", type == PunishType.BAN ? "banned" : "muted")),
                    err -> sender.sendMessage(net.kyori.adventure.text.Component.text(err)));
            return true;
        }

        long duration = 0;
        int reasonFrom = 1;
        if (timed) {
            duration = Durations.parse(args[1]);
            if (duration <= 0) {
                sender.sendMessage(svc.msg().msg("general.bad-duration", "input", args[1]));
                return true;
            }
            reasonFrom = 2;
        }

        Player online = Bukkit.getPlayer(t.id());
        if (online != null && online.hasPermission("cactusguard.bypass") && !sender.hasPermission("cactusguard.admin")) {
            sender.sendMessage(svc.msg().msg("general.cant-punish-bypass", "name", t.name()));
            return true;
        }
        if (sender instanceof Player sp && sp.getUniqueId().equals(t.id())) {
            sender.sendMessage(svc.msg().msg("general.cant-punish-self"));
            return true;
        }

        PunishType type = switch (name) {
            case "ban", "tempban" -> PunishType.BAN;
            case "mute", "tempmute" -> PunishType.MUTE;
            case "warn" -> PunishType.WARN;
            default -> PunishType.KICK;
        };
        String reason = join(args, reasonFrom);
        if (reason.isBlank()) reason = svc.defaultReason();
        if (reason.length() > 200) reason = reason.substring(0, 200);

        svc.punish(nameOf(sender), idOf(sender), t.id(), t.name(), type, reason, duration,
                p -> sender.sendMessage(svc.msg().msg("punish.done", "id", String.valueOf(p.id()), "name", t.name())),
                err -> sender.sendMessage(net.kyori.adventure.text.Component.text(err)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) return online(args[0]);
        if (args.length == 2 && (name.equals("tempban") || name.equals("tempmute"))) {
            return filter(List.of("30m", "1h", "6h", "1d", "7d", "30d"), args[1]);
        }
        return List.of();
    }
}
