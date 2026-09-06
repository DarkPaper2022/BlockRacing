package top.lqsnow.blockracing.listeners;

import io.papermc.paper.event.entity.EntityCompostItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import top.lqsnow.blockracing.Main;
import top.lqsnow.blockracing.managers.Game;
import top.lqsnow.blockracing.managers.Goal;
import top.lqsnow.blockracing.managers.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static top.lqsnow.blockracing.managers.Goal.TaskAction.*;

/** Real actions, separate from the intentionally coarse legacy use-block/consume rules. */
public final class TaskActionListener implements Listener {
    private static final NamespacedKey COMPOST_OUTPUT = new NamespacedKey("blockracing", "compost_output_round");

    private static boolean active(Player player) {
        return Game.getCurrentGameState() == Game.GameState.INGAME
                && (Team.redTeamPlayers.contains(player.getName()) || Team.blueTeamPlayers.contains(player.getName()));
    }

    private static void afterAction(Player player, Runnable check) {
        String epoch = Goal.progressEpoch();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (active(player) && epoch.equals(Goal.progressEpoch())) check.run();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilkClear(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && active(player)
                && event.getCause() == EntityPotionEffectEvent.Cause.MILK
                && event.getOldEffect() != null && event.getNewEffect() == null) {
            Goal.recordAction(player, MILK_CLEANSE);
        }
    }

    static boolean isCleaning(CauldronLevelChangeEvent.ChangeReason reason) {
        return reason == CauldronLevelChangeEvent.ChangeReason.ARMOR_WASH
                || reason == CauldronLevelChangeEvent.ChangeReason.BANNER_WASH
                || reason == CauldronLevelChangeEvent.ChangeReason.SHULKER_WASH;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronClean(CauldronLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && active(player) && isCleaning(event.getReason())
                && event.getBlock().getType() == Material.WATER_CAULDRON
                && event.getNewLevel() < event.getOldLevel()) Goal.recordAction(player, CAULDRON_CLEAN);
    }

    static boolean takesResult(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, MOVE_TO_OTHER_INVENTORY,
                    HOTBAR_SWAP, HOTBAR_MOVE_AND_READD, SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    static boolean completedLoomTake(int dyeBefore, int dyeAfter, int ownedBefore, int ownedAfter, boolean newDrop) {
        return dyeBefore > 0 && dyeAfter < dyeBefore && (ownedAfter > ownedBefore || newDrop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoomResult(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !active(player)
                || event.getView().getTopInventory().getType() != InventoryType.LOOM
                || event.getRawSlot() != 3 || !takesResult(event.getAction())) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.getType().name().endsWith("_BANNER")) return;
        ItemStack result = item.clone();
        Inventory loom = event.getView().getTopInventory();
        int dyeBefore = amount(loom.getItem(1));
        int ownedBefore = owned(player, result);
        Set<UUID> dropsBefore = nearbyItems(player.getLocation(), result);
        afterAction(player, () -> {
            // Opening/previewing, a full inventory, or closing the GUI without taking the result cannot count.
            boolean newDrop = !dropsBefore.containsAll(nearbyItems(player.getLocation(), result));
            if (completedLoomTake(dyeBefore, amount(loom.getItem(1)), ownedBefore, owned(player, result), newDrop))
                Goal.recordAction(player, LOOM_CRAFT);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompostFill(EntityCompostItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !active(player) || !event.willRaiseLevel()
                || !(event.getBlock().getBlockData() instanceof Levelled level) || level.getLevel() != 6) return;
        org.bukkit.block.Block block = event.getBlock();
        afterAction(player, () -> {
            if (block.getType() == Material.COMPOSTER && block.getBlockData() instanceof Levelled now && now.getLevel() >= 7)
                Goal.recordAction(player, COMPOST_FILL);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!active(player) || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getState() instanceof Jukebox box && !box.hasRecord() && event.getItem() != null) {
            ItemStack inserted = event.getItem().clone();
            afterAction(player, () -> {
                if (block.getState() instanceof Jukebox now && now.isPlaying() && now.getRecord().isSimilar(inserted))
                    Goal.recordAction(player, JUKEBOX_PLAY);
            });
        } else if (block.getType() == Material.COMPOSTER && block.getBlockData() instanceof Levelled level && level.getLevel() == 8) {
            // Tag only newly emitted bone meal after a successful player extraction. Pickup is counted separately.
            org.bukkit.Location output = block.getLocation().add(0.5, 1, 0.5);
            Set<UUID> before = nearbyItems(output, null);
            afterAction(player, () -> {
                if (block.getType() != Material.COMPOSTER || !(block.getBlockData() instanceof Levelled now) || now.getLevel() != 0) return;
                for (org.bukkit.entity.Entity entity : output.getWorld().getNearbyEntities(output, 1.5, 1.5, 1.5)) {
                    if (entity instanceof Item drop && !before.contains(drop.getUniqueId()) && drop.getItemStack().getType() == Material.BONE_MEAL)
                        drop.getPersistentDataContainer().set(COMPOST_OUTPUT, PersistentDataType.STRING, Goal.progressEpoch());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompostPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && active(player)
                && event.getItem().getItemStack().getType() == Material.BONE_MEAL
                && event.getRemaining() < event.getItem().getItemStack().getAmount()
                && Goal.progressEpoch().equals(event.getItem().getPersistentDataContainer().get(COMPOST_OUTPUT, PersistentDataType.STRING)))
            Goal.recordAction(player, COMPOST_COLLECT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompostDropMerge(org.bukkit.event.entity.ItemMergeEvent event) {
        if (event.getEntity().getItemStack().getType() == Material.BONE_MEAL
                && Goal.progressEpoch().equals(event.getEntity().getPersistentDataContainer().get(COMPOST_OUTPUT, PersistentDataType.STRING)))
            event.getTarget().getPersistentDataContainer().set(COMPOST_OUTPUT, PersistentDataType.STRING, Goal.progressEpoch());
    }

    private static int amount(ItemStack item) { return item == null ? 0 : item.getAmount(); }
    private static int owned(Player player, ItemStack result) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) if (stack != null && stack.isSimilar(result)) count += stack.getAmount();
        ItemStack cursor = player.getItemOnCursor();
        return count + (cursor.isSimilar(result) ? cursor.getAmount() : 0);
    }
    private static Set<UUID> nearbyItems(org.bukkit.Location center, ItemStack match) {
        Set<UUID> ids = new HashSet<>();
        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, 1.5, 1.5, 1.5))
            if (entity instanceof Item item && (match == null || item.getItemStack().isSimilar(match))) ids.add(item.getUniqueId());
        return ids;
    }
}
