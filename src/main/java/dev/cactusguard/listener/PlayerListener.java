package dev.cactusguard.listener;

import dev.cactusguard.Services;
import dev.cactusguard.model.PunishType;
import dev.cactusguard.model.Punishment;
import dev.cactusguard.util.Durations;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.Optional;

/** Enforces bans and mutes and tells staff what they should know when someone joins. */
public final class PlayerListener implements Listener {

    private final Services svc;

    public PlayerListener(Services svc) { this.svc = svc; }

    /** Already async, so the database can be read directly without blocking the server. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        try {
            Optional<Punishment> ban = svc.db().activePunishment(e.getUniqueId(), PunishType.BAN);
            ban.ifPresent(b -> e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, svc.screen(b)));
        } catch (SQLException ex) {
            // Fail open: a database hiccup must never lock everyone out.
            svc.plugin().getLogger().warning("Ban check failed for " + e.getName() + ": " + ex.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        svc.loadMute(p.getUniqueId());

        if (svc.config().getBoolean("notes.alert-on-join", true)) {
            svc.async(() -> new int[]{svc.db().countWarnings(p.getUniqueId()), svc.db().countNotes(p.getUniqueId())},
                    c -> {
                        if (c[0] > 0 || c[1] > 0) {
                            svc.alertStaff(svc.msg().msg("notes.join-alert", "name", p.getName(),
                                    "warns", String.valueOf(c[0]), "notes", String.valueOf(c[1])));
                        }
                    }, err -> { });
        }
        if (p.hasPermission("cactusguard.reports.manage")) {
            svc.async(() -> svc.db().countPendingReports(), n -> {
                if (n > 0 && p.isOnline()) p.sendMessage(svc.msg().msg("report.waiting", "count", String.valueOf(n)));
            }, err -> { });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { svc.unload(e.getPlayer().getUniqueId()); }

    /** Muted players can't chat. Reads the in-memory cache only. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Punishment mute = svc.liveMute(e.getPlayer().getUniqueId());
        if (mute == null) return;
        e.setCancelled(true);
        String left = mute.permanent() ? "permanently"
                : "for another " + Durations.format(mute.expires() - System.currentTimeMillis());
        e.getPlayer().sendMessage(svc.msg().msg("punish.muted-chat", "left", left, "reason", mute.reason()));
    }
}
