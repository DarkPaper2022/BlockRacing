package top.lqsnow.blockracing.network;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import top.lqsnow.blockracing.managers.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskBoardBridgeTest {
    @Test void spectatorDoesNotReceiveAnotherTeamsSnapshotAndPregameHasNoOldTargets() throws Exception {
        var preferences = LanguageManager.class.getDeclaredField("preferences");
        preferences.setAccessible(true);
        Object oldPreferences = preferences.get(null);
        var oldState = Game.currentGameState;
        var oldRed = List.copyOf(Team.redTeamPlayers);
        var oldBlue = List.copyOf(Team.blueTeamPlayers);
        var oldTargets = Block.redTeamBlocks;
        int oldScore = Game.blueTeamProgressScore;
        Player viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "BoardViewer";
                    case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                    case "locale" -> Locale.SIMPLIFIED_CHINESE;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        try {
            preferences.set(null, new YamlConfiguration());
            Team.redTeamPlayers.clear();
            Team.blueTeamPlayers.clear();
            Game.currentGameState = Game.GameState.INGAME;
            Game.blueTeamProgressScore = 999;
            Block.redTeamBlocks = List.of("SECRET_OLD_TASK");
            var spectator = TaskBoardBridge.snapshot(viewer);
            assertEquals("", spectator.get("team"));
            assertEquals(0, spectator.get("score"));
            assertEquals(List.of(), spectator.get("tasks"));
            Team.redTeamPlayers.add("BoardViewer");
            Game.currentGameState = Game.GameState.PREGAME;
            var pregame = TaskBoardBridge.snapshot(viewer);
            assertEquals("red", pregame.get("team"));
            assertEquals(List.of(), pregame.get("tasks"));
            assertEquals(List.of("SECRET_OLD_TASK"), Block.redTeamBlocks);
        } finally {
            preferences.set(null, oldPreferences);
            Game.currentGameState = oldState;
            Team.redTeamPlayers.clear(); Team.redTeamPlayers.addAll(oldRed);
            Team.blueTeamPlayers.clear(); Team.blueTeamPlayers.addAll(oldBlue);
            Block.redTeamBlocks = oldTargets;
            Game.blueTeamProgressScore = oldScore;
        }
    }

    @Test void immediateOpenRequestSendsSnapshotAndThrottlesSpam() throws Exception {
        var preferences = LanguageManager.class.getDeclaredField("preferences");
        preferences.setAccessible(true);
        Object oldPreferences = preferences.get(null);
        var oldState = Game.currentGameState;
        var oldRed = List.copyOf(Team.redTeamPlayers);
        List<byte[]> sentPayloads = new ArrayList<>();
        UUID viewerId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Player viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "TabTester";
                    case "getUniqueId" -> viewerId;
                    case "locale" -> Locale.SIMPLIFIED_CHINESE;
                    case "getListeningPluginChannels" -> Set.of(BoardWire.SNAPSHOT);
                    case "sendPluginMessage" -> { sentPayloads.add((byte[]) args[2]); yield null; }
                    default -> null;
                });
        try {
            preferences.set(null, new YamlConfiguration());
            Team.redTeamPlayers.clear();
            Team.redTeamPlayers.add("TabTester");
            Game.currentGameState = Game.GameState.PREGAME;

            TaskBoardBridge bridge = new TaskBoardBridge(null);
            // First open request sends immediately (0-tick wait)
            bridge.onPluginMessageReceived(BoardWire.REQUEST, viewer, new byte[]{1});
            assertEquals(1, sentPayloads.size());
            // Immediate spam within 200ms cooldown is throttled
            bridge.onPluginMessageReceived(BoardWire.REQUEST, viewer, new byte[]{1});
            assertEquals(1, sentPayloads.size());
            // Channel registration also pushes initial snapshot
            bridge.onRegisterChannel(new org.bukkit.event.player.PlayerRegisterChannelEvent(viewer, BoardWire.SNAPSHOT));
            assertEquals(2, sentPayloads.size());
        } finally {
            preferences.set(null, oldPreferences);
            Game.currentGameState = oldState;
            Team.redTeamPlayers.clear();
            Team.redTeamPlayers.addAll(oldRed);
        }
    }
}
