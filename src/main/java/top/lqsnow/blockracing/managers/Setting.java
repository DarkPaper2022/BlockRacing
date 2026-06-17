package top.lqsnow.blockracing.managers;

import lombok.Getter;
import lombok.Setter;

public class Setting {
    public static final int MAX_BLOCK_AMOUNT_LIMIT = 128;
    public static final int MIN_AVAILABLE_TASK_AMOUNT = 1;
    public static final int MAX_AVAILABLE_TASK_AMOUNT = 128;

    @Getter
    private static boolean enableMediumBlock;
    @Getter
    private static boolean enableHardBlock;
    @Getter
    private static int blockAmount;
    @Getter
    private static int availableTaskAmount;
    @Getter
    private static int maxTeamChestNum;
    @Getter
    private static  int maxTeamWaypointNum;
    @Getter
    private static boolean speedMode;
    public enum GameMode {NORMAL, RACING}
    @Getter
    @Setter
    private static GameMode currentGameMode = GameMode.NORMAL;

    public static void getSettings(){
        enableMediumBlock = Config.MEDIUM_BLOCK.getBoolean();
        enableHardBlock = Config.HARD_BLOCK.getBoolean();
        blockAmount = clamp(Config.BLOCK_AMOUNT.getInt(), 10, MAX_BLOCK_AMOUNT_LIMIT);
        availableTaskAmount = clamp(Config.AVAILABLE_TASK_AMOUNT.getInt(), MIN_AVAILABLE_TASK_AMOUNT, MAX_AVAILABLE_TASK_AMOUNT);
        speedMode = Config.SPEED_MODE.getBoolean();
        maxTeamChestNum = Config.MAX_TEAM_CHEST_NUM.getInt();
        maxTeamWaypointNum = Config.MAX_TEAM_WAYPOINT_NUM.getInt();
        setCurrentGameMode(GameMode.valueOf(Config.GAME_MODE.getString().toUpperCase()));
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
        Setting.blockAmount = clamp(blockAmount, 10, MAX_BLOCK_AMOUNT_LIMIT);
        Config.BLOCK_AMOUNT.setInt(Setting.blockAmount);
    }

    public static void setAvailableTaskAmount(int availableTaskAmount) {
        Setting.availableTaskAmount = clamp(availableTaskAmount, MIN_AVAILABLE_TASK_AMOUNT, MAX_AVAILABLE_TASK_AMOUNT);
        Config.AVAILABLE_TASK_AMOUNT.setInt(Setting.availableTaskAmount);
    }

    public static void setMaxTeamChestNum(int chestNum) {
        Setting.maxTeamChestNum = chestNum;
        Config.MAX_TEAM_CHEST_NUM.setInt(chestNum);
    }

    public static void setSpeedMode(boolean speedMode) {
        Setting.speedMode = speedMode;
        Config.SPEED_MODE.setBoolean(speedMode);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
