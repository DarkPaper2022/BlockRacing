package top.lqsnow.blockracing.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementCountingTest {
    private List<String> red, blue;
    private Game.GameState state;
    @BeforeEach void setup() {
        state = Game.currentGameState; Game.currentGameState = Game.GameState.INGAME;
        red = List.copyOf(Team.redTeamPlayers); blue = List.copyOf(Team.blueTeamPlayers);
        Team.redTeamPlayers.clear(); Team.redTeamPlayers.addAll(List.of("A", "B"));
        Team.blueTeamPlayers.clear(); Team.blueTeamPlayers.add("C");
        Goal.resetProgress(); Goal.clearDefinitions();
    }
    @AfterEach void reset() {
        Game.currentGameState = state;
        Team.redTeamPlayers.clear(); Team.redTeamPlayers.addAll(red);
        Team.blueTeamPlayers.clear(); Team.blueTeamPlayers.addAll(blue);
        Goal.resetProgress(); Goal.clearDefinitions();
    }
    @Test void actualCompletionRecorderDeduplicatesPlayersEventsRecipesAndHiddenDisplayFlags() {
        Player a = player("A", Set.of()), b = player("B", Set.of());
        for (int i = 0; i < 20; i++) completionEvent(a, advancement("minecraft:story/goal_" + i, true, false));
        for (int i = 10; i < 30; i++) completionEvent(b, advancement("minecraft:story/goal_" + i, true, false));
        for (int i = 0; i < 10; i++) Goal.recordAdvancement(a, advancement("minecraft:story/goal_0", true, false));
        Goal.recordAdvancement(a, advancement("minecraft:recipes/tools/stone_pickaxe", false, false));
        Goal.recordAdvancement(a, advancement("minecraft:recipes/fake_display", true, false));
        Goal.recordAdvancement(a, advancement("minecraft:technical/counter", false, false));
        Goal.recordAdvancement(a, advancement("custom:counter", true, false));
        Goal.recordAdvancement(player("spectator", Set.of()), advancement("minecraft:story/spectator", true, false));
        Goal.recordAdvancement(player("C", Set.of()), advancement("minecraft:story/enemy", true, false));
        assertEquals(30, count());
        // A hidden *displayed* challenge is a real advancement, unlike a display-less technical counter.
        Goal.recordAdvancement(a, advancement("minecraft:nether/all_effects", true, true));
        assertEquals(31, count());
    }
    @Test void loginImportCountsOnlyWholeAdvancementsNotCriteriaAndIsIdempotent() {
        Advancement done = advancement("minecraft:story/mine_stone", true, false);
        Advancement partial = advancement("minecraft:adventure/kill_all_mobs", true, false);
        List<Advancement> catalog = List.of(done, partial, advancement("minecraft:recipes/building_blocks/stone", false, false));
        Player a = player("A", Set.of(done.getKey().toString()));
        for (int i = 0; i < 5; i++) Goal.refreshAdvancements(a, catalog.iterator());
        assertEquals(1, count());
        // Restart retains the recorded member contribution, and repeated re-import does not add another copy.
        YamlConfiguration saved = new YamlConfiguration(); Goal.saveProgress(saved); Goal.restoreProgress(saved);
        Goal.refreshAdvancements(a, catalog.iterator());
        assertEquals(1, count());
        Goal.refreshAdvancements(player("B", Set.of(partial.getKey().toString())), catalog.iterator());
        assertEquals(2, count());
    }
    @Test void specificAdvancementAndCountUseTheSameFullIdLedger() {
        Goal.recordAdvancement(player("A", Set.of()), advancement("minecraft:adventure/revaulting", true, false));
        String target = Goal.registerDefinition("SINGLE", "Revaulting", "advancement:adventure/revaulting");
        assertEquals(1, Goal.teamProgress(target, List.of("A", "B"), List.of(), n -> null).current());
        assertEquals(1, count());
    }
    private int count() {
        String goal = Goal.registerDefinition("COUNT", "Count", "advancement-count:30");
        return Goal.teamProgress(goal, List.of("A", "B"), List.of(), n -> null).current();
    }
    private static void completionEvent(Player player, Advancement advancement) {
        try {
            var method = top.lqsnow.blockracing.listeners.BasicListener.class.getDeclaredMethod("onAdvancement", org.bukkit.event.player.PlayerAdvancementDoneEvent.class);
            method.setAccessible(true);
            method.invoke(new top.lqsnow.blockracing.listeners.BasicListener(), new org.bukkit.event.player.PlayerAdvancementDoneEvent(player, advancement));
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Advancement advancement(String key, boolean display, boolean hidden) {
        Object shown = Proxy.newProxyInstance(Advancement.class.getClassLoader(), new Class<?>[]{io.papermc.paper.advancement.AdvancementDisplay.class},
                (p, m, args) -> m.getName().equals("isHidden") ? hidden : null);
        return (Advancement) Proxy.newProxyInstance(Advancement.class.getClassLoader(), new Class<?>[]{Advancement.class}, (p, m, args) -> switch (m.getName()) {
            case "getKey" -> NamespacedKey.fromString(key);
            case "getDisplay" -> display ? shown : null;
            case "getCriteria" -> List.of("criterion_one", "criterion_two");
            default -> null;
        });
    }
    private static Player player(String name, Set<String> completed) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, args) -> switch (m.getName()) {
            case "getName" -> name;
            case "getAdvancementProgress" -> Proxy.newProxyInstance(AdvancementProgress.class.getClassLoader(), new Class<?>[]{AdvancementProgress.class},
                    (q, method, argv) -> method.getName().equals("isDone") ? completed.contains(((Advancement) args[0]).getKey().toString()) : null);
            default -> null;
        });
    }
}
