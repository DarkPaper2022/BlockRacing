package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MultiTeamRoundSettlementTest {
    @TempDir
    Path tempDir;
    private Server previousServer;

    @BeforeEach
    void setup() throws Exception {
        TaskSamplingTest.configure();
        installServerStub();
        Field progressFileField = GameProgressStore.class.getDeclaredField("file");
        progressFileField.setAccessible(true);
        progressFileField.set(null, new File(tempDir.toFile(), "game-progress.yml"));

        Map<String, Integer> pool = new HashMap<>();
        for (int i = 0; i < 30; i++) pool.put("EASY_" + i, 1);
        for (int i = 0; i < 100; i++) pool.put("NORMAL_" + i, 2 + i % 9);
        for (int i = 0; i < 7; i++) pool.put("BONUS_" + i, 11 + i);
        Block.targetScores = Map.copyOf(pool);

        for (TeamId t : TeamId.ALL) {
            String id = t.id();
            Block.getTeamBlocks(id).clear();
            Block.getTeamRemainingBlocks(id).clear();
            Block.setTeamBonusBlocks(id, List.of());
            Team.getPlayers(id).clear();
            Game.setTeamScore(id, 0);
            Game.setTeamProgressScore(id, 0);
            Game.setTeamTotalScore(id, 0);
            Game.setTeamWinScore(id, 0);
            Game.setTeamCurrentBlockAmount(id, 0);
            Game.setTeamTotalBlockAmount(id, 0);
        }
        Game.collectAmount.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, previousServer);
        Game.currentGameState = Game.GameState.PREGAME;
    }

    @Test
    void fourTeamMutualExclusionAndVictorySettlement() {
        List<String> targetList = List.of("EASY_0", "NORMAL_1", "NORMAL_2", "BONUS_0");
        for (TeamId t : TeamId.ALL) {
            String id = t.id();
            Block.getTeamBlocks(id).addAll(targetList);
            Block.getTeamRemainingBlocks(id).addAll(targetList);
            Block.setTeamBonusBlocks(id, List.of("BONUS_0"));
            Game.setTeamTotalBlockAmount(id, 4);
            Game.setTeamCurrentBlockAmount(id, 0);
            Game.setTeamTotalScore(id, 6); // EASY_0 (1) + NORMAL_1 (3) + NORMAL_2 (2) = 6
            Game.setTeamWinScore(id, 4);   // Win at 4 pts
        }

        Team.getPlayers("red").add("Alice");
        Team.getPlayers("blue").add("Bob");
        Team.getPlayers("green").add("Charlie");
        Team.getPlayers("yellow").add("Diana");

        Game.currentGameState = Game.GameState.INGAME;

        // Red team completes EASY_0
        Game.teamTaskComplete("red", "EASY_0", "Alice");
        assertEquals(Game.GameState.INGAME, Game.currentGameState);
        assertEquals(1, Game.getTeamProgressScore("red"));
        assertEquals(1, Game.getTeamScore("red"));

        // Mutual exclusion: EASY_0 removed from blue, green, and yellow remaining lists
        assertFalse(Block.getTeamRemainingBlocks("red").contains("EASY_0"));
        assertFalse(Block.getTeamRemainingBlocks("blue").contains("EASY_0"));
        assertFalse(Block.getTeamRemainingBlocks("green").contains("EASY_0"));
        assertFalse(Block.getTeamRemainingBlocks("yellow").contains("EASY_0"));

        assertEquals(3, Game.getTeamTotalBlockAmount("blue"));
        assertEquals(3, Game.getTeamTotalBlockAmount("green"));
        assertEquals(3, Game.getTeamTotalBlockAmount("yellow"));

        // Green team completes BONUS_0 (11 pts) -> triggers green victory!
        Game.teamTaskComplete("green", "BONUS_0", "Charlie");
        assertEquals(Game.GameState.END, Game.currentGameState);
        assertEquals(11, Game.getTeamProgressScore("green"));
        assertEquals(11, Game.getTeamScore("green"));
        assertEquals(1, Game.getTeamCurrentBlockAmount("green"));
        assertEquals(1, Game.collectAmount.get("Charlie"));

        // Yellow team attempting to complete after END is ignored
        Game.teamTaskComplete("yellow", "NORMAL_1", "Diana");
        assertEquals(0, Game.getTeamProgressScore("yellow"));
    }

    private void installServerStub() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        Logger logger = Logger.getLogger("MultiTeamRoundSettlementTest");
        Server server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOnlinePlayers" -> List.of();
                    case "getLogger" -> logger;
                    case "getWorlds" -> List.of();
                    default -> null;
                });
        serverField.set(null, server);

        YamlConfiguration messages = new YamlConfiguration();
        Field cacheStringField = Message.class.getDeclaredField("cacheString");
        cacheStringField.setAccessible(true);
        Field cacheListField = Message.class.getDeclaredField("cacheStringList");
        cacheListField.setAccessible(true);
        for (Message msg : Message.values()) {
            String value = msg == Message.MESSAGE_LANG ? "zh_cn" : msg.name();
            messages.set(msg.getPath(), value);
            cacheStringField.set(msg, value);
            cacheListField.set(msg, List.of(value));
        }
        Field chineseField = LanguageManager.class.getDeclaredField("chinese");
        chineseField.setAccessible(true);
        chineseField.set(null, messages);
        Field englishField = LanguageManager.class.getDeclaredField("english");
        englishField.setAccessible(true);
        englishField.set(null, messages);
    }
}
