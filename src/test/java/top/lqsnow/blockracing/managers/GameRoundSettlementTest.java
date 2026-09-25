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

class GameRoundSettlementTest {
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
        Block.redTeamBlocks.clear();
        Block.blueTeamBlocks.clear();
        Block.redTeamBonusBlocks.clear();
        Block.blueTeamBonusBlocks.clear();
        Team.redTeamPlayers.clear();
        Team.blueTeamPlayers.clear();
        Block.redTeamRemainingBlocks.clear();
        Block.blueTeamRemainingBlocks.clear();
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
    void bonusRewardGrantsSpendableScoreWithoutWinningAndMainThresholdWinsRound() {
        Block.redTeamBlocks = new ArrayList<>(List.of("EASY_0", "NORMAL_2", "BONUS_0"));
        Block.blueTeamBlocks = new ArrayList<>(List.of("EASY_0", "NORMAL_2", "BONUS_0"));
        Block.redTeamBonusBlocks = new ArrayList<>(List.of("BONUS_0"));
        Block.blueTeamBonusBlocks = new ArrayList<>(List.of("BONUS_0"));
        Block.redTeamRemainingBlocks = new ArrayList<>(Block.redTeamBlocks);
        Block.blueTeamRemainingBlocks = new ArrayList<>(Block.blueTeamBlocks);
        Game.redTeamTotalBlockAmount = 3;
        Game.blueTeamTotalBlockAmount = 3;
        Game.redTeamCurrentBlockAmount = 0;
        Game.blueTeamCurrentBlockAmount = 0;
        Game.redTeamTotalScore = Block.getMainTotalScore(Block.redTeamBlocks); // 1 + 4 = 5
        Game.blueTeamTotalScore = Block.getMainTotalScore(Block.blueTeamBlocks);
        Game.redTeamWinScore = 3;
        Game.blueTeamWinScore = 3;
        Game.redTeamProgressScore = 0;
        Game.blueTeamProgressScore = 0;
        Game.redTeamScore = 0;
        Game.blueTeamScore = 0;
        Game.currentGameState = Game.GameState.INGAME;

        // Completing a bonus task grants spendable score (11) and removes it from both teams, but does not add progress score or trigger win
        Game.redTaskComplete("BONUS_0", "Alice");
        assertEquals(Game.GameState.INGAME, Game.currentGameState);
        assertEquals(0, Game.redTeamProgressScore);
        assertEquals(11, Game.redTeamScore);
        assertEquals(1, Game.redTeamCurrentBlockAmount);
        assertFalse(Block.redTeamRemainingBlocks.contains("BONUS_0"));
        assertFalse(Block.blueTeamRemainingBlocks.contains("BONUS_0"));
        assertEquals(2, Game.blueTeamTotalBlockAmount);
        assertTrue(Files.exists(tempDir.resolve("game-progress.yml")));

        // Completing EASY_0 (1 pt) advances progress score to 1 (still < 3 win score)
        Game.redTaskComplete("EASY_0", "Alice");
        assertEquals(Game.GameState.INGAME, Game.currentGameState);
        assertEquals(1, Game.redTeamProgressScore);
        assertEquals(12, Game.redTeamScore);

        // Completing NORMAL_2 (4 pts) reaches 5 >= 3 win score and settles the game into END state
        Game.redTaskComplete("NORMAL_2", "Alice");
        assertEquals(Game.GameState.END, Game.currentGameState);
        assertEquals(5, Game.redTeamProgressScore);
        assertEquals(3, Game.redTeamCurrentBlockAmount);
        assertEquals(3, Game.collectAmount.get("Alice"));
        assertFalse(Files.exists(tempDir.resolve("game-progress.yml")));

        // Further completions after END are ignored
        Game.blueTaskComplete("EASY_0", "Bob");
        assertEquals(0, Game.blueTeamProgressScore);
    }

    private void installServerStub() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = (Server) serverField.get(null);
        Logger logger = Logger.getLogger("GameRoundSettlementTest");
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
        Field prefsField = LanguageManager.class.getDeclaredField("preferences");
        prefsField.setAccessible(true);
        prefsField.set(null, new YamlConfiguration());
    }
}
