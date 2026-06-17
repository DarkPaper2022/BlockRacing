package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;
import top.lqsnow.blockracing.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static top.lqsnow.blockracing.managers.Gui.checkBlockInventory;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public class Block {
    private static final Random RANDOM = new Random();
    private static final float EASY_DISABLE_PROGRESS = 0.68f;
    private static final int EASY_START_WEIGHT = 90;
    private static final int MEDIUM_EARLY_START_WEIGHT = 30;
    private static final int MEDIUM_MID_WEIGHT = 70;
    private static final int MEDIUM_LATE_WEIGHT = 84;
    private static final float MEDIUM_WEIGHT_TURNING_POINT = 0.35f;
    private static final int HARD_EARLY_START_WEIGHT = 1;
    private static final int HARD_MID_WEIGHT = 30;
    private static final int HARD_LATE_WEIGHT = 90;
    private static final float HARD_WEIGHT_TURNING_POINT = 0.5f;
    private static final double RELATED_WOOD_SERIES_WEIGHT_MULTIPLIER = 0.1D;
    private static final double MINIMUM_SELECTION_WEIGHT = 0.01D;
    private static final String STRIPPED_PREFIX = "STRIPPED_";
    private static final String FAMILY_TAG_PREFIX = "family:";
    private static final String CATEGORY_TAG_PREFIX = "category:";
    private static final String GROUP_TAG_PREFIX = "group:";
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

    public static List<String> easyBlocks, mediumBlocks, hardBlocks, dyedBlocks, endBlocks, draftoutGoals, blocks;
    public static List<String> allBlocks = new ArrayList<>();
    public static int maxBlockAmount;
    public static List<String> redTeamBlocks = new ArrayList<>();
    public static List<String> blueTeamBlocks = new ArrayList<>();
    public static List<String> redTeamRemainingBlocks = new ArrayList<>();
    public static List<String> blueTeamRemainingBlocks = new ArrayList<>();

    public Block() {
        easyBlocks = List.of(readFile("EasyBlocks.txt"));
        mediumBlocks = List.of(readFile("MediumBlocks.txt"));
        hardBlocks = List.of(readFile("HardBlocks.txt"));
        dyedBlocks = List.of(readFile("DyedBlocks.txt"));
        endBlocks = List.of(readFile("EndBlocks.txt"));
        draftoutGoals = Goal.load(readFile("DraftoutGoals.txt"));
        addUpBlocks();
    }

    public static void addUpBlocks() {
        allBlocks.clear();
        allBlocks.addAll(List.copyOf(easyBlocks));
        if (Setting.isEnableMediumBlock()) {
            allBlocks.addAll(List.copyOf(mediumBlocks));
            allBlocks.addAll(List.copyOf(draftoutGoals));
        }
        if (Setting.isEnableHardBlock()) allBlocks.addAll(List.copyOf(hardBlocks));
        if (Setting.isEnableDyedBlock()) allBlocks.addAll(List.copyOf(dyedBlocks));
        if (Setting.isEnableEndBlock()) allBlocks.addAll(List.copyOf(endBlocks));
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

        List<String> easyTemp = new ArrayList<>(easyBlocks);
        List<String> mediumTemp = new ArrayList<>(mediumBlocks);
        mediumTemp.addAll(draftoutGoals);
        List<String> hardTemp = new ArrayList<>(hardBlocks);
        List<String> dyedTemp = new ArrayList<>(dyedBlocks);
        List<String> endTemp = new ArrayList<>(endBlocks);
        List<String> targetBlocks = new ArrayList<>();
        Set<String> suppressedRelatedTags = new HashSet<>();

        for (int i = 0; i < blockAmount; i++) {
            // Calculate weights for each difficulty
            int easyWeight = 0;
            int mediumWeight = 0;
            int hardWeight = 0;
            int dyedWeight = 0;
            int endWeight = 0;

            if (easyTemp.size() != 0) easyWeight = calculateEasyBlocksWeight((float) i / blockAmount);

            if (mediumTemp.size() != 0)
                mediumWeight = Setting.isEnableMediumBlock() ? calculateMediumBlocksWeight((float) i / blockAmount) : 0;

            if (hardTemp.size() != 0)
                hardWeight = Setting.isEnableHardBlock() ? calculateHardBlocksWeight((float) i / blockAmount) : 0;

            if (dyedTemp.size() != 0)
                dyedWeight = Setting.isEnableDyedBlock() ? calculateDyedBlocksWeight((float) i / blockAmount) : 0;

            if (endTemp.size() != 0)
                endWeight = Setting.isEnableEndBlock() ? calculateEndBlocksWeight((float) i / blockAmount) : 0;

            // Choose difficulty based on weights
            String difficulty = chooseDifficulty(easyWeight, mediumWeight, hardWeight, dyedWeight, endWeight);

            // Select a block from the corresponding difficulty list
            String selectedBlock = selectBlock(difficulty, easyTemp, mediumTemp, hardTemp, dyedTemp, endTemp, suppressedRelatedTags);

            // Add the selected block to targetBlocks
            targetBlocks.add(selectedBlock);
            suppressedRelatedTags.addAll(getRelatedBlockTags(selectedBlock));

            // Remove the selected block from the corresponding difficulty list
            switch (difficulty) {
                case "easy" -> easyTemp.remove(selectedBlock);
                case "medium" -> mediumTemp.remove(selectedBlock);
                case "hard" -> hardTemp.remove(selectedBlock);
                case "dyed" -> dyedTemp.remove(selectedBlock);
                case "end" -> endTemp.remove(selectedBlock);
                default -> throw new IllegalArgumentException("Invalid difficulty");
            }
        }

        return targetBlocks;
    }

    // Method to choose difficulty based on weights
    private static String chooseDifficulty(int easyWeight, int mediumWeight, int hardWeight, int dyedWeight, int endWeight) {
        int totalWeight = easyWeight + mediumWeight + hardWeight + dyedWeight + endWeight;
        if (totalWeight <= 0) {
            throw new IllegalStateException("No block pools are available for selection.");
        }
        int randomNumber = RANDOM.nextInt(totalWeight);

        if (randomNumber < easyWeight) {
            return "easy";
        } else if (randomNumber < easyWeight + mediumWeight) {
            return "medium";
        } else if (randomNumber < easyWeight + mediumWeight + hardWeight) {
            return "hard";
        } else if (randomNumber < easyWeight + mediumWeight + hardWeight + dyedWeight) {
            return "dyed";
        } else {
            return "end";
        }
    }

    // Method to select a block from the corresponding difficulty list
    private static String selectBlock(String difficulty, List<String> easyTemp, List<String> mediumTemp,
                                      List<String> hardTemp, List<String> dyedTemp, List<String> endTemp,
                                      Set<String> suppressedRelatedTags) {
        return switch (difficulty) {
            case "easy" -> selectWeightedBlockFromList(easyTemp, suppressedRelatedTags);
            case "medium" -> selectWeightedBlockFromList(mediumTemp, suppressedRelatedTags);
            case "hard" -> selectWeightedBlockFromList(hardTemp, suppressedRelatedTags);
            case "dyed" -> selectWeightedBlockFromList(dyedTemp, suppressedRelatedTags);
            case "end" -> selectWeightedBlockFromList(endTemp, suppressedRelatedTags);
            default -> throw new IllegalArgumentException("Invalid difficulty");
        };
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

    // Calculate weight for easy blocks
    // Weight decreases through the game but keeps a minimum fallback weight of 1.
    public static int calculateEasyBlocksWeight(float progress) {
        if (progress >= EASY_DISABLE_PROGRESS) {
            return 1;
        }

        return Math.max(1, (int) (EASY_START_WEIGHT - EASY_START_WEIGHT * progress / EASY_DISABLE_PROGRESS));
    }

    // Calculate weight for medium blocks
    // Weight ramps up early, then keeps growing in the late game.
    public static int calculateMediumBlocksWeight(float progress) {
        if (progress <= MEDIUM_WEIGHT_TURNING_POINT) {
            return (int) (MEDIUM_EARLY_START_WEIGHT
                    + (MEDIUM_MID_WEIGHT - MEDIUM_EARLY_START_WEIGHT) * progress / MEDIUM_WEIGHT_TURNING_POINT);
        } else {
            return (int) (MEDIUM_MID_WEIGHT
                    + (MEDIUM_LATE_WEIGHT - MEDIUM_MID_WEIGHT)
                    * (progress - MEDIUM_WEIGHT_TURNING_POINT) / (1 - MEDIUM_WEIGHT_TURNING_POINT));
        }
    }

    // Calculate weight for hard blocks
    // Weight grows throughout the game and becomes dominant late.
    public static int calculateHardBlocksWeight(float progress) {
        if (progress <= HARD_WEIGHT_TURNING_POINT) {
            return (int) (HARD_EARLY_START_WEIGHT
                    + (HARD_MID_WEIGHT - HARD_EARLY_START_WEIGHT) * progress / HARD_WEIGHT_TURNING_POINT);
        } else {
            return (int) (HARD_MID_WEIGHT
                    + (HARD_LATE_WEIGHT - HARD_MID_WEIGHT)
                    * (progress - HARD_WEIGHT_TURNING_POINT) / (1 - HARD_WEIGHT_TURNING_POINT));
        }
    }

    // Calculate weight for dyed blocks
    // Weight remains constant at 10 regardless of progress
    public static int calculateDyedBlocksWeight(float progress) {
        return 10;
    }

    // Calculate weight for end blocks
    // Weight is 0 for progress from 0 to 0.8 (Unless only the end block is left),
    // then increases to 60 as progress goes from 0.8 to 1
    public static int calculateEndBlocksWeight(float progress) {
        float prop = (float) endBlocks.size() / blocks.size();
        if (progress <= 1 - prop) {
            if (progress <= 0.8) return 0;
            if (progress > 0.8) return (int) (60 * (progress - 0.8) / 0.2);
        } else {
            return 60;
        }
        return 0;
    }

    // Check if there are any problems with the blocks imported from the file
    public static boolean checkBlock() {
        boolean flag = true;
        for (String str : blocks) {
            if (Goal.isKnownGoalId(str)) {
                if (!Goal.isValid(str)) {
                    Bukkit.getLogger().severe("[BlockRacing] Invalid Draftout goal: " + str);
                    sendAll(String.format(Message.NOTICE_ERROR_BLOCK.getString(), Goal.getDisplayName(str)));
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
        easyBlocks = List.of(readFile("EasyBlocks.txt"));
        mediumBlocks = List.of(readFile("MediumBlocks.txt"));
        hardBlocks = List.of(readFile("HardBlocks.txt"));
        dyedBlocks = List.of(readFile("DyedBlocks.txt"));
        endBlocks = List.of(readFile("EndBlocks.txt"));
        draftoutGoals = Goal.load(readFile("DraftoutGoals.txt"));
        addUpBlocks();
    }

    public static String[] readFile(String fileName) {
        try {
            File file = new File(Main.getInstance().getDataFolder(), fileName);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.equals("")) lines.add(line);
            }
            reader.close();
            return lines.toArray(new String[0]);
        } catch (IOException e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "[BlockRacing] Error reading blocks file!", e);
        }
        return null;
    }
}
