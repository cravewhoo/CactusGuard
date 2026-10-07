package dev.cactusguard.model;

import java.util.UUID;

/**
 * A single punishment record.
 * @param expires epoch millis, or -1 for permanent
 */
public record Punishment(long id, UUID target, String targetName, UUID staff, String staffName,
                         PunishType type, String reason, long created, long expires, boolean active,
                         String removedBy) {

    public boolean permanent() { return expires < 0; }

    public boolean expired() { return expires >= 0 && expires <= System.currentTimeMillis(); }

    public boolean isLive() { return active && !expired(); }
}
