package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;
import top.lqsnow.blockracing.Main;
import top.lqsnow.blockracing.utils.TranslationUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

import static top.lqsnow.blockracing.managers.Gui.checkBlockInventory;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public class Block {
    private static final Random RANDOM = new Random();
    private static final int EASY_SCORE = 1;
    private static final int NORMAL_SCORE = 2;
    private static final int MIN_HARD_SCORE = 3;
    private static final int MAX_HARD_SCORE = 10;
    private static final int MAX_EASY_TARGETS_PER_GAME = 8;
    private static final double RELATED_WOOD_SERIES_WEIGHT_MULTIPLIER = 0.1D;
    private static final double MINIMUM_SELECTION_WEIGHT = 0.01D;
    private static final String STRIPPED_PREFIX = "STRIPPED_";
    private static final String FAMILY_TAG_PREFIX = "family:";
    private static final String CATEGORY_TAG_PREFIX = "category:";
    private static final String GROUP_TAG_PREFIX = "group:";
    private static final String TARGETS_FILE_NAME = "Targets.csv";
    private static final List<String> WOOD_FAMILIES = List.of(
            "DARK_OAK",
            "PALE_OAK",
            "MANGROVE",
            "CRIMSON",
            "SPRUCE",
            "JUNGLE",
            "CHERRY",
            "BAMBOO",
            "WARPED",
            "BIRCH",
            "ACACIA",
            "OAK"
    );
    private static final Map<String, Set<String>> WOOD_CATEGORY_GROUPS = createWoodCategoryGroups();
    private static final Map<String, Set<String>> RELATED_BLOCK_TAG_CACHE = new HashMap<>();

    public static List<String> blocks;
    public static List<String> allBlocks = new ArrayList<>();
    public static Map<String, Integer> targetScores = new HashMap<>();
    private static final Map<String, String> chineseDisplayNames = new HashMap<>();
    public static int maxBlockAmount;
    public static List<String> redTeamBlocks = new ArrayList<>();
    public static List<String> blueTeamBlocks = new ArrayList<>();
    public static List<String> redTeamRemainingBlocks = new ArrayList<>();
    public static List<String> blueTeamRemainingBlocks = new ArrayList<>();

    public Block() {
        loadTargets();
        addUpBlocks();
    }

    public static void addUpBlocks() {
        allBlocks.clear();
        for (String target : targetScores.keySet()) {
            int score = getTargetScore(target);
            if (score == EASY_SCORE || (score == NORMAL_SCORE && Setting.isEnableMediumBlock())
                    || (score >= MIN_HARD_SCORE && Setting.isEnableHardBlock())) {
                allBlocks.add(target);
            }
        }
        blocks = List.copyOf(allBlocks);
        maxBlockAmount = blocks.size();
    }

    public static void setupBlocks() {
        redTeamRemainingBlocks.clear();
        blueTeamRemainingBlocks.clear();

        List<String> sharedBlocks = generateBlocks();
        redTeamBlocks = List.copyOf(sharedBlocks);
        blueTeamBlocks = List.copyOf(sharedBlocks);
        redTeamRemainingBlocks.addAll(List.copyOf(redTeamBlocks));
        blueTeamRemainingBlocks.addAll(List.copyOf(blueTeamBlocks));
        Bukkit.getLogger().info("[BlockRacing] Blocks generate complete.");
        Bukkit.getLogger().info("Red team blocks: " + redTeamBlocks.toString());
        Bukkit.getLogger().info("Blue team blocks: " + blueTeamBlocks.toString());
    }

    public static List<String> generateSampleBlocks(int blockAmount) {
        return generateBlocks(blockAmount);
    }

    private static List<String> generateBlocks() {
        return generateBlocks(Setting.getBlockAmount());
    }

    private static List<String> generateBlocks(int blockAmount) {
        addUpBlocks();

        List<String> onePointTargets = new ArrayList<>();
        List<String> scoredTargets = new ArrayList<>();
        for (String target : blocks) {
            if (getTargetScore(target) == EASY_SCORE) {
                onePointTargets.add(target);
            } else {
                scoredTargets.add(target);
            }
        }

        List<String> targetBlocks = new ArrayList<>();
        Set<String> suppressedRelatedTags = new HashSet<>();
        int easyTargetAmount = Math.min(Math.min(MAX_EASY_TARGETS_PER_GAME, onePointTargets.size()), blockAmount);

        for (int i = 0; i < easyTargetAmount; i++) {
            addSelectedTarget(targetBlocks, onePointTargets, suppressedRelatedTags);
        }

        int scoredTargetAmount = Math.min(blockAmount - targetBlocks.size(), scoredTargets.size());
        for (int i = 0; i < scoredTargetAmount; i++) {
            addSelectedTarget(targetBlocks, scoredTargets, suppressedRelatedTags);
        }

        Collections.shuffle(targetBlocks, RANDOM);
        Bukkit.getLogger().info("[BlockRacing] Generated targets: amount=" + targetBlocks.size()
                + ", one-point=" + countTargetsByScore(targetBlocks, EASY_SCORE)
                + ", total-score=" + getTotalScore(targetBlocks));
        return targetBlocks;
    }

    private static void addSelectedTarget(List<String> targetBlocks, List<String> targetPool, Set<String> suppressedRelatedTags) {
        String selectedBlock = selectWeightedBlockFromList(targetPool, suppressedRelatedTags);
        targetBlocks.add(selectedBlock);
        suppressedRelatedTags.addAll(getRelatedBlockTags(selectedBlock));
        targetPool.remove(selectedBlock);
    }

    // Method to select a weighted block from a list.
    // Wood-family and wood-category related blocks are reduced after one has appeared.
    private static String selectWeightedBlockFromList(List<String> blockList, Set<String> suppressedRelatedTags) {
        double totalWeight = 0;
        List<Double> weights = new ArrayList<>(blockList.size());

        for (String candidate : blockList) {
            double weight = calculateRelatedBlockSelectionWeight(candidate, suppressedRelatedTags);
            weights.add(weight);
            totalWeight += weight;
        }

        double randomValue = RANDOM.nextDouble(totalWeight);

        for (int i = 0; i < blockList.size(); i++) {
            randomValue -= weights.get(i);
            if (randomValue < 0) {
                return blockList.get(i);
            }
        }

        return blockList.get(blockList.size() - 1);
    }

    private static double calculateRelatedBlockSelectionWeight(String candidate, Set<String> suppressedRelatedTags) {
        if (suppressedRelatedTags.isEmpty()) {
            return 1.0D;
        }

        Set<String> candidateTags = getRelatedBlockTags(candidate);
        if (candidateTags.isEmpty()) {
            return 1.0D;
        }

        for (String tag : candidateTags) {
            if (suppressedRelatedTags.contains(tag)) {
                return Math.max(MINIMUM_SELECTION_WEIGHT, RELATED_WOOD_SERIES_WEIGHT_MULTIPLIER);
            }
        }

        return 1.0D;
    }

    private static Set<String> getRelatedBlockTags(String blockName) {
        return RELATED_BLOCK_TAG_CACHE.computeIfAbsent(blockName, Block::createRelatedBlockTags);
    }

    private static Set<String> createRelatedBlockTags(String blockName) {
        String normalizedBlockName = normalizeWoodBlockName(blockName);
        String family = extractWoodFamily(normalizedBlockName);
        if (family == null || !normalizedBlockName.startsWith(family + "_")) {
            return Set.of();
        }

        String category = normalizedBlockName.substring(family.length() + 1);
        if (category.isEmpty()) {
            return Set.of();
        }

        Set<String> tags = new HashSet<>();
        tags.add(FAMILY_TAG_PREFIX + family);
        tags.add(CATEGORY_TAG_PREFIX + category);

        for (Map.Entry<String, Set<String>> entry : WOOD_CATEGORY_GROUPS.entrySet()) {
            if (entry.getValue().contains(category)) {
                tags.add(GROUP_TAG_PREFIX + entry.getKey());
            }
        }

        return Set.copyOf(tags);
    }

    private static String normalizeWoodBlockName(String blockName) {
        return blockName.startsWith(STRIPPED_PREFIX) ? blockName.substring(STRIPPED_PREFIX.length()) : blockName;
    }

    private static String extractWoodFamily(String normalizedBlockName) {
        for (String family : WOOD_FAMILIES) {
            if (normalizedBlockName.startsWith(family + "_")) {
                return family;
            }
        }

        return null;
    }

    private static Map<String, Set<String>> createWoodCategoryGroups() {
        Map<String, Set<String>> categoryGroups = new HashMap<>();
        categoryGroups.put("SIGN_SERIES", Set.of("SIGN", "HANGING_SIGN"));
        categoryGroups.put("SAPLING_SERIES", Set.of("SAPLING", "PROPAGULE", "FUNGUS"));
        return Collections.unmodifiableMap(categoryGroups);
    }

    // Check if there are any problems with the blocks imported from the file
    public static boolean checkBlock() {
        boolean flag = true;
        for (String str : blocks) {
            if (Goal.isKnownGoalId(str)) {
                if (!Goal.isValid(str)) {
                    Bukkit.getLogger().severe("[BlockRacing] Invalid Draftout goal: " + str);
                    sendAll(String.format(Message.NOTICE_ERROR_BLOCK.getString(), getDisplayName(str)));
                    flag = false;
                }
                continue;
            }
            try {
                ItemStack item = ItemCreator.of(CompMaterial.fromMaterial(Material.valueOf(str))).amount(64).make();
                checkBlockInventory.setItem(0, item);
            } catch (Exception e) {
                Bukkit.getLogger().severe(String.format("[BlockRacing] " + Message.NOTICE_ERROR_BLOCK.getString(), str));
                sendAll(String.format(Message.NOTICE_ERROR_BLOCK.getString(), str));
                flag = false;
            }
        }
        return flag;
    }

    public static void reloadBlock() {
        loadTargets();
        addUpBlocks();
    }

    private static void loadTargets() {
        Map<String, Integer> loadedTargetScores = new HashMap<>();

        Goal.clearDefinitions();
        chineseDisplayNames.clear();

        for (List<String> row : readCsvFile(TARGETS_FILE_NAME)) {
            if (row.size() < 3) {
                Bukkit.getLogger().warning("[BlockRacing] Invalid target CSV row: " + row);
                continue;
            }

            String id = row.get(0).trim();
            String type = row.get(1).trim().toLowerCase(Locale.ROOT);
            String rawScore = row.get(2).trim().toLowerCase(Locale.ROOT);
            String displayName = row.size() >= 4 ? row.get(3).trim() : "";
            String requirement = row.size() >= 5 ? row.get(4).trim() : "";
            String chineseDisplayName = row.size() >= 6 ? row.get(5).trim() : "";

            if ("deprecated".equals(rawScore)) {
                continue;
            }

            if (id.isEmpty() || type.isEmpty() || rawScore.isEmpty()) {
                Bukkit.getLogger().warning("[BlockRacing] Invalid target CSV row: " + row);
                continue;
            }

            int score;
            try {
                score = Integer.parseInt(rawScore);
            } catch (NumberFormatException exception) {
                Bukkit.getLogger().warning("[BlockRacing] Invalid target score: " + rawScore + " in " + row);
                continue;
            }

            if (score < EASY_SCORE || score > MAX_HARD_SCORE) {
                Bukkit.getLogger().warning("[BlockRacing] Target score out of range: " + score + " in " + row);
                continue;
            }

            String target = switch (type) {
                case "block" -> id;
                case "goal" -> Goal.registerDefinition(id, displayName, requirement);
                default -> null;
            };

            if (target == null) {
                Bukkit.getLogger().warning("[BlockRacing] Invalid target CSV row: " + row);
                continue;
            }

            if (!chineseDisplayName.isEmpty()) {
                chineseDisplayNames.put(target, chineseDisplayName);
            }

            loadedTargetScores.put(target, score);
        }

        targetScores = Map.copyOf(loadedTargetScores);
        Bukkit.getLogger().info("[BlockRacing] Loaded targets: " + targetScores.size());
    }

    public static int getTargetScore(String target) {
        return targetScores.getOrDefault(target, EASY_SCORE);
    }

    public static int getTotalScore(List<String> targets) {
        int totalScore = 0;
        for (String target : targets) {
            totalScore += getTargetScore(target);
        }
        return totalScore;
    }

    private static int countTargetsByScore(List<String> targets, int score) {
        int count = 0;
        for (String target : targets) {
            if (getTargetScore(target) == score) {
                count += 1;
            }
        }
        return count;
    }

    public static String getDisplayName(String target) {
        String lang = Message.MESSAGE_LANG.getString();
        boolean chinese = lang != null && "zh_cn".equals(lang.trim().toLowerCase(Locale.ROOT));
        if (chinese) {
            String chineseDisplayName = chineseDisplayNames.get(target);
            if (chineseDisplayName != null && !chineseDisplayName.isBlank()) {
                return chineseDisplayName;
            }

            return Goal.isGoal(target) ? Goal.getDisplayName(target) : target;
        }

        return Goal.isGoal(target) ? Goal.getDisplayName(target) : TranslationUtil.getValue(target);
    }

    private static List<List<String>> readCsvFile(String fileName) {
        List<List<String>> rows = new ArrayList<>();
        try {
            File file = new File(Main.getInstance().getDataFolder(), fileName);
            BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    if (line.toLowerCase(Locale.ROOT).startsWith("id,")) {
                        continue;
                    }
                }
                rows.add(parseCsvLine(line));
            }
            reader.close();
        } catch (IOException e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "[BlockRacing] Error reading targets file!", e);
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }
}
