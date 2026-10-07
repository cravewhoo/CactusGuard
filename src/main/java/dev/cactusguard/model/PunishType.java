package dev.cactusguard.model;

public enum PunishType {
    BAN("Ban"), MUTE("Mute"), WARN("Warning"), KICK("Kick");

    private final String label;
    PunishType(String label) { this.label = label; }
    public String label() { return label; }
}
