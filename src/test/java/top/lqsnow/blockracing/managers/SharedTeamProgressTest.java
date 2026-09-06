package top.lqsnow.blockracing.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SharedTeamProgressTest {
    private static final List<String> RED = List.of("Alex", "Steve");
    private static final Function<String, Player> OFFLINE = ignored -> null;

    private Object previousLanguage;
    private List<String> previousRed;
    private List<String> previousBlue;

    @BeforeEach void reset() throws Exception {
        var cache = Message.class.getDeclaredField("cacheString");
        cache.setAccessible(true);
        previousLanguage = cache.get(Message.MESSAGE_LANG);
        cache.set(Message.MESSAGE_LANG, "zh_cn");
        previousRed = List.copyOf(Team.redTeamPlayers);
        previousBlue = List.copyOf(Team.blueTeamPlayers);
        Team.redTeamPlayers.clear();
        Team.redTeamPlayers.addAll(RED);
        Team.blueTeamPlayers.clear();
        Team.blueTeamPlayers.add("Blue");
        Goal.resetProgress();
        Goal.clearDefinitions();
    }

    @AfterEach void cleanup() throws Exception {
        var cache = Message.class.getDeclaredField("cacheString");
        cache.setAccessible(true);
        cache.set(Message.MESSAGE_LANG, previousLanguage);
        Team.redTeamPlayers.clear();
        Team.redTeamPlayers.addAll(previousRed);
        Team.blueTeamPlayers.clear();
        Team.blueTeamPlayers.addAll(previousBlue);
        Goal.resetProgress();
        Goal.clearDefinitions();
    }

    private String target(String rule) {
        return Goal.registerDefinition("TEST", "Test goal", rule);
    }

    private Goal.Progress progress(String target) {
        return Goal.teamProgress(target, RED, List.of(), OFFLINE);
    }

    @Test void sumsFractionalDamageBeforeRoundingAndNeverDoubleAddsOnRecheck() throws Exception {
        YamlConfiguration events = new YamlConfiguration();
        events.set("DAMAGE_DEALT.Alex", 49.6);
        events.set("DAMAGE_DEALT.Steve", 50.4);
        events.set("DAMAGE_DEALT.Blue", 900);
        Goal.restoreProgress(events);
        String target = target("damage-dealt:100");
        for (int i = 0; i < 20; i++) assertEquals(100, progress(target).current());
        assertEquals(Goal.TEAM_COMPLETION_SOURCE, Goal.findCompletionSource(target, RED, List.of(), "Chest"));
        assertNull(Goal.findCompletionSource(target, List.of("Alex"), List.of(), "Chest"));
        assertEquals(49, Goal.teamProgress(target, List.of("Alex", "Alex"), List.of(), OFFLINE).current());
        assertEquals(900, Goal.teamProgress(target, List.of("Blue"), List.of(), OFFLINE).current());
        YamlConfiguration saved = new YamlConfiguration();
        Goal.saveProgress(saved);
        YamlConfiguration parsed = new YamlConfiguration();
        parsed.loadFromString(saved.saveToString());
        Goal.restoreProgress(parsed);
        assertEquals(100, progress(target).current());
        assertEquals(49.6, parsed.getDouble("DAMAGE_DEALT.Alex"));
    }

    @Test void unionsThirtyDistinctAdvancementsAcrossOfflineMembersAndRecovery() throws Exception {
        YamlConfiguration events = new YamlConfiguration();
        events.set("COMPLETED_ADVANCEMENTS.Alex", IntStream.range(0, 20).mapToObj(i -> "minecraft:story/goal_" + i).toList());
        events.set("COMPLETED_ADVANCEMENTS.Steve", IntStream.range(10, 30).mapToObj(i -> "minecraft:story/goal_" + i).toList());
        events.set("COMPLETED_ADVANCEMENTS.Blue", List.of("minecraft:story/enemy_only"));
        Goal.restoreProgress(events);
        String target = target("advancement-count:30");
        assertEquals(30, progress(target).current());
        assertEquals(30, progress(target).completed().size());
        YamlConfiguration saved = new YamlConfiguration();
        Goal.saveProgress(saved);
        Goal.resetProgress();
        YamlConfiguration restored = new YamlConfiguration();
        restored.loadFromString(saved.saveToString());
        Goal.restoreProgress(restored);
        assertEquals(30, progress(target).current());
        assertEquals(1, Goal.teamProgress(target, List.of("Blue"), List.of(), OFFLINE).current());
        assertEquals(0, Goal.teamProgress(target, List.of(), List.of(), OFFLINE).current());
    }

    @Test void advancementFilterExcludesRecipesHiddenAndDatapackCounters() {
        assertTrue(Goal.isCountableAdvancement(NamespacedKey.minecraft("story/mine_stone"), true));
        assertFalse(Goal.isCountableAdvancement(NamespacedKey.minecraft("recipes/tools/wooden_pickaxe"), true));
        assertFalse(Goal.isCountableAdvancement(NamespacedKey.minecraft("technical/counter"), false));
        assertFalse(Goal.isCountableAdvancement(new NamespacedKey("custom", "counter"), true));
    }

    @Test void countsKillsButDeduplicatesEntityTypesAndCrafts() {
        YamlConfiguration events = new YamlConfiguration();
        events.set("KILL_COUNTS.Alex", 7);
        events.set("KILL_COUNTS.Steve", 8);
        events.set("KILLED_ENTITY_TYPES.Alex", List.of("ZOMBIE", "CREEPER"));
        events.set("KILLED_ENTITY_TYPES.Steve", List.of("ZOMBIE", "SKELETON"));
        events.set("CRAFTED_ITEMS.Alex", List.of("STONE", "CHEST"));
        events.set("CRAFTED_ITEMS.Steve", List.of("CHEST", "FURNACE"));
        Goal.restoreProgress(events);
        assertEquals(15, progress(target("kill-total:15")).current());
        assertEquals(3, progress(target("kill-unique-hostile:3")).current());
        assertEquals(3, progress(target("craft-unique:3")).current());
    }

    @Test void consumeAllAndBooleanGoalsUseTeamUnion() {
        YamlConfiguration events = new YamlConfiguration();
        events.set("CONSUMED_ITEMS.Alex", List.of("APPLE"));
        events.set("CONSUMED_ITEMS.Steve", List.of("BREAD", "APPLE"));
        events.set("FISHED_TREASURE_PLAYERS", List.of("Alex"));
        events.set("MINED_BLOCKS.Steve", List.of("OBSIDIAN"));
        Goal.restoreProgress(events);
        assertEquals(2, progress(target("consume-all:APPLE,BREAD")).current());
        assertEquals(1, progress(target("fish-treasure")).current());
        assertEquals(1, progress(target("break:OBSIDIAN")).current());
    }

    @Test void poolsCurrentInventoriesAndChestsWithoutCountingDuplicateReferences() {
        Inventory chest = inventory(Inventory.class, stack(Material.STONE, 2), stack(Material.DIAMOND, 1));
        Player alex = player("Alex", 0, inventory(PlayerInventory.class, stack(Material.STONE, 2)));
        Player steve = player("Steve", 0, inventory(PlayerInventory.class, stack(Material.DIAMOND, 1)));
        Map<String, Player> players = Map.of("Alex", alex, "Steve", steve);
        String all = target("item:STONE*4,DIAMOND*2");
        assertEquals(2, Goal.teamProgress(all, RED, List.of(chest, chest), players::get).current());
        String tooMany = target("item:STONE*6");
        assertEquals(0, Goal.teamProgress(tooMany, RED, List.of(chest, chest), players::get).current());
        String unique = target("item-unique:2:STONE,DIAMOND");
        assertEquals(2, Goal.teamProgress(unique, RED, List.of(chest), players::get).current());
        assertEquals(0, Goal.teamProgress(target("item:STONE*4"), RED, List.of(chest), OFFLINE).current());
    }

    @Test void duplicateMaterialRulesDoNotHideHigherQuantityAndTransferredItemsAreNotHistorical() {
        Inventory chest = inventory(Inventory.class, stack(Material.STONE, 2));
        assertEquals(0, Goal.teamProgress(target("item:STONE,STONE*4"), RED, List.of(chest), OFFLINE).current());
        assertEquals(1, Goal.teamProgress(target("item-unique:1:STONE,STONE*4"), RED, List.of(chest), OFFLINE).current());
        ItemStack[] contents = { stack(Material.DIAMOND, 1) };
        Inventory liveChest = inventory(Inventory.class, contents);
        String target = target("item:DIAMOND");
        assertEquals(1, Goal.teamProgress(target, RED, List.of(liveChest), OFFLINE).current());
        contents[0] = null;
        assertEquals(0, Goal.teamProgress(target, RED, List.of(liveChest), OFFLINE).current());
    }

    @Test void equipmentMustBeWornTogetherByOneMember() {
        Player alex = player("Alex", 0, inventory(PlayerInventory.class, stack(Material.IRON_HELMET, 1), null, null, null));
        Player steve = player("Steve", 0, inventory(PlayerInventory.class, null, stack(Material.IRON_CHESTPLATE, 1), null, null));
        Map<String, Player> members = Map.of("Alex", alex, "Steve", steve);
        Goal.Progress progress = Goal.teamProgress(target("equipment-all:IRON_HELMET,IRON_CHESTPLATE"), RED, List.of(), members::get);
        assertEquals(1, progress.current());
        assertEquals(2, progress.required());
    }

    @Test void spectatorAndInvalidDamageNeverContributeAndTeamsCannotChangeMidRound() {
        Goal.recordDamageDealt(player("Spectator", 0, null), 500);
        Goal.recordDamageDealt(player("Alex", 0, null), -2);
        Goal.recordDamageDealt(player("Alex", 0, null), Double.NaN);
        Goal.recordDamageDealt(player("Alex", 0, null), 12.5);
        String target = target("damage-dealt:100");
        assertEquals(12, progress(target).current());
        assertEquals(0, Goal.teamProgress(target, List.of("Spectator"), List.of(), OFFLINE).current());
        Game.GameState previous = Game.currentGameState;
        try {
            Game.currentGameState = Game.GameState.INGAME;
            assertFalse(Team.joinTeam(player("Alex", 0, null), null, false));
        } finally {
            Game.currentGameState = previous;
        }
    }

    @Test void levelsAreBestSinglePlayerNotSum() {
        String target = target("level:30");
        Map<String, Player> players = Map.of("Alex", player("Alex", 19, null), "Steve", player("Steve", 20, null));
        assertEquals(20, Goal.teamProgress(target, RED, List.of(), players::get).current());
    }

    @Test void wearDurationCannotBeSplitBetweenPlayersAndDisconnectResetsOnlyContinuity() {
        YamlConfiguration events = new YamlConfiguration();
        events.set("CONTINUOUS_WEAR_TICKS.Alex|CARVED_PUMPKIN", 180);
        events.set("CONTINUOUS_WEAR_TICKS.Steve|CARVED_PUMPKIN", 160);
        events.set("DAMAGE_DEALT.Alex", 15);
        Goal.restoreProgress(events);
        String target = target("wear-continuous:CARVED_PUMPKIN:10");
        Player alex = player("Alex", 0, null);
        Map<String, Player> players = Map.of("Alex", alex, "Steve", player("Steve", 0, null));
        assertEquals(9, Goal.teamProgress(target, RED, List.of(), players::get).current());
        Goal.playerDisconnected(alex);
        assertEquals(8, Goal.teamProgress(target, RED, List.of(), players::get).current());
        assertEquals(15, progress(target("damage-dealt:15")).current());
    }

    private static ItemStack stack(Material type, int amount) {
        // Paper 26 ItemStack's public constructor requires a live registry; this no-arg test double does not.
        return new ItemStack() {
            @Override public Material getType() { return type; }
            @Override public int getAmount() { return amount; }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends Inventory> T inventory(Class<T> type, ItemStack... contents) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("getContents")) return contents;
            int armorIndex = List.of("getHelmet", "getChestplate", "getLeggings", "getBoots").indexOf(method.getName());
            if (armorIndex >= 0) return contents.length > armorIndex ? contents[armorIndex] : null;
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return null;
        });
    }

    private static Player player(String name, int level, PlayerInventory inventory) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getName" -> name;
                    case "getLevel" -> level;
                    case "getInventory" -> inventory;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
