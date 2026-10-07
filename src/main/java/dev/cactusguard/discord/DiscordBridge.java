package dev.cactusguard.discord;

import dev.cactusguard.util.Theme;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends log embeds to a Discord webhook. Fully asynchronous: events are queued and posted by a
 * single daemon thread, which also honours Discord's rate limits. Never touches the main thread.
 */
public final class DiscordBridge {

    /** What kind of event an embed describes (controls colour and per-event toggles). */
    public enum Kind {
        PUNISHMENT("punishments", Theme.EMBED_RED),
        PARDON("punishments", Theme.EMBED_GREEN),
        REPORT("reports", Theme.EMBED_YELLOW),
        REPORT_UPDATE("reports", Theme.EMBED_GREEN),
        NOTE("notes", Theme.EMBED_GREEN),
        SYSTEM("system", Theme.EMBED_GREEN);

        final String toggle;
        final int color;

        Kind(String toggle, int color) {
            this.toggle = toggle;
            this.color = color;
        }
    }

    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");
    private static final int QUEUE_CAP = 200;

    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAP);
    private final Thread worker;

    private volatile boolean enabled;
    private volatile String url = "";
    private volatile String username = "CactusGuard";
    private volatile String avatar = "";
    private volatile Map<String, Boolean> toggles = Map.of();
    private volatile boolean running = true;

    public DiscordBridge(Logger log) {
        this.log = log;
        this.worker = new Thread(this::run, "CactusGuard-Discord");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void configure(boolean enabled, String url, String username, String avatar, Map<String, Boolean> toggles) {
        this.url = url == null ? "" : url.trim();
        this.enabled = enabled && validUrl(this.url);
        this.username = username == null || username.isBlank() ? "CactusGuard" : username;
        this.avatar = avatar == null ? "" : avatar.trim();
        this.toggles = Map.copyOf(toggles);
        if (enabled && !this.enabled) {
            log.warning("Discord bridge is enabled but webhook-url is missing or invalid; bridge stays off.");
        }
    }

    public boolean isEnabled() { return enabled; }

    static boolean validUrl(String u) {
        return u.startsWith("https://discord.com/api/webhooks/")
                || u.startsWith("https://discordapp.com/api/webhooks/")
                || u.startsWith("https://canary.discord.com/api/webhooks/")
                || u.startsWith("https://ptb.discord.com/api/webhooks/");
    }

    /** Queue an embed. Safe to call from any thread; silently drops if disabled or the queue is full. */
    public void send(Kind kind, String title, String description, Map<String, String> fields) {
        if (!enabled || !toggles.getOrDefault(kind.toggle, true)) return;
        String json = buildPayload(kind, title, description, fields);
        if (!queue.offer(json)) {
            log.warning("Discord queue full; dropping a log message.");
        }
    }

    public void send(Kind kind, String title, String description) {
        send(kind, title, description, new LinkedHashMap<>());
    }

    private String buildPayload(Kind kind, String title, String description, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"username\":").append(Json.str(username));
        if (!avatar.isEmpty()) sb.append(",\"avatar_url\":").append(Json.str(avatar));
        sb.append(",\"allowed_mentions\":{\"parse\":[]}");
        sb.append(",\"embeds\":[{\"title\":").append(Json.str(cut(title, 256)));
        sb.append(",\"description\":").append(Json.str(cut(description, 2000)));
        sb.append(",\"color\":").append(kind.color);
        sb.append(",\"timestamp\":").append(Json.str(Instant.now().toString()));
        sb.append(",\"footer\":{\"text\":\"CactusGuard\"}");
        if (!fields.isEmpty()) {
            sb.append(",\"fields\":[");
            boolean first = true;
            int n = 0;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (n++ >= 25) break;
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"name\":").append(Json.str(cut(e.getKey(), 256)))
                        .append(",\"value\":").append(Json.str(cut(e.getValue().isBlank() ? "-" : e.getValue(), 1024)))
                        .append(",\"inline\":true}");
            }
            sb.append(']');
        }
        sb.append("}]}");
        return sb.toString();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    private void run() {
        while (running) {
            String payload;
            try {
                payload = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            post(payload, 0);
            try {
                Thread.sleep(300); // gentle pacing, well under Discord's limits
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void post(String payload, int attempt) {
        if (!enabled || attempt > 3) return;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 429) {
                long waitMs = 2000;
                Matcher m = RETRY_AFTER.matcher(res.body());
                if (m.find()) waitMs = (long) (Double.parseDouble(m.group(1)) * 1000) + 100;
                Thread.sleep(Math.min(waitMs, 30_000));
                post(payload, attempt + 1);
            } else if (code >= 400) {
                log.warning("Discord webhook returned HTTP " + code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warning("Discord webhook failed: " + e.getMessage());
        }
    }

    /** Stops the worker. Pending messages (e.g. the shutdown notice) get a short window to flush. */
    public void shutdown() {
        long end = System.currentTimeMillis() + 3000;
        while (!queue.isEmpty() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
        running = false;
        worker.interrupt();
    }
}
