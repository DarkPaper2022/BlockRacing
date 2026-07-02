package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import top.lqsnow.blockracing.Main;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

public class TeamWorldManager {
    private static final Map<String, String> TEAM_WORLD_PREFIX = Map.of(
            "red", "world_red",
            "blue", "world_blue"
    );

    private static long gameSeed;

    public static long getGameSeed() {
        return gameSeed;
    }

    public static void createTeamWorlds(long seed) {
        gameSeed = seed;
        for (String team : new String[]{"red", "blue"}) {
            String prefix = TEAM_WORLD_PREFIX.get(team);
            createAndSetupWorld(prefix, seed, World.Environment.NORMAL);
            createAndSetupWorld(prefix + "_nether", seed, World.Environment.NETHER);
            createAndSetupWorld(prefix + "_the_end", seed, World.Environment.THE_END);
        }
        Bukkit.getLogger().info("[BlockRacing] Created team worlds with seed: " + seed);
    }

    private static World createAndSetupWorld(String name, long seed, World.Environment environment) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            existing.getEntities().clear();
            return existing;
        }

        WorldCreator creator = new WorldCreator(name);
        creator.seed(seed);
        creator.environment(environment);
        World world = creator.createWorld();
        if (world == null) {
            Bukkit.getLogger().severe("[BlockRacing] Failed to create world: " + name);
            return null;
        }

        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.LOCATOR_BAR, false);
        world.setDifficulty(org.bukkit.Difficulty.HARD);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(1000);
        world.getWorldBorder().setCenter(world.getSpawnLocation());
        world.getWorldBorder().setSize(59999968);

        return world;
    }

    public static World getTeamOverworld(String team) {
        String prefix = TEAM_WORLD_PREFIX.get(team);
        return prefix != null ? Bukkit.getWorld(prefix) : null;
    }

    public static World getTeamNether(String team) {
        String prefix = TEAM_WORLD_PREFIX.get(team);
        return prefix != null ? Bukkit.getWorld(prefix + "_nether") : null;
    }

    public static World getTeamEnd(String team) {
        String prefix = TEAM_WORLD_PREFIX.get(team);
        return prefix != null ? Bukkit.getWorld(prefix + "_the_end") : null;
    }

    public static String getTeamForWorld(World world) {
        String name = world.getName();
        for (Map.Entry<String, String> entry : TEAM_WORLD_PREFIX.entrySet()) {
            if (name.equals(entry.getValue())
                    || name.equals(entry.getValue() + "_nether")
                    || name.equals(entry.getValue() + "_the_end")) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean isTeamWorld(String worldName) {
        for (String prefix : TEAM_WORLD_PREFIX.values()) {
            if (worldName.equals(prefix)
                    || worldName.equals(prefix + "_nether")
                    || worldName.equals(prefix + "_the_end")) {
                return true;
            }
        }
        return false;
    }

    public static World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }

    public static void teleportToTeamWorld(Player player) {
        String team = null;
        if (top.lqsnow.blockracing.managers.Team.redTeamPlayers.contains(player.getName())) {
            team = "red";
        } else if (top.lqsnow.blockracing.managers.Team.blueTeamPlayers.contains(player.getName())) {
            team = "blue";
        }
        if (team == null) return;

        World target = getTeamOverworld(team);
        if (target != null) {
            player.teleport(target.getSpawnLocation());
        }
    }

    public static void moveAllToLobby() {
        World lobby = getLobbyWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(lobby.getSpawnLocation());
        }
    }

    public static void deleteTeamWorlds() {
        moveAllToLobby();
        for (String team : new String[]{"red", "blue"}) {
            String prefix = TEAM_WORLD_PREFIX.get(team);
            deleteWorld(prefix);
            deleteWorld(prefix + "_nether");
            deleteWorld(prefix + "_the_end");
        }
        Bukkit.getLogger().info("[BlockRacing] Team worlds deleted.");
    }

    public static void deleteLeftoverWorlds() {
        for (String team : new String[]{"red", "blue"}) {
            String prefix = TEAM_WORLD_PREFIX.get(team);
            deleteWorldFiles(prefix);
            deleteWorldFiles(prefix + "_nether");
            deleteWorldFiles(prefix + "_the_end");
        }
    }

    private static void deleteWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            player.teleport(getLobbyWorld().getSpawnLocation());
        }

        Bukkit.unloadWorld(world, false);
        deleteWorldFiles(name);
    }

    private static void deleteWorldFiles(String name) {
        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        if (worldFolder.exists()) {
            deleteRecursively(worldFolder);
            Bukkit.getLogger().info("[BlockRacing] Deleted world folder: " + name);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        file.delete();
    }
}
