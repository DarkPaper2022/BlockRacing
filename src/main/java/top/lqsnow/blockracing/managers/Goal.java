package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
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

public class Goal {
    public static final String PREFIX = "DRAFTOUT:";
    private static final int PROGRESS_DETAIL_LIMIT = 30;
    private static final int PROGRESS_ITEMS_PER_LINE = 6;

    public static final String TEAM_COMPLETION_SOURCE = "[team effort]";
    private static final Map<String, Set<String>> COMPLETED_ADVANCEMENTS = new HashMap<>();
    private static final Map<String, Set<TaskAction>> COMPLETED_ACTIONS = new HashMap<>();
    private static String progressEpoch = java.util.UUID.randomUUID().toString();
    public static String progressEpoch() { return progressEpoch; }

    public enum TaskAction { MILK_CLEANSE, LOOM_CRAFT, CAULDRON_CLEAN, COMPOST_FILL, COMPOST_COLLECT, JUKEBOX_PLAY }
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
    private static final Map<String, Set<Material>> MINED_BLOCKS = new HashMap<>();
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

        DEFINITIONS.put(normalizedId, new Definition(normalizedId, normalizedLabel, requirement, rawRequirement.trim()));
        return encode(normalizedId);
    }

    public static void saveProgress(org.bukkit.configuration.ConfigurationSection section) {
        section.set("progress-epoch", progressEpoch);
        section.set("sharing-policy", "sum-union-v1");
        saveSets(section, "COMPLETED_ADVANCEMENTS", COMPLETED_ADVANCEMENTS);
        saveSets(section, "COMPLETED_ACTIONS", COMPLETED_ACTIONS);
        saveSets(section, "KILLED_ENTITY_TYPES", KILLED_ENTITY_TYPES);
        saveSets(section, "BRED_ENTITY_TYPES", BRED_ENTITY_TYPES);
        saveSets(section, "TAMED_ENTITY_TYPES", TAMED_ENTITY_TYPES);
        saveSets(section, "CONSUMED_ITEMS", CONSUMED_ITEMS);
        saveSets(section, "CONSUMED_POTIONS", CONSUMED_POTIONS);
        saveSets(section, "CRAFTED_ITEMS", CRAFTED_ITEMS);
        saveSets(section, "USED_BLOCKS", USED_BLOCKS);
        saveSets(section, "MINED_BLOCKS", MINED_BLOCKS);
        saveSets(section, "DEATH_CAUSES", DEATH_CAUSES);
        saveSets(section, "DEATH_ATTACKERS", DEATH_ATTACKERS);
        saveSets(section, "DEATH_PROJECTILES", DEATH_PROJECTILES);
        saveSets(section, "SPIED_ENTITY_TYPES", SPIED_ENTITY_TYPES);
        section.createSection("KILL_COUNTS", new HashMap<>(KILL_COUNTS));
        section.createSection("UNDEAD_KILL_COUNTS", new HashMap<>(UNDEAD_KILL_COUNTS));
        section.createSection("ARTHROPOD_KILL_COUNTS", new HashMap<>(ARTHROPOD_KILL_COUNTS));
        section.createSection("CONTINUOUS_WEAR_TICKS", new HashMap<>(CONTINUOUS_WEAR_TICKS));
        section.createSection("DAMAGE_DEALT", new HashMap<>(DAMAGE_DEALT));
        section.createSection("DAMAGE_TAKEN", new HashMap<>(DAMAGE_TAKEN));
        section.set("FISHED_TREASURE_PLAYERS", new ArrayList<>(FISHED_TREASURE_PLAYERS));
        section.set("MAX_LEVEL_VILLAGER_PLAYERS", new ArrayList<>(MAX_LEVEL_VILLAGER_PLAYERS));
    }

    public static void restoreProgress(org.bukkit.configuration.ConfigurationSection section) {
        resetProgress();
        if (section == null) return;
        String savedEpoch = section.getString("progress-epoch");
        if (savedEpoch != null) progressEpoch = java.util.UUID.fromString(savedEpoch).toString();
        restoreSets(section, "COMPLETED_ADVANCEMENTS", COMPLETED_ADVANCEMENTS, value -> value);
        restoreSets(section, "COMPLETED_ACTIONS", COMPLETED_ACTIONS, TaskAction::valueOf);
        restoreSets(section, "KILLED_ENTITY_TYPES", KILLED_ENTITY_TYPES, EntityType::valueOf);
        restoreSets(section, "BRED_ENTITY_TYPES", BRED_ENTITY_TYPES, EntityType::valueOf);
        restoreSets(section, "TAMED_ENTITY_TYPES", TAMED_ENTITY_TYPES, EntityType::valueOf);
        restoreSets(section, "CONSUMED_ITEMS", CONSUMED_ITEMS, Material::valueOf);
        restoreSets(section, "CONSUMED_POTIONS", CONSUMED_POTIONS, value -> value);
        restoreSets(section, "CRAFTED_ITEMS", CRAFTED_ITEMS, Material::valueOf);
        restoreSets(section, "USED_BLOCKS", USED_BLOCKS, Material::valueOf);
        restoreSets(section, "MINED_BLOCKS", MINED_BLOCKS, Material::valueOf);
        restoreSets(section, "DEATH_CAUSES", DEATH_CAUSES, EntityDamageEvent.DamageCause::valueOf);
        restoreSets(section, "DEATH_ATTACKERS", DEATH_ATTACKERS, EntityType::valueOf);
        restoreSets(section, "DEATH_PROJECTILES", DEATH_PROJECTILES, EntityType::valueOf);
        restoreSets(section, "SPIED_ENTITY_TYPES", SPIED_ENTITY_TYPES, EntityType::valueOf);
        restoreNumbers(section.getConfigurationSection("KILL_COUNTS"), KILL_COUNTS, Number::intValue);
        restoreNumbers(section.getConfigurationSection("UNDEAD_KILL_COUNTS"), UNDEAD_KILL_COUNTS, Number::intValue);
        restoreNumbers(section.getConfigurationSection("ARTHROPOD_KILL_COUNTS"), ARTHROPOD_KILL_COUNTS, Number::intValue);
        restoreNumbers(section.getConfigurationSection("CONTINUOUS_WEAR_TICKS"), CONTINUOUS_WEAR_TICKS, Number::intValue);
        restoreNumbers(section.getConfigurationSection("DAMAGE_DEALT"), DAMAGE_DEALT, Number::doubleValue);
        restoreNumbers(section.getConfigurationSection("DAMAGE_TAKEN"), DAMAGE_TAKEN, Number::doubleValue);
        FISHED_TREASURE_PLAYERS.addAll(section.getStringList("FISHED_TREASURE_PLAYERS"));
        MAX_LEVEL_VILLAGER_PLAYERS.addAll(section.getStringList("MAX_LEVEL_VILLAGER_PLAYERS"));
    }

    private static <T> void saveSets(org.bukkit.configuration.ConfigurationSection parent,
                                     String key, Map<String, Set<T>> values) {
        var section = parent.createSection(key);
        values.forEach((player, items) -> section.set(player, items.stream()
                .map(item -> item instanceof Enum<?> value ? value.name() : item.toString()).sorted().toList()));
    }

    private static <T> void restoreSets(org.bukkit.configuration.ConfigurationSection parent,
                                        String key, Map<String, Set<T>> values,
                                        java.util.function.Function<String, T> decode) {
        var section = parent.getConfigurationSection(key);
        if (section == null) return;
        for (String player : section.getKeys(false)) {
            Set<T> items = new HashSet<>();
            for (String item : section.getStringList(player)) items.add(decode.apply(item));
            values.put(player, items);
        }
    }

    private static <T extends Number> void restoreNumbers(org.bukkit.configuration.ConfigurationSection section,
                                                          Map<String, T> values,
                                                          java.util.function.Function<Number, T> decode) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (raw instanceof Number number) values.put(key, decode.apply(number));
        }
    }

    public static void resetProgress() {
        progressEpoch = java.util.UUID.randomUUID().toString();
        COMPLETED_ADVANCEMENTS.clear();
        COMPLETED_ACTIONS.clear();
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
        MINED_BLOCKS.clear();
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

    private static boolean isParticipant(Player player) {
        return Team.redTeamPlayers.contains(player.getName()) || Team.blueTeamPlayers.contains(player.getName());
    }

    public static void recordKill(Player player, EntityType entityType) {
        if (!isParticipant(player)) return;
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
        if (!isParticipant(player)) return;
        BRED_ENTITY_TYPES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(entityType);
    }

    public static void recordTame(Player player, EntityType entityType) {
        if (!isParticipant(player)) return;
        TAMED_ENTITY_TYPES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(entityType);
    }

    public static void recordConsume(Player player, Material material) {
        if (!isParticipant(player)) return;
        CONSUMED_ITEMS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordConsumePotion(Player player, String potionType) {
        if (!isParticipant(player)) return;
        CONSUMED_POTIONS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(potionType);
    }

    public static void recordCraft(Player player, Material material) {
        if (!isParticipant(player)) return;
        CRAFTED_ITEMS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordUseBlock(Player player, Material material) {
        if (!isParticipant(player)) return;
        USED_BLOCKS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordAction(Player player, TaskAction action) {
        if (isParticipant(player)) COMPLETED_ACTIONS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(action);
    }

    /** Alias only when the requirement asks for the ordinary ore; explicit deepslate goals stay strict. */
    static boolean matchesMinedBlock(Material wanted, Set<Material> mined) {
        if (mined.contains(wanted)) return true;
        Material deep = switch (wanted) {
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
            case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            default -> null;
        };
        return deep != null && mined.contains(deep);
    }

    public static void recordBreak(Player player, Material material) {
        if (!isParticipant(player)) return;
        MINED_BLOCKS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(material);
    }

    public static void recordDamageDealt(Player player, double amount) {
        if (!isParticipant(player)) return;
        if (Double.isFinite(amount) && amount > 0) DAMAGE_DEALT.merge(player.getName(), amount, Double::sum);
    }

    public static void recordDamageTaken(Player player, double amount) {
        if (!isParticipant(player)) return;
        if (Double.isFinite(amount) && amount > 0) DAMAGE_TAKEN.merge(player.getName(), amount, Double::sum);
    }

    public static void recordDeath(Player player, EntityDamageEvent.DamageCause cause, EntityType attackerType) {
        if (!isParticipant(player)) return;
        if (cause != null) {
            DEATH_CAUSES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(cause);
        }
        if (attackerType != null) {
            DEATH_ATTACKERS.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(attackerType);
        }
    }

    public static void recordDeathProjectile(Player player, EntityType projectileType) {
        if (!isParticipant(player)) return;
        if (projectileType != null) {
            DEATH_PROJECTILES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(projectileType);
        }
    }

    public static void recordFishTreasure(Player player) {
        if (!isParticipant(player)) return;
        FISHED_TREASURE_PLAYERS.add(player.getName());
    }

    public static void recordMaxLevelVillager(Player player) {
        if (!isParticipant(player)) return;
        MAX_LEVEL_VILLAGER_PLAYERS.add(player.getName());
    }

    public static void recordSpiedEntity(Player player, EntityType entityType) {
        if (!isParticipant(player)) return;
        SPIED_ENTITY_TYPES.computeIfAbsent(player.getName(), ignored -> new HashSet<>()).add(entityType);
    }

    public static void recordContinuousWearTick(Player player, Material material, int ticks) {
        if (!isParticipant(player)) return;
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

        List<String> members = teamMembers(player.getName());
        List<Inventory> chests = Team.redTeamPlayers.contains(player.getName()) ? Game.redTeamChest
                : Team.blueTeamPlayers.contains(player.getName()) ? Game.blueTeamChest : List.of();
        Progress progress = teamProgress(target, members, chests, Bukkit::getPlayer);
        if (progress == null) {
            return List.of();
        }

        List<String> lore = new ArrayList<>();
        boolean chinese = LanguageManager.usesChinese(player);
        if (definition.requirement() instanceof AdvancementCountRequirement) {
            lore.add("§7" + (chinese ? "有效进度去重总数：" : "Distinct completed advancements: ") + progress.current());
            lore.add("§7" + (chinese ? "含已记录的离线队友；不含配方解锁；开局已有进度也计入"
                    : "Includes recorded offline members and pre-round completions; excludes recipes"));
        }
        lore.add("§b" + (isIndividualState(definition.requirement())
                ? (chinese ? "队伍最佳个人进度 · 须同一人满足" : "Team's best player · one player must satisfy all")
                : (chinese ? "队伍共享进度" : "Shared team progress")));
        if (definition.requirement() instanceof ItemRequirement || definition.requirement() instanceof ItemUniqueRequirement || definition.requirement() instanceof ItemTotalRequirement) {
            lore.add("§7" + (chinese ? "合计在线队友背包与队伍箱 · 不累计历史持有" : "Online inventories + team chests · currently held only"));
        }
        int current = Math.min(progress.current(), progress.required());
        int required = progress.required();
        lore.add(Message.MENU_TARGET_LIST_PROGRESS_LINE.getString(player)
                .replace("%current%", String.valueOf(current))
                .replace("%required%", String.valueOf(required)));

        if (required > 1 && required <= 30) {
            StringBuilder bar = new StringBuilder("§7[");
            int filledBars = (int) ((double) current / required * 10);
            for (int i = 0; i < 10; i++) {
                bar.append(i < filledBars ? "§a■" : "§8■");
            }
            bar.append("§7]");
            lore.add(bar.toString());
        }

        addDetailLore(lore, Message.MENU_TARGET_LIST_PROGRESS_DONE_LINE.getString(player), progress.completed(), player);
        addDetailLore(lore, Message.MENU_TARGET_LIST_PROGRESS_MISSING_LINE.getString(player), progress.missing(), player);
        return lore;
    }

    /** Read-only numeric UI projection; does not check off targets or import advancements. */
    public static int[] getBoardProgress(String target, Player player) {
        List<Inventory> chests = Team.redTeamPlayers.contains(player.getName()) ? Game.redTeamChest
                : Team.blueTeamPlayers.contains(player.getName()) ? Game.blueTeamChest : List.of();
        Progress progress = teamProgress(target, teamMembers(player.getName()), chests, Bukkit::getPlayer);
        return progress == null ? new int[]{0, 1}
                : new int[]{Math.max(0, progress.current()), Math.max(1, progress.required())};
    }

    public static boolean isBoardProgressIndividual(String target) {
        Definition definition = DEFINITIONS.get(decode(target));
        return definition != null && isIndividualState(definition.requirement());
    }

    public static String findCompletionSource(String target, List<String> teamPlayers, List<Inventory> teamChests, String chestSource) {
        Definition definition = DEFINITIONS.get(decode(target));
        if (definition == null) {
            return null;
        }

        Progress progress = teamProgress(target, teamPlayers, teamChests, Bukkit::getPlayer);
        return progress != null && progress.current() >= progress.required() ? TEAM_COMPLETION_SOURCE : null;
    }


    private static List<String> teamMembers(String player) {
        if (Team.redTeamPlayers.contains(player)) return Team.redTeamPlayers;
        if (Team.blueTeamPlayers.contains(player)) return Team.blueTeamPlayers;
        return List.of(player);
    }

    /** Raw counters remain per player; aggregation never writes back into the ledger. */
    static Progress teamProgress(String target, List<String> members, List<Inventory> chests,
                                 java.util.function.Function<String, Player> players) {
        Requirement r = getRequirement(target);
        if (r == null) return null;
        List<String> names = members.stream().distinct().toList();
        if (r instanceof ActionRequirement v) {
            Set<TaskAction> done = union(COMPLETED_ACTIONS, names);
            return countProgress((int) v.actions().stream().filter(done::contains).count(), v.actions().size());
        }
        if (r instanceof KillCountRequirement v) return countProgress(sum(KILL_COUNTS, names), v.amount());
        if (r instanceof DamageRequirement v) {
            var map = v.kind() == DamageRequirement.Kind.DEALT ? DAMAGE_DEALT : DAMAGE_TAKEN;
            // Sum fractional damage before rounding, not each member's displayed integer.
            return countProgress((int) Math.min(Integer.MAX_VALUE, Math.floor(sumDouble(map, names))), v.amount());
        }
        if (r instanceof KillUniqueRequirement v) {
            if (v.category() == EntityCategory.UNDEAD_WITH_REPETITION) return countProgress(sum(UNDEAD_KILL_COUNTS, names), v.amount());
            if (v.category() == EntityCategory.ARTHROPOD_WITH_REPETITION) return countProgress(sum(ARTHROPOD_KILL_COUNTS, names), v.amount());
            return collectionProgress(union(KILLED_ENTITY_TYPES, names).stream().filter(v.category()::contains)
                    .map(Goal::displayEntity).toList(), v.amount());
        }
        if (r instanceof KillRequirement v) return flag(union(KILLED_ENTITY_TYPES, names).contains(v.entityType()));
        if (r instanceof BreedRequirement v) return flag(union(BRED_ENTITY_TYPES, names).contains(v.entityType()));
        if (r instanceof BreedUniqueRequirement v) return collectionProgress(
                union(BRED_ENTITY_TYPES, names).stream().map(Goal::displayEntity).toList(), v.amount());
        if (r instanceof TameRequirement v) return flag(union(TAMED_ENTITY_TYPES, names).contains(v.entityType()));
        if (r instanceof ConsumeRequirement v) return flag(union(CONSUMED_ITEMS, names).contains(v.material()));
        if (r instanceof ConsumePotionRequirement v) return flag(union(CONSUMED_POTIONS, names).contains(v.potionType()));
        if (r instanceof ConsumeUniqueRequirement v) return collectionProgress(union(CONSUMED_ITEMS, names).stream()
                .filter(Material::isEdible).map(Goal::displayMaterial).toList(), v.amount());
        if (r instanceof ConsumeAllRequirement v) {
            Set<Material> consumed = union(CONSUMED_ITEMS, names);
            List<String> done = v.items().stream().map(ItemTarget::material).distinct()
                    .filter(consumed::contains).map(Goal::displayMaterial).sorted().toList();
            List<String> missing = v.items().stream().map(ItemTarget::material).distinct()
                    .filter(item -> !consumed.contains(item)).map(Goal::displayMaterial).sorted().toList();
            return new Progress(done.size(), (int) countUniqueMaterials(v.items()), done, missing);
        }
        if (r instanceof CraftUniqueRequirement v) return collectionProgress(
                union(CRAFTED_ITEMS, names).stream().map(Goal::displayMaterial).toList(), v.amount());
        if (r instanceof SpyUniqueRequirement v) return collectionProgress(
                union(SPIED_ENTITY_TYPES, names).stream().map(Goal::displayEntity).toList(), v.amount());
        if (r instanceof UseBlockRequirement v) return flag(union(USED_BLOCKS, names).contains(v.material()));
        if (r instanceof BreakRequirement v) return flag(matchesMinedBlock(v.material(), union(MINED_BLOCKS, names)));
        if (r instanceof DeathCauseRequirement v) return flag(union(DEATH_CAUSES, names).contains(v.cause()));
        if (r instanceof DeathAttackerRequirement v) return flag(union(DEATH_ATTACKERS, names).contains(v.entityType()));
        if (r instanceof DeathProjectileRequirement v) return flag(union(DEATH_PROJECTILES, names).contains(v.entityType()));
        if (r instanceof FishTreasureRequirement) return flag(names.stream().anyMatch(FISHED_TREASURE_PLAYERS::contains));
        if (r instanceof VillagerMaxLevelRequirement) return flag(names.stream().anyMatch(MAX_LEVEL_VILLAGER_PLAYERS::contains));
        if (r instanceof AdvancementRequirement v) return flag(union(COMPLETED_ADVANCEMENTS, names).contains("minecraft:" + v.key()));
        if (r instanceof AdvancementCountRequirement v) return collectionProgress(
                new ArrayList<>(union(COMPLETED_ADVANCEMENTS, names)), v.amount());

        List<Player> online = names.stream().map(players).filter(Objects::nonNull).toList();
        if (r instanceof ItemRequirement || r instanceof ItemUniqueRequirement || r instanceof ItemTotalRequirement || r instanceof EnchantedItemRequirement) {
            // Identity-deduplicate inventories so an accidental duplicate chest reference cannot mint items.
            Set<Inventory> inventories = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            inventories.addAll(chests);
            online.stream().map(Player::getInventory).forEach(inventories::add);
            inventories.remove(null);
            if (r instanceof EnchantedItemRequirement v) {
                return flag(inventories.stream().anyMatch(inventory -> inventoryContainsEnchantedItem(inventory, v)));
            }
            Map<Material, Integer> amounts = new java.util.EnumMap<>(Material.class);
            for (Inventory inventory : inventories) {
                for (ItemStack stack : inventory.getContents()) {
                    if (stack != null && stack.getType() != Material.AIR)
                        amounts.merge(stack.getType(), stack.getAmount(), Integer::sum);
                }
            }
            if (r instanceof ItemRequirement v) return sharedItemProgress(amounts, v.items(), (int) countUniqueMaterials(v.items()), true);
            if (r instanceof ItemTotalRequirement v) return countProgress((int) Math.min(Integer.MAX_VALUE,
                    v.items().stream().distinct().mapToLong(material -> amounts.getOrDefault(material, 0)).sum()), v.amount());
            ItemUniqueRequirement v = (ItemUniqueRequirement) r;
            return sharedItemProgress(amounts, v.items(), v.amount(), false);
        }
        // Simultaneous equipment/effects, levels, location and continuous wear are never stitched together.
        Progress best = countProgress(0, individualRequired(r));
        for (Player player : online) {
            Progress candidate = individualProgress(r, player);
            if (candidate.current() > best.current()) best = candidate;
        }
        return best;
    }

    private static Progress sharedItemProgress(Map<Material, Integer> amounts, List<ItemTarget> items, int required, boolean requireAll) {
        Map<Material, Integer> thresholds = new java.util.EnumMap<>(Material.class);
        items.forEach(item -> thresholds.merge(item.material(), item.amount(), requireAll ? Math::max : Math::min));
        items = thresholds.entrySet().stream().map(entry -> new ItemTarget(entry.getKey(), entry.getValue())).toList();
        Set<Material> completed = new HashSet<>();
        List<String> done = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ItemTarget item : items) {
            if (amounts.getOrDefault(item.material(), 0) >= item.amount()) completed.add(item.material());
        }
        Set<Material> seen = new HashSet<>();
        for (ItemTarget item : items) {
            if (!seen.add(item.material())) continue;
            (completed.contains(item.material()) ? done : missing).add(displayItemTarget(item));
        }
        done.sort(String::compareTo);
        missing.sort(String::compareTo);
        return new Progress(completed.size(), required, done, missing);
    }

    private static int individualRequired(Requirement r) {
        if (r instanceof EquipmentRequirement v) return v.requireAll() ? (int) countUniqueMaterials(v.items()) : 1;
        if (r instanceof UniqueLeatherArmorColorsRequirement v) return v.amount();
        if (r instanceof LevelRequirement v) return v.level();
        if (r instanceof EffectCountRequirement v) return v.amount();
        if (r instanceof WearContinuousRequirement v) return v.ticks() / 20;
        return 1;
    }

    private static Progress individualProgress(Requirement r, Player p) {
        if (r instanceof EquipmentRequirement v) {
            List<Material> equipped = getEquipment(p);
            int matched = (int) v.items().stream().map(ItemTarget::material).distinct().filter(equipped::contains).count();
            return countProgress(v.requireAll() ? matched : Math.min(1, matched), individualRequired(r));
        }
        if (r instanceof UniqueLeatherArmorColorsRequirement v) return countProgress(countUniqueLeatherArmorColors(p), v.amount());
        if (r instanceof LevelRequirement v) return countProgress(p.getLevel(), v.level());
        if (r instanceof ColoredEquipmentRequirement v) return flag(equipmentContainsColoredLeather(p, v.material(), v.dyeColor()));
        if (r instanceof LocationRequirement v) return flag(isAtLocation(p, v.type()));
        if (r instanceof EffectRequirement v) return flag(p.getActivePotionEffects().stream()
                .anyMatch(effect -> effect.getType().getKey().getKey().equals(v.effectKey())));
        if (r instanceof HungerRequirement) return flag(p.getFoodLevel() <= 0);
        if (r instanceof EffectCountRequirement v) {
            List<String> effects = p.getActivePotionEffects().stream()
                    .map(effect -> formatKey(effect.getType().getKey().getKey())).sorted().toList();
            return new Progress(effects.size(), v.amount(), effects, List.of());
        }
        if (r instanceof WearContinuousRequirement v) return countProgress(
                CONTINUOUS_WEAR_TICKS.getOrDefault(wearProgressKey(p.getName(), v.material()), 0) / 20, v.ticks() / 20);
        throw new IllegalArgumentException("Unhandled individual goal requirement: " + r);
    }

    private static boolean isIndividualState(Requirement r) {
        return r instanceof EquipmentRequirement || r instanceof ColoredEquipmentRequirement
                || r instanceof UniqueLeatherArmorColorsRequirement || r instanceof LevelRequirement
                || r instanceof LocationRequirement || r instanceof EffectRequirement
                || r instanceof EffectCountRequirement || r instanceof HungerRequirement
                || r instanceof WearContinuousRequirement;
    }

    static <T> Set<T> union(Map<String, Set<T>> values, List<String> names) {
        Set<T> result = new HashSet<>();
        names.forEach(name -> result.addAll(values.getOrDefault(name, Set.of())));
        return result;
    }

    private static int sum(Map<String, Integer> values, List<String> names) {
        return (int) Math.min(Integer.MAX_VALUE, names.stream().distinct()
                .mapToLong(name -> values.getOrDefault(name, 0)).sum());
    }

    private static double sumDouble(Map<String, Double> values, List<String> names) {
        return names.stream().distinct().mapToDouble(name -> values.getOrDefault(name, 0D)).sum();
    }

    private static Progress flag(boolean done) {
        return countProgress(done ? 1 : 0, 1);
    }

    private static Progress collectionProgress(List<String> values, int required) {
        return new Progress(values.size(), required, values.stream().sorted().toList(), List.of());
    }

    static boolean isCountableAdvancement(NamespacedKey key, boolean hasDisplay) {
        return key.getNamespace().equals("minecraft") && !key.getKey().startsWith("recipes/") && hasDisplay;
    }

    public static void recordAdvancement(Player player, Advancement advancement) {
        if (!isParticipant(player)) return;
        if (isCountableAdvancement(advancement.getKey(), advancement.getDisplay() != null)) {
            COMPLETED_ADVANCEMENTS.computeIfAbsent(player.getName(), ignored -> new HashSet<>())
                    .add(advancement.getKey().toString());
        }
    }

    /** Import each player's completed progress once on start/login, not for every goal every five ticks. */
    public static void refreshAdvancements(Player player) {
        refreshAdvancements(player, Bukkit.advancementIterator());
    }

    static void refreshAdvancements(Player player, Iterator<Advancement> iterator) {
        if (!isParticipant(player)) return;
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (isCountableAdvancement(advancement.getKey(), advancement.getDisplay() != null)
                    && player.getAdvancementProgress(advancement).isDone()) recordAdvancement(player, advancement);
        }
    }

    public static void playerDisconnected(Player player) {
        // Offline time breaks the continuity requirement, but not accumulated or unique event ledgers.
        CONTINUOUS_WEAR_TICKS.keySet().removeIf(key -> key.startsWith(player.getName() + "|"));
    }

    public static Requirement getRequirement(String target) {
        Definition definition = DEFINITIONS.get(decode(target));
        return definition == null ? null : definition.requirement();
    }

    public static Material getGoalIcon(String target) {
        Requirement requirement = getRequirement(target);
        if (requirement == null) {
            return Material.WRITABLE_BOOK;
        }

        if (requirement instanceof BreakRequirement broken) {
            return broken.material();
        }
        if (requirement instanceof KillRequirement || requirement instanceof KillCountRequirement || requirement instanceof KillUniqueRequirement) {
            return Material.DIAMOND_SWORD;
        }
        if (requirement instanceof BreedRequirement || requirement instanceof BreedUniqueRequirement) {
            return Material.WHEAT_SEEDS;
        }
        if (requirement instanceof TameRequirement) {
            return Material.LEAD;
        }
        if (requirement instanceof AdvancementRequirement || requirement instanceof AdvancementCountRequirement) {
            return Material.KNOWLEDGE_BOOK;
        }
        if (requirement instanceof ConsumeAllRequirement || requirement instanceof ConsumeUniqueRequirement) {
            return Material.COOKED_BEEF;
        }
        if (requirement instanceof ConsumeRequirement || requirement instanceof ConsumePotionRequirement) {
            return Material.GOLDEN_APPLE;
        }
        if (requirement instanceof CraftUniqueRequirement) {
            return Material.CRAFTING_TABLE;
        }
        if (requirement instanceof EquipmentRequirement || requirement instanceof ColoredEquipmentRequirement || requirement instanceof UniqueLeatherArmorColorsRequirement) {
            return Material.IRON_CHESTPLATE;
        }
        if (requirement instanceof WearContinuousRequirement) {
            return Material.LEATHER_HELMET;
        }
        if (requirement instanceof ItemRequirement || requirement instanceof ItemUniqueRequirement || requirement instanceof ItemTotalRequirement) {
            return Material.CHEST;
        }
        if (requirement instanceof EnchantedItemRequirement) {
            return Material.ENCHANTED_BOOK;
        }
        if (requirement instanceof LevelRequirement) {
            return Material.EXPERIENCE_BOTTLE;
        }
        if (requirement instanceof LocationRequirement) {
            return Material.COMPASS;
        }
        if (requirement instanceof EffectRequirement || requirement instanceof EffectCountRequirement) {
            return Material.POTION;
        }
        if (requirement instanceof FishTreasureRequirement) {
            return Material.FISHING_ROD;
        }
        if (requirement instanceof SpyUniqueRequirement) {
            return Material.SPYGLASS;
        }
        if (requirement instanceof UseBlockRequirement) {
            return Material.FLINT_AND_STEEL;
        }
        if (requirement instanceof DamageRequirement) {
            return Material.SHIELD;
        }
        if (requirement instanceof DeathCauseRequirement || requirement instanceof DeathAttackerRequirement || requirement instanceof DeathProjectileRequirement) {
            return Material.WITHER_SKELETON_SKULL;
        }
        if (requirement instanceof VillagerMaxLevelRequirement) {
            return Material.EMERALD;
        }
        if (requirement instanceof HungerRequirement) {
            return Material.ROTTEN_FLESH;
        }

        return Material.WRITABLE_BOOK;
    }

    public static String getGoalTypeLabel(String target, Player viewer) {
        String label = getGoalTypeLabel(target);
        if (LanguageManager.usesChinese(viewer)) return label;
        return switch (label) {
            case "挖掘" -> "Mining";
            case "击杀" -> "Kills";
            case "繁殖" -> "Breeding";
            case "驯服" -> "Taming";
            case "进度" -> "Advancement";
            case "食用" -> "Food";
            case "合成" -> "Crafting";
            case "装备" -> "Equipment";
            case "穿戴" -> "Wearing";
            case "收集" -> "Collection";
            case "附魔" -> "Enchanting";
            case "等级" -> "Level";
            case "到达" -> "Location";
            case "效果" -> "Effect";
            case "钓鱼" -> "Fishing";
            case "观察" -> "Observation";
            case "使用" -> "Interaction";
            case "伤害" -> "Damage";
            case "死亡" -> "Death";
            case "交易" -> "Trading";
            case "饥饿" -> "Hunger";
            default -> "Goal";
        };
    }

    public static String getGoalTypeLabel(String target) {
        Requirement requirement = getRequirement(target);
        if (requirement == null) {
            return getDefaultGoalType();
        }

        if (requirement instanceof BreakRequirement) return "挖掘";
        if (requirement instanceof KillRequirement || requirement instanceof KillCountRequirement || requirement instanceof KillUniqueRequirement) return "击杀";
        if (requirement instanceof BreedRequirement || requirement instanceof BreedUniqueRequirement) return "繁殖";
        if (requirement instanceof TameRequirement) return "驯服";
        if (requirement instanceof AdvancementRequirement || requirement instanceof AdvancementCountRequirement) return "进度";
        if (requirement instanceof ConsumeRequirement || requirement instanceof ConsumePotionRequirement || requirement instanceof ConsumeAllRequirement || requirement instanceof ConsumeUniqueRequirement) return "食用";
        if (requirement instanceof CraftUniqueRequirement) return "合成";
        if (requirement instanceof EquipmentRequirement || requirement instanceof ColoredEquipmentRequirement || requirement instanceof UniqueLeatherArmorColorsRequirement) return "装备";
        if (requirement instanceof WearContinuousRequirement) return "穿戴";
        if (requirement instanceof ItemRequirement || requirement instanceof ItemUniqueRequirement || requirement instanceof ItemTotalRequirement) return "收集";
        if (requirement instanceof EnchantedItemRequirement) return "附魔";
        if (requirement instanceof LevelRequirement) return "等级";
        if (requirement instanceof LocationRequirement) return "到达";
        if (requirement instanceof EffectRequirement || requirement instanceof EffectCountRequirement) return "效果";
        if (requirement instanceof FishTreasureRequirement) return "钓鱼";
        if (requirement instanceof SpyUniqueRequirement) return "观察";
        if (requirement instanceof UseBlockRequirement || requirement instanceof ActionRequirement) return "使用";
        if (requirement instanceof DamageRequirement) return "伤害";
        if (requirement instanceof DeathCauseRequirement || requirement instanceof DeathAttackerRequirement || requirement instanceof DeathProjectileRequirement) return "死亡";
        if (requirement instanceof VillagerMaxLevelRequirement) return "交易";
        if (requirement instanceof HungerRequirement) return "饥饿";

        return getDefaultGoalType();
    }

    private static String getDefaultGoalType() {
        return "任务";
    }

    private static int getGoalScoreColor(int score) {
        if (score >= 11) return 0xAA0000;
        if (score >= 5) return 0xFFAA00;
        if (score >= 3) return 0xFF5555;
        if (score == 2) return 0xFFFF55;
        return 0x55FF55;
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

    private static Progress countProgress(int current, int required) {
        return new Progress(current, required, List.of(), List.of());
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
        return java.util.Arrays.asList(
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        );
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

        return new Definition(id, label, requirement, parts[2].trim());
    }

    private static Requirement parseRequirement(String rawRequirement) {
        if (rawRequirement.startsWith("actions:")) {
            try {
                List<TaskAction> actions = java.util.Arrays.stream(rawRequirement.substring(8).split(",", -1))
                        .map(String::trim).map(TaskAction::valueOf).distinct().toList();
                return new ActionRequirement(actions);
            } catch (IllegalArgumentException ex) { return null; }
        }
        if (rawRequirement.startsWith("item-total:")) {
            String[] parts = rawRequirement.substring(11).split(":", 2);
            if (parts.length != 2) return null;
            int amount = parsePositiveInt(parts[0]).orElse(-1);
            List<ItemTarget> items = parseItems(parts[1]);
            if (amount < 1 || items.isEmpty() || items.stream().anyMatch(item -> item.amount() != 1)) return null;
            return new ItemTotalRequirement(amount, items.stream().map(ItemTarget::material).distinct().toList());
        }
        if (rawRequirement.startsWith("item-unique:")) {
            return parseItemUniqueRequirement(rawRequirement.substring("item-unique:".length()));
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

        if (rawRequirement.startsWith("break:")) {
            Material material = Material.getMaterial(rawRequirement.substring("break:".length()).trim());
            return material == null ? null : new BreakRequirement(material);
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

    private static void addDetailLore(List<String> lore, String template, List<String> items, Player player) {
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
            lore.add(Message.MENU_TARGET_LIST_PROGRESS_MORE_LINE.getString(player)
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
        String displayName = Block.getDisplayName(material.name());
        return displayName == null || displayName.isBlank() || displayName.equals(material.name())
                ? formatKey(material.name())
                : displayName;
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

    public static String getRawRequirement(String target) {
        Definition definition = DEFINITIONS.get(decode(target));
        return definition == null ? null : definition.rawRequirement();
    }

    private record Definition(String id, String label, Requirement requirement, String rawRequirement) {
    }

    record Progress(int current, int required, List<String> completed, List<String> missing) {
    }

    private sealed interface Requirement permits ItemTotalRequirement, ActionRequirement, ItemRequirement, ItemUniqueRequirement, EnchantedItemRequirement, EquipmentRequirement, ColoredEquipmentRequirement, UniqueLeatherArmorColorsRequirement, AdvancementRequirement, AdvancementCountRequirement, LevelRequirement, LocationRequirement, EffectRequirement, EffectCountRequirement, HungerRequirement, KillRequirement, KillCountRequirement, KillUniqueRequirement, BreedRequirement, BreedUniqueRequirement, TameRequirement, ConsumeRequirement, ConsumePotionRequirement, ConsumeAllRequirement, ConsumeUniqueRequirement, CraftUniqueRequirement, FishTreasureRequirement, SpyUniqueRequirement, WearContinuousRequirement, UseBlockRequirement, BreakRequirement, DamageRequirement, DeathCauseRequirement, DeathAttackerRequirement, DeathProjectileRequirement, VillagerMaxLevelRequirement {
    }

    private record ItemRequirement(List<ItemTarget> items) implements Requirement {
    }

    private record ItemTotalRequirement(int amount, List<Material> items) implements Requirement { }

    private record ActionRequirement(List<TaskAction> actions) implements Requirement { }

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

    private record BreakRequirement(Material material) implements Requirement {
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
