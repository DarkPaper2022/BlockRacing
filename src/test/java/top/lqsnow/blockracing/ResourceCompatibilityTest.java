package top.lqsnow.blockracing;

import org.bukkit.Material;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;
import top.lqsnow.blockracing.managers.Block;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceCompatibilityTest {
    @SuppressWarnings("unchecked")
    private static List<List<String>> targets() throws Exception {
        var parse = Block.class.getDeclaredMethod("parseCsvLine", String.class);
        parse.setAccessible(true);
        try (var reader = new BufferedReader(new InputStreamReader(
                ResourceCompatibilityTest.class.getResourceAsStream("/Targets.csv"), StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> {
                try { return (List<String>) parse.invoke(null, line); }
                catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            }).toList();
        }
    }

    @Test
    void configuredTasksHaveUniqueIdsAndValidScores() throws Exception {
        Set<String> seen = new HashSet<>();
        for (var row : targets()) {
            assertEquals(6, row.size(), row.toString());
            assertTrue(seen.add(row.get(0)), "Duplicate ID: " + row.get(0));
            int score = Integer.parseInt(row.get(2));
            assertTrue(score == -1 || score > 0, row.toString());
            assertTrue(Set.of("block", "goal").contains(row.get(1)));
            assertFalse(row.get(5).isBlank(), "Missing Chinese label: " + row.get(0));
        }
    }

    @Test
    void activeMaterialsAndGoalItemRequirementsExistInPaper262() throws Exception {
        for (var row : targets()) {
            if (row.get(2).equals("-1")) continue;
            if (row.get(1).equals("block")) {
                assertNotNull(Material.getMaterial(row.get(0)), row.get(0));
            } else {
                String requirement = row.get(4);
                assertFalse(requirement.isBlank(), row.get(0));
                if (requirement.startsWith("item:") || requirement.startsWith("item-unique:")
                        || requirement.startsWith("enchanted-item:") || requirement.startsWith("break:")) {
                    String items = requirement.startsWith("item-unique:") ? requirement.split(":", 3)[2]
                            : requirement.startsWith("enchanted-item:") ? requirement.split(":", 3)[1]
                            : requirement.substring(requirement.indexOf(':') + 1);
                    for (String item : items.split(",")) {
                        String name = item.split("\\*")[0];
                        assertNotNull(Material.getMaterial(name), row.get(0) + ": " + name);
                    }
                }
            }
        }
    }

    @Test
    void activeBlocksHaveEnglishAndCsvChineseNames() throws Exception {
        JSONObject english;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/en_us.json"), StandardCharsets.UTF_8)) {
            english = (JSONObject) new JSONParser().parse(reader);
        }
        for (var row : targets()) {
            if (!row.get(1).equals("block") || row.get(2).equals("-1")) continue;
            String id = row.get(0).toLowerCase(java.util.Locale.ROOT);
            assertTrue(english.containsKey("block.minecraft." + id) || english.containsKey("item.minecraft." + id),
                    "Missing English translation: " + id);
        }
    }

    @Test
    void activeGoalsWithoutServerRegistriesParseOnTheNewApi() throws Exception {
        for (var row : targets()) {
            if (!row.get(1).equals("goal") || row.get(2).equals("-1")) continue;
            // Enchantment resolution requires Paper's live RegistryAccess; covered by the server checklist.
            if (row.get(4).startsWith("enchanted-item:")) continue;
            assertNotNull(top.lqsnow.blockracing.managers.Goal.registerDefinition(
                    row.get(0), row.get(3), row.get(4)), row.get(0) + ": " + row.get(4));
        }
    }

    @Test
    void preservesDraftoutTasksAndAddsMinecraft262Targets() throws Exception {
        Set<String> ids = new HashSet<>();
        for (var row : targets()) ids.add(row.get(0));
        assertTrue(ids.containsAll(List.of("SULFUR", "CINNABAR", "SULFUR_SPIKE", "POTENT_SULFUR",
                "CHISELED_CINNABAR", "CHISELED_SULFUR", "KILL_WITHER", "OBTAIN_8_UNIQUE_DISCS",
                "OBTAIN_ANY_NAUTILUS_ARMOR", "COLLECT_ALL_COPPER_VARIANTS")));
        assertNull(getClass().getResource("/EasyBlocks.txt"), "Legacy block pools must not replace Targets.csv");
    }
}
