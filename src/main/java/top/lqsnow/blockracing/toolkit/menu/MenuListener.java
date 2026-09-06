package top.lqsnow.blockracing.toolkit.menu;

import org.bukkit.Bukkit;
import top.lqsnow.blockracing.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuView menu)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()
                && event.getWhoClicked() instanceof Player player) {
            int slot = event.getRawSlot();
            var click = event.getClick();
            // Opening/closing an inventory during InventoryClickEvent is unsafe.
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu) {
                    menu.click(player, slot, click);
                }
            });
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MenuView
                && event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }
}
