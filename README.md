<h1 align="center">❀ ᴄᴀᴄᴛᴜѕɢᴜᴀʀᴅ</h1>
<p align="center"><b>Minimal staff suite for Paper 1.21.x</b><br>
Punishments · Reports · Staff notes · Discord log bridge<br>
All driven by one bright green GUI.</p>

---

## Why

Most staff plugins are three plugins stitched together. CactusGuard is one small plugin that does the
daily staff loop well: **punish, report, remember, log**. One ~95 KB jar, local SQLite storage,
no hosted API and no heavy dependencies.

## Features

- **GUI first.** `/cg` opens a hub: pick a player, pick an action, pick a duration, pick a reason. No syntax to remember.
- **Punishments.** Warn, mute, kick, ban, with timed variants, history and one-click pardons.
- **Warn escalation.** After N warnings, auto tempmute / tempban / kick (configurable).
- **Reports.** Players use `/report <player> <reason>`. Staff review in a GUI: click to claim, shift-click to resolve, right-click to dismiss. Reporters are told the outcome.
- **Staff notes.** Private notes per player, stored locally. Staff get an alert when a player with notes or warnings joins.
- **Discord bridge.** Every punishment, pardon, report and note posts a green embed to your webhook. Async, rate-limit aware, never blocks the server.
- **Small-caps font + green theme.** Bright `#39FF14` everywhere.
- **Every message is editable.** All text lives in `messages.yml`.

## Install

1. Download the jar from [Releases](../../releases) (or build it, below).
2. Drop it in `plugins/` on a **Paper 1.21.x** server (Java 21).
3. Start once, then edit `plugins/CactusGuard/config.yml` and `messages.yml`.
4. `/cg reload` after changes.

The SQLite driver is downloaded automatically by Paper on first start.

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/cg` | Open the staff hub GUI | `cactusguard.use` |
| `/cg players` · `/cg <player>` | Player list / that player's menu | `cactusguard.use` |
| `/cg reports` | Open the report inbox | `cactusguard.reports.manage` |
| `/cg history <player>` | Punishment history | `cactusguard.history` |
| `/cg notes <player>` | Staff notes (add inside the GUI) | `cactusguard.notes` |
| `/cg reload` · `/cg stats` | Admin tools | `cactusguard.admin` |
| `/ban` `/tempban` `/unban` | Ban tools (quick path) | `cactusguard.punish` / `unpunish` |
| `/mute` `/tempmute` `/unmute` | Mute tools | `cactusguard.punish` / `unpunish` |
| `/warn` `/kick` | Warn or kick | `cactusguard.punish` |
| `/report <player> <reason>` | Report a player | `cactusguard.report` (default: everyone) |

Durations: `30m`, `2h`, `7d`, `1w`, `1y`, or combined like `1d6h30m`.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `cactusguard.admin` | op | Everything below |
| `cactusguard.use` | op | Open the GUI |
| `cactusguard.punish` / `unpunish` | op | Punish / pardon |
| `cactusguard.history` | op | View history |
| `cactusguard.notes` | op | Read and write notes |
| `cactusguard.reports.manage` | op | Review reports |
| `cactusguard.notify` | op | Receive staff alerts |
| `cactusguard.bypass` | false | Cannot be punished (admins can still override) |
| `cactusguard.report` | true | File reports |

## Discord setup

1. Discord: *Server Settings → Integrations → Webhooks → New Webhook → Copy URL*.
2. In `config.yml` set `discord.enabled: true` and paste `webhook-url`.
3. `/cg reload`.

Only real `discord.com` / `discordapp.com` webhook URLs are accepted. Individual log categories can be switched off under `discord.log`.

## Build

```bash
mvn package        # needs JDK 21
# -> target/CactusGuard-1.0.0.jar
```

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): how the code is organised
- [`docs/MEMORY.md`](docs/MEMORY.md): design decisions and project memory
- [`CHANGELOG.md`](CHANGELOG.md)

## License

Copyright (c) 2026 CactusGuard. **All Rights Reserved.** See [`LICENSE`](LICENSE).
