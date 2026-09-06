package top.lqsnow.blockracing.menus;

import org.junit.jupiter.api.Test;
import top.lqsnow.blockracing.managers.Block;
import top.lqsnow.blockracing.managers.Setting;
import top.lqsnow.blockracing.managers.Goal;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetListMenuTest {
    @Test
    void categoryHeadersDoNotChangeCommandIndicesAndBonusUsesConfiguredThreshold() throws Exception {
        var threshold = Setting.class.getDeclaredField("bonusScoreThreshold");
        threshold.setAccessible(true);
        threshold.setInt(null, 20);
        Goal.registerDefinition("KILL_WITHER", "Kill the Wither", "kill:WITHER");
        Block.targetScores = Map.of("ZOMBIE_HEAD", 30, "DRAFTOUT:KILL_WITHER", 10, "STONE", 1, "DIAMOND", 15);
        List<String> targets = List.of("ZOMBIE_HEAD", "DRAFTOUT:KILL_WITHER", "STONE", "DIAMOND");
        var entries = TargetListMenu.buildEntries(targets);
        assertEquals(3, entries.stream().filter(e -> e.target() == null).count());
        assertEquals(4, entries.stream().filter(e -> e.target() != null).count());
        for (var entry : entries) {
            if (entry.target() != null) assertEquals(entry.target(), targets.get(entry.index() - 1));
        }
        assertEquals(0, entries.stream().filter(e -> "DIAMOND".equals(e.target())).findFirst().orElseThrow().category());
        assertEquals(2, entries.stream().filter(e -> "ZOMBIE_HEAD".equals(e.target())).findFirst().orElseThrow().category());
        var goals = TargetListMenu.buildEntries(targets, TargetListMenu.Filter.GOALS);
        assertEquals(1, goals.stream().filter(e -> e.target() != null).count());
        assertEquals(2, goals.stream().filter(e -> e.target() != null).findFirst().orElseThrow().index());
        var bonus = TargetListMenu.buildEntries(targets, TargetListMenu.Filter.BONUS);
        assertEquals("ZOMBIE_HEAD", bonus.stream().filter(e -> e.target() != null).findFirst().orElseThrow().target());
    }

    @Test
    void filteredEmptyListsHaveNoOrphanHeaders() {
        assertTrue(TargetListMenu.buildEntries(List.of()).isEmpty());
        Block.targetScores = Map.of("STONE", 1);
        assertTrue(TargetListMenu.buildEntries(List.of("STONE"), TargetListMenu.Filter.GOALS).isEmpty());
        var filter = TargetListMenu.Filter.ALL;
        for (int i = 0; i < 4; i++) filter = filter.next();
        assertEquals(TargetListMenu.Filter.ALL, filter);
    }
}
