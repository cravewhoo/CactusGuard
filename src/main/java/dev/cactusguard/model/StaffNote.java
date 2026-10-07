package dev.cactusguard.model;

import java.util.UUID;

public record StaffNote(long id, UUID target, String targetName, String author, String text, long created) {}
