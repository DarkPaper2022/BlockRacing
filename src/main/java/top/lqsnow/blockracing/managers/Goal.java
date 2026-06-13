package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public class Goal {
    public static final String PREFIX = "DRAFTOUT:";

    private static final Map<String, Definition> DEFINITIONS = new HashMap<>();

    public static List<String> load(String[] lines) {
        DEFINITIONS.clear();
        List<String> goals = new ArrayList<>();

        if (lines == null) {
            return goals;
        }

        for (String line : lines) {
            if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }

            Definition definition = parseDefinition(line);
            if (definition == null) {
                Bukkit.getLogger().warning("[BlockRacing] Invalid Draftout goal line: " + line);
                continue;
            }

            DEFINITIONS.put(definition.id(), definition);
            goals.add(encode(definition.id()));
        }

        Bukkit.getLogger().info("[BlockRacing] Loaded Draftout goals: " + goals.size());
        return List.copyOf(goals);
    }

    public static boolean isGoal(String target) {
        return target != null && target.startsWith(PREFIX) && DEFINITIONS.containsKey(decode(target));
    }

    public static boolean isKnownGoalId(String target) {
        return target != null && target.startsWith(PREFIX);
    }

    public static String getDisplayName(String target) {
        Definition definition = DEFINITIONS.get(decode(target));
        return definition == null ? target : definition.label();
    }

    public static String findCompletionSource(String target, List<String> teamPlayers, List<Inventory> teamChests, String chestSource) {
        Definition definition = DEFINITIONS.get(decode(target));
        if (definition == null) {
            return null;
        }

        for (String playerName : teamPlayers) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null && isCompletedByPlayer(definition, player)) {
                return playerName;
            }
        }

        if (definition.requirement() instanceof ItemRequirement itemRequirement) {
            for (Inventory chest : teamChests) {
                if (inventoryContainsAll(chest, itemRequirement.items())) {
                    return chestSource;
                }
            }
        }

        return null;
    }

    public static boolean isValid(String target) {
        Definition definition = DEFINITIONS.get(decode(target));
        if (definition == null) {
            return false;
        }

        Requirement requirement = definition.requirement();
        if (requirement instanceof ItemRequirement itemRequirement) {
            return itemRequirement.items().stream()
                    .map(ItemTarget::material)
                    .allMatch(Objects::nonNull);
        }

        if (requirement instanceof EquipmentRequirement equipmentRequirement) {
            return equipmentRequirement.items().stream()
                    .map(ItemTarget::material)
                    .allMatch(Objects::nonNull);
        }

        if (requirement instanceof AdvancementRequirement advancementRequirement) {
            return Bukkit.getAdvancement(NamespacedKey.minecraft(advancementRequirement.key())) != null;
        }

        return true;
    }

    private static boolean isCompletedByPlayer(Definition definition, Player player) {
        Requirement requirement = definition.requirement();

        if (requirement instanceof ItemRequirement itemRequirement) {
            return inventoryContainsAll(player.getInventory(), itemRequirement.items());
        }

        if (requirement instanceof EquipmentRequirement equipmentRequirement) {
            return equipmentRequirement.requireAll()
                    ? equipmentContainsAll(player, equipmentRequirement.items())
                    : equipmentContainsAny(player, equipmentRequirement.items());
        }

        if (requirement instanceof AdvancementRequirement advancementRequirement) {
            Advancement advancement = Bukkit.getAdvancement(NamespacedKey.minecraft(advancementRequirement.key()));
            if (advancement == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            return progress.isDone();
        }

        if (requirement instanceof AdvancementCountRequirement advancementCountRequirement) {
            return countCompletedAdvancements(player) >= advancementCountRequirement.amount();
        }

        if (requirement instanceof LevelRequirement levelRequirement) {
            return player.getLevel() >= levelRequirement.level();
        }

        if (requirement instanceof LocationRequirement locationRequirement) {
            return isAtLocation(player, locationRequirement.type());
        }

        return false;
    }

    private static boolean equipmentContainsAll(Player player, List<ItemTarget> items) {
        List<Material> equipment = getEquipment(player);
        return items.stream().allMatch(item -> equipment.contains(item.material()));
    }

    private static boolean equipmentContainsAny(Player player, List<ItemTarget> items) {
        List<Material> equipment = getEquipment(player);
        return items.stream().anyMatch(item -> equipment.contains(item.material()));
    }

    private static List<Material> getEquipment(Player player) {
        List<Material> equipment = new ArrayList<>();
        addEquipment(equipment, player.getInventory().getHelmet());
        addEquipment(equipment, player.getInventory().getChestplate());
        addEquipment(equipment, player.getInventory().getLeggings());
        addEquipment(equipment, player.getInventory().getBoots());
        return equipment;
    }

    private static void addEquipment(List<Material> equipment, ItemStack item) {
        if (item != null) {
            equipment.add(item.getType());
        }
    }

    private static boolean isAtLocation(Player player, LocationRequirement.Type type) {
        return switch (type) {
            case HEIGHT_LIMIT -> player.getLocation().getY() >= player.getWorld().getMaxHeight() - 1;
            case BEDROCK -> player.getLocation().getY() <= player.getWorld().getMinHeight() + 6;
            case NETHER_ROOF -> player.getWorld().getEnvironment().equals(World.Environment.NETHER)
                    && player.getLocation().getY() >= 128;
        };
    }

    private static int countCompletedAdvancements(Player player) {
        int count = 0;
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            try {
                if (player.getAdvancementProgress(iterator.next()).isDone()) {
                    count++;
                }
            } catch (IllegalArgumentException exception) {
                Bukkit.getLogger().log(Level.FINE, "[BlockRacing] Skipped invalid advancement while counting.", exception);
            }
        }
        return count;
    }

    private static boolean inventoryContainsAll(Inventory inventory, List<ItemTarget> items) {
        for (ItemTarget item : items) {
            if (count(inventory, item.material()) < item.amount()) {
                return false;
            }
        }
        return true;
    }

    private static int count(Inventory inventory, Material material) {
        int amount = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private static Definition parseDefinition(String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }

        String id = parts[0].trim();
        String label = parts[1].trim();
        Requirement requirement = parseRequirement(parts[2].trim());

        if (id.isEmpty() || label.isEmpty() || requirement == null) {
            return null;
        }

        return new Definition(id, label, requirement);
    }

    private static Requirement parseRequirement(String rawRequirement) {
        if (rawRequirement.startsWith("item:")) {
            List<ItemTarget> items = new ArrayList<>();
            for (String rawItem : rawRequirement.substring("item:".length()).split(",")) {
                ItemTarget item = parseItem(rawItem.trim());
                if (item == null) {
                    return null;
                }
                items.add(item);
            }
            return items.isEmpty() ? null : new ItemRequirement(List.copyOf(items));
        }

        if (rawRequirement.startsWith("advancement:")) {
            String key = rawRequirement.substring("advancement:".length()).trim();
            return key.isEmpty() ? null : new AdvancementRequirement(key);
        }

        if (rawRequirement.startsWith("equipment-all:")) {
            return parseEquipmentRequirement(rawRequirement.substring("equipment-all:".length()), true);
        }

        if (rawRequirement.startsWith("equipment-any:")) {
            return parseEquipmentRequirement(rawRequirement.substring("equipment-any:".length()), false);
        }

        if (rawRequirement.startsWith("advancement-count:")) {
            return parsePositiveInt(rawRequirement.substring("advancement-count:".length()))
                    .map(AdvancementCountRequirement::new);
        }

        if (rawRequirement.startsWith("level:")) {
            return parsePositiveInt(rawRequirement.substring("level:".length()))
                    .map(LevelRequirement::new);
        }

        if (rawRequirement.startsWith("location:")) {
            return parseLocationRequirement(rawRequirement.substring("location:".length()));
        }

        return null;
    }

    private static Requirement parseEquipmentRequirement(String rawItems, boolean requireAll) {
        List<ItemTarget> items = parseItems(rawItems);
        return items.isEmpty() ? null : new EquipmentRequirement(List.copyOf(items), requireAll);
    }

    private static Requirement parseLocationRequirement(String rawLocationType) {
        try {
            return new LocationRequirement(LocationRequirement.Type.valueOf(rawLocationType.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<ItemTarget> parseItems(String rawItems) {
        List<ItemTarget> items = new ArrayList<>();
        for (String rawItem : rawItems.split(",")) {
            ItemTarget item = parseItem(rawItem.trim());
            if (item == null) {
                return List.of();
            }
            items.add(item);
        }
        return items;
    }

    private static ItemTarget parseItem(String rawItem) {
        if (rawItem.isEmpty()) {
            return null;
        }

        String[] parts = rawItem.split("\\*", 2);
        Material material = Material.getMaterial(parts[0].trim());
        if (material == null) {
            return null;
        }

        int amount = 1;
        if (parts.length == 2) {
            amount = parsePositiveInt(parts[1]).orElse(-1);
        }

        return amount <= 0 ? null : new ItemTarget(material, amount);
    }

    private static PositiveInt parsePositiveInt(String rawValue) {
        try {
            int value = Integer.parseInt(rawValue.trim());
            return value <= 0 ? PositiveInt.empty() : PositiveInt.of(value);
        } catch (NumberFormatException exception) {
            return PositiveInt.empty();
        }
    }

    private static String encode(String id) {
        return PREFIX + id;
    }

    private static String decode(String target) {
        return target != null && target.startsWith(PREFIX) ? target.substring(PREFIX.length()) : target;
    }

    private record Definition(String id, String label, Requirement requirement) {
    }

    private sealed interface Requirement permits ItemRequirement, EquipmentRequirement, AdvancementRequirement, AdvancementCountRequirement, LevelRequirement, LocationRequirement {
    }

    private record ItemRequirement(List<ItemTarget> items) implements Requirement {
    }

    private record EquipmentRequirement(List<ItemTarget> items, boolean requireAll) implements Requirement {
    }

    private record AdvancementRequirement(String key) implements Requirement {
    }

    private record AdvancementCountRequirement(int amount) implements Requirement {
    }

    private record LevelRequirement(int level) implements Requirement {
    }

    private record LocationRequirement(Type type) implements Requirement {
        private enum Type {
            HEIGHT_LIMIT,
            BEDROCK,
            NETHER_ROOF
        }
    }

    private record ItemTarget(Material material, int amount) {
    }

    private record PositiveInt(Integer value) {
        private static PositiveInt of(int value) {
            return new PositiveInt(value);
        }

        private static PositiveInt empty() {
            return new PositiveInt(null);
        }

        private <T> T map(java.util.function.IntFunction<T> mapper) {
            return value == null ? null : mapper.apply(value);
        }

        private int orElse(int fallback) {
            return value == null ? fallback : value;
        }
    }
}
