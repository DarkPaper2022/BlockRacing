package top.lqsnow.blockracing.menus;

import org.junit.jupiter.api.Test;
import top.lqsnow.blockracing.managers.Block;
import top.lqsnow.blockracing.managers.Goal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TargetIconCatalogTest {
    @Test
    @SuppressWarnings("unchecked")
    void everyEnabledGoalHasMatchingMetadata() throws Exception {
        var parser = Block.class.getDeclaredMethod("parseCsvLine", String.class);
        parser.setAccessible(true);
        var ids = new HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/Targets.csv"), StandardCharsets.UTF_8))) {
            reader.readLine();
            for (String line; (line = reader.readLine()) != null;) {
                List<String> row = (List<String>) parser.invoke(null, line);
                if (!row.get(1).equals("goal") || row.get(2).equals("-1")) continue;
                var icon = TargetIconCatalog.matching(row.get(0), row.get(4));
                assertNotNull(icon, row.get(0));
                assertNotNull(icon.material());
                assertFalse(icon.actionLabel(true).isBlank());
                assertFalse(icon.actionLabel(false).isBlank());
                assertTrue(icon.modelKey().matches("blockracing:task/[a-z0-9_]+"));
                assertTrue(ids.add(row.get(0)));
            }
        }
        assertEquals(ids, TargetIconCatalog.all().keySet());
    }

    @Test
    void similarGoalsHaveDifferentQuantitiesOrActions() {
        var five = TargetIconCatalog.all().get("OBTAIN_5_UNIQUE_DISCS");
        var eight = TargetIconCatalog.all().get("OBTAIN_8_UNIQUE_DISCS");
        assertEquals(5, five.stackAmount());
        assertEquals(8, eight.stackAmount());
        assertNotEquals(five.modelKey(), eight.modelKey());
        assertEquals("WITHER_SPAWN_EGG", TargetIconCatalog.all().get("KILL_WITHER").material().name());
        assertEquals("STONECUTTER", TargetIconCatalog.all().get("USE_STONECUTTER").material().name());
        assertNotEquals(TargetIconCatalog.all().get("KILL_WARDEN").action(),
                TargetIconCatalog.all().get("DIE_TO_WARDEN").action());
    }

    @Test
    void largeCountsAndUnitsAreNeverTruncated() {
        var hundred = TargetIconCatalog.all().get("CRAFT_100_UNIQUE_ITEMS");
        assertEquals("100", hundred.badge());
        assertEquals(1, hundred.stackAmount());
        var minutes = TargetIconCatalog.all().get("WEAR_CARVED_PUMPKIN_5_MINUTES");
        assertEquals("5M", minutes.badge());
        assertEquals(1, minutes.stackAmount());
        assertEquals("ALL", TargetIconCatalog.all().get("COLLECT_ALL_COPPER_VARIANTS").badge());
        assertEquals("ANY", TargetIconCatalog.all().get("OBTAIN_ANY_NAUTILUS_ARMOR").badge());
    }

    @Test
    void editedServerGoalsDoNotUseOutdatedIcons() {
        Goal.registerDefinition("KILL_WITHER", "Server override", "kill:ZOMBIE");
        assertNull(TargetIconCatalog.matching("DRAFTOUT:KILL_WITHER", Goal.getRawRequirement("DRAFTOUT:KILL_WITHER")));
        Goal.registerDefinition("KILL_WITHER", "Kill Wither", "kill:WITHER");
        assertNotNull(TargetIconCatalog.matching("DRAFTOUT:KILL_WITHER", Goal.getRawRequirement("DRAFTOUT:KILL_WITHER")));
        assertNull(TargetIconCatalog.matching("UNKNOWN", "kill:WITHER"));
        assertNull(TargetIconCatalog.matching("STONE", null));
    }
}
