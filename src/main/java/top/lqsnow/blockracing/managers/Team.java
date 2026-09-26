package top.lqsnow.blockracing.managers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static top.lqsnow.blockracing.managers.Scoreboard.scoreboard;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public class Team {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    // Compatibility fields for red & blue
    public static org.bukkit.scoreboard.Team redTeam;
    public static org.bukkit.scoreboard.Team blueTeam;
    public static org.bukkit.scoreboard.Team greenTeam;
    public static org.bukkit.scoreboard.Team yellowTeam;

    public static List<String> redTeamPlayers = new CopyOnWriteArrayList<>();
    public static List<String> blueTeamPlayers = new CopyOnWriteArrayList<>();
    public static List<String> greenTeamPlayers = new CopyOnWriteArrayList<>();
    public static List<String> yellowTeamPlayers = new CopyOnWriteArrayList<>();

    private static final Map<String, List<String>> PLAYERS_BY_TEAM = new ConcurrentHashMap<>();
    private static final Map<String, org.bukkit.scoreboard.Team> SCOREBOARD_TEAMS = new ConcurrentHashMap<>();

    static {
        PLAYERS_BY_TEAM.put("red", redTeamPlayers);
        PLAYERS_BY_TEAM.put("blue", blueTeamPlayers);
        PLAYERS_BY_TEAM.put("green", greenTeamPlayers);
        PLAYERS_BY_TEAM.put("yellow", yellowTeamPlayers);
    }

    public static void createTeam() {
        if (scoreboard == null) return;
        SCOREBOARD_TEAMS.clear();
        for (TeamId teamId : TeamId.ALL) {
            String id = teamId.id();
            org.bukkit.scoreboard.Team sbTeam = scoreboard.getTeam(id);
            if (sbTeam == null) {
                sbTeam = scoreboard.registerNewTeam(id);
            }
            Message nameMsg = getTeamNameMessage(id);
            Message prefixMsg = getTeamPrefixMessage(id);
            sbTeam.displayName(LEGACY_SERIALIZER.deserialize(nameMsg.getString()));
            sbTeam.prefix(LEGACY_SERIALIZER.deserialize(prefixMsg.getString()));
            sbTeam.color(teamId.textColor());
            SCOREBOARD_TEAMS.put(id, sbTeam);
        }
        redTeam = SCOREBOARD_TEAMS.get("red");
        blueTeam = SCOREBOARD_TEAMS.get("blue");
        greenTeam = SCOREBOARD_TEAMS.get("green");
        yellowTeam = SCOREBOARD_TEAMS.get("yellow");
        Scoreboard.syncPlayerTeams();
    }

    public static org.bukkit.scoreboard.Team getScoreboardTeam(String teamId) {
        return SCOREBOARD_TEAMS.get(teamId.toLowerCase(Locale.ROOT));
    }

    public static List<String> getPlayers(String teamId) {
        return PLAYERS_BY_TEAM.getOrDefault(teamId.toLowerCase(Locale.ROOT), List.of());
    }

    public static List<String> getActiveTeamIds() {
        List<String> list = new ArrayList<>();
        for (TeamId t : TeamId.ALL) {
            if (!getPlayers(t.id()).isEmpty()) {
                list.add(t.id());
            }
        }
        return list;
    }

    public static boolean joinTeam(Player player, String teamId, boolean sendMessage) {
        TeamId team = TeamId.fromString(teamId);
        if (team == null) return false;
        org.bukkit.scoreboard.Team sbTeam = getScoreboardTeam(team.id());
        return joinTeam(player, sbTeam, sendMessage);
    }

    public static boolean joinTeam(Player player, org.bukkit.scoreboard.Team targetTeam, boolean sendMessage) {
        if (Game.getCurrentGameState() == Game.GameState.INGAME) return false;
        if (targetTeam == null) return false;
        String targetId = targetTeam.getName().toLowerCase(Locale.ROOT);
        TeamId team = TeamId.fromString(targetId);
        if (team == null) return false;

        List<String> targetList = getPlayers(targetId);
        if (targetList.contains(player.getName())) {
            if (sendMessage) {
                player.sendMessage(getAlreadyInTeamMessage(targetId).getString(player));
            }
            return false;
        }

        // Leave all other teams
        for (TeamId other : TeamId.ALL) {
            String otherId = other.id();
            List<String> otherList = getPlayers(otherId);
            if (otherList.contains(player.getName())) {
                org.bukkit.scoreboard.Team otherSb = getScoreboardTeam(otherId);
                if (otherSb != null) otherSb.removeEntry(player.getName());
                otherList.remove(player.getName());
            }
        }

        targetTeam.addEntry(player.getName());
        targetList.add(player.getName());
        Scoreboard.syncPlayerTeams();

        if (sendMessage) {
            sendAll(getJoinTeamMessage(targetId),
                    (viewer, text) -> text.replace("%player%", player.getName()));
        }
        top.lqsnow.blockracing.network.TaskBoardBridge.pushAll();
        return true;
    }

    public static boolean isPlayerInTeam(Player player, String teamId) {
        return getPlayers(teamId).contains(player.getName());
    }

    public static boolean isPlayerInRedTeam(Player player) {
        return redTeamPlayers.contains(player.getName());
    }

    public static boolean isPlayerInBlueTeam(Player player) {
        return blueTeamPlayers.contains(player.getName());
    }

    public static void clearTeams() {
        for (TeamId t : TeamId.ALL) {
            String id = t.id();
            org.bukkit.scoreboard.Team sb = getScoreboardTeam(id);
            if (sb != null) {
                new HashSet<>(sb.getEntries()).forEach(sb::removeEntry);
            }
            getPlayers(id).clear();
        }
        Scoreboard.syncPlayerTeams();
    }

    public static void restoreTeams(Map<String, List<String>> teams) {
        clearTeams();
        if (teams != null) {
            teams.forEach((id, members) -> {
                String teamId = id.toLowerCase(Locale.ROOT);
                List<String> list = getPlayers(teamId);
                org.bukkit.scoreboard.Team sb = getScoreboardTeam(teamId);
                if (list != null && members != null) {
                    list.addAll(members);
                    if (sb != null) {
                        members.forEach(sb::addEntry);
                    }
                }
            });
        }
        Scoreboard.syncPlayerTeams();
    }

    public static void restoreTeams(List<String> redPlayers, List<String> bluePlayers) {
        Map<String, List<String>> map = new HashMap<>();
        map.put("red", redPlayers != null ? redPlayers : List.of());
        map.put("blue", bluePlayers != null ? bluePlayers : List.of());
        restoreTeams(map);
    }

    public static String getTeam(Player player) {
        return player == null ? "" : getTeam(player.getName());
    }

    public static String getTeam(String playerName) {
        if (playerName == null) return "";
        for (TeamId t : TeamId.ALL) {
            if (getPlayers(t.id()).contains(playerName)) {
                return t.id();
            }
        }
        return "";
    }

    public static Message getTeamNameMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.TEAM_RED_NAME;
            case "blue" -> Message.TEAM_BLUE_NAME;
            case "green" -> Message.TEAM_GREEN_NAME;
            case "yellow" -> Message.TEAM_YELLOW_NAME;
            default -> Message.TEAM_RED_NAME;
        };
    }

    public static Message getTeamPrefixMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.TEAM_RED_PREFIX;
            case "blue" -> Message.TEAM_BLUE_PREFIX;
            case "green" -> Message.TEAM_GREEN_PREFIX;
            case "yellow" -> Message.TEAM_YELLOW_PREFIX;
            default -> Message.TEAM_RED_PREFIX;
        };
    }

    public static Message getTeamChatMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.TEAM_RED_CHAT;
            case "blue" -> Message.TEAM_BLUE_CHAT;
            case "green" -> Message.TEAM_GREEN_CHAT;
            case "yellow" -> Message.TEAM_YELLOW_CHAT;
            default -> Message.TEAM_RED_CHAT;
        };
    }

    public static Message getTeamColorMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.TEAM_RED_COLOR;
            case "blue" -> Message.TEAM_BLUE_COLOR;
            case "green" -> Message.TEAM_GREEN_COLOR;
            case "yellow" -> Message.TEAM_YELLOW_COLOR;
            default -> Message.TEAM_RED_COLOR;
        };
    }

    public static Message getJoinTeamMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_JOIN_RED;
            case "blue" -> Message.NOTICE_JOIN_BLUE;
            case "green" -> Message.NOTICE_JOIN_GREEN;
            case "yellow" -> Message.NOTICE_JOIN_YELLOW;
            default -> Message.NOTICE_JOIN_RED;
        };
    }

    public static Message getAlreadyInTeamMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_ALREADY_IN_RED;
            case "blue" -> Message.NOTICE_ALREADY_IN_BLUE;
            case "green" -> Message.NOTICE_ALREADY_IN_GREEN;
            case "yellow" -> Message.NOTICE_ALREADY_IN_YELLOW;
            default -> Message.NOTICE_ALREADY_IN_RED;
        };
    }

    public static Message getTeamCollectMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_RED_COLLECT;
            case "blue" -> Message.NOTICE_BLUE_COLLECT;
            case "green" -> Message.NOTICE_GREEN_COLLECT;
            case "yellow" -> Message.NOTICE_YELLOW_COLLECT;
            default -> Message.NOTICE_RED_COLLECT;
        };
    }

    public static Message getTeamWinMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_RED_WIN;
            case "blue" -> Message.NOTICE_BLUE_WIN;
            case "green" -> Message.NOTICE_GREEN_WIN;
            case "yellow" -> Message.NOTICE_YELLOW_WIN;
            default -> Message.NOTICE_RED_WIN;
        };
    }

    public static Message getTeamChestMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_RED_TEAM_CHEST;
            case "blue" -> Message.NOTICE_BLUE_TEAM_CHEST;
            case "green" -> Message.NOTICE_GREEN_TEAM_CHEST;
            case "yellow" -> Message.NOTICE_YELLOW_TEAM_CHEST;
            default -> Message.NOTICE_RED_TEAM_CHEST;
        };
    }

    public static Message getMenuTeamChestMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.MENU_RED_CHEST;
            case "blue" -> Message.MENU_BLUE_CHEST;
            case "green" -> Message.MENU_GREEN_CHEST;
            case "yellow" -> Message.MENU_YELLOW_CHEST;
            default -> Message.MENU_RED_CHEST;
        };
    }

    public static Message getRemoveWaypointMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_RED_REMOVE_WAYPOINT;
            case "blue" -> Message.NOTICE_BLUE_REMOVE_WAYPOINT;
            case "green" -> Message.NOTICE_GREEN_REMOVE_WAYPOINT;
            case "yellow" -> Message.NOTICE_YELLOW_REMOVE_WAYPOINT;
            default -> Message.NOTICE_RED_REMOVE_WAYPOINT;
        };
    }

    public static Message getScoreboardScoreMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.SCOREBOARD_RED_SCORE;
            case "blue" -> Message.SCOREBOARD_BLUE_SCORE;
            case "green" -> Message.SCOREBOARD_GREEN_SCORE;
            case "yellow" -> Message.SCOREBOARD_YELLOW_SCORE;
            default -> Message.SCOREBOARD_RED_SCORE;
        };
    }

    public static Message getRankingMessage(String teamId) {
        return switch (teamId.toLowerCase(Locale.ROOT)) {
            case "red" -> Message.NOTICE_RANKING_RED;
            case "blue" -> Message.NOTICE_RANKING_BLUE;
            case "green" -> Message.NOTICE_RANKING_GREEN;
            case "yellow" -> Message.NOTICE_RANKING_YELLOW;
            default -> Message.NOTICE_RANKING_RED;
        };
    }
}
