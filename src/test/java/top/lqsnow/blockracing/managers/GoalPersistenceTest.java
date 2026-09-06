package top.lqsnow.blockracing.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoalPersistenceTest {
    @Test void roundEpochSurvivesRecoveryButNotANewRound() {
        Goal.resetProgress();
        String epoch = Goal.progressEpoch();
        YamlConfiguration saved = new YamlConfiguration();
        Goal.saveProgress(saved);
        Goal.resetProgress();
        assertNotEquals(epoch, Goal.progressEpoch());
        Goal.restoreProgress(saved);
        assertEquals(epoch, Goal.progressEpoch());
        Goal.resetProgress();
    }
    @Test
    void goalEventsSurviveYamlRoundTrip() throws Exception {
        YamlConfiguration original = new YamlConfiguration();
        original.set("KILLED_ENTITY_TYPES.Alex", List.of("WITHER", "ZOMBIE"));
        original.set("KILL_COUNTS.Alex", 12);
        original.set("CONSUMED_ITEMS.Alex", List.of("APPLE", "BREAD"));
        original.set("MINED_BLOCKS.Alex", List.of("OBSIDIAN"));
        original.set("CONSUMED_POTIONS.Alex", List.of("WATER"));
        original.set("DEATH_CAUSES.Alex", List.of("DROWNING"));
        original.set("DAMAGE_DEALT.Alex", 12.5);
        original.set("CONTINUOUS_WEAR_TICKS.Alex:CARVED_PUMPKIN", 210);
        original.set("FISHED_TREASURE_PLAYERS", List.of("Alex"));
        original.set("MAX_LEVEL_VILLAGER_PLAYERS", List.of("Alex"));
        Goal.restoreProgress(original);
        YamlConfiguration saved = new YamlConfiguration();
        Goal.saveProgress(saved);
        Goal.resetProgress();
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(saved.saveToString());
        Goal.restoreProgress(reloaded);
        YamlConfiguration restored = new YamlConfiguration();
        Goal.saveProgress(restored);
        for (String key : original.getKeys(true)) {
            if (!original.isConfigurationSection(key)) assertEquals(original.get(key), restored.get(key), key);
        }
        Goal.resetProgress();
    }

    @Test
    void savedGameIncludesVictoryCurrencyAndBonusSeparately() throws Exception {
        TaskSamplingTest.configure();
        Game.redTeamScore = 17;
        Game.redTeamProgressScore = 30;
        Game.redTeamTotalScore = 101;
        Game.redTeamWinScore = 51;
        Block.redTeamBonusBlocks = List.of("ZOMBIE_HEAD");
        YamlConfiguration saved = GameProgressStore.snapshot();
        assertEquals(17, saved.getInt("scores.red"));
        assertEquals(30, saved.getInt("victory.red-progress"));
        assertEquals(101, saved.getInt("victory.red-total"));
        assertEquals(51, saved.getInt("victory.red-threshold"));
        assertEquals(List.of("ZOMBIE_HEAD"), saved.getStringList("blocks.red-bonus"));
        assertEquals(11, saved.getInt("settings.bonus-score-threshold"));
        assertTrue(GameProgressStore.compatibleState(saved));
        saved.set("targets-fingerprint", "modified-target-rules");
        assertFalse(GameProgressStore.compatibleState(saved));
        saved.set("format-version", 1);
        assertFalse(GameProgressStore.compatibleState(saved));
    }
}
