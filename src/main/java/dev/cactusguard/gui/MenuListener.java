package dev.cactusguard.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/** Routes inventory events to {@link Menu} and makes sure nothing can be taken out of a menu. */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true); // covers shift-click from the player inventory as well
            menu.click(e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof Menu || e.getSource().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }
}
