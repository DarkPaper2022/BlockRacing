package top.lqsnow.blockracing.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;
import top.lqsnow.blockracing.utils.TranslationUtil;

import java.util.*;

import static top.lqsnow.blockracing.managers.Block.*;
import static top.lqsnow.blockracing.managers.Game.*;

public final class Scoreboard {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final Map<UUID, PlayerBoard> PLAYER_BOARDS = new HashMap<>();

    public static final org.bukkit.scoreboard.Scoreboard scoreboard = getInitialScoreboard();
    public static Objective sidebar;

    private static org.bukkit.scoreboard.Scoreboard getInitialScoreboard() {
        try {
            return Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getNewScoreboard() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Scoreboard() {
    }

    public static void createScoreboard() {
        if (scoreboard != null) {
            sidebar = createSidebar(scoreboard);
        }
    }

    public static void setPreGameScoreboard() {
        if (scoreboard != null && sidebar != null) {
            renderPreGame(scoreboard, sidebar, null);
        }
        PLAYER_BOARDS.forEach((uuid, view) ->
                renderPreGame(view.scoreboard(), view.sidebar(), Bukkit.getPlayer(uuid)));
    }

    public static void setInGameScoreboard() {
        if (scoreboard != null && sidebar != null) {
            renderInGame(scoreboard, sidebar, null);
        }
        PLAYER_BOARDS.forEach((uuid, view) ->
                renderInGame(view.scoreboard(), view.sidebar(), Bukkit.getPlayer(uuid)));
    }

    public static String getBlockDisplay(String block) {
        return getBlockDisplay(block, null);
    }

    public static String getBlockDisplay(String block, Player player) {
        return Game.getTargetDisplayName(block, player) + " §7(" + Block.getTargetScore(block) + ")";
    }

    public static void showScoreboard(Player player) {
        PlayerBoard view = createPlayerBoard(player);
        PLAYER_BOARDS.put(player.getUniqueId(), view);
        syncPlayerTeams(view, player);
        renderCurrent(view, player);
        player.setScoreboard(view.scoreboard());
    }

    public static void refreshPlayer(Player player) {
        PlayerBoard view = PLAYER_BOARDS.get(player.getUniqueId());
        if (view == null) {
            showScoreboard(player);
            return;
        }
        syncPlayerTeams(view, player);
        renderCurrent(view, player);
        player.setScoreboard(view.scoreboard());
    }

    public static void removePlayer(Player player) {
        PLAYER_BOARDS.remove(player.getUniqueId());
    }

    public static void syncPlayerTeams() {
        PLAYER_BOARDS.forEach((uuid, view) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                syncPlayerTeams(view, player);
            }
        });
    }

    public static void updateScoreboard() {
        if (getCurrentGameState().equals(GameState.PREGAME)) {
            setPreGameScoreboard();
        } else if (getCurrentGameState().equals(GameState.INGAME) || getCurrentGameState().equals(GameState.END)) {
            setInGameScoreboard();
        }
    }

    private static PlayerBoard createPlayerBoard(Player player) {
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = createSidebar(board);
        for (TeamId teamId : TeamId.ALL) {
            Team team = board.registerNewTeam(teamId.id());
            localizeTeam(team, teamId, player);
        }
        return new PlayerBoard(board, objective);
    }

    private static Objective createSidebar(org.bukkit.scoreboard.Scoreboard board) {
        Objective objective = board.registerNewObjective("sidebar", Criteria.DUMMY, Component.empty());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 1; i <= 15; i++) {
            Team team = board.registerNewTeam("SLOT_" + i);
            team.addEntry(genEntry(i));
        }
        return objective;
    }

    private static void renderCurrent(PlayerBoard view, Player player) {
        if (getCurrentGameState().equals(GameState.INGAME) || getCurrentGameState().equals(GameState.END)) {
            renderInGame(view.scoreboard(), view.sidebar(), player);
        } else {
            renderPreGame(view.scoreboard(), view.sidebar(), player);
        }
    }

    private static void renderPreGame(org.bukkit.scoreboard.Scoreboard board,
                                      Objective objective, Player player) {
        clearLines(board);

        String baseMode = Setting.getCurrentGameMode().equals(Setting.GameMode.NORMAL)
                ? text(Message.SCOREBOARD_MODE_NORMAL, player)
                : text(Message.SCOREBOARD_MODE_RACING, player);
        String displayedGameMode = Setting.isSpeedMode()
                ? baseMode + " + " + text(Message.SCOREBOARD_MODE_SPEED, player)
                : baseMode;

        String blocks = text(Message.SCOREBOARD_BLOCKS_EASY, player)
                + (Setting.isEnableMediumBlock() ? " " + text(Message.SCOREBOARD_BLOCKS_MEDIUM, player) : "")
                + (Setting.isEnableHardBlock() ? " " + text(Message.SCOREBOARD_BLOCKS_HARD, player) : "");

        setTitle(objective, text(Message.SCOREBOARD_PREGAME_TITLE, player));
        Map<Integer, String> lines = new HashMap<>();
        for (int slot = 11; slot >= 1; slot--) {
            String originalMessage = text(Message.valueOf("SCOREBOARD_PREGAME_SLOT" + slot), player);
            if (slot == 1 && isLegacySlogan(originalMessage)) {
                originalMessage = player == null || LanguageManager.usesChinese(player)
                        ? "§7/language §8> §b切换语言"
                        : "§7/language §8> §bLanguage";
            }
            if (originalMessage.isEmpty()) {
                continue;
            }
            lines.put(slot, originalMessage
                    .replace("%game_mode%", displayedGameMode)
                    .replace("%block_amount%", String.valueOf(Setting.getBlockAmount()))
                    .replace("%victory_percent%", Setting.getVictoryScorePercent() + "%")
                    .replace("%mutual_exclusion%", text(Setting.isMutualExclusion()
                            ? Message.MENU_ENABLED : Message.MENU_DISABLED, player))
                    .replace("%blocks%", blocks));
        }
        lines.forEach((slot, line) -> setSlot(board, objective, slot, line));
    }

    private static void renderInGame(org.bukkit.scoreboard.Scoreboard board,
                                     Objective objective, Player player) {
        clearLines(board);
        setTitle(objective, text(Message.SCOREBOARD_INGAME_TITLE, player));

        // Determine which teams to show on scoreboard
        // Show teams with players (a single team may play alone); fall back to red/blue if none
        List<String> displayTeams = new ArrayList<>(top.lqsnow.blockracing.managers.Team.getActiveTeamIds());
        if (displayTeams.isEmpty()) {
            displayTeams.add(TeamId.RED.id());
            displayTeams.add(TeamId.BLUE.id());
        }

        int currentSlot = 15;
        for (String teamId : displayTeams) {
            if (currentSlot < 1) break;
            Message scoreMsg = top.lqsnow.blockracing.managers.Team.getScoreboardScoreMessage(teamId);
            String scoreLine = text(scoreMsg, player)
                    .replace("%score%", String.valueOf(Game.getTeamScore(teamId)))
                    .replace("%progress_score%", String.valueOf(Game.getTeamProgressScore(teamId)))
                    .replace("%win_score%", String.valueOf(Game.getTeamWinScore(teamId)))
                    .replace("%total_score%", String.valueOf(Game.getTeamTotalScore(teamId)))
                    .replace("%current_block%", String.valueOf(Game.getTeamCurrentBlockAmount(teamId)))
                    .replace("%total_block%", String.valueOf(Game.getTeamTotalBlockAmount(teamId)));
            setSlot(board, objective, currentSlot--, scoreLine);
        }

        // Blank line
        if (currentSlot >= 1) {
            setSlot(board, objective, currentSlot--, " ");
        }

        // Render favorite slots (for spectators without a team, don't fill with blanks)
        String playerTeam = top.lqsnow.blockracing.managers.Team.getTeam(player);
        if (!playerTeam.isEmpty()) {
            // Combined divider and team pinned header
            if (currentSlot >= 1) {
                String header = text(Message.SCOREBOARD_FAVORITES_HEADER, player);
                setSlot(board, objective, currentSlot--, header);
            }

            List<String> favorites = FavoriteManager.getFavorites(playerTeam);
            int maxSlots = Math.min(10, Math.max(1, Setting.getMaxFavoriteTargets()));
            for (int i = 0; i < maxSlots; i++) {
                if (currentSlot < 1) break;
                if (i < favorites.size()) {
                    String target = favorites.get(i);
                    setSlot(board, objective, currentSlot--, " §6" + (i + 1) + ". " + getBlockDisplay(target, player));
                } else {
                    setSlot(board, objective, currentSlot--, " ");
                }
            }
        }
    }

    private static void syncPlayerTeams(PlayerBoard view, Player player) {
        for (TeamId teamId : TeamId.ALL) {
            Team team = view.scoreboard().getTeam(teamId.id());
            if (team == null) continue;
            new HashSet<>(team.getEntries()).forEach(team::removeEntry);
            top.lqsnow.blockracing.managers.Team.getPlayers(teamId.id()).forEach(team::addEntry);
            localizeTeam(team, teamId, player);
        }
    }

    private static void localizeTeam(Team team, TeamId teamId, Player player) {
        Message nameMsg = top.lqsnow.blockracing.managers.Team.getTeamNameMessage(teamId.id());
        Message prefixMsg = top.lqsnow.blockracing.managers.Team.getTeamPrefixMessage(teamId.id());
        team.displayName(LEGACY_SERIALIZER.deserialize(text(nameMsg, player)));
        team.prefix(LEGACY_SERIALIZER.deserialize(text(prefixMsg, player)));
        team.color(teamId.textColor());
    }

    private static String text(Message message, Player player) {
        return player == null ? message.getString() : message.getString(player);
    }

    private static boolean isLegacySlogan(String text) {
        return text != null
                && text.replaceAll("(?i)§[0-9A-FK-OR]", "").equalsIgnoreCase("Enjoy the game!");
    }

    private static void clearLines(org.bukkit.scoreboard.Scoreboard board) {
        for (int slot = 1; slot <= 15; slot++) {
            board.resetScores(genEntry(slot));
        }
    }

    private static String genEntry(int slot) {
        return "\u00A7" + Integer.toHexString(slot) + "\u00A7r";
    }

    private static void setTitle(Objective objective, String title) {
        objective.displayName(LEGACY_SERIALIZER.deserialize(title));
    }

    private static void setSlot(org.bukkit.scoreboard.Scoreboard board,
                                Objective objective, int slot, String text) {
        Team team = board.getTeam("SLOT_" + slot);
        if (team == null || text == null) {
            return;
        }
        String entry = genEntry(slot);
        objective.getScore(entry).setScore(slot);
        team.prefix(LEGACY_SERIALIZER.deserialize(text));
        team.suffix(Component.empty());
    }

    private record PlayerBoard(org.bukkit.scoreboard.Scoreboard scoreboard, Objective sidebar) {
    }
}
