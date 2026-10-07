package dev.cactusguard.model;

import java.util.UUID;

public record Report(long id, UUID reporter, String reporterName, UUID target, String targetName,
                     String reason, long created, Status status, String handler, String world) {

    public enum Status { OPEN, CLAIMED, RESOLVED, DISMISSED }
}
