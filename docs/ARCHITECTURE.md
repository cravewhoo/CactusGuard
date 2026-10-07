# Architecture

```
dev.cactusguard
├─ CactusGuard.java        entry point: wires everything, registers commands
├─ Services.java           the brain: punish / pardon / reports / notes + alerts + Discord
├─ command/                thin: parse input, call Services
│   ├─ GuardCommand        /cg  (opens GUI, plus text subcommands)
│   ├─ PunishCommands      /ban /tempban /mute /warn /kick /unban /unmute
│   └─ ReportCommand       /report
├─ gui/
│   ├─ Menu                base class: inventory + slot -> click handler
│   ├─ MenuListener        routes clicks, blocks item theft
│   ├─ Screens             every screen in flow order (Hub > Players > Player > Duration > Reason ...)
│   └─ ChatPrompt          captures the next chat line for custom reasons / notes
├─ listener/PlayerListener ban check at login, mute check in chat, join alerts
├─ storage/Database        SQLite, one connection behind a lock
├─ discord/DiscordBridge   queued webhook embeds, handles 429 rate limits
├─ model/                  Punishment, Report, StaffNote (immutable records)
└─ util/                   Messages (messages.yml), Theme, Font, Durations, Players
```

## Rules the code follows

1. **Commands and menus never touch the database.** They call `Services`, which runs SQL off the main thread and returns on the main thread (`Services.async`).
2. **One exception:** `AsyncPlayerPreLoginEvent` is already async, so it reads the database directly.
3. **Chat never hits the database.** Mutes are cached in memory; the cache is loaded on join.
4. **Fail open.** If the database errors during login, the player is allowed in rather than locking everyone out.
5. **No user text is parsed as markup.** Messages use `Placeholder.unparsed`, so names and reasons cannot inject MiniMessage.
6. **No hardcoded user-facing strings.** Everything goes through `messages.yml`.
7. **One active ban and one active mute per player.** A new one replaces the old.
8. **Warnings and kicks are records, never "active".**
