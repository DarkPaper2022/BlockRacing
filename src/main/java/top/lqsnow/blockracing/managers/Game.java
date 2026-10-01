package top.lqsnow.blockracing.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import top.lqsnow.blockracing.Main;
import top.lqsnow.blockracing.commands.Restart;
import top.lqsnow.blockracing.toolkit.item.ItemBuilder;
import top.lqsnow.blockracing.toolkit.material.Materials;
import top.lqsnow.blockracing.toolkit.text.Texts;
import static top.lqsnow.blockracing.listeners.BasicListener.editAmountPlayer;
import static top.lqsnow.blockracing.listeners.BasicListener.editVictoryPercentPlayer;
import static top.lqsnow.blockracing.managers.Block.*;
import static top.lqsnow.blockracing.managers.Block.blueTeamBlocks;
import static top.lqsnow.blockracing.managers.Block.blueTeamRemainingBlocks;
import static top.lqsnow.blockracing.managers.Block.checkBlock;
import static top.lqsnow.blockracing.managers.Block.redTeamBlocks;
import static top.lqsnow.blockracing.managers.Block.redTeamRemainingBlocks;
import static top.lqsnow.blockracing.managers.Block.setupBlocks;
import static top.lqsnow.blockracing.managers.Gui.closeAllPlayersMenu;
import static top.lqsnow.blockracing.managers.Scoreboard.updateScoreboard;
import static top.lqsnow.blockracing.managers.Team.blueTeamPlayers;
import static top.lqsnow.blockracing.managers.Team.redTeamPlayers;
import top.lqsnow.blockracing.utils.ColorUtil;
import static top.lqsnow.blockracing.utils.ColorUtil.t;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;
import static top.lqsnow.blockracing.utils.CommandUtil.sendBlue;
import static top.lqsnow.blockracing.utils.CommandUtil.sendRed;
import top.lqsnow.blockracing.utils.TranslationUtil;

public class Game {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    public enum GameState {
        PREGAME, INGAME, END
    }

    public static GameState currentGameState = GameState.PREGAME;
    public static List<String> readyPlayers = new ArrayList<>();
    private static final Map<String, Integer> teamScores = new ConcurrentHashMap<>();
    private static final Map<String, Integer> teamProgressScores = new ConcurrentHashMap<>();
    private static final Map<String, Integer> teamTotalScores = new ConcurrentHashMap<>();
    private static final Map<String, Integer> teamWinScores = new ConcurrentHashMap<>();
    private static final Map<String, Integer> teamCurrentBlockAmounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> teamTotalBlockAmounts = new ConcurrentHashMap<>();
    private static final Map<String, List<Inventory>> teamChests = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, Location>> teamWaypoints = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, Material>> teamWaypointIcons = new ConcurrentHashMap<>();

    public static int redTeamScore = 0;
    public static int blueTeamScore = 0;
    public static int redTeamProgressScore = 0;
    public static int blueTeamProgressScore = 0;
    public static int redTeamTotalScore = 0;
    public static int blueTeamTotalScore = 0;
    public static int redTeamWinScore = 0;
    public static int blueTeamWinScore = 0;

    public static int redTeamCurrentBlockAmount = 0;
    public static int blueTeamCurrentBlockAmount = 0;
    public static int redTeamTotalBlockAmount = 0;
    public static int blueTeamTotalBlockAmount = 0;
    public static List<String> freeRandomTPList = new ArrayList<>();

    public static ArrayList<Inventory> redTeamChest = new ArrayList<>();
    public static ArrayList<Inventory> blueTeamChest = new ArrayList<>();

    public static HashMap<Integer, Location> redWaypoint = new HashMap<>();
    public static HashMap<Integer, Location> blueWaypoint = new HashMap<>();

    public static HashMap<Integer, Material> redWaypointIconCache = new HashMap<>();
    public static HashMap<Integer, Material> blueWaypointIconCache = new HashMap<>();

    static {
        for (TeamId t : TeamId.ALL) {
            teamScores.put(t.id(), 0);
            teamProgressScores.put(t.id(), 0);
            teamTotalScores.put(t.id(), 0);
            teamWinScores.put(t.id(), 0);
            teamCurrentBlockAmounts.put(t.id(), 0);
            teamTotalBlockAmounts.put(t.id(), 0);
            teamChests.put(t.id(), new ArrayList<>());
            teamWaypoints.put(t.id(), new HashMap<>());
            teamWaypointIcons.put(t.id(), new HashMap<>());
        }
    }

    public static int getTeamScore(String team) {
        syncCompatFieldsToMap();
        return teamScores.getOrDefault(team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public static void setTeamScore(String team, int score) {
        teamScores.put(team.toLowerCase(java.util.Locale.ROOT), score);
        syncCompatFieldsFromMap();
    }

    public static int getTeamProgressScore(String team) {
        syncCompatFieldsToMap();
        return teamProgressScores.getOrDefault(team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public static void setTeamProgressScore(String team, int score) {
        teamProgressScores.put(team.toLowerCase(java.util.Locale.ROOT), score);
        syncCompatFieldsFromMap();
    }

    public static int getTeamTotalScore(String team) {
        syncCompatFieldsToMap();
        return teamTotalScores.getOrDefault(team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public static void setTeamTotalScore(String team, int score) {
        teamTotalScores.put(team.toLowerCase(java.util.Locale.ROOT), score);
        syncCompatFieldsFromMap();
    }

    public static int getTeamWinScore(String team) {
        syncCompatFieldsToMap();
        return teamWinScores.getOrDefault(team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public static void setTeamWinScore(String team, int score) {
        teamWinScores.put(team.toLowerCase(java.util.Locale.ROOT), score);
        syncCompatFieldsFromMap();
    }

    public static int getTeamCurrentBlockAmount(String team) {
        syncCompatFieldsToMap();
        return teamCurrentBlockAmounts.getOrDefault(team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public static void setTeamCurrentBlockAmount(String team, int amount) {
        teamCurrentBlockAmounts.put(team.toLowerCase(java.util.Locale.ROOT), amount);
        syncCompatFieldsFromMap();
    }

    public static int getTeamTotalBlockAmount(String team) {
        syncCompatFieldsToMap();
        return teamTotalBlockAmounts.getOrDefault(team.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public static void setTeamTotalBlockAmount(String team, int amount) {
        teamTotalBlockAmounts.put(team.toLowerCase(java.util.Locale.ROOT), amount);
        syncCompatFieldsFromMap();
    }

    public static List<Inventory> getTeamChests(String team) {
        syncCompatChestsToMap();
        return teamChests.computeIfAbsent(team.toLowerCase(java.util.Locale.ROOT), k -> new ArrayList<>());
    }

    public static Map<Integer, Location> getTeamWaypoints(String team) {
        syncCompatWaypointsToMap();
        return teamWaypoints.computeIfAbsent(team.toLowerCase(java.util.Locale.ROOT), k -> new HashMap<>());
    }

    public static Map<Integer, Material> getTeamWaypointIcons(String team) {
        return teamWaypointIcons.computeIfAbsent(team.toLowerCase(java.util.Locale.ROOT), k -> new HashMap<>());
    }

    public static void syncCompatFieldsToMap() {
        if (redTeamScore != 0 || !teamScores.containsKey("red") || teamScores.get("red") == 0) teamScores.put("red", redTeamScore);
        if (blueTeamScore != 0 || !teamScores.containsKey("blue") || teamScores.get("blue") == 0) teamScores.put("blue", blueTeamScore);
        if (redTeamProgressScore != 0 || !teamProgressScores.containsKey("red") || teamProgressScores.get("red") == 0) teamProgressScores.put("red", redTeamProgressScore);
        if (blueTeamProgressScore != 0 || !teamProgressScores.containsKey("blue") || teamProgressScores.get("blue") == 0) teamProgressScores.put("blue", blueTeamProgressScore);
        if (redTeamTotalScore != 0 || !teamTotalScores.containsKey("red") || teamTotalScores.get("red") == 0) teamTotalScores.put("red", redTeamTotalScore);
        if (blueTeamTotalScore != 0 || !teamTotalScores.containsKey("blue") || teamTotalScores.get("blue") == 0) teamTotalScores.put("blue", blueTeamTotalScore);
        if (redTeamWinScore != 0 || !teamWinScores.containsKey("red") || teamWinScores.get("red") == 0) teamWinScores.put("red", redTeamWinScore);
        if (blueTeamWinScore != 0 || !teamWinScores.containsKey("blue") || teamWinScores.get("blue") == 0) teamWinScores.put("blue", blueTeamWinScore);
        if (redTeamCurrentBlockAmount != 0 || !teamCurrentBlockAmounts.containsKey("red") || teamCurrentBlockAmounts.get("red") == 0) teamCurrentBlockAmounts.put("red", redTeamCurrentBlockAmount);
        if (blueTeamCurrentBlockAmount != 0 || !teamCurrentBlockAmounts.containsKey("blue") || teamCurrentBlockAmounts.get("blue") == 0) teamCurrentBlockAmounts.put("blue", blueTeamCurrentBlockAmount);
        if (redTeamTotalBlockAmount != 0 || !teamTotalBlockAmounts.containsKey("red") || teamTotalBlockAmounts.get("red") == 0) teamTotalBlockAmounts.put("red", redTeamTotalBlockAmount);
        if (blueTeamTotalBlockAmount != 0 || !teamTotalBlockAmounts.containsKey("blue") || teamTotalBlockAmounts.get("blue") == 0) teamTotalBlockAmounts.put("blue", blueTeamTotalBlockAmount);
    }

    public static void syncCompatFieldsFromMap() {
        redTeamScore = teamScores.getOrDefault("red", 0);
        blueTeamScore = teamScores.getOrDefault("blue", 0);
        redTeamProgressScore = teamProgressScores.getOrDefault("red", 0);
        blueTeamProgressScore = teamProgressScores.getOrDefault("blue", 0);
        redTeamTotalScore = teamTotalScores.getOrDefault("red", 0);
        blueTeamTotalScore = teamTotalScores.getOrDefault("blue", 0);
        redTeamWinScore = teamWinScores.getOrDefault("red", 0);
        blueTeamWinScore = teamWinScores.getOrDefault("blue", 0);
        redTeamCurrentBlockAmount = teamCurrentBlockAmounts.getOrDefault("red", 0);
        blueTeamCurrentBlockAmount = teamCurrentBlockAmounts.getOrDefault("blue", 0);
        redTeamTotalBlockAmount = teamTotalBlockAmounts.getOrDefault("red", 0);
        blueTeamTotalBlockAmount = teamTotalBlockAmounts.getOrDefault("blue", 0);
    }

    private static void syncCompatChestsToMap() {
        if (!redTeamChest.isEmpty() && teamChests.get("red").isEmpty()) {
            teamChests.put("red", redTeamChest);
        }
        if (!blueTeamChest.isEmpty() && teamChests.get("blue").isEmpty()) {
            teamChests.put("blue", blueTeamChest);
        }
    }

    private static void syncCompatWaypointsToMap() {
        if (!redWaypoint.isEmpty() && teamWaypoints.get("red").isEmpty()) {
            teamWaypoints.put("red", redWaypoint);
        }
        if (!blueWaypoint.isEmpty() && teamWaypoints.get("blue").isEmpty()) {
            teamWaypoints.put("blue", blueWaypoint);
        }
    }

    public static int redTeamRollCount;
    public static int blueTeamRollCount;
    public static List<String> redRollPlayers = new ArrayList<>();
    public static List<String> blueRollPlayers = new ArrayList<>();
    public static List<String> inGamePlayers = new ArrayList<>();
    public static ArrayList<String> locateCommandPermission = new ArrayList<>();
    public static int locateCost;
    public static Map<String, Integer> collectAmount = new HashMap<>();
    private static final Deque<Location> randomTpPool = new ArrayDeque<>();

    public static void initChest() {
        int teamChestNum = Setting.getMaxTeamChestNum();
        for (TeamId t : TeamId.ALL) {
            String teamId = t.id();
            List<Inventory> chests = teamChests.computeIfAbsent(teamId, k -> new ArrayList<>());
            chests.clear();
            Message titleMsg = Team.getMenuTeamChestMessage(teamId);
            for (int i = 0; i < teamChestNum; i++) {
                chests.add(Bukkit.createInventory(null, 6 * 9,
                        LEGACY_SERIALIZER.deserialize(titleMsg.getString() + " " + (i + 1))));
            }
        }
        redTeamChest = (ArrayList<Inventory>) teamChests.get("red");
        blueTeamChest = (ArrayList<Inventory>) teamChests.get("blue");
    }

    public static void playerLogin(Player player) {
        Scoreboard.showScoreboard(player);

        if (getCurrentGameState().equals(GameState.PREGAME)) {
            player.setGameMode(GameMode.ADVENTURE);
            resetPregameInventory(player);
            Message.NOTICE_WELCOME_LINES.getStringList(player).stream()
                    .map(line -> line.replace("%player%", player.getName()))
                    .forEach(player::sendMessage);
            player.teleport(getPrimaryWorld().getSpawnLocation());
        } else if (getCurrentGameState().equals(GameState.INGAME)) {
            if (GameProgressStore.hasRecoveredGame()) {
                Message.NOTICE_RECOVERED_GAME.getStringList(player).forEach(player::sendMessage);
                player.sendMessage(LEGACY_SERIALIZER.deserialize(
                                Message.NOTICE_RECOVERED_RESET_BUTTON.getString(player))
                        .clickEvent(ClickEvent.runCommand("/restartgame"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                LEGACY_SERIALIZER.deserialize(
                                        Message.NOTICE_RECOVERED_RESET_HOVER.getString(player)))));
            }
            // Spectator
            if (Team.getTeam(player).isEmpty()) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(Message.NOTICE_SPECTATOR_JOIN.getString(player));
                return;
            }

            Goal.refreshAdvancements(player);

            // Players who choose a team before the start of the game and exit, but enter
            // after the start of the game
            if (!inGamePlayers.contains(player.getName())) {
                initPlayer(player);
                inGamePlayers.add(player.getName());
                freeRandomTPList.add(player.getName());
                GameProgressStore.saveNow();
            }
        } else if (getCurrentGameState().equals(GameState.END)) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        // The permissions will disappear when the player exits and re-enters,
        // permissions need to be given again.
        if (locateCommandPermission.contains(player.getName())) {
            player.addAttachment(Main.getInstance(), "minecraft.command.locate", true);
        }

        checkUpdate(player);
    }

    private static void checkUpdate(Player player) {
        player.resetTitle();
        if (!Config.CONFIG_VERSION.getString().equals(Main.getVersion())
                || !Message.MESSAGE_VERSION.getString().equals(Main.getVersion())) {
            if (Message.NOTICE_VERSION_MISMATCH.getString() != null) {
                Message.NOTICE_VERSION_MISMATCH_LINES.getStringList(player)
                        .forEach(player::sendMessage);
                player.showTitle(Title.title(
                        LEGACY_SERIALIZER.deserialize(Message.NOTICE_VERSION_MISMATCH_TITLE.getString(player)),
                        LEGACY_SERIALIZER.deserialize(Message.NOTICE_VERSION_MISMATCH_SUBTITLE.getString(player)),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(100), Duration.ZERO)
                ));
            } else {
                player.sendMessage(ColorUtil.t(
                        "&cWarning! The current file versions of your config.yml and lang.yml do not correspond to the plugin version! You may have updated the plugin, but did not update the configuration file! This may lead to some unexpected errors! You can delete the two configuration files in the \\plugins\\BlockRacing folder, and then restart the server, or download the latest version of the configuration file on GitHub to replace it!"));
                player.showTitle(Title.title(
                        LEGACY_SERIALIZER.deserialize(ColorUtil.t("&cWarning! Version Mismatch!")),
                        LEGACY_SERIALIZER.deserialize(ColorUtil.t("&cPlease check the specific information in the chat!")),
                        Title.Times.times(Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO)
                ));
            }
            Bukkit.getLogger().severe(Message.NOTICE_VERSION_MISMATCH.getString());
        }
    }

    public static void playerQuit(Player player) {
        RandomTeleportManager.onPlayerQuit(player);
        Goal.playerDisconnected(player);
        if (currentGameState == GameState.INGAME) GameProgressStore.saveNow();
        redRollPlayers.remove(player.getName());
        blueRollPlayers.remove(player.getName());
        readyPlayers.remove(player.getName());
        editAmountPlayer.remove(player.getName());
        editVictoryPercentPlayer.remove(player.getName());
        Restart.removeVote(player);
    }

    public static void playerReady(Player player) {
        if (!readyPlayers.contains(player.getName())) {
            readyPlayers.add(player.getName());
            sendAll(Message.NOTICE_READY, (viewer, text) -> text.replace("%player%", player.getName()));
            // Check if the game can start, just notice players
            if (readyPlayers.size() == Bukkit.getOnlinePlayers().size()) {
                sendAll(Message.NOTICE_ALL_READY);
            }
        } else {
            readyPlayers.remove(player.getName());
            sendAll(Message.NOTICE_CANCEL_READY, (viewer, text) -> text.replace("%player%", player.getName()));
        }
    }

    // Check if the game can start. If not, send the reason to the player; if
    // possible, start the game directly
    public static void checkStartDemands(Player player) {

        // Game already start
        if (getCurrentGameState().equals(GameState.INGAME))
            return;

        // Check if all team players are ready (spectators who haven't joined a team do not block game start)
        List<String> teamPlayerNames = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!Team.getTeam(p).isEmpty()) {
                teamPlayerNames.add(p.getName());
            }
        }
        List<String> unreadyPlayers = new ArrayList<>(teamPlayerNames);
        unreadyPlayers.removeAll(readyPlayers);
        if (!unreadyPlayers.isEmpty()) {
            player.sendMessage(Message.NOTICE_EXIST_UNREADY.getString(player));
            player.sendMessage(Message.NOTICE_UNREADY_PLAYERS.getString(player) + unreadyPlayers);
            return;
        }

        // A single team may play alone; it still needs at least one player
        List<String> activeTeams = Team.getActiveTeamIds();
        if (activeTeams.isEmpty()) {
            player.sendMessage(Message.NOTICE_NOT_ENOUGH_PLAYERS.getString(player));
            return;
        }

        // Blocks have problems
        Block.refreshAvailableBlocksAndClampAmount();
        if (Setting.getBlockAmount() == 0 || !checkBlock()) {
            return;
        }
        try {
            Block.validateTestTargets();
        } catch (IllegalArgumentException invalidFixture) {
            Bukkit.getLogger().warning("[BlockRacing] Refusing to start: " + invalidFixture.getMessage());
            player.sendMessage("§c" + invalidFixture.getMessage());
            return;
        }

        // Start the game
        Bukkit.getLogger().info("[BlockRacing] Starting game with RTP pool ready="
                + RandomTeleportManager.getReadyCount()
                + "; cache misses use foreground center-only generation");
        sendAll(Message.NOTICE_START);
        startGame();
    }

    public static void startGame() {
        // Init
        setCurrentGameState(GameState.INGAME);
        closeAllPlayersMenu();
        editAmountPlayer.clear();
        editVictoryPercentPlayer.clear();
        redTeamRollCount = 0;
        blueTeamRollCount = 0;
        redRollPlayers.clear();
        blueRollPlayers.clear();
        redTeamScore = 0;
        blueTeamScore = 0;
        redTeamCurrentBlockAmount = 0;
        blueTeamCurrentBlockAmount = 0;
        collectAmount.clear();
        freeRandomTPList.clear();
        Goal.resetProgress();
        FavoriteManager.reset();
        inGamePlayers.clear();
        setupBlocks();

        int targetCount = Block.redTeamBlocks.size();
        int totalScore = Block.getMainTotalScore(Block.redTeamBlocks);
        int winScore = getWinScore(totalScore);

        for (TeamId t : TeamId.ALL) {
            String id = t.id();
            teamScores.put(id, 0);
            teamProgressScores.put(id, 0);
            teamTotalScores.put(id, totalScore);
            teamWinScores.put(id, winScore);
            teamCurrentBlockAmounts.put(id, 0);
            teamTotalBlockAmounts.put(id, targetCount);
        }
        syncCompatFieldsFromMap();

        locateCost = Setting.getLocateCost();
        updateScoreboard();
        Bukkit.getOnlinePlayers().forEach(RandomTeleportManager::grantFreeRtp);
        Bukkit.getOnlinePlayers().forEach((Player player) -> freeRandomTPList.add(player.getName()));
        new runPer5Tick().runTaskTimer(Main.getInstance(), 0L, 5L);
        World world = getPrimaryWorld();
        world.setDifficulty(Difficulty.HARD);
        world.setTime(1000);
        world.setStorm(false);
        world.setThundering(false);
        world.getEntities().stream().filter(e -> e instanceof Item).forEach(Entity::remove);
        world.setGameRule(GameRules.LOCATOR_BAR, false);

        // World border
        world.getWorldBorder().setCenter(world.getSpawnLocation());
        world.getWorldBorder().setSize(59999968);

        // Processing of unselected team players (spectators)
        inGamePlayers.addAll(getOnlinePlayersString());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Team.getTeam(player).isEmpty()) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
                inGamePlayers.remove(player.getName());
            }
        }

        // Settings for each player
        for (String p : inGamePlayers) {
            // General
            Player player = Bukkit.getPlayer(p);
            initPlayer(player);
            Goal.refreshAdvancements(player);
        }

        for (TeamId t : TeamId.ALL) {
            List<String> players = Team.getPlayers(t.id());
            if (!players.isEmpty()) {
                Bukkit.getLogger().info(t.id() + " team players: " + players);
            }
        }
        if (Setting.getCurrentGameMode().equals(Setting.GameMode.NORMAL))
            Bukkit.getLogger().info("Game mode: Normal");
        else if (Setting.getCurrentGameMode().equals(Setting.GameMode.RACING))
            Bukkit.getLogger().info("Game mode: Racing");
        Bukkit.getLogger().info(Setting.isSpeedMode() ? "Speed mode: On" : "Speed mode: Off");
        GameProgressStore.saveNow();
        top.lqsnow.blockracing.network.TaskBoardBridge.pushAll();
    }

    public static void resumeRecoveredGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Team.getTeam(player).isEmpty())
                Goal.refreshAdvancements(player);
        }
        new runPer5Tick().runTaskTimer(Main.getInstance(), 0L, 5L);
    }

    // Player init
    public static void initPlayer(Player player) {
        // General
        player.getInventory().clear();
        RandomTeleportManager.requestRtp(player, RandomTeleportManager.RequestReason.INITIAL, true);
        player.setHealth(20);
        player.setExp(0);
        player.setLevel(0);
        player.setFoodLevel(20);
        player.setSaturation(10);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().addItem(ItemBuilder.of(Material.STONE_PICKAXE).build());
        player.getInventory().addItem(ItemBuilder.of(Material.STONE_AXE).build());
        player.getInventory().addItem(ItemBuilder.of(Material.STONE_SHOVEL).build());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1200, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 1200, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false));

        // Speed mode
        if (Setting.isSpeedMode()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, -1, 4, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 1, false, false));
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, -1, 1, false, false));
            }, 1300L); // 延迟发放 避免冲突
            player.getInventory().addItem(ItemBuilder.of(Material.IRON_PICKAXE)
                    .enchant(Enchantment.SILK_TOUCH, 1)
                    .build());
            player.getInventory().addItem(ItemBuilder.of(Material.GOLDEN_CARROT).amount(64).build());

            ItemStack damagedElytra = new ItemStack(Material.ELYTRA);
            ItemMeta elytraMeta = damagedElytra.getItemMeta();
            Damageable damageable = (Damageable) elytraMeta;
            damageable.setDamage(damagedElytra.getType().getMaxDurability() - 1);
            Repairable repairable = (Repairable) elytraMeta;
            repairable.setRepairCost(15);
            damagedElytra.setItemMeta(elytraMeta);
            player.getInventory().addItem(damagedElytra);

            ItemStack xpBook = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) xpBook.getItemMeta();
            meta.addStoredEnchant(Enchantment.MENDING, 1, true);
            xpBook.setItemMeta(meta);
            player.getInventory().addItem(xpBook);
        }
    }

    // Roll
    public static void roll(Player player) {
        player.sendMessage(Message.NOTICE_CANNOT_ROLL.getString(player));
    }

    public static void locate(Player player) {
        String team = Team.getTeam(player);
        if (team.isEmpty()) {
            player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
            return;
        }
        int curScore = getTeamScore(team);
        if (locateCommandPermission.contains(player.getName())) {
            player.sendMessage(Message.NOTICE_LOCATE_ALREADY_BOUGHT.getString(player));
            return;
        }
        if (curScore >= locateCost) {
            setTeamScore(team, curScore - locateCost);
            player.addAttachment(Main.getInstance(), "minecraft.command.locate", true);
            locateCommandPermission.add(player.getName());
            sendAll(Message.NOTICE_BUY_LOCATE, (viewer, text) -> text.replace("%player%", player.getName()));
            playSound(() -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
            updateScoreboard();
            GameProgressStore.saveNow();
            return;
        }
        player.sendMessage(Message.NOTICE_NOT_ENOUGH_SCORE.getString(player));
    }

    // Random Teleport
    public static void randomTeleport(Player player, boolean avoidOcean) {
        RandomTeleportManager.requestRtp(player, RandomTeleportManager.RequestReason.USER, avoidOcean);
    }

    private static ItemStack createRuleBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Texts.component(Message.RULE_BOOK_TITLE.getString(player)));
        meta.author(Texts.component(Message.RULE_BOOK_AUTHOR.getString(player)));
        meta.pages(Texts.components(Message.RULE_BOOK_PAGES.getStringList(player)));
        book.setItemMeta(meta);
        return book;
    }

    private static void randomTeleportSynchronously(Player player, World playerWorld, boolean avoidOcean,
                                                    int maxAttempts) {
        Random random = new Random();
        Location offset = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Location candidate = pollRandomTeleportCandidate();
            double randX = candidate != null ? candidate.getX() : (random.nextInt(20000) - 10000);
            double randZ = candidate != null ? candidate.getZ() : (random.nextInt(20000) - 10000);
            Location current = playerWorld.getHighestBlockAt(new Location(playerWorld, randX, 0, randZ)).getLocation().add(0, 1, 0);
            offset = current;
            if (!avoidOcean || !isOcean(current.getBlock().getBiome())) {
                break;
            }
        }
        if (offset == null) return;
        completeRandomTeleport(player, offset, avoidOcean);
    }

    private static void startAsyncRandomTeleport(Player player, World world, boolean avoidOcean,
                                                 int attempt, int maxAttempts) {
        Random random = new Random();
        Location candidate = pollRandomTeleportCandidate();
        int blockX = candidate != null ? candidate.getBlockX() : random.nextInt(20000) - 10000;
        int blockZ = candidate != null ? candidate.getBlockZ() : random.nextInt(20000) - 10000;
        world.getChunkAtAsync(blockX >> 4, blockZ >> 4, true)
                .whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    if (attempt < maxAttempts) {
                        startAsyncRandomTeleport(player, world, avoidOcean, attempt + 1, maxAttempts);
                    } else {
                        randomTeleportSynchronously(player, world, avoidOcean, 1);
                    }
                    return;
                }
                Location offset = world.getHighestBlockAt(blockX, blockZ).getLocation().add(0, 1, 0);
                if (avoidOcean && isOcean(offset.getBlock().getBiome()) && attempt < maxAttempts) {
                    startAsyncRandomTeleport(player, world, true, attempt + 1, maxAttempts);
                    return;
                }
                completeRandomTeleport(player, offset, avoidOcean);
                }));
    }

    private static void completeRandomTeleport(Player player, Location offset, boolean avoidOcean) {
        player.teleport(offset);

        String x = String.format("%.1f", offset.getX());
        String y = String.format("%.1f", offset.getY());
        String z = String.format("%.1f", offset.getZ());

        player.sendMessage(Message.NOTICE_TP_SUCCESS.getString(player).replace("%x%", x).replace("%y%", y).replace("%z%", z));
        if (avoidOcean && isOcean(offset.getBlock().getBiome())) {
            player.sendMessage(Message.NOTICE_TP_OCEAN.getString(player));
        }
    }

    public static void resetPregameInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().addItem(createRuleBook(player));
    }

    public static void buySupply(Player buyer) {
        if (!Setting.isSpeedMode()) {
            buyer.sendMessage(Message.NOTICE_SUPPLY_SPEED_ONLY.getString(buyer));
            return;
        }
        String team = Team.getTeam(buyer);
        if (team.isEmpty()) {
            buyer.sendMessage(Message.NOTICE_SPECTATOR.getString(buyer));
            return;
        }
        int curScore = getTeamScore(team);
        if (curScore < 5) {
            buyer.sendMessage(Message.NOTICE_NOT_ENOUGH_SCORE.getString(buyer));
            return;
        }

        setTeamScore(team, curScore - 5);
        List<String> teammates = Team.getPlayers(team);
        for (String teammateName : teammates) {
            Player teammate = Bukkit.getPlayerExact(teammateName);
            if (teammate != null && teammate.isOnline()) {
                giveOrDrop(teammate, new ItemStack(Material.GOLDEN_CARROT, 16));
                giveOrDrop(teammate, new ItemStack(Material.FIREWORK_ROCKET, 32));
            }
        }

        String teamName = Team.getTeamNameMessage(team).getString(buyer);
        sendAll(Message.NOTICE_SUPPLY_PURCHASED, (viewer, text) -> text
                .replace("%player%", buyer.getName())
                .replace("%team%", teamName));

        playSound(() -> Sound.ENTITY_PLAYER_LEVELUP);
        updateScoreboard();
        GameProgressStore.saveNow();
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static boolean isOcean(Biome biome) {
        return biome == Biome.OCEAN || biome == Biome.DEEP_OCEAN || biome == Biome.DEEP_COLD_OCEAN
                || biome == Biome.LUKEWARM_OCEAN || biome == Biome.DEEP_FROZEN_OCEAN || biome == Biome.COLD_OCEAN
                || biome == Biome.WARM_OCEAN || biome == Biome.DEEP_LUKEWARM_OCEAN || biome == Biome.FROZEN_OCEAN;
    }

    public static synchronized void addRandomTeleportCandidate(Location location) {
    }

    public static synchronized Location pollRandomTeleportCandidate() {
        return RandomTeleportManager.pollCandidate();
    }

    public static synchronized int getRandomTeleportPoolSize() {
        return RandomTeleportManager.getPoolSize();
    }

    public static synchronized List<Location> getRandomTeleportPoolSnapshot() {
        return RandomTeleportManager.getPoolSnapshot();
    }

    // Waypoints
    // Return value: true -> waypoint changed, false -> waypoint doesn't change
    public static boolean waypoint(Player player, int index, ClickType clickType) {
        String team = Team.getTeam(player);
        if (!team.isEmpty()) {
            Location waypoint = getWaypoint(team, index);
            String action = "";
            if (clickType.equals(ClickType.LEFT)) {
                action = "left";
            } else if (clickType.equals(ClickType.RIGHT)) {
                action = "right";
            }

            switch (action) {
                case "left" -> {
                    if (waypoint == null) {
                        setWaypoint(player, team, index);
                        return true;
                    } else {
                        player.teleport(waypoint);
                        String x = String.format("%.1f", waypoint.getX());
                        String y = String.format("%.1f", waypoint.getY());
                        String z = String.format("%.1f", waypoint.getZ());

                        player.sendMessage(Message.NOTICE_TP_SUCCESS.getString(player).replace("%x%", x).replace("%y%", y)
                                .replace("%z%", z));
                        return false;
                    }
                }
                case "right" -> removeWaypoint(player, index);
            }
        }
        return false;
    }

    public static Location getWaypoint(String team, int index) {
        Map<Integer, Location> map = getTeamWaypoints(team);
        return map.get(index);
    }

    public static void setWaypoint(Player player, String team, int index) {
        Location waypoint = player.getLocation();
        getTeamWaypoints(team).put(index, waypoint);
        if ("red".equalsIgnoreCase(team)) {
            redWaypoint.put(index, waypoint);
        } else if ("blue".equalsIgnoreCase(team)) {
            blueWaypoint.put(index, waypoint);
        }
        GameProgressStore.saveNow();
    }

    private static void removeWaypoint(Player player, int index) {
        Component message = LEGACY_SERIALIZER.deserialize(
                        Message.NOTICE_REMOVE_WAYPOINT.getString(player).replace("%index%", String.valueOf(index)))
                .clickEvent(ClickEvent.runCommand("/waypoint remove " + index));
        player.sendMessage(message);
        player.closeInventory();
    }

    // Run per 2t
    // Before the game, provide regeneration and saturation effects
    public static class runPer2Tick extends BukkitRunnable {
        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10, 255));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 10, 255));
            }
            if (getCurrentGameState().equals(GameState.INGAME))
                this.cancel();
        }
    }

    // Run per 5t
    // During the game
    public static class runPer5Tick extends BukkitRunnable {

        @Override
        public void run() {
            if (!getCurrentGameState().equals(GameState.INGAME)) {
                this.cancel();
                return;
            }

            // Inventory check
            updateTimedGoalProgress();
            for (TeamId t : TeamId.ALL) {
                String teamId = t.id();
                if (Team.getPlayers(teamId).isEmpty()) continue;
                checkTeamInventory(teamId);
                if (!getCurrentGameState().equals(GameState.INGAME)) {
                    this.cancel();
                    return;
                }
            }
        }
    }

    private static void updateTimedGoalProgress() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Team.getTeam(player).isEmpty()) {
                continue;
            }
            Goal.recordContinuousWearTick(player, Material.CARVED_PUMPKIN, 5);
            Goal.recordSpyglassTarget(player);
        }
    }

    private static void showRanking() {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(collectAmount.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        sendAll(Message.NOTICE_RANKING);
        for (Map.Entry<String, Integer> entry : entries) {
            String pName = entry.getKey();
            String team = Team.getTeam(pName);
            if (!team.isEmpty()) {
                Message rankMsg = Team.getRankingMessage(team);
                sendAll(rankMsg, (viewer, text) -> text
                        .replace("%player%", pName).replace("%amount%", entry.getValue().toString()));
            } else {
                sendAll(Message.NOTICE_RANKING_OFFLINE, (viewer, text) -> text
                        .replace("%player%", pName).replace("%amount%", entry.getValue().toString()));
            }
        }
        sendAll(Message.NOTICE_RANKING_DIVIDER);
    }

    private static void checkTeamInventory(String teamId) {
        List<String> currentBlocks = getCurrentBlocks(teamId);
        List<String> players = Team.getPlayers(teamId);
        List<Inventory> chests = getTeamChests(teamId);
        String chestSource = Team.getTeamChestMessage(teamId).getString();

        for (String target : currentBlocks) {
            if (Goal.isGoal(target)) {
                String source = Goal.findCompletionSource(target, players, chests, chestSource);
                if (source != null) {
                    teamTaskComplete(teamId, target, source);
                    return;
                }
                continue;
            }
            for (String name : players) {
                Player player = Bukkit.getPlayer(name);
                if (player != null && player.getInventory().contains(Material.valueOf(target))) {
                    teamTaskComplete(teamId, target, name);
                    return;
                }
            }
            for (Inventory chest : chests) {
                if (chest.contains(Material.valueOf(target))) {
                    teamTaskComplete(teamId, target, chestSource);
                    return;
                }
            }
        }
    }

    public static void redTaskComplete(String block, String player) {
        teamTaskComplete("red", block, player);
    }

    public static void blueTaskComplete(String block, String player) {
        teamTaskComplete("blue", block, player);
    }

    public static void greenTaskComplete(String block, String player) {
        teamTaskComplete("green", block, player);
    }

    public static void yellowTaskComplete(String block, String player) {
        teamTaskComplete("yellow", block, player);
    }

    public static void teamTaskComplete(String teamId, String block, String player) {
        if (currentGameState != GameState.INGAME) return;
        List<String> remaining = Block.getTeamRemainingBlocks(teamId);
        if (!remaining.contains(block)) return;

        Message collectMsg = Team.getTeamCollectMessage(teamId);
        String chestNotice = Team.getTeamChestMessage(teamId).getString();

        sendAll(collectMsg, (viewer, text) -> text
                .replace("%block%", getTargetDisplayName(block, viewer))
                .replace("%player%", Goal.TEAM_COMPLETION_SOURCE.equals(player)
                        ? (LanguageManager.usesChinese(viewer) ? "队伍协作" : "Team effort")
                        : player.equals(chestNotice)
                        ? Team.getTeamChestMessage(teamId).getString(viewer) : player));

        Bukkit.getLogger().info(collectMsg.getString()
                .replace("%block%", getTargetDisplayName(block)).replace("%player%", player).replaceAll("§.", ""));
        playSound(() -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

        remaining.remove(block);
        FavoriteManager.onTaskCompletedOrRemoved(teamId, block);

        // Mutual task exclusion across all other teams; when disabled every team can score it
        if (Setting.isMutualExclusion()) {
            for (TeamId other : TeamId.ALL) {
                String otherId = other.id();
                if (otherId.equalsIgnoreCase(teamId)) continue;
                List<String> otherRem = Block.getTeamRemainingBlocks(otherId);
                if (skipMutualTask(otherRem, block)) {
                    FavoriteManager.onTaskCompletedOrRemoved(otherId, block);
                    setTeamTotalBlockAmount(otherId, getTeamTotalBlockAmount(otherId) - 1);
                }
            }
        }

        int targetScore = Block.getTargetScore(block);
        setTeamProgressScore(teamId, getTeamProgressScore(teamId) + targetScore);
        setTeamScore(teamId, getTeamScore(teamId) + targetScore);
        setTeamCurrentBlockAmount(teamId, getTeamCurrentBlockAmount(teamId) + 1);

        if (!Goal.TEAM_COMPLETION_SOURCE.equals(player)) collect(player);

        updateScoreboard();
        GameProgressStore.saveNow();
        top.lqsnow.blockracing.network.TaskBoardBridge.pushAll();

        if (getTeamProgressScore(teamId) >= getTeamWinScore(teamId)) {
            teamWin(teamId);
            return;
        }

        // Check if all non-bonus targets have been exhausted without any team reaching win score
        List<String> activeTeams = Team.getActiveTeamIds();
        boolean hasRemainingRegular = false;
        for (String id : activeTeams) {
            for (String t : Block.getTeamRemainingBlocks(id)) {
                if (!Block.isBonusTarget(t)) {
                    hasRemainingRegular = true;
                    break;
                }
            }
            if (hasRemainingRegular) break;
        }
        if (!hasRemainingRegular && !activeTeams.isEmpty()) {
            // Settle by highest progress score
            String leadingTeam = activeTeams.get(0);
            int highestScore = getTeamProgressScore(leadingTeam);
            for (int i = 1; i < activeTeams.size(); i++) {
                String candidate = activeTeams.get(i);
                int score = getTeamProgressScore(candidate);
                if (score > highestScore) {
                    highestScore = score;
                    leadingTeam = candidate;
                }
            }
            teamWin(leadingTeam);
            return;
        }

        // Put items into all other active teams' chests in Normal mode
        if (!Block.isBonusTarget(block) && !Goal.isGoal(block) && Setting.getCurrentGameMode().equals(Setting.GameMode.NORMAL)) {
            for (TeamId other : TeamId.ALL) {
                String otherId = other.id();
                if (otherId.equalsIgnoreCase(teamId) || Team.getPlayers(otherId).isEmpty()) continue;
                // Without mutual exclusion the gift would complete the receiver's own copy of the task.
                if (Block.getTeamRemainingBlocks(otherId).contains(block)) continue;
                List<Inventory> opponentChests = getTeamChests(otherId);
                boolean placed = false;
                for (int i = opponentChests.size() - 1; i >= 0; i--) {
                    Inventory chest = opponentChests.get(i);
                    int emptyPos = chest.firstEmpty();
                    if (emptyPos != -1) {
                        chest.setItem(emptyPos, Materials.stack(block, 64));
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    sendAll(Message.NOTICE_TEAM_CHEST_FULL, (viewer, text) -> text
                            .replace("%team%", Team.getTeamNameMessage(otherId).getString(viewer))
                            .replace("%block%", getTargetDisplayName(block, viewer)));
                }
            }
            GameProgressStore.saveNow();
        }
    }

    public static void redWin() {
        teamWin("red");
    }

    public static void blueWin() {
        teamWin("blue");
    }

    public static void greenWin() {
        teamWin("green");
    }

    public static void yellowWin() {
        teamWin("yellow");
    }

    public static void teamWin(String teamId) {
        Bukkit.getLogger().info("[BlockRacing] Round settled: winner=" + teamId
                + " progress=" + getTeamProgressScore(teamId) + "/" + getTeamWinScore(teamId));
        Message winMsg = Team.getTeamWinMessage(teamId);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.closeInventory();
            player.showTitle(Title.title(
                    LEGACY_SERIALIZER.deserialize(winMsg.getString(player)),
                    Component.empty()
            ));
            player.setGameMode(GameMode.SPECTATOR);
        }
        sendAll(winMsg);
        playSound(() -> Sound.UI_TOAST_CHALLENGE_COMPLETE);
        showRanking();
        setCurrentGameState(GameState.END);
        GameProgressStore.clear();
        top.lqsnow.blockracing.network.TaskBoardBridge.pushAll();
    }

    public static List<String> getCurrentBlocks(String team) {
        int availableTaskAmount = Math.max(1, Setting.getAvailableTaskAmount());
        List<String> rem = Block.getTeamRemainingBlocks(team);
        List<String> bonus = Block.getTeamBonusBlocks(team);
        return getAvailableTargets(rem, bonus, availableTaskAmount);
    }

    private static List<String> getAvailableTargets(List<String> remainingTargets, List<String> bonusTargets, int availableTaskAmount) {
        List<String> availableTargets = new ArrayList<>();
        for (String target : remainingTargets) {
            if (Block.isBonusTarget(target)) {
                continue;
            }
            availableTargets.add(target);
            if (availableTargets.size() >= availableTaskAmount) {
                break;
            }
        }

        for (String target : bonusTargets) {
            if (remainingTargets.contains(target)) {
                availableTargets.add(target);
            }
        }
        return availableTargets;
    }

    public static String getTargetDisplayName(String target, Player viewer) {
        return Block.getDisplayName(target, viewer);
    }

    public static String getTargetDisplayName(String target) {
        return Block.getDisplayName(target);
    }

    private static int getWinScore(int totalScore) {
        int percent = Setting.getVictoryScorePercent();
        return Math.max(1, (int) Math.ceil(totalScore * (percent / 100.0)));
    }

    private static boolean skipMutualTask(List<String> opponentRemainingBlocks, String completedBlock) {
        return opponentRemainingBlocks.remove(completedBlock);
    }

    public static List<String> getOnlinePlayersString() {
        List<String> onlinePlayers = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach((Player player) -> onlinePlayers.add(player.getName()));
        return onlinePlayers;
    }

    public static List<String> getOnlineTeamPlayers(String team) {
        List<String> onlineTeamPlayers = new ArrayList<>();
        for (String player : Team.getPlayers(team)) {
            if (Bukkit.getPlayerExact(player) != null) {
                onlineTeamPlayers.add(player);
            }
        }
        return onlineTeamPlayers;
    }

    public static void playSound(java.util.function.Supplier<Sound> soundSupplier) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        playSound(soundSupplier.get());
    }

    public static void playSound(Sound sound) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player, sound, 1F, 1F);
        }
    }

    public static String getCoords(Location location) {
        return String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    public static void collect(String p) {
        if (collectAmount.containsKey(p)) {
            int currentAmount = collectAmount.get(p);
            collectAmount.put(p, currentAmount + 1);
        } else {
            collectAmount.put(p, 1);
        }
    }

    public static GameState getCurrentGameState() {
        return currentGameState;
    }

    public static void setCurrentGameState(GameState currentGameState) {
        Game.currentGameState = currentGameState;
    }

    public static World getPrimaryWorld() {
        return Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No overworld is loaded"));
    }
}
