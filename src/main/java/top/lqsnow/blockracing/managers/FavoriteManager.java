package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import top.lqsnow.blockracing.network.TaskBoardBridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FavoriteManager {
    public static final int DEFAULT_MAX_FAVORITES = 5;
    private static final Map<String, List<String>> FAVORITES = new ConcurrentHashMap<>();

    static {
        for (TeamId t : TeamId.ALL) {
            FAVORITES.put(t.id(), new ArrayList<>());
        }
    }

    private FavoriteManager() {
    }

    public static int getMaxFavorites() {
        return Setting.getMaxFavoriteTargets();
    }

    public static synchronized List<String> getFavorites(String team) {
        if (team == null) return List.of();
        List<String> list = FAVORITES.get(team.toLowerCase(Locale.ROOT));
        return list == null ? List.of() : List.copyOf(list);
    }

    public static synchronized boolean isFavorited(String team, String target) {
        if (team == null || target == null) return false;
        List<String> list = FAVORITES.get(team.toLowerCase(Locale.ROOT));
        return list != null && list.contains(target);
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
        List<String> remaining = Block.getTeamRemainingBlocks(team);
        if (!remaining.contains(target)) {
            player.sendMessage(Message.NOTICE_FAVORITE_NOT_AVAILABLE.getString(player));
            return false;
        }

        List<String> list = FAVORITES.computeIfAbsent(team.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
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
        if (team == null) return false;
        List<String> list = FAVORITES.get(team.toLowerCase(Locale.ROOT));
        if (list != null && list.remove(target)) {
            onFavoritesChanged();
            return true;
        }
        return false;
    }

    public static synchronized void reset() {
        for (TeamId t : TeamId.ALL) {
            FAVORITES.put(t.id(), new ArrayList<>());
        }
    }

    public static synchronized void restore(Map<String, List<String>> map) {
        reset();
        if (map == null) return;
        map.forEach((teamId, favs) -> {
            String id = teamId.toLowerCase(Locale.ROOT);
            List<String> targetList = FAVORITES.computeIfAbsent(id, k -> new ArrayList<>());
            List<String> remaining = Block.getTeamRemainingBlocks(id);
            if (favs != null) {
                for (String t : favs) {
                    if (targetList.size() < getMaxFavorites() && remaining.contains(t)) {
                        targetList.add(t);
                    }
                }
            }
        });
    }

    public static synchronized void restore(List<String> redFavs, List<String> blueFavs) {
        Map<String, List<String>> map = new HashMap<>();
        if (redFavs != null) map.put("red", redFavs);
        if (blueFavs != null) map.put("blue", blueFavs);
        restore(map);
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
        List<String> members = Team.getPlayers(team);
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
