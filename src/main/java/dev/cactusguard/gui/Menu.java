package dev.cactusguard.gui;

import dev.cactusguard.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base for every CactusGuard screen. A menu owns its inventory and a slot -> click handler map.
 * {@link MenuListener} routes clicks here, so subclasses only describe layout and actions.
 */
public abstract class Menu implements InventoryHolder {

    protected final Player viewer;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();
    private Inventory inv;

    protected Menu(Player viewer) { this.viewer = viewer; }

    /** Inventory title (already rendered). */
    protected abstract Component title();

    /** Rows, 1 to 6. */
    protected int rows() { return 3; }

    /** Fill slots and register handlers. Called each time the menu is (re)drawn. */
    protected abstract void draw();

    @Override
    public @NotNull Inventory getInventory() { return inv; }

    /** Build and show. Safe to call again to refresh in place. */
    public final void open() {
        if (!viewer.isOnline()) return;
        handlers.clear();
        inv = Bukkit.createInventory(this, rows() * 9, title());
        draw();
        viewer.openInventory(inv);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inv.setItem(slot, item);
        if (onClick != null) handlers.put(slot, onClick);
    }

    protected final void set(int slot, ItemStack item) { set(slot, item, null); }

    /** Thin green frame along the bottom row, so every screen feels the same. */
    protected final void footer() {
        ItemStack pane = icon(Material.LIME_STAINED_GLASS_PANE, Component.empty());
        int start = (rows() - 1) * 9;
        for (int i = start; i < start + 9; i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    final void click(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        if (e.getClick() == ClickType.DOUBLE_CLICK) return;
        Consumer<InventoryClickEvent> h = handlers.get(e.getSlot());
        if (h != null) h.accept(e);
    }

    // ------------------------------------------------------------ item helpers

    public static ItemStack icon(Material m, Component name, Component... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) meta.lore(List.of(lore));
        it.setItemMeta(meta);
        return it;
    }

    protected static Component green(String s) { return Theme.item(s, Theme.GREEN, true); }

    protected static Component gray(String s) { return Theme.item(s, Theme.GRAY); }

    protected static Component lime(String s) { return Theme.item(s, Theme.LIME); }
}
