package dev.cactusguard.util;

import dev.cactusguard.gui.Screens;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Resolves a typed name to a player this server has seen. Never makes a network call. */
public final class Players {

    private Players() {}

    /**
     * Online players match exactly. Offline players are matched only from the server's local cache
     * ({@code getOfflinePlayerIfCached}), so a typo can never create a ghost record.
     * Call from the main thread.
     */
    public static Screens.Who resolve(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return new Screens.Who(online.getUniqueId(), online.getName());
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(input);
        if (op != null && op.getName() != null) return new Screens.Who(op.getUniqueId(), op.getName());
        return null;
    }
}
