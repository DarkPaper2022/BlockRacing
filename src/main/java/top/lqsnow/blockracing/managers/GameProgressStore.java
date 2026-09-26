package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import top.lqsnow.blockracing.Main;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static top.lqsnow.blockracing.managers.Block.*;
import static top.lqsnow.blockracing.managers.Game.*;

public final class GameProgressStore {
    private static final int FORMAT_VERSION = 3;
    private static File file;
    private static boolean recoveredGame;

    private GameProgressStore() {
    }

    public static void startAutosave() {
        Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (Game.getCurrentGameState() == Game.GameState.INGAME) {
                saveNow();
            }
        }, 100L, 100L);
    }

    public static boolean load() {
        file = new File(Main.getInstance().getDataFolder(), "game-progress.yml");
        if (!file.isFile()) {
            return false;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(file);
        if (!Game.GameState.INGAME.name().equals(state.getString("state"))) {
            clear();
            return false;
        }
        try {
            if (!compatibleState(state)) {
                preserveIncompatibleState();
                Main.getInstance().getLogger().warning("Saved game uses different task rules; preserved a backup and starting in pregame.");
                return false;
            }
            Game.currentGameState = Game.GameState.INGAME;

            for (TeamId t : TeamId.ALL) {
                String id = t.id();
                Game.setTeamScore(id, state.getInt("scores." + id, 0));
                Game.setTeamProgressScore(id, state.getInt("victory." + id + "-progress", 0));
                Game.setTeamTotalScore(id, state.getInt("victory." + id + "-total", 0));
                Game.setTeamWinScore(id, state.getInt("victory." + id + "-threshold", 0));
                Game.setTeamCurrentBlockAmount(id, state.getInt("progress." + id + "-current", 0));
                Game.setTeamTotalBlockAmount(id, state.getInt("progress." + id + "-total", 0));

                List<String> allBlocks = state.getStringList("blocks." + id + "-all");
                if (!allBlocks.isEmpty()) {
                    Block.getTeamBlocks(id).clear();
                    Block.getTeamBlocks(id).addAll(allBlocks);
                }
                List<String> remBlocks = state.getStringList("blocks." + id + "-remaining");
                if (!remBlocks.isEmpty()) {
                    Block.getTeamRemainingBlocks(id).clear();
                    Block.getTeamRemainingBlocks(id).addAll(remBlocks);
                }
                List<String> bonusBlocks = state.getStringList("blocks." + id + "-bonus");
                if (!bonusBlocks.isEmpty()) {
                    Block.setTeamBonusBlocks(id, bonusBlocks);
                }

                Map<Integer, Location> wps = readLocations(state.getConfigurationSection("waypoints." + id));
                Game.getTeamWaypoints(id).clear();
                Game.getTeamWaypoints(id).putAll(wps);

                restoreChests(state, "chests." + id, Game.getTeamChests(id));
            }

            Setting.setBonusScoreThreshold(state.getInt("settings.bonus-score-threshold"));
            Setting.setBonusTargetAmount(state.getInt("settings.bonus-target-amount"));
            Setting.setAvailableTaskAmount(state.getInt("settings.available-task-amount"));
            Setting.setLocateCost(state.getInt("settings.locate-cost"));
            Setting.setRandomTeleportCost(state.getInt("settings.random-teleport-cost"));
            Goal.restoreProgress(state.getConfigurationSection("goals"));

            Game.redTeamRollCount = state.getInt("rolls.red");
            Game.blueTeamRollCount = state.getInt("rolls.blue");
            Game.locateCost = state.getInt("locate-cost");
            Setting.setSpeedMode(state.getBoolean("settings.speed-mode", Setting.isSpeedMode()));
            try {
                Setting.setCurrentGameMode(Setting.GameMode.valueOf(
                        state.getString("settings.game-mode", Setting.getCurrentGameMode().name())));
            } catch (IllegalArgumentException ignored) {
            }

            Map<String, List<String>> restoredTeamMap = new HashMap<>();
            for (TeamId t : TeamId.ALL) {
                restoredTeamMap.put(t.id(), state.getStringList("teams." + t.id()));
            }
            Team.restoreTeams(restoredTeamMap);

            replace(Game.inGamePlayers, state.getStringList("players.in-game"));
            replace(Game.freeRandomTPList, state.getStringList("players.free-random-tp"));
            replace(Game.locateCommandPermission, state.getStringList("players.locate-permission"));
            Game.collectAmount = readIntegerMap(state.getConfigurationSection("collected"));

            Map<String, List<String>> restoredFavs = new HashMap<>();
            for (TeamId t : TeamId.ALL) {
                restoredFavs.put(t.id(), state.getStringList("favorites." + t.id()));
            }
            FavoriteManager.restore(restoredFavs);

            recoveredGame = true;
            Main.getInstance().getLogger().info("Recovered unfinished BlockRacing game progress.");
            return true;
        } catch (RuntimeException ex) {
            preserveIncompatibleState();
            Goal.resetProgress();
            Main.getInstance().getLogger().log(Level.SEVERE,
                    "Unable to restore game-progress.yml; starting in pregame instead.", ex);
            Game.currentGameState = Game.GameState.PREGAME;
            return false;
        }
    }

    static boolean compatibleState(YamlConfiguration state) {
        int ver = state.getInt("format-version");
        return (ver == FORMAT_VERSION || ver == 2)
                && Block.definitionFingerprint().equals(state.getString("targets-fingerprint"));
    }

    private static void preserveIncompatibleState() {
        try {
            Files.copy(file.toPath(), file.toPath().resolveSibling(
                    "game-progress-incompatible-" + java.util.UUID.randomUUID() + ".yml"));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to preserve incompatible saved game", ex);
        }
    }

    public static synchronized void saveNow() {
        if (file == null) {
            file = new File(Main.getInstance().getDataFolder(), "game-progress.yml");
        }
        YamlConfiguration state = snapshot();
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            Files.writeString(temporary.toPath(), state.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Unable to save game progress.", ex);
        }
    }

    public static boolean hasRecoveredGame() {
        return recoveredGame;
    }

    public static void clear() {
        recoveredGame = false;
        if (file == null) {
            file = new File(Main.getInstance().getDataFolder(), "game-progress.yml");
        }
        try {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(new File(file.getParentFile(), file.getName() + ".tmp").toPath());
        } catch (IOException ex) {
            Main.getInstance().getLogger().log(Level.WARNING, "Unable to remove old game progress.", ex);
        }
    }

    static YamlConfiguration snapshot() {
        YamlConfiguration state = new YamlConfiguration();
        state.set("format-version", FORMAT_VERSION);
        state.set("targets-fingerprint", Block.definitionFingerprint());
        state.set("state", Game.getCurrentGameState().name());

        for (TeamId t : TeamId.ALL) {
            String id = t.id();
            state.set("scores." + id, Game.getTeamScore(id));
            state.set("victory." + id + "-progress", Game.getTeamProgressScore(id));
            state.set("victory." + id + "-total", Game.getTeamTotalScore(id));
            state.set("victory." + id + "-threshold", Game.getTeamWinScore(id));
            state.set("progress." + id + "-current", Game.getTeamCurrentBlockAmount(id));
            state.set("progress." + id + "-total", Game.getTeamTotalBlockAmount(id));
            state.set("blocks." + id + "-all", new ArrayList<>(Block.getTeamBlocks(id)));
            state.set("blocks." + id + "-remaining", new ArrayList<>(Block.getTeamRemainingBlocks(id)));
            state.set("blocks." + id + "-bonus", new ArrayList<>(Block.getTeamBonusBlocks(id)));
            state.set("teams." + id, new ArrayList<>(Team.getPlayers(id)));
            state.set("favorites." + id, new ArrayList<>(FavoriteManager.getFavorites(id)));

            Map<Integer, Location> wps = Game.getTeamWaypoints(id);
            wps.forEach((index, location) -> state.set("waypoints." + id + "." + index, location));
            saveChests(state, "chests." + id, Game.getTeamChests(id));
        }

        state.set("settings.bonus-score-threshold", Setting.getBonusScoreThreshold());
        state.set("settings.bonus-target-amount", Setting.getBonusTargetAmount());
        state.set("settings.available-task-amount", Setting.getAvailableTaskAmount());
        state.set("settings.locate-cost", Setting.getLocateCost());
        state.set("settings.random-teleport-cost", Setting.getRandomTeleportCost());
        Goal.saveProgress(state.createSection("goals"));

        state.set("rolls.red", Game.redTeamRollCount);
        state.set("rolls.blue", Game.blueTeamRollCount);
        state.set("locate-cost", Game.locateCost);
        state.set("settings.speed-mode", Setting.isSpeedMode());
        state.set("settings.game-mode", Setting.getCurrentGameMode().name());
        state.set("players.in-game", new ArrayList<>(Game.inGamePlayers));
        state.set("players.free-random-tp", new ArrayList<>(Game.freeRandomTPList));
        state.set("players.locate-permission", new ArrayList<>(Game.locateCommandPermission));
        Game.collectAmount.forEach((name, amount) -> state.set("collected." + name, amount));

        return state;
    }

    private static void saveChests(YamlConfiguration state, String path, List<Inventory> chests) {
        for (int index = 0; index < chests.size(); index++) {
            state.set(path + "." + index, Arrays.asList(chests.get(index).getContents()));
        }
    }

    private static void restoreChests(YamlConfiguration state, String path, List<Inventory> chests) {
        for (int index = 0; index < chests.size(); index++) {
            List<?> values = state.getList(path + "." + index, List.of());
            ItemStack[] contents = new ItemStack[chests.get(index).getSize()];
            for (int slot = 0; slot < values.size() && slot < contents.length; slot++) {
                if (values.get(slot) instanceof ItemStack item) {
                    contents[slot] = item;
                }
            }
            chests.get(index).setContents(contents);
        }
    }

    private static HashMap<Integer, Location> readLocations(ConfigurationSection section) {
        HashMap<Integer, Location> locations = new HashMap<>();
        if (section == null) {
            return locations;
        }
        for (String key : section.getKeys(false)) {
            Location location = section.getLocation(key);
            if (location != null) {
                locations.put(Integer.parseInt(key), location);
            }
        }
        return locations;
    }

    private static Map<String, Integer> readIntegerMap(ConfigurationSection section) {
        Map<String, Integer> values = new HashMap<>();
        if (section != null) {
            section.getKeys(false).forEach(key -> values.put(key, section.getInt(key)));
        }
        return values;
    }

    private static void replace(List<String> target, List<String> values) {
        target.clear();
        target.addAll(values);
    }
}
