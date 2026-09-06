package top.lqsnow.blockracing.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskSamplingTest {
    static void configure() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("medium-block", true);
        config.set("hard-block", true);
        config.set("block-amount", 64);
        config.set("max-easy-targets-per-game", 8);
        config.set("available-task-amount", 64);
        config.set("bonus-score-threshold", 11);
        config.set("bonus-target-amount", 3);
        config.set("locate-cost", 5);
        config.set("random-teleport-cost", 2);
        config.set("game-mode", "racing");
        Field field = Config.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, config);
        Setting.getSettings();
    }

    @BeforeEach
    void setup() throws Exception {
        configure();
        Map<String, Integer> pool = new HashMap<>();
        for (int i = 0; i < 30; i++) pool.put("EASY_" + i, 1);
        for (int i = 0; i < 100; i++) pool.put("NORMAL_" + i, 2 + i % 9);
        for (int i = 0; i < 7; i++) pool.put("BONUS_" + i, 11 + i);
        Block.targetScores = Map.copyOf(pool);
    }

    @Test
    void samplesKeepCountsUniquenessAndBonusSeparation() {
        for (int iteration = 0; iteration < 100; iteration++) {
            List<String> regular = Block.generateSampleBlocks(64);
            assertEquals(64, regular.size());
            assertEquals(64, new HashSet<>(regular).size());
            assertEquals(8, regular.stream().filter(t -> Block.getTargetScore(t) == 1).count());
            assertTrue(regular.stream().noneMatch(Block::isBonusTarget));
            List<String> bonus = Block.generateBonusBlocks();
            assertEquals(3, bonus.size());
            assertEquals(3, new HashSet<>(bonus).size());
            assertTrue(bonus.stream().allMatch(Block::isBonusTarget));
            assertEquals(Block.getTotalScore(regular), Block.getMainTotalScore(
                    java.util.stream.Stream.concat(regular.stream(), bonus.stream()).toList()));
        }
    }

    @Test
    void aSmallPoolDoesNotExceedEasyCapToFillMissingSlots() {
        Setting.setEnableMediumBlock(false);
        Setting.setEnableHardBlock(false);
        Block.refreshAvailableBlocksAndClampAmount();
        assertEquals(8, Setting.getBlockAmount());
        assertEquals(8, Block.generateSampleBlocks(64).size());
        Setting.setMaxEasyTargetsPerGame(0);
        Block.refreshAvailableBlocksAndClampAmount();
        assertEquals(0, Setting.getBlockAmount());
        assertTrue(Block.generateSampleBlocks(64).isEmpty());
    }

    @Test
    void configuredBonusThresholdChangesWhichPoolOwnsTargets() {
        Setting.setBonusScoreThreshold(15);
        List<String> regular = Block.generateSampleBlocks(128);
        assertTrue(regular.contains("BONUS_0"));
        assertTrue(regular.stream().allMatch(t -> Block.getTargetScore(t) < 15));
        assertEquals(3, Block.generateBonusBlocks().size());
    }

    @Test
    void amountClampsToBothConfiguredAndAvailableLimits() {
        assertEquals(120, Block.clampBlockAmount(300, 120));
        assertEquals(128, Block.clampBlockAmount(300, 500));
        assertEquals(10, Block.clampBlockAmount(3, 120));
        assertEquals(7, Block.clampBlockAmount(10, 7));
        assertEquals(0, Block.clampBlockAmount(64, 0));
    }

    @Test
    void victoryThresholdUsesHalfTheInitialScoreRoundedUp() throws Exception {
        var method = Game.class.getDeclaredMethod("getWinScore", int.class);
        method.setAccessible(true);
        assertEquals(51, method.invoke(null, 101));
        assertEquals(50, method.invoke(null, 100));
        assertEquals(1, method.invoke(null, 1));
    }

    @Test
    void mainRetainsSharedDrawAndBonusVisibilityBeyondTheRegularWindow() {
        Block.setupBlocks();
        assertEquals(Block.redTeamBlocks, Block.blueTeamBlocks);
        assertEquals(67, Game.getCurrentBlocks("red").size());
        Setting.setAvailableTaskAmount(4);
        assertEquals(7, Game.getCurrentBlocks("red").size());
        assertTrue(Game.getCurrentBlocks("red").containsAll(Block.redTeamBonusBlocks));
    }
}
