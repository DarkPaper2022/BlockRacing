package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.RayTraceResult;
import top.lqsnow.blockracing.utils.TranslationUtil;

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
    private static final int PROGRESS_DETAIL_LIMIT = 30;
    private static final int PROGRESS_ITEMS_PER_LINE = 6;

    private static final Map<String, Definition> DEFINITIONS = new HashMap<>();
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
    private static final Map<String, Set<EntityType>> DEATH_PROJECTILES = new HashMap<>();
    private static final Set<String> FISHED_TREASURE_PLAYERS = new HashSet<>();
    private static final Set<String> MAX_LEVEL_VILLAGER_PLAYERS = new HashSet<>();
    private static final Map<String, Set<EntityType>> SPIED_ENTITY_TYPES = new HashMap<>();
    private static final Map<String, Integer> CONTINUOUS_WEAR_TICKS = new HashMap<>();
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

    public static void clearDefinitions() {
        DEFINITIONS.clear();
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
        DEATH_PROJECTILES.clear();
        FISHED_TREASURE_PLAYERS.clear();
        MAX_LEVEL_VILLAGER_PLAYERS.clear();
        SPIED_ENTITY_TYPES.clear();
        CONTINUOUS_WEAR_TICKS.clear();
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

    public static void recordDeathProjectile(Player player, EntityType projectileType) {
        if (projectileType != null) {
            DEATH_PROJECTILES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(projectileType);
        }
    }

    public static void recordFishTreasure(Player player) {
        FISHED_TREASURE_PLAYERS.add(player.getName());
    }

    public static void recordMaxLevelVillager(Player player) {
        MAX_LEVEL_VILLAGER_PLAYERS.add(player.getName());
    }

    public static void recordSpiedEntity(Player player, EntityType entityType) {
        SPIED_ENTITY_TYPES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(entityType);
    }

    public static void recordContinuousWearTick(Player player, Material material, int ticks) {
        String key = wearProgressKey(player.getName(), material);
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.getType().equals(material)) {
            CONTINUOUS_WEAR_TICKS.merge(key, ticks, Integer::sum);
        } else {
            CONTINUOUS_WEAR_TICKS.remove(key);
        }
    }

    public static void recordSpyglassTarget(Player player) {
        ItemStack itemInUse = player.getItemInUse();
        if (itemInUse == null || !itemInUse.getType().equals(Material.SPYGLASS)) {
            return;
        }

        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), 64D,
                FluidCollisionMode.NEVER, true, 0.1D, entity -> entity instanceof LivingEntity && !entity.equals(player));
        Entity hitEntity = result == null ? null : result.getHitEntity();
        if (hitEntity != null) {
            recordSpiedEntity(player, hitEntity.getType());
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
        return definition.label();
    }

    public static List<String> getProgressLore(String target, Player player) {
        Definition definition = DEFINITIONS.get(decode(target));
        if (definition == null || player == null) {
            return List.of();
        }

        Progress progress = getProgress(definition.requirement(), player);
        if (progress == null) {
            return List.of();
        }

        List<String> lore = new ArrayList<>();
        lore.add(Message.MENU_TARGET_LIST_PROGRESS_LINE.getString()
                .replace("%current%", String.valueOf(Math.min(progress.current(), progress.required())))
                .replace("%required%", String.valueOf(progress.required())));
        addDetailLore(lore, Message.MENU_TARGET_LIST_PROGRESS_DONE_LINE.getString(), progress.completed());
        addDetailLore(lore, Message.MENU_TARGET_LIST_PROGRESS_MISSING_LINE.getString(), progress.missing());
        return lore;
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

        if (definition.requirement() instanceof ItemUniqueRequirement itemUniqueRequirement) {
            for (Inventory chest : teamChests) {
                if (inventoryContainsUnique(chest, itemUniqueRequirement.items(), itemUniqueRequirement.amount())) {
                    return chestSource;
                }
            }
        }

        if (definition.requirement() instanceof EnchantedItemRequirement enchantedItemRequirement) {
            for (Inventory chest : teamChests) {
                if (inventoryContainsEnchantedItem(chest, enchantedItemRequirement)) {
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

        if (requirement instanceof ItemUniqueRequirement itemUniqueRequirement) {
            return itemUniqueRequirement.items().stream()
                    .map(ItemTarget::material)
                    .allMatch(Objects::nonNull)
                    && countUniqueMaterials(itemUniqueRequirement.items()) >= itemUniqueRequirement.amount();
        }

        if (requirement instanceof EnchantedItemRequirement enchantedItemRequirement) {
            return enchantedItemRequirement.material() != null && enchantedItemRequirement.enchantments().stream()
                    .allMatch(enchantmentTarget -> enchantmentTarget.enchantment() != null);
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

        if (requirement instanceof ItemUniqueRequirement itemUniqueRequirement) {
            return inventoryContainsUnique(player.getInventory(), itemUniqueRequirement.items(), itemUniqueRequirement.amount());
        }

        if (requirement instanceof EnchantedItemRequirement enchantedItemRequirement) {
            return inventoryContainsEnchantedItem(player.getInventory(), enchantedItemRequirement);
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

        if (requirement instanceof DeathProjectileRequirement deathProjectileRequirement) {
            return DEATH_PROJECTILES.getOrDefault(player.getName(), Set.of()).contains(deathProjectileRequirement.entityType());
        }

        if (requirement instanceof FishTreasureRequirement) {
            return FISHED_TREASURE_PLAYERS.contains(player.getName());
        }

        if (requirement instanceof VillagerMaxLevelRequirement) {
            return MAX_LEVEL_VILLAGER_PLAYERS.contains(player.getName());
        }

        if (requirement instanceof SpyUniqueRequirement spyUniqueRequirement) {
            return SPIED_ENTITY_TYPES.getOrDefault(player.getName(), Set.of()).size() >= spyUniqueRequirement.amount();
        }

        if (requirement instanceof WearContinuousRequirement wearContinuousRequirement) {
            return CONTINUOUS_WEAR_TICKS.getOrDefault(wearProgressKey(player.getName(), wearContinuousRequirement.material()), 0) >= wearContinuousRequirement.ticks();
        }

        return false;
    }

    private static Progress getProgress(Requirement requirement, Player player) {
        String playerName = player.getName();

        if (requirement instanceof ItemRequirement itemRequirement) {
            return itemProgress(player.getInventory(), itemRequirement.items());
        }

        if (requirement instanceof ItemUniqueRequirement itemUniqueRequirement) {
            return itemUniqueProgress(player.getInventory(), itemUniqueRequirement.items(), itemUniqueRequirement.amount());
        }

        if (requirement instanceof EnchantedItemRequirement enchantedItemRequirement) {
            String itemName = displayEnchantedItemRequirement(enchantedItemRequirement);
            if (inventoryContainsEnchantedItem(player.getInventory(), enchantedItemRequirement)) {
                return new Progress(1, 1, List.of(itemName), List.of());
            }
            return new Progress(0, 1, List.of(), List.of(itemName));
        }

        if (requirement instanceof AdvancementCountRequirement advancementCountRequirement) {
            return countProgress(countCompletedAdvancements(player), advancementCountRequirement.amount());
        }

        if (requirement instanceof EffectCountRequirement effectCountRequirement) {
            List<String> activeEffects = player.getActivePotionEffects().stream()
                    .map(effect -> formatKey(effect.getType().getKey().getKey()))
                    .sorted()
                    .toList();
            return new Progress(activeEffects.size(), effectCountRequirement.amount(), activeEffects, List.of());
        }

        if (requirement instanceof KillCountRequirement killCountRequirement) {
            return countProgress(KILL_COUNTS.getOrDefault(playerName, 0), killCountRequirement.amount());
        }

        if (requirement instanceof KillUniqueRequirement killUniqueRequirement) {
            if (killUniqueRequirement.category().equals(EntityCategory.UNDEAD_WITH_REPETITION)) {
                return countProgress(UNDEAD_KILL_COUNTS.getOrDefault(playerName, 0), killUniqueRequirement.amount());
            }
            if (killUniqueRequirement.category().equals(EntityCategory.ARTHROPOD_WITH_REPETITION)) {
                return countProgress(ARTHROPOD_KILL_COUNTS.getOrDefault(playerName, 0), killUniqueRequirement.amount());
            }
            List<String> killedEntities = KILLED_ENTITY_TYPES.getOrDefault(playerName, Set.of()).stream()
                    .filter(killUniqueRequirement.category()::contains)
                    .map(Goal::displayEntity)
                    .sorted()
                    .toList();
            return new Progress(killedEntities.size(), killUniqueRequirement.amount(), killedEntities, List.of());
        }

        if (requirement instanceof BreedUniqueRequirement breedUniqueRequirement) {
            List<String> bredEntities = BRED_ENTITY_TYPES.getOrDefault(playerName, Set.of()).stream()
                    .map(Goal::displayEntity)
                    .sorted()
                    .toList();
            return new Progress(bredEntities.size(), breedUniqueRequirement.amount(), bredEntities, List.of());
        }

        if (requirement instanceof ConsumeAllRequirement consumeAllRequirement) {
            Set<Material> consumedItems = CONSUMED_ITEMS.getOrDefault(playerName, Set.of());
            List<String> completed = consumeAllRequirement.items().stream()
                    .map(ItemTarget::material)
                    .filter(consumedItems::contains)
                    .map(Goal::displayMaterial)
                    .sorted()
                    .toList();
            List<String> missing = consumeAllRequirement.items().stream()
                    .map(ItemTarget::material)
                    .filter(material -> !consumedItems.contains(material))
                    .map(Goal::displayMaterial)
                    .sorted()
                    .toList();
            return new Progress(completed.size(), consumeAllRequirement.items().size(), completed, missing);
        }

        if (requirement instanceof ConsumeUniqueRequirement consumeUniqueRequirement) {
            List<String> consumedFoods = CONSUMED_ITEMS.getOrDefault(playerName, Set.of()).stream()
                    .filter(Material::isEdible)
                    .map(Goal::displayMaterial)
                    .sorted()
                    .toList();
            return new Progress(consumedFoods.size(), consumeUniqueRequirement.amount(), consumedFoods, List.of());
        }

        if (requirement instanceof CraftUniqueRequirement craftUniqueRequirement) {
            List<String> craftedItems = CRAFTED_ITEMS.getOrDefault(playerName, Set.of()).stream()
                    .map(Goal::displayMaterial)
                    .sorted()
                    .toList();
            return new Progress(craftedItems.size(), craftUniqueRequirement.amount(), craftedItems, List.of());
        }

        if (requirement instanceof DamageRequirement damageRequirement) {
            Map<String, Double> damageMap = damageRequirement.kind().equals(DamageRequirement.Kind.DEALT) ? DAMAGE_DEALT : DAMAGE_TAKEN;
            return countProgress((int) Math.floor(damageMap.getOrDefault(playerName, 0D)), damageRequirement.amount());
        }

        if (requirement instanceof FishTreasureRequirement) {
            return countProgress(FISHED_TREASURE_PLAYERS.contains(playerName) ? 1 : 0, 1);
        }

        if (requirement instanceof VillagerMaxLevelRequirement) {
            return countProgress(MAX_LEVEL_VILLAGER_PLAYERS.contains(playerName) ? 1 : 0, 1);
        }

        if (requirement instanceof SpyUniqueRequirement spyUniqueRequirement) {
            List<String> spiedEntities = SPIED_ENTITY_TYPES.getOrDefault(playerName, Set.of()).stream()
                    .map(Goal::displayEntity)
                    .sorted()
                    .toList();
            return new Progress(spiedEntities.size(), spyUniqueRequirement.amount(), spiedEntities, List.of());
        }

        if (requirement instanceof WearContinuousRequirement wearContinuousRequirement) {
            return countProgress(CONTINUOUS_WEAR_TICKS.getOrDefault(wearProgressKey(playerName, wearContinuousRequirement.material()), 0) / 20, wearContinuousRequirement.ticks() / 20);
        }

        return null;
    }

    private static Progress countProgress(int current, int required) {
        return new Progress(current, required, List.of(), List.of());
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

    private static Progress itemProgress(Inventory inventory, List<ItemTarget> items) {
        List<String> completed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ItemTarget item : items) {
            String displayName = displayItemTarget(item);
            if (count(inventory, item.material()) >= item.amount()) {
                completed.add(displayName);
            } else {
                missing.add(displayName);
            }
        }
        completed.sort(String::compareTo);
        missing.sort(String::compareTo);
        return new Progress(completed.size(), items.size(), completed, missing);
    }

    private static boolean inventoryContainsUnique(Inventory inventory, List<ItemTarget> items, int amount) {
        Set<Material> matchedItems = new HashSet<>();
        for (ItemTarget item : items) {
            if (count(inventory, item.material()) >= item.amount()) {
                matchedItems.add(item.material());
            }
        }
        return matchedItems.size() >= amount;
    }

    private static boolean inventoryContainsEnchantedItem(Inventory inventory, EnchantedItemRequirement requirement) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType().equals(requirement.material()) && itemMatchesEnchantments(item, requirement.enchantments())) {
                return true;
            }
        }
        return false;
    }

    private static boolean itemMatchesEnchantments(ItemStack item, List<EnchantmentTarget> enchantments) {
        for (EnchantmentTarget enchantmentTarget : enchantments) {
            if (item.getEnchantmentLevel(enchantmentTarget.enchantment()) < enchantmentTarget.level()) {
                return false;
            }
        }
        return true;
    }

    private static Progress itemUniqueProgress(Inventory inventory, List<ItemTarget> items, int amount) {
        List<String> completed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<Material> completedMaterials = new HashSet<>();
        Set<Material> missingMaterials = new HashSet<>();

        for (ItemTarget item : items) {
            if (count(inventory, item.material()) >= item.amount()) {
                if (completedMaterials.add(item.material())) {
                    completed.add(displayItemTarget(item));
                }
                missingMaterials.remove(item.material());
            } else if (!completedMaterials.contains(item.material()) && missingMaterials.add(item.material())) {
                missing.add(displayItemTarget(item));
            }
        }

        completed.sort(String::compareTo);
        missing.sort(String::compareTo);
        return new Progress(completed.size(), amount, completed, missing);
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
        if (rawRequirement.startsWith("item-unique:")) {
            return parseItemUniqueRequirement(rawRequirement.substring("item-unique:".length()));
        }

        if (rawRequirement.startsWith("item-any:")) {
            List<ItemTarget> items = parseItems(rawRequirement.substring("item-any:".length()));
            return items.isEmpty() ? null : new ItemUniqueRequirement(1, List.copyOf(items));
        }

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

        if (rawRequirement.startsWith("enchanted-item:")) {
            return parseEnchantedItemRequirement(rawRequirement.substring("enchanted-item:".length()));
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

        if (rawRequirement.equals("fish-treasure")) {
            return new FishTreasureRequirement();
        }

        if (rawRequirement.startsWith("spy-unique:")) {
            return parsePositiveInt(rawRequirement.substring("spy-unique:".length()))
                    .map(SpyUniqueRequirement::new);
        }

        if (rawRequirement.startsWith("wear-continuous:")) {
            return parseWearContinuousRequirement(rawRequirement.substring("wear-continuous:".length()));
        }

        if (rawRequirement.equals("villager-max-level")) {
            return new VillagerMaxLevelRequirement();
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

        if (rawRequirement.startsWith("death-projectile:")) {
            return parseEntityRequirement(rawRequirement.substring("death-projectile:".length()), DeathProjectileRequirement::new);
        }

        return null;
    }

    private static Requirement parseItemUniqueRequirement(String rawRequirement) {
        String[] parts = rawRequirement.split(":", 2);
        if (parts.length != 2) {
            return null;
        }

        PositiveInt amount = parsePositiveInt(parts[0]);
        if (amount.value() == null) {
            return null;
        }

        List<ItemTarget> items = parseItems(parts[1]);
        if (items.isEmpty() || countUniqueMaterials(items) < amount.value()) {
            return null;
        }

        return new ItemUniqueRequirement(amount.value(), List.copyOf(items));
    }

    private static Requirement parseEnchantedItemRequirement(String rawRequirement) {
        String[] parts = rawRequirement.split(":", 2);
        if (parts.length != 2) {
            return null;
        }

        Material material = Material.getMaterial(parts[0].trim());
        if (material == null) {
            return null;
        }

        List<EnchantmentTarget> enchantments = new ArrayList<>();
        for (String rawEnchantment : parts[1].split(",")) {
            EnchantmentTarget enchantmentTarget = parseEnchantmentTarget(rawEnchantment.trim());
            if (enchantmentTarget == null) {
                return null;
            }
            enchantments.add(enchantmentTarget);
        }
        return enchantments.isEmpty() ? null : new EnchantedItemRequirement(material, List.copyOf(enchantments));
    }

    private static EnchantmentTarget parseEnchantmentTarget(String rawEnchantment) {
        String[] parts = rawEnchantment.split("\\*", 2);
        if (parts.length != 2) {
            return null;
        }

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
        int level = parsePositiveInt(parts[1]).orElse(-1);
        return enchantment == null || level <= 0 ? null : new EnchantmentTarget(enchantment, level);
    }

    private static Requirement parseWearContinuousRequirement(String rawRequirement) {
        String[] parts = rawRequirement.split(":", 2);
        if (parts.length != 2) {
            return null;
        }

        Material material = Material.getMaterial(parts[0].trim());
        PositiveInt seconds = parsePositiveInt(parts[1]);
        if (material == null || seconds.value() == null) {
            return null;
        }

        return new WearContinuousRequirement(material, seconds.value() * 20);
    }

    private static long countUniqueMaterials(List<ItemTarget> items) {
        return items.stream()
                .map(ItemTarget::material)
                .distinct()
                .count();
    }

    private static void addDetailLore(List<String> lore, String template, List<String> items) {
        if (items.isEmpty()) {
            return;
        }

        int displayAmount = Math.min(items.size(), PROGRESS_DETAIL_LIMIT);
        for (int start = 0; start < displayAmount; start += PROGRESS_ITEMS_PER_LINE) {
            int end = Math.min(displayAmount, start + PROGRESS_ITEMS_PER_LINE);
            String line = String.join("、", items.subList(start, end));
            lore.add(start == 0 ? template.replace("%items%", line) : "§f" + line);
        }
        if (items.size() > displayAmount) {
            lore.add(Message.MENU_TARGET_LIST_PROGRESS_MORE_LINE.getString()
                    .replace("%amount%", String.valueOf(items.size() - displayAmount)));
        }
    }

    private static String displayItemTarget(ItemTarget item) {
        String displayName = displayMaterial(item.material());
        return item.amount() == 1 ? displayName : displayName + "*" + item.amount();
    }

    private static String displayEnchantedItemRequirement(EnchantedItemRequirement requirement) {
        List<String> enchantments = requirement.enchantments().stream()
                .map(enchantmentTarget -> formatKey(enchantmentTarget.enchantment().getKey().getKey()) + " " + enchantmentTarget.level())
                .toList();
        return displayMaterial(requirement.material()) + " (" + String.join(", ", enchantments) + ")";
    }

    private static String displayMaterial(Material material) {
        String translatedName = TranslationUtil.getValue(material.name());
        return translatedName == null || translatedName.isBlank() ? formatKey(material.name()) : translatedName;
    }

    private static String displayEntity(EntityType entityType) {
        return formatKey(entityType.name());
    }

    private static String wearProgressKey(String playerName, Material material) {
        return playerName + "|" + material.name();
    }

    private static String formatKey(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("[_\\-]");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
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

    private static String decode(String target) {
        return target != null && target.startsWith(PREFIX) ? target.substring(PREFIX.length()) : target;
    }

    private record Definition(String id, String label, Requirement requirement) {
    }

    private record Progress(int current, int required, List<String> completed, List<String> missing) {
    }

    private sealed interface Requirement permits ItemRequirement, ItemUniqueRequirement, EnchantedItemRequirement, EquipmentRequirement, ColoredEquipmentRequirement, UniqueLeatherArmorColorsRequirement, AdvancementRequirement, AdvancementCountRequirement, LevelRequirement, LocationRequirement, EffectRequirement, EffectCountRequirement, HungerRequirement, KillRequirement, KillCountRequirement, KillUniqueRequirement, BreedRequirement, BreedUniqueRequirement, TameRequirement, ConsumeRequirement, ConsumePotionRequirement, ConsumeAllRequirement, ConsumeUniqueRequirement, CraftUniqueRequirement, FishTreasureRequirement, SpyUniqueRequirement, WearContinuousRequirement, UseBlockRequirement, DamageRequirement, DeathCauseRequirement, DeathAttackerRequirement, DeathProjectileRequirement, VillagerMaxLevelRequirement {
    }

    private record ItemRequirement(List<ItemTarget> items) implements Requirement {
    }

    private record ItemUniqueRequirement(int amount, List<ItemTarget> items) implements Requirement {
    }

    private record EnchantedItemRequirement(Material material, List<EnchantmentTarget> enchantments) implements Requirement {
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

    private record FishTreasureRequirement() implements Requirement {
    }

    private record SpyUniqueRequirement(int amount) implements Requirement {
    }

    private record WearContinuousRequirement(Material material, int ticks) implements Requirement {
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

    private record DeathProjectileRequirement(EntityType entityType) implements Requirement {
    }

    private record VillagerMaxLevelRequirement() implements Requirement {
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

    private record EnchantmentTarget(Enchantment enchantment, int level) {
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
