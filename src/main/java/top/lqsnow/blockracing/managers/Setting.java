package top.lqsnow.blockracing.managers;


public class Setting {
    public static final int MAX_BLOCK_AMOUNT_LIMIT = 128;
    public static final int MIN_AVAILABLE_TASK_AMOUNT = 1;
    public static final int MAX_AVAILABLE_TASK_AMOUNT = 128;
    public static final int MIN_EASY_TARGETS_PER_GAME = 0;
    public static final int MAX_EASY_TARGETS_PER_GAME = 128;
    public static final int MIN_COST = 0;
    public static final int MAX_COST = 128;
    public static final int MIN_VICTORY_SCORE_PERCENT = 1;
    public static final int MAX_VICTORY_SCORE_PERCENT = 100;
    public static final int DEFAULT_VICTORY_SCORE_PERCENT = 50;
    public static final int MIN_FAVORITE_TARGETS = 1;
    public static final int MAX_FAVORITE_TARGETS = 10;
    public static final int DEFAULT_MAX_FAVORITE_TARGETS = 5;

    private static boolean enableMediumBlock;
    private static boolean enableHardBlock;
    private static int blockAmount;
    private static int maxEasyTargetsPerGame;
    private static int availableTaskAmount;
    private static int locateCost;
    private static int randomTeleportCost;
    private static int bonusScoreThreshold;
    private static int bonusTargetAmount;
    private static int maxTeamChestNum;
    private static  int maxTeamWaypointNum;
    private static int maxFavoriteTargets = DEFAULT_MAX_FAVORITE_TARGETS;
    private static int victoryScorePercent;
    private static boolean speedMode;
    private static boolean mutualExclusion = true;
    public enum GameMode {NORMAL, RACING}
    private static GameMode currentGameMode = GameMode.NORMAL;

    public static void getSettings(){
        enableMediumBlock = Config.MEDIUM_BLOCK.getBoolean();
        enableHardBlock = Config.HARD_BLOCK.getBoolean();
        blockAmount = clamp(Config.BLOCK_AMOUNT.getInt(), 10, MAX_BLOCK_AMOUNT_LIMIT);
        maxEasyTargetsPerGame = clamp(Config.MAX_EASY_TARGETS_PER_GAME.getInt(), MIN_EASY_TARGETS_PER_GAME, MAX_EASY_TARGETS_PER_GAME);
        availableTaskAmount = clamp(Config.AVAILABLE_TASK_AMOUNT.getInt(), MIN_AVAILABLE_TASK_AMOUNT, MAX_AVAILABLE_TASK_AMOUNT);
        bonusScoreThreshold = clamp(Config.BONUS_SCORE_THRESHOLD.getInt(), 11, 99);
        bonusTargetAmount = clamp(Config.BONUS_TARGET_AMOUNT.getInt(), 1, 10);
        locateCost = clamp(Config.LOCATE_COST.getInt(), MIN_COST, MAX_COST);
        randomTeleportCost = clamp(Config.RANDOM_TELEPORT_COST.getInt(), MIN_COST, MAX_COST);
        speedMode = Config.SPEED_MODE.getBoolean();
        // Missing key keeps the original rule: tasks are mutually exclusive.
        mutualExclusion = Config.MUTUAL_EXCLUSION.getBoolean(true);
        maxTeamChestNum = clamp(Config.MAX_TEAM_CHEST_NUM.getInt(), 1, 53);
        maxTeamWaypointNum = clamp(Config.MAX_TEAM_WAYPOINT_NUM.getInt(), 1, 53);
        int favConfig = Config.MAX_FAVORITE_TARGETS.getInt();
        maxFavoriteTargets = clamp(favConfig > 0 ? favConfig : DEFAULT_MAX_FAVORITE_TARGETS,
                MIN_FAVORITE_TARGETS, MAX_FAVORITE_TARGETS);
        int percent = Config.VICTORY_SCORE_PERCENT.getInt();
        victoryScorePercent = clamp(percent > 0 ? percent : DEFAULT_VICTORY_SCORE_PERCENT,
                MIN_VICTORY_SCORE_PERCENT, MAX_VICTORY_SCORE_PERCENT);
        try {
            setCurrentGameMode(GameMode.valueOf(Config.GAME_MODE.getString().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException ex) {
            setCurrentGameMode(GameMode.RACING);
        }
    }

    public static void setEnableMediumBlock(boolean enableMediumBlock) {
        Setting.enableMediumBlock = enableMediumBlock;
        Config.MEDIUM_BLOCK.setBoolean(enableMediumBlock);
    }

    public static void setEnableHardBlock(boolean enableHardBlock) {
        Setting.enableHardBlock = enableHardBlock;
        Config.HARD_BLOCK.setBoolean(enableHardBlock);
    }

    public static void setBlockAmount(int blockAmount) {
        Setting.blockAmount = clamp(blockAmount, 0, MAX_BLOCK_AMOUNT_LIMIT);
        Config.BLOCK_AMOUNT.setInt(Setting.blockAmount);
    }

    public static void setMaxEasyTargetsPerGame(int maxEasyTargetsPerGame) {
        Setting.maxEasyTargetsPerGame = clamp(maxEasyTargetsPerGame, MIN_EASY_TARGETS_PER_GAME, MAX_EASY_TARGETS_PER_GAME);
        Config.MAX_EASY_TARGETS_PER_GAME.setInt(Setting.maxEasyTargetsPerGame);
    }

    public static void setAvailableTaskAmount(int availableTaskAmount) {
        Setting.availableTaskAmount = clamp(availableTaskAmount, MIN_AVAILABLE_TASK_AMOUNT, MAX_AVAILABLE_TASK_AMOUNT);
        Config.AVAILABLE_TASK_AMOUNT.setInt(Setting.availableTaskAmount);
    }

    public static void setLocateCost(int locateCost) {
        Setting.locateCost = clamp(locateCost, MIN_COST, MAX_COST);
        Config.LOCATE_COST.setInt(Setting.locateCost);
    }

    public static void setRandomTeleportCost(int randomTeleportCost) {
        Setting.randomTeleportCost = clamp(randomTeleportCost, MIN_COST, MAX_COST);
        Config.RANDOM_TELEPORT_COST.setInt(Setting.randomTeleportCost);
    }

    public static void setBonusScoreThreshold(int threshold) {
        Setting.bonusScoreThreshold = clamp(threshold, 11, 99);
        Config.BONUS_SCORE_THRESHOLD.setInt(Setting.bonusScoreThreshold);
    }

    public static void setBonusTargetAmount(int amount) {
        Setting.bonusTargetAmount = clamp(amount, 1, 10);
        Config.BONUS_TARGET_AMOUNT.setInt(Setting.bonusTargetAmount);
    }

    public static void setMaxTeamChestNum(int chestNum) {
        Setting.maxTeamChestNum = chestNum;
        Config.MAX_TEAM_CHEST_NUM.setInt(chestNum);
    }

    public static void setMaxFavoriteTargets(int maxFavoriteTargets) {
        Setting.maxFavoriteTargets = clamp(maxFavoriteTargets, MIN_FAVORITE_TARGETS, MAX_FAVORITE_TARGETS);
        Config.MAX_FAVORITE_TARGETS.setInt(Setting.maxFavoriteTargets);
    }

    public static void setVictoryScorePercent(int percent) {
        Setting.victoryScorePercent = clamp(percent, MIN_VICTORY_SCORE_PERCENT, MAX_VICTORY_SCORE_PERCENT);
        Config.VICTORY_SCORE_PERCENT.setInt(Setting.victoryScorePercent);
    }

    public static void setSpeedMode(boolean speedMode) {
        Setting.speedMode = speedMode;
        Config.SPEED_MODE.setBoolean(speedMode);
    }

    public static void setMutualExclusion(boolean mutualExclusion) {
        Setting.mutualExclusion = mutualExclusion;
        Config.MUTUAL_EXCLUSION.setBoolean(mutualExclusion);
    }

    public static void toggleMutualExclusion() {
        setMutualExclusion(!isMutualExclusion());
    }

    public static void toggleMediumBlock() {
        setEnableMediumBlock(!isEnableMediumBlock());
    }

    public static void toggleHardBlock() {
        setEnableHardBlock(!isEnableHardBlock());
    }

    public static void toggleSpeedMode() {
        setSpeedMode(!isSpeedMode());
    }

    public static boolean isEnableMediumBlock() { return enableMediumBlock; }
    public static boolean isEnableHardBlock() { return enableHardBlock; }
    public static int getBlockAmount() { return blockAmount; }
    public static int getMaxEasyTargetsPerGame() { return maxEasyTargetsPerGame; }
    public static int getAvailableTaskAmount() { return availableTaskAmount; }
    public static int getLocateCost() { return locateCost; }
    public static int getRandomTeleportCost() { return randomTeleportCost; }
    public static int getBonusScoreThreshold() { return bonusScoreThreshold; }
    public static int getBonusTargetAmount() { return bonusTargetAmount; }
    public static int getMaxTeamChestNum() { return maxTeamChestNum; }
    public static int getMaxTeamWaypointNum() { return maxTeamWaypointNum; }
    public static int getMaxFavoriteTargets() { return maxFavoriteTargets; }
    public static int getVictoryScorePercent() { return victoryScorePercent; }
    public static boolean isSpeedMode() { return speedMode; }
    public static boolean isMutualExclusion() { return mutualExclusion; }
    public static GameMode getCurrentGameMode() { return currentGameMode; }

    public static void setCurrentGameMode(GameMode mode) {
        currentGameMode = mode;
        Config.GAME_MODE.setString(mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
