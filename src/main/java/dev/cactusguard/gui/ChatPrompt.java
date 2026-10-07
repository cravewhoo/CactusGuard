package dev.cactusguard.gui;

import dev.cactusguard.Services;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures a staff member's next chat line (for custom reasons and notes) so they never need to
 * remember a command syntax. Typing "cancel" aborts. The captured line is never shown publicly.
 */
public final class ChatPrompt implements Listener {

    private final Services svc;
    private final Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<>();

    public ChatPrompt(Services svc) { this.svc = svc; }

    /** Close the menu, show the prompt text, and call back with the next chat line. */
    public void ask(Player p, String promptKey, Consumer<String> onText) {
        p.closeInventory();
        p.sendMessage(svc.msg().msg(promptKey));
        waiting.put(p.getUniqueId(), onText);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = waiting.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        // Chat is async; hop to the main thread before touching the world or opening menus.
        Bukkit.getScheduler().runTask(svc.plugin(), () -> {
            if (text.equalsIgnoreCase("cancel") || text.isEmpty()) {
                p.sendMessage(svc.msg().msg("gui.prompt-cancelled"));
            } else {
                cb.accept(text.length() > 300 ? text.substring(0, 300) : text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { waiting.remove(e.getPlayer().getUniqueId()); }
}
