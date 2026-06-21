package top.lqsnow.blockracing.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import top.lqsnow.blockracing.Main;
import top.lqsnow.blockracing.managers.*;
import top.lqsnow.blockracing.menus.PreGameMenu;

import java.util.ArrayList;
import java.util.List;

import static top.lqsnow.blockracing.managers.Gui.updateMenu;
import static top.lqsnow.blockracing.managers.Scoreboard.updateScoreboard;
import static top.lqsnow.blockracing.managers.Team.isPlayerInBlueTeam;
import static top.lqsnow.blockracing.managers.Team.isPlayerInRedTeam;
import static top.lqsnow.blockracing.utils.ColorUtil.t;
import static top.lqsnow.blockracing.managers.Block.*;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public class BasicListener implements Listener {
    public static List<String> editAmountPlayer = new ArrayList<>();

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        Game.playerLogin(event.getPlayer());
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        Game.playerQuit(event.getPlayer());
    }

    @EventHandler
    private void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        // Open menu
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            Gui.openMenu(event.getPlayer());
        }
    }

    @EventHandler
    private void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Change block amount
        if (editAmountPlayer.contains(player.getName())) {
            if (!Game.getCurrentGameState().equals(Game.GameState.PREGAME)) return;
            if (event.getMessage().equals("quit")) {
                player.sendMessage(Message.NOTICE_SET_BLOCKS_QUIT.getString());
                editAmountPlayer.remove(player.getName());
                event.setCancelled(true);
                return;
            }
            boolean flag;
            int blockAmount = 0;
            try {
                blockAmount = Integer.parseInt(event.getMessage());
                flag = true;
            } catch (Exception ex) {
                player.sendMessage(Message.NOTICE_SET_BLOCKS_ERROR.getString());
                flag = false;
            } finally {
                event.setCancelled(true);
            }
            if (flag) {
                setBlockAmount(blockAmount, true);
                editAmountPlayer.remove(player.getName());
            }
        }

        // Change chat format
        if (isPlayerInRedTeam(player)) {
            event.setFormat(t(Message.TEAM_RED_CHAT.getString()));
        } else if (isPlayerInBlueTeam(player)) {
            event.setFormat(t(Message.TEAM_BLUE_CHAT.getString()));
        }
    }

    @EventHandler
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        event.getPlayer().sendMessage(Message.NOTICE_SPAWN_PROTECT.getString());
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false));
            if (Game.getCurrentGameState().equals(Game.GameState.INGAME) && Setting.isSpeedMode()) event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HASTE, -1, 4, false, false));
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 1, false, false));
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, -1, 1, false, false));
        }, 10L);
    }

    @EventHandler
    private void onEntityDeath(EntityDeathEvent event) {
        if (!isInGame()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            Goal.recordKill(killer, event.getEntityType());
        }
    }

    @EventHandler
    private void onEntityBreed(EntityBreedEvent event) {
        if (!isInGame() || !(event.getBreeder() instanceof Player player)) {
            return;
        }
        Goal.recordBreed(player, event.getEntityType());
    }

    @EventHandler
    private void onEntityTame(EntityTameEvent event) {
        if (!isInGame() || !(event.getOwner() instanceof Player player)) {
            return;
        }
        Goal.recordTame(player, event.getEntityType());
    }

    @EventHandler
    private void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!isInGame()) {
            return;
        }
        ItemStack item = event.getItem();
        Goal.recordConsume(event.getPlayer(), item.getType());
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof PotionMeta potionMeta && potionMeta.getBasePotionType() != null) {
            Goal.recordConsumePotion(event.getPlayer(), potionMeta.getBasePotionType().name());
        }
    }

    @EventHandler
    private void onPlayerFish(PlayerFishEvent event) {
        if (!isInGame() || !event.getState().equals(PlayerFishEvent.State.CAUGHT_FISH)) {
            return;
        }
        if (event.getCaught() instanceof Item item && isTreasureFishingLoot(item.getItemStack().getType())) {
            Goal.recordFishTreasure(event.getPlayer());
        }
    }

    @EventHandler
    private void onCraftItem(CraftItemEvent event) {
        if (!isInGame() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().equals(Material.AIR)) {
            result = event.getRecipe().getResult();
        }
        if (result != null && !result.getType().equals(Material.AIR)) {
            Goal.recordCraft(player, result.getType());
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent event) {
        if (!isInGame() || !(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory() instanceof MerchantInventory merchantInventory)
                || !(merchantInventory.getMerchant() instanceof Villager villager)) {
            return;
        }

        boolean wasMaster = villager.getVillagerLevel() >= 5;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (!wasMaster && villager.isValid() && villager.getVillagerLevel() >= 5) {
                Goal.recordMaxLevelVillager(player);
            }
        });
    }

    @EventHandler
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (!isInGame()) {
            return;
        }
        Action action = event.getAction();
        if (!action.equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        org.bukkit.block.Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            Goal.recordUseBlock(event.getPlayer(), clickedBlock.getType());
        }
    }

    @EventHandler
    private void onEntityDamage(EntityDamageEvent event) {
        if (!isInGame()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            Goal.recordDamageTaken(player, event.getFinalDamage());
        }

        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            Player damager = getPlayerDamager(damageByEntityEvent.getDamager());
            if (damager != null) {
                Goal.recordDamageDealt(damager, event.getFinalDamage());
            }
        }
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent event) {
        if (!isInGame()) {
            return;
        }

        EntityDamageEvent damageEvent = event.getEntity().getLastDamageCause();
        Entity attacker = null;
        if (damageEvent instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            attacker = damageByEntityEvent.getDamager();
            if (attacker instanceof Projectile projectile) {
                Goal.recordDeathProjectile(event.getEntity(), projectile.getType());
                if (projectile.getShooter() instanceof Entity shooter) {
                    attacker = shooter;
                }
            }
        }
        Goal.recordDeath(event.getEntity(), damageEvent == null ? null : damageEvent.getCause(), attacker == null ? null : attacker.getType());
    }

    private static boolean isTreasureFishingLoot(Material material) {
        return switch (material) {
            case BOW, ENCHANTED_BOOK, FISHING_ROD, NAME_TAG, NAUTILUS_SHELL, SADDLE -> true;
            default -> false;
        };
    }

    private static Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean isInGame() {
        return Game.getCurrentGameState().equals(Game.GameState.INGAME);
    }

    public static void setBlockAmount(int blockAmount, Boolean sendMessage) {
        Block.addUpBlocks();
        int maxAllowedBlockAmount = Math.min(maxBlockAmount, Setting.MAX_BLOCK_AMOUNT_LIMIT);
        if (blockAmount < 10) {
            if (sendMessage) sendAll(Message.NOTICE_SET_BLOCKS_SUCCESS.getString() + 10);
            blockAmount = 10;
        } else if (blockAmount > maxAllowedBlockAmount) {
            if (sendMessage) sendAll(Message.NOTICE_SET_BLOCKS_SUCCESS.getString() + maxAllowedBlockAmount);
            blockAmount = maxAllowedBlockAmount;
        } else {
            if (sendMessage) sendAll(Message.NOTICE_SET_BLOCKS_SUCCESS.getString() + blockAmount);
        }
        Setting.setBlockAmount(blockAmount);
        updateMenu(new PreGameMenu());
        updateScoreboard();
    }
}
