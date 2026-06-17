package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import top.lqsnow.blockracing.Main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

public class Goal {
    public static final String PREFIX = "DRAFTOUT:";

    private static final Map<String, Definition> DEFINITIONS = new HashMap<>();
    private static final Map<String, String> DISPLAY_NAMES = new HashMap<>();
    private static final Map<String, Set<EntityType>> KILLED_ENTITY_TYPES = new HashMap<>();
    private static final Map<String, Integer> KILL_COUNTS = new HashMap<>();
    private static final Map<String, Integer> UNDEAD_KILL_COUNTS = new HashMap<>();
    private static final Map<String, Integer> ARTHROPOD_KILL_COUNTS = new HashMap<>();
    private static final Map<String, Set<EntityType>> BRED_ENTITY_TYPES = new HashMap<>();
    private static final Map<String, Set<EntityType>> TAMED_ENTITY_TYPES = new HashMap<>();
    private static final Map<String, Set<Material>> CONSUMED_ITEMS = new HashMap<>();
    private static final Map<String, Set<String>> CONSUMED_POTIONS = new HashMap<>();
    private static final Map<String, Set<Material>> CRAFTED_ITEMS = new HashMap<>();
    private static final Map<String, Set<Material>> USED_BLOCKS = new HashMap<>();
    private static final Map<String, Double> DAMAGE_DEALT = new HashMap<>();
    private static final Map<String, Double> DAMAGE_TAKEN = new HashMap<>();
    private static final Map<String, Set<EntityDamageEvent.DamageCause>> DEATH_CAUSES = new HashMap<>();
    private static final Map<String, Set<EntityType>> DEATH_ATTACKERS = new HashMap<>();
    private static final Set<EntityType> HOSTILE_ENTITY_TYPES = EnumSet.of(
            EntityType.BLAZE,
            EntityType.BOGGED,
            EntityType.BREEZE,
            EntityType.CAVE_SPIDER,
            EntityType.CREEPER,
            EntityType.DROWNED,
            EntityType.ELDER_GUARDIAN,
            EntityType.ENDERMAN,
            EntityType.ENDERMITE,
            EntityType.EVOKER,
            EntityType.GHAST,
            EntityType.GUARDIAN,
            EntityType.HOGLIN,
            EntityType.HUSK,
            EntityType.MAGMA_CUBE,
            EntityType.PARCHED,
            EntityType.PHANTOM,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.PILLAGER,
            EntityType.RAVAGER,
            EntityType.SHULKER,
            EntityType.SILVERFISH,
            EntityType.SKELETON,
            EntityType.SLIME,
            EntityType.SPIDER,
            EntityType.STRAY,
            EntityType.VEX,
            EntityType.VINDICATOR,
            EntityType.WARDEN,
            EntityType.WITCH,
            EntityType.WITHER_SKELETON,
            EntityType.ZOGLIN,
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIFIED_PIGLIN
    );
    private static final Set<EntityType> UNDEAD_ENTITY_TYPES = EnumSet.of(
            EntityType.DROWNED,
            EntityType.HUSK,
            EntityType.PHANTOM,
            EntityType.SKELETON,
            EntityType.SKELETON_HORSE,
            EntityType.STRAY,
            EntityType.WITHER,
            EntityType.WITHER_SKELETON,
            EntityType.ZOGLIN,
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_HORSE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIFIED_PIGLIN
    );
    private static final Set<EntityType> ARTHROPOD_ENTITY_TYPES = EnumSet.of(
            EntityType.BEE,
            EntityType.CAVE_SPIDER,
            EntityType.ENDERMITE,
            EntityType.SILVERFISH,
            EntityType.SPIDER
    );

    public static List<String> load(String[] lines) {
        DEFINITIONS.clear();
        DISPLAY_NAMES.clear();
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

        loadDisplayNames();
        Bukkit.getLogger().info("[BlockRacing] Loaded Draftout goals: " + goals.size());
        return List.copyOf(goals);
    }

    public static void clearDefinitions() {
        DEFINITIONS.clear();
        DISPLAY_NAMES.clear();
    }

    public static String registerDefinition(String id, String label, String rawRequirement) {
        if (id == null || label == null || rawRequirement == null) {
            return null;
        }

        String normalizedId = id.trim();
        String normalizedLabel = label.trim();
        Requirement requirement = parseRequirement(rawRequirement.trim());

        if (normalizedId.isEmpty() || normalizedLabel.isEmpty() || requirement == null) {
            return null;
        }

        DEFINITIONS.put(normalizedId, new Definition(normalizedId, normalizedLabel, requirement));
        return encode(normalizedId);
    }

    public static void loadRegisteredDisplayNames() {
        DISPLAY_NAMES.clear();
        loadDisplayNames();
        Bukkit.getLogger().info("[BlockRacing] Loaded Draftout goals: " + DEFINITIONS.size());
    }

    public static void resetProgress() {
        KILLED_ENTITY_TYPES.clear();
        KILL_COUNTS.clear();
        UNDEAD_KILL_COUNTS.clear();
        ARTHROPOD_KILL_COUNTS.clear();
        BRED_ENTITY_TYPES.clear();
        TAMED_ENTITY_TYPES.clear();
        CONSUMED_ITEMS.clear();
        CONSUMED_POTIONS.clear();
        CRAFTED_ITEMS.clear();
        USED_BLOCKS.clear();
        DAMAGE_DEALT.clear();
        DAMAGE_TAKEN.clear();
        DEATH_CAUSES.clear();
        DEATH_ATTACKERS.clear();
    }

    public static void recordKill(Player player, EntityType entityType) {
        String playerName = player.getName();
        KILLED_ENTITY_TYPES.computeIfAbsent(playerName, ignored -> new HashSet<>()).add(entityType);
        KILL_COUNTS.merge(playerName, 1, Integer::sum);
        if (UNDEAD_ENTITY_TYPES.contains(entityType)) {
            UNDEAD_KILL_COUNTS.merge(playerName, 1, Integer::sum);
        }
        if (ARTHROPOD_ENTITY_TYPES.contains(entityType)) {
            ARTHROPOD_KILL_COUNTS.merge(playerName, 1, Integer::sum);
        }
    }

    public static void recordBreed(Player player, EntityType entityType) {
        BRED_ENTITY_TYPES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(entityType);
    }

    public static void recordTame(Player player, EntityType entityType) {
        TAMED_ENTITY_TYPES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(entityType);
    }

    public static void recordConsume(Player player, Material material) {
        CONSUMED_ITEMS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordConsumePotion(Player player, String potionType) {
        CONSUMED_POTIONS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(potionType);
    }

    public static void recordCraft(Player player, Material material) {
        CRAFTED_ITEMS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordUseBlock(Player player, Material material) {
        USED_BLOCKS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordDamageDealt(Player player, double amount) {
        DAMAGE_DEALT.merge(player.getName(), amount, Double::sum);
    }

    public static void recordDamageTaken(Player player, double amount) {
        DAMAGE_TAKEN.merge(player.getName(), amount, Double::sum);
    }

    public static void recordDeath(Player player, EntityDamageEvent.DamageCause cause, EntityType attackerType) {
        if (cause != null) {
            DEATH_CAUSES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(cause);
        }
        if (attackerType != null) {
            DEATH_ATTACKERS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(attackerType);
        }
    }

    public static boolean isGoal(String target) {
        return target != null && target.startsWith(PREFIX) && DEFINITIONS.containsKey(decode(target));
    }

    public static boolean isKnownGoalId(String target) {
        return target != null && target.startsWith(PREFIX);
    }

    public static String getDisplayName(String target) {
        Definition definition = DEFINITIONS.get(decode(target));
        if (definition == null) {
            return target;
        }
        return DISPLAY_NAMES.getOrDefault(definition.id(), definition.label());
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

        if (requirement instanceof ColoredEquipmentRequirement coloredEquipmentRequirement) {
            return coloredEquipmentRequirement.material() != null && coloredEquipmentRequirement.dyeColor() != null;
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

        if (requirement instanceof ColoredEquipmentRequirement coloredEquipmentRequirement) {
            return equipmentContainsColoredLeather(player, coloredEquipmentRequirement.material(), coloredEquipmentRequirement.dyeColor());
        }

        if (requirement instanceof UniqueLeatherArmorColorsRequirement uniqueLeatherArmorColorsRequirement) {
            return countUniqueLeatherArmorColors(player) >= uniqueLeatherArmorColorsRequirement.amount();
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

        if (requirement instanceof EffectRequirement effectRequirement) {
            return player.getActivePotionEffects().stream()
                    .anyMatch(effect -> effect.getType().getKey().getKey().equals(effectRequirement.effectKey()));
        }

        if (requirement instanceof EffectCountRequirement effectCountRequirement) {
            return player.getActivePotionEffects().size() >= effectCountRequirement.amount();
        }

        if (requirement instanceof HungerRequirement hungerRequirement) {
            return hungerRequirement.type().equals(HungerRequirement.Type.EMPTY)
                    && player.getFoodLevel() <= 0;
        }

        if (requirement instanceof KillRequirement killRequirement) {
            return KILLED_ENTITY_TYPES.getOrDefault(player.getName(), Set.of()).contains(killRequirement.entityType());
        }

        if (requirement instanceof KillCountRequirement killCountRequirement) {
            return KILL_COUNTS.getOrDefault(player.getName(), 0) >= killCountRequirement.amount();
        }

        if (requirement instanceof KillUniqueRequirement killUniqueRequirement) {
            if (killUniqueRequirement.category().equals(EntityCategory.UNDEAD_WITH_REPETITION)) {
                return UNDEAD_KILL_COUNTS.getOrDefault(player.getName(), 0) >= killUniqueRequirement.amount();
            }
            if (killUniqueRequirement.category().equals(EntityCategory.ARTHROPOD_WITH_REPETITION)) {
                return ARTHROPOD_KILL_COUNTS.getOrDefault(player.getName(), 0) >= killUniqueRequirement.amount();
            }
            return countMatchingEntityTypes(KILLED_ENTITY_TYPES.getOrDefault(player.getName(), Set.of()), killUniqueRequirement.category()) >= killUniqueRequirement.amount();
        }

        if (requirement instanceof BreedRequirement breedRequirement) {
            return BRED_ENTITY_TYPES.getOrDefault(player.getName(), Set.of()).contains(breedRequirement.entityType());
        }

        if (requirement instanceof BreedUniqueRequirement breedUniqueRequirement) {
            return BRED_ENTITY_TYPES.getOrDefault(player.getName(), Set.of()).size() >= breedUniqueRequirement.amount();
        }

        if (requirement instanceof TameRequirement tameRequirement) {
            return TAMED_ENTITY_TYPES.getOrDefault(player.getName(), Set.of()).contains(tameRequirement.entityType());
        }

        if (requirement instanceof ConsumeRequirement consumeRequirement) {
            return CONSUMED_ITEMS.getOrDefault(player.getName(), Set.of()).contains(consumeRequirement.material());
        }

        if (requirement instanceof ConsumePotionRequirement consumePotionRequirement) {
            return CONSUMED_POTIONS.getOrDefault(player.getName(), Set.of()).contains(consumePotionRequirement.potionType());
        }

        if (requirement instanceof ConsumeAllRequirement consumeAllRequirement) {
            return consumeAllRequirement.items().stream()
                    .map(ItemTarget::material)
                    .allMatch(material -> CONSUMED_ITEMS.getOrDefault(player.getName(), Set.of()).contains(material));
        }

        if (requirement instanceof ConsumeUniqueRequirement consumeUniqueRequirement) {
            long edibleConsumedItems = CONSUMED_ITEMS.getOrDefault(player.getName(), Set.of()).stream()
                    .filter(Material::isEdible)
                    .count();
            return edibleConsumedItems >= consumeUniqueRequirement.amount();
        }

        if (requirement instanceof CraftUniqueRequirement craftUniqueRequirement) {
            return CRAFTED_ITEMS.getOrDefault(player.getName(), Set.of()).size() >= craftUniqueRequirement.amount();
        }

        if (requirement instanceof UseBlockRequirement useBlockRequirement) {
            return USED_BLOCKS.getOrDefault(player.getName(), Set.of()).contains(useBlockRequirement.material());
        }

        if (requirement instanceof DamageRequirement damageRequirement) {
            Map<String, Double> damageMap = damageRequirement.kind().equals(DamageRequirement.Kind.DEALT) ? DAMAGE_DEALT : DAMAGE_TAKEN;
            return damageMap.getOrDefault(player.getName(), 0D) >= damageRequirement.amount();
        }

        if (requirement instanceof DeathCauseRequirement deathCauseRequirement) {
            return DEATH_CAUSES.getOrDefault(player.getName(), Set.of()).contains(deathCauseRequirement.cause());
        }

        if (requirement instanceof DeathAttackerRequirement deathAttackerRequirement) {
            return DEATH_ATTACKERS.getOrDefault(player.getName(), Set.of()).contains(deathAttackerRequirement.entityType());
        }

        return false;
    }

    private static long countMatchingEntityTypes(Set<EntityType> entityTypes, EntityCategory category) {
        return entityTypes.stream().filter(category::contains).count();
    }

    private static boolean equipmentContainsAll(Player player, List<ItemTarget> items) {
        List<Material> equipment = getEquipment(player);
        return items.stream().allMatch(item -> equipment.contains(item.material()));
    }

    private static boolean equipmentContainsAny(Player player, List<ItemTarget> items) {
        List<Material> equipment = getEquipment(player);
        return items.stream().anyMatch(item -> equipment.contains(item.material()));
    }

    private static boolean equipmentContainsColoredLeather(Player player, Material material, DyeColor dyeColor) {
        return getEquipmentItems(player).stream().anyMatch(item -> isColoredLeatherArmor(item, material, dyeColor));
    }

    private static int countUniqueLeatherArmorColors(Player player) {
        Set<org.bukkit.Color> colors = new HashSet<>();
        for (ItemStack item : getEquipmentItems(player)) {
            ItemMeta meta = item == null ? null : item.getItemMeta();
            if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
                colors.add(leatherArmorMeta.getColor());
            }
        }
        return colors.size();
    }

    private static boolean isColoredLeatherArmor(ItemStack item, Material material, DyeColor dyeColor) {
        if (item == null || !item.getType().equals(material)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta instanceof LeatherArmorMeta leatherArmorMeta
                && leatherArmorMeta.getColor().equals(dyeColor.getColor());
    }

    private static List<Material> getEquipment(Player player) {
        List<Material> equipment = new ArrayList<>();
        for (ItemStack item : getEquipmentItems(player)) {
            addEquipment(equipment, item);
        }
        return equipment;
    }

    private static List<ItemStack> getEquipmentItems(Player player) {
        return List.of(
                emptyIfNull(player.getInventory().getHelmet()),
                emptyIfNull(player.getInventory().getChestplate()),
                emptyIfNull(player.getInventory().getLeggings()),
                emptyIfNull(player.getInventory().getBoots())
        );
    }

    private static ItemStack emptyIfNull(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
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

        if (rawRequirement.startsWith("equipment-colored:")) {
            return parseColoredEquipmentRequirement(rawRequirement.substring("equipment-colored:".length()));
        }

        if (rawRequirement.startsWith("equipment-unique-leather-colors:")) {
            return parsePositiveInt(rawRequirement.substring("equipment-unique-leather-colors:".length()))
                    .map(UniqueLeatherArmorColorsRequirement::new);
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

        if (rawRequirement.startsWith("effect:")) {
            String effectKey = rawRequirement.substring("effect:".length()).trim().toLowerCase(Locale.ROOT);
            return effectKey.isEmpty() ? null : new EffectRequirement(effectKey);
        }

        if (rawRequirement.startsWith("effects-count:")) {
            return parsePositiveInt(rawRequirement.substring("effects-count:".length()))
                    .map(EffectCountRequirement::new);
        }

        if (rawRequirement.equals("hunger:empty")) {
            return new HungerRequirement(HungerRequirement.Type.EMPTY);
        }

        if (rawRequirement.startsWith("kill:")) {
            return parseEntityRequirement(rawRequirement.substring("kill:".length()), KillRequirement::new);
        }

        if (rawRequirement.startsWith("kill-total:")) {
            return parsePositiveInt(rawRequirement.substring("kill-total:".length()))
                    .map(KillCountRequirement::new);
        }

        if (rawRequirement.startsWith("kill-unique-hostile:")) {
            return parsePositiveInt(rawRequirement.substring("kill-unique-hostile:".length()))
                    .map(amount -> new KillUniqueRequirement(EntityCategory.HOSTILE, amount));
        }

        if (rawRequirement.startsWith("kill-undead:")) {
            return parsePositiveInt(rawRequirement.substring("kill-undead:".length()))
                    .map(amount -> new KillUniqueRequirement(EntityCategory.UNDEAD_WITH_REPETITION, amount));
        }

        if (rawRequirement.startsWith("kill-arthropod:")) {
            return parsePositiveInt(rawRequirement.substring("kill-arthropod:".length()))
                    .map(amount -> new KillUniqueRequirement(EntityCategory.ARTHROPOD_WITH_REPETITION, amount));
        }

        if (rawRequirement.startsWith("breed:")) {
            return parseEntityRequirement(rawRequirement.substring("breed:".length()), BreedRequirement::new);
        }

        if (rawRequirement.startsWith("breed-unique:")) {
            return parsePositiveInt(rawRequirement.substring("breed-unique:".length()))
                    .map(BreedUniqueRequirement::new);
        }

        if (rawRequirement.startsWith("tame:")) {
            return parseEntityRequirement(rawRequirement.substring("tame:".length()), TameRequirement::new);
        }

        if (rawRequirement.startsWith("consume:")) {
            Material material = Material.getMaterial(rawRequirement.substring("consume:".length()).trim());
            return material == null ? null : new ConsumeRequirement(material);
        }

        if (rawRequirement.startsWith("consume-all:")) {
            List<ItemTarget> items = parseItems(rawRequirement.substring("consume-all:".length()));
            return items.isEmpty() ? null : new ConsumeAllRequirement(List.copyOf(items));
        }

        if (rawRequirement.startsWith("consume-potion:")) {
            String potionType = rawRequirement.substring("consume-potion:".length()).trim().toUpperCase(Locale.ROOT);
            return potionType.isEmpty() ? null : new ConsumePotionRequirement(potionType);
        }

        if (rawRequirement.startsWith("consume-unique:")) {
            return parsePositiveInt(rawRequirement.substring("consume-unique:".length()))
                    .map(ConsumeUniqueRequirement::new);
        }

        if (rawRequirement.startsWith("craft-unique:")) {
            return parsePositiveInt(rawRequirement.substring("craft-unique:".length()))
                    .map(CraftUniqueRequirement::new);
        }

        if (rawRequirement.startsWith("use-block:")) {
            Material material = Material.getMaterial(rawRequirement.substring("use-block:".length()).trim());
            return material == null ? null : new UseBlockRequirement(material);
        }

        if (rawRequirement.startsWith("damage-dealt:")) {
            return parsePositiveInt(rawRequirement.substring("damage-dealt:".length()))
                    .map(amount -> new DamageRequirement(DamageRequirement.Kind.DEALT, amount));
        }

        if (rawRequirement.startsWith("damage-taken:")) {
            return parsePositiveInt(rawRequirement.substring("damage-taken:".length()))
                    .map(amount -> new DamageRequirement(DamageRequirement.Kind.TAKEN, amount));
        }

        if (rawRequirement.startsWith("death-cause:")) {
            try {
                return new DeathCauseRequirement(EntityDamageEvent.DamageCause.valueOf(rawRequirement.substring("death-cause:".length()).trim()));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        if (rawRequirement.startsWith("death-attacker:")) {
            return parseEntityRequirement(rawRequirement.substring("death-attacker:".length()), DeathAttackerRequirement::new);
        }

        return null;
    }

    private static Requirement parseEntityRequirement(String rawEntityType, java.util.function.Function<EntityType, Requirement> factory) {
        try {
            return factory.apply(EntityType.valueOf(rawEntityType.trim()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Requirement parseEquipmentRequirement(String rawItems, boolean requireAll) {
        List<ItemTarget> items = parseItems(rawItems);
        return items.isEmpty() ? null : new EquipmentRequirement(List.copyOf(items), requireAll);
    }

    private static Requirement parseColoredEquipmentRequirement(String rawRequirement) {
        String[] parts = rawRequirement.split(",", 2);
        if (parts.length != 2) {
            return null;
        }

        Material material = Material.getMaterial(parts[0].trim());
        if (material == null) {
            return null;
        }

        try {
            DyeColor dyeColor = DyeColor.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            return new ColoredEquipmentRequirement(material, dyeColor);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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

    private static void loadDisplayNames() {
        String lang = Message.MESSAGE_LANG.getString();
        if (lang == null || lang.isBlank()) {
            return;
        }

        String normalizedLang = lang.trim().toLowerCase(Locale.ROOT);
        if (!"zh_cn".equals(normalizedLang)) {
            return;
        }

        String resourceName = "DraftoutGoals_" + normalizedLang + ".yml";
        File file = new File(Main.getInstance().getDataFolder(), resourceName);
        YamlConfiguration displayConfig = YamlConfiguration.loadConfiguration(file);

        try (InputStream input = Main.getInstance().getResource(resourceName)) {
            if (input != null) {
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                displayConfig.setDefaults(defaults);
            }
        } catch (IOException exception) {
            Main.getInstance().getLogger().log(Level.WARNING,
                    "[BlockRacing] Error reading Draftout goal display resource: " + resourceName, exception);
        }

        for (String id : DEFINITIONS.keySet()) {
            String displayName = displayConfig.getString(id);
            if (displayName != null && !displayName.isBlank()) {
                DISPLAY_NAMES.put(id, displayName);
            }
        }

        if (!DISPLAY_NAMES.isEmpty()) {
            Bukkit.getLogger().info("[BlockRacing] Loaded Draftout goal display names for "
                    + normalizedLang + ": " + DISPLAY_NAMES.size());
        }
    }

    private static String decode(String target) {
        return target != null && target.startsWith(PREFIX) ? target.substring(PREFIX.length()) : target;
    }

    private record Definition(String id, String label, Requirement requirement) {
    }

    private sealed interface Requirement permits ItemRequirement, EquipmentRequirement, ColoredEquipmentRequirement, UniqueLeatherArmorColorsRequirement, AdvancementRequirement, AdvancementCountRequirement, LevelRequirement, LocationRequirement, EffectRequirement, EffectCountRequirement, HungerRequirement, KillRequirement, KillCountRequirement, KillUniqueRequirement, BreedRequirement, BreedUniqueRequirement, TameRequirement, ConsumeRequirement, ConsumePotionRequirement, ConsumeAllRequirement, ConsumeUniqueRequirement, CraftUniqueRequirement, UseBlockRequirement, DamageRequirement, DeathCauseRequirement, DeathAttackerRequirement {
    }

    private record ItemRequirement(List<ItemTarget> items) implements Requirement {
    }

    private record EquipmentRequirement(List<ItemTarget> items, boolean requireAll) implements Requirement {
    }

    private record ColoredEquipmentRequirement(Material material, DyeColor dyeColor) implements Requirement {
    }

    private record UniqueLeatherArmorColorsRequirement(int amount) implements Requirement {
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

    private record EffectRequirement(String effectKey) implements Requirement {
    }

    private record EffectCountRequirement(int amount) implements Requirement {
    }

    private record HungerRequirement(Type type) implements Requirement {
        private enum Type {
            EMPTY
        }
    }

    private record KillRequirement(EntityType entityType) implements Requirement {
    }

    private record KillCountRequirement(int amount) implements Requirement {
    }

    private record KillUniqueRequirement(EntityCategory category, int amount) implements Requirement {
    }

    private record BreedRequirement(EntityType entityType) implements Requirement {
    }

    private record BreedUniqueRequirement(int amount) implements Requirement {
    }

    private record TameRequirement(EntityType entityType) implements Requirement {
    }

    private record ConsumeRequirement(Material material) implements Requirement {
    }

    private record ConsumePotionRequirement(String potionType) implements Requirement {
    }

    private record ConsumeAllRequirement(List<ItemTarget> items) implements Requirement {
    }

    private record ConsumeUniqueRequirement(int amount) implements Requirement {
    }

    private record CraftUniqueRequirement(int amount) implements Requirement {
    }

    private record UseBlockRequirement(Material material) implements Requirement {
    }

    private record DamageRequirement(Kind kind, int amount) implements Requirement {
        private enum Kind {
            DEALT,
            TAKEN
        }
    }

    private record DeathCauseRequirement(EntityDamageEvent.DamageCause cause) implements Requirement {
    }

    private record DeathAttackerRequirement(EntityType entityType) implements Requirement {
    }

    private enum EntityCategory {
        HOSTILE {
            @Override
            boolean contains(EntityType entityType) {
                return HOSTILE_ENTITY_TYPES.contains(entityType);
            }
        },
        UNDEAD_WITH_REPETITION {
            @Override
            boolean contains(EntityType entityType) {
                return UNDEAD_ENTITY_TYPES.contains(entityType);
            }
        },
        ARTHROPOD_WITH_REPETITION {
            @Override
            boolean contains(EntityType entityType) {
                return ARTHROPOD_ENTITY_TYPES.contains(entityType);
            }
        };

        abstract boolean contains(EntityType entityType);
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
