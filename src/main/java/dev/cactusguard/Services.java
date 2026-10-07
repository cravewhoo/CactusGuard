package dev.cactusguard;

import dev.cactusguard.discord.DiscordBridge;
import dev.cactusguard.model.PunishType;
import dev.cactusguard.model.Punishment;
import dev.cactusguard.model.Report;
import dev.cactusguard.model.StaffNote;
import dev.cactusguard.storage.Database;
import dev.cactusguard.util.Durations;
import dev.cactusguard.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The brain. Commands and menus only collect input and call in here; this class talks to the
 * database (always off the main thread), applies punishments, alerts staff and logs to Discord.
 */
public final class Services {

    private final CactusGuard plugin;
    private final Database db;
    private final DiscordBridge discord;
    private final Messages msg;

    /** Live mutes, so chat events never touch the database. */
    private final Map<UUID, Punishment> muteCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reportCooldown = new ConcurrentHashMap<>();

    public Services(CactusGuard plugin, Database db, DiscordBridge discord, Messages msg) {
        this.plugin = plugin;
        this.db = db;
        this.discord = discord;
        this.msg = msg;
    }

    public Database db() { return db; }

    public DiscordBridge discord() { return discord; }

    public Messages msg() { return msg; }

    public FileConfiguration config() { return plugin.getConfig(); }

    public CactusGuard plugin() { return plugin; }

    // ------------------------------------------------------------ async

    @FunctionalInterface
    public interface SqlTask<T> {
        T run() throws SQLException;
    }

    /** Run a DB task off-thread, then hand the result back on the main thread. */
    public <T> void async(SqlTask<T> task, Consumer<T> onMain, Consumer<String> onError) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = task.run();
                Bukkit.getScheduler().runTask(plugin, () -> onMain.accept(result));
            } catch (SQLException e) {
                plugin.getLogger().severe("Database error: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> onError.accept(MiniMessage.miniMessage()
                        .stripTags(msg.raw("general.db-error"))));
            }
        });
    }

    // ------------------------------------------------------------ alerts

    public void alertStaff(Component message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("cactusguard.notify")) p.sendMessage(message);
        }
    }

    private void broadcast(Component message) {
        if (config().getBoolean("punishments.public-broadcast", true)) Bukkit.broadcast(message);
        else alertStaff(message);
    }

    // ------------------------------------------------------------ punishments

    public String defaultReason() { return config().getString("punishments.default-reason", "No reason given"); }

    /** Record, apply, announce and log a punishment. {@code durationMs <= 0} means permanent / not timed. */
    public void punish(String staffName, UUID staffId, UUID target, String targetName, PunishType type,
                       String reason, long durationMs, Consumer<Punishment> done, Consumer<String> fail) {
        long expires = durationMs > 0 ? System.currentTimeMillis() + durationMs : -1;
        async(() -> {
            // One active ban / mute at a time: a new one replaces the old.
            if (type == PunishType.BAN || type == PunishType.MUTE) db.removeActive(target, type, "replaced");
            return db.addPunishment(target, targetName, staffId, staffName, type, reason, expires);
        }, p -> {
            apply(p);
            announce(p);
            logPunishment(p);
            if (type == PunishType.WARN) checkWarnThreshold(p);
            done.accept(p);
        }, fail);
    }

    private void apply(Punishment p) {
        Player online = Bukkit.getPlayer(p.target());
        if (p.type() == PunishType.MUTE) muteCache.put(p.target(), p);
        if (online == null) return;
        switch (p.type()) {
            case BAN, KICK -> online.kick(screen(p));
            case MUTE -> online.sendMessage(msg.msg("punish.muted-notice", "time", forText(p), "reason", p.reason()));
            case WARN -> online.sendMessage(msg.msg("punish.warned-notice", "reason", p.reason()));
        }
    }

    private static String forText(Punishment p) {
        return p.permanent() ? "" : " for " + Durations.format(p.expires() - p.created());
    }

    /** The disconnect screen for bans and kicks. */
    public Component screen(Punishment p) {
        String title = switch (p.type()) {
            case BAN -> msg.text(p.permanent() ? "punish.screen-ban-perm" : "punish.screen-ban-temp");
            default -> msg.text("punish.screen-kick");
        };
        Component out = Component.text(title, dev.cactusguard.util.Theme.GREEN)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                .appendNewline().appendNewline()
                .append(line(msg.text("punish.screen-reason"), p.reason()));
        if (p.type() == PunishType.BAN && !p.permanent()) {
            out = out.appendNewline().append(line(msg.text("punish.screen-left"),
                    Durations.format(Math.max(0, p.expires() - System.currentTimeMillis()))));
        }
        out = out.appendNewline().append(line(msg.text("punish.screen-case"), "#" + p.id()));
        String appeal = config().getString("punishments.appeal-text", "");
        if (appeal != null && !appeal.isBlank()) {
            out = out.appendNewline().appendNewline()
                    .append(Component.text(appeal, dev.cactusguard.util.Theme.LIME));
        }
        return out;
    }

    private static Component line(String label, String value) {
        return Component.text(label + ": ", dev.cactusguard.util.Theme.GRAY)
                .append(Component.text(value, net.kyori.adventure.text.format.NamedTextColor.WHITE));
    }

    private void announce(Punishment p) {
        String verb = switch (p.type()) {
            case BAN -> p.permanent() ? "banned" : "temp-banned";
            case MUTE -> p.permanent() ? "muted" : "temp-muted";
            case WARN -> "warned";
            case KICK -> "kicked";
        };
        boolean timed = !p.permanent() && (p.type() == PunishType.BAN || p.type() == PunishType.MUTE);
        broadcast(msg.msg("punish.broadcast", "target", p.targetName(), "verb", verb,
                "time", timed ? " for " + Durations.format(p.expires() - p.created()) : "",
                "staff", p.staffName(), "reason", p.reason()));
    }

    private void logPunishment(Punishment p) {
        boolean timed = !p.permanent() && (p.type() == PunishType.BAN || p.type() == PunishType.MUTE);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Player", p.targetName());
        f.put("Staff", p.staffName());
        f.put("Type", p.type().label());
        f.put("Duration", timed ? Durations.format(p.expires() - p.created()) : "-");
        f.put("Case", "#" + p.id());
        discord.send(DiscordBridge.Kind.PUNISHMENT, p.type().label() + " issued", "**Reason:** " + p.reason(), f);
    }

    private void checkWarnThreshold(Punishment warn) {
        int threshold = config().getInt("punishments.warn-threshold", 3);
        if (threshold <= 0) return;
        async(() -> db.countWarnings(warn.target()), count -> {
            if (count < threshold || count % threshold != 0) return;
            String action = config().getString("punishments.warn-threshold-action", "tempmute").toLowerCase();
            long dur = Durations.parse(config().getString("punishments.warn-threshold-duration", "1h"));
            if (dur <= 0) dur = 3_600_000L;
            String reason = msg.text("punish.threshold-reason", "count", String.valueOf(count));
            PunishType type = switch (action) {
                case "tempban" -> PunishType.BAN;
                case "kick" -> PunishType.KICK;
                default -> PunishType.MUTE;
            };
            punish("CactusGuard", null, warn.target(), warn.targetName(), type, reason,
                    type == PunishType.KICK ? 0 : dur, x -> { }, e -> { });
        }, e -> { });
    }

    /** End the active ban or mute. Reports back through {@code done(removedCount)}. */
    public void pardon(String staffName, UUID target, String targetName, PunishType type,
                       Consumer<Integer> done, Consumer<String> fail) {
        async(() -> db.removeActive(target, type, staffName), removed -> {
            if (removed > 0) {
                if (type == PunishType.MUTE) muteCache.remove(target);
                alertStaff(msg.msg("punish.pardoned", "target", targetName,
                        "verb", type == PunishType.BAN ? "unbanned" : "unmuted", "staff", staffName));
                Map<String, String> f = new LinkedHashMap<>();
                f.put("Player", targetName);
                f.put("Staff", staffName);
                discord.send(DiscordBridge.Kind.PARDON,
                        type == PunishType.BAN ? "Player unbanned" : "Player unmuted", "", f);
            }
            done.accept(removed);
        }, fail);
    }

    // ------------------------------------------------------------ mute cache

    public void loadMute(UUID id) {
        async(() -> db.activePunishment(id, PunishType.MUTE), opt -> {
            if (opt.isPresent()) muteCache.put(id, opt.get()); else muteCache.remove(id);
        }, e -> { });
    }

    public void unload(UUID id) {
        muteCache.remove(id);
        reportCooldown.remove(id);
    }

    /** The live mute for this player, or null. Expired mutes are dropped. */
    public Punishment liveMute(UUID id) {
        Punishment p = muteCache.get(id);
        if (p != null && p.expired()) {
            muteCache.remove(id);
            return null;
        }
        return p;
    }

    // ------------------------------------------------------------ reports

    public long reportCooldownLeft(UUID reporter) {
        Long last = reportCooldown.get(reporter);
        if (last == null) return 0;
        long left = last + config().getLong("reports.cooldown-seconds", 120) * 1000L - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public void markReported(UUID reporter) { reportCooldown.put(reporter, System.currentTimeMillis()); }

    public void clearCooldown(UUID reporter) { reportCooldown.remove(reporter); }

    public void announceReport(Report r) {
        alertStaff(msg.msg("report.alert", "id", String.valueOf(r.id()), "target", r.targetName(),
                "reporter", r.reporterName(), "reason", r.reason()));
        try {
            Sound sound = Sound.valueOf(config().getString("reports.notify-sound", "BLOCK_NOTE_BLOCK_CHIME"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("cactusguard.reports.manage")) p.playSound(p.getLocation(), sound, 1f, 1.4f);
            }
        } catch (IllegalArgumentException ignored) {
            // bad sound name in config: skip the ping
        }
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Reported", r.targetName());
        f.put("Reporter", r.reporterName());
        f.put("World", r.world() == null ? "-" : r.world());
        f.put("Report", "#" + r.id());
        discord.send(DiscordBridge.Kind.REPORT, "New report", "**Reason:** " + r.reason(), f);
    }

    /** Change a report's status and notify everyone who should know. */
    public void updateReport(String by, long id, Report.Status status, Consumer<Report> done, Consumer<String> fail) {
        async(() -> {
            var opt = db.getReport(id);
            if (opt.isEmpty()) return null;
            db.setReportStatus(id, status, by);
            return opt.get();
        }, r -> {
            if (r == null) {
                done.accept(null);
                return;
            }
            String word = switch (status) {
                case CLAIMED -> "claimed";
                case RESOLVED -> "resolved";
                case DISMISSED -> "dismissed";
                default -> "reopened";
            };
            alertStaff(msg.msg("report.updated", "id", String.valueOf(id), "status", word, "by", by));
            Map<String, String> f = new LinkedHashMap<>();
            f.put("Report", "#" + id);
            f.put("Reported", r.targetName());
            f.put("Staff", by);
            discord.send(DiscordBridge.Kind.REPORT_UPDATE, "Report " + word, "", f);
            Player reporter = Bukkit.getPlayer(r.reporter());
            if (reporter != null && (status == Report.Status.RESOLVED || status == Report.Status.DISMISSED)) {
                reporter.sendMessage(msg.msg("report.reporter-update", "id", String.valueOf(id),
                        "target", r.targetName(), "status", word));
            }
            done.accept(r);
        }, fail);
    }

    // ------------------------------------------------------------ notes

    public void noteAdded(StaffNote n) {
        alertStaff(msg.msg("notes.alert", "target", n.targetName(), "author", n.author()));
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Player", n.targetName());
        f.put("Author", n.author());
        f.put("Note", "#" + n.id());
        discord.send(DiscordBridge.Kind.NOTE, "Staff note added", n.text(), f);
    }
}
