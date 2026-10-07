# Project memory

Why CactusGuard looks the way it does.

## Origin
A minimalistic 1.21.x staff plugin that combines punishments, player reports and staff notes with a
Discord log bridge, in a bright green theme.

## Design goals
- One small plugin for the whole daily staff loop: punish, report, remember, log.
- A GUI as the main interface, with quick commands as a fast path.
- Staff notes stored locally, so there is no hosted API or API key to depend on.
- Small command surface: no IP tools, no web UI, no multi-language packs.

## Decisions
| Decision | Why |
|---|---|
| GUI is the main interface | Staff click instead of remembering syntax. Quick commands stay for speed. |
| SQLite only | Zero setup. MySQL can come later behind `Database`. |
| SQLite driver via `libraries:` in plugin.yml | Keeps the jar around 95 KB; Paper downloads it. |
| Java `HttpClient` for Discord | No dependency. One daemon thread, queue capped at 200. |
| Webhook host allow-list | A config typo or malicious edit cannot make the server POST elsewhere. |
| Small-caps Unicode, not a resource pack | Works on any client with no download. |
| `messages.yml` for all text | Server owners can retheme or translate without code. |
| `api-version: 1.21`, built against 1.21.11 | Loads on every 1.21.x. |

## Bugs found while building (so they stay fixed)
- `plugin.yml` used `description: ${project.description}`; Maven filtering inserted `: ` and broke the YAML, so the plugin would not load. Now a quoted literal.
- Inner-class method `title(String)` clashed with `Menu.title()`; renamed the helper to `heading`.
- First draft escaped user text by hand with `esc()`; any miss was a markup-injection hole. Replaced by `Placeholder.unparsed`.

## Verification status
- Compiles clean with `-Xlint:all`, no warnings.
- Loaded and enabled on a real Paper 1.21.11 server once (no errors). Not tested further on a live server by request.
- Not yet verified: GUI clicks in-game, Discord delivery against a real webhook.

## Ideas, not built
IP bans and alt detection, MySQL backend, punishment appeal flow, Folia support, report evidence snapshots.
