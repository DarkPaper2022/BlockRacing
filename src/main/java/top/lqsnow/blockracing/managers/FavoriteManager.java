package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import top.lqsnow.blockracing.network.TaskBoardBridge;

import java.util.*;

public final class FavoriteManager {
    public static final int DEFAULT_MAX_FAVORITES = 5;
    private static final List<String> redTeamFavorites = new ArrayList<>();
    private static final List<String> blueTeamFavorites = new ArrayList<>();

    private FavoriteManager() {
    }

    public static int getMaxFavorites() {
        return Setting.getMaxFavoriteTargets();
    }

    public static synchronized List<String> getFavorites(String team) {
        if ("red".equalsIgnoreCase(team)) {
            return List.copyOf(redTeamFavorites);
        } else if ("blue".equalsIgnoreCase(team)) {
            return List.copyOf(blueTeamFavorites);
        }
        return List.of();
    }

    public static synchronized boolean isFavorited(String team, String target) {
        if (target == null) return false;
        if ("red".equalsIgnoreCase(team)) {
            return redTeamFavorites.contains(target);
        } else if ("blue".equalsIgnoreCase(team)) {
            return blueTeamFavorites.contains(target);
        }
        return false;
    }

    public static synchronized boolean toggleFavorite(Player player, String target) {
        String team = Team.getTeam(player);
        if (team.isEmpty()) {
            player.sendMessage(Message.NOTICE_FAVORITE_NO_TEAM.getString(player));
            return false;
        }
        if (Game.getCurrentGameState() != Game.GameState.INGAME) {
            player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
            return false;
        }
        List<String> remaining = "red".equals(team) ? Block.redTeamRemainingBlocks : Block.blueTeamRemainingBlocks;
        if (!remaining.contains(target)) {
            player.sendMessage(Message.NOTICE_FAVORITE_NOT_AVAILABLE.getString(player));
            return false;
        }

        List<String> list = "red".equals(team) ? redTeamFavorites : blueTeamFavorites;
        if (list.contains(target)) {
            list.remove(target);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
            broadcastTeam(team, Message.NOTICE_FAVORITE_REMOVED, player, target);
            onFavoritesChanged();
            return true;
        }

        if (list.size() >= getMaxFavorites()) {
            player.sendMessage(Message.NOTICE_FAVORITE_LIMIT_REACHED.getString(player)
                    .replace("%max%", String.valueOf(getMaxFavorites())));
            return false;
        }

        list.add(target);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        broadcastTeam(team, Message.NOTICE_FAVORITE_ADDED, player, target);
        onFavoritesChanged();
        return true;
    }

    public static synchronized boolean onTaskCompletedOrRemoved(String team, String target) {
        List<String> list = "red".equalsIgnoreCase(team) ? redTeamFavorites
                : "blue".equalsIgnoreCase(team) ? blueTeamFavorites : null;
        if (list != null && list.remove(target)) {
            onFavoritesChanged();
            return true;
        }
        return false;
    }

    public static synchronized void reset() {
        redTeamFavorites.clear();
        blueTeamFavorites.clear();
    }

    public static synchronized void restore(List<String> redFavs, List<String> blueFavs) {
        redTeamFavorites.clear();
        blueTeamFavorites.clear();
        if (redFavs != null) {
            for (String t : redFavs) {
                if (redTeamFavorites.size() < getMaxFavorites() && Block.redTeamRemainingBlocks.contains(t)) {
                    redTeamFavorites.add(t);
                }
            }
        }
        if (blueFavs != null) {
            for (String t : blueFavs) {
                if (blueTeamFavorites.size() < getMaxFavorites() && Block.blueTeamRemainingBlocks.contains(t)) {
                    blueTeamFavorites.add(t);
                }
            }
        }
    }

    private static void onFavoritesChanged() {
        try {
            Scoreboard.updateScoreboard();
        } catch (Throwable ignored) {}
        try {
            TaskBoardBridge.pushAll();
        } catch (Throwable ignored) {}
        try {
            GameProgressStore.saveNow();
        } catch (Throwable ignored) {}
    }

    private static void broadcastTeam(String team, Message message, Player actor, String target) {
        List<String> members = "red".equals(team) ? Team.redTeamPlayers : Team.blueTeamPlayers;
        for (String name : members) {
            Player p = Bukkit.getPlayer(name);
            if (p != null && p.isOnline()) {
                p.sendMessage(message.getString(p)
                        .replace("%player%", actor.getName())
                        .replace("%target%", Game.getTargetDisplayName(target, p)));
            }
        }
    }
}
