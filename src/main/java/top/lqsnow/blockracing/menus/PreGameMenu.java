package top.lqsnow.blockracing.menus;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import top.lqsnow.blockracing.commands.RandomTeam;
import top.lqsnow.blockracing.managers.Block;
import top.lqsnow.blockracing.managers.Game;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.Setting;
import top.lqsnow.blockracing.managers.Team;
import top.lqsnow.blockracing.toolkit.item.ItemBuilder;
import top.lqsnow.blockracing.toolkit.menu.MenuButton;
import top.lqsnow.blockracing.toolkit.menu.MenuView;
import top.lqsnow.blockracing.managers.TeamId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static top.lqsnow.blockracing.listeners.BasicListener.editAmountPlayer;
import static top.lqsnow.blockracing.listeners.BasicListener.editVictoryPercentPlayer;
import static top.lqsnow.blockracing.managers.Gui.updateMenu;
import static top.lqsnow.blockracing.managers.Scoreboard.updateScoreboard;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public final class PreGameMenu extends MenuView {
    private static final Set<Integer> GREEN_BACKGROUND = Set.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    );
    private static final Set<Integer> BLUE_BACKGROUND = Set.of(31, 40);

    public PreGameMenu() {
        super(54, player -> Message.MENU_PREGAME_TITLE.getString(player));

        // 4 Team Selection buttons: Red (11), Blue (12), Green (13), Yellow (14)
        for (TeamId teamId : TeamId.ALL) {
            int slot = 11 + teamId.ordinal();
            setButton(slot, MenuButton.of(
                    player -> {
                        Message nameMsg = Team.getTeamNameMessage(teamId.id());
                        Message loreMsg = switch (teamId) {
                            case RED -> Message.MENU_JOIN_RED_LORE;
                            case BLUE -> Message.MENU_JOIN_BLUE_LORE;
                            case GREEN -> Message.MENU_JOIN_GREEN_LORE;
                            case YELLOW -> Message.MENU_JOIN_YELLOW_LORE;
                        };
                        List<String> lore = new ArrayList<>(loreMsg.getStringList(player));
                        List<String> members = Team.getPlayers(teamId.id());
                        if (!members.isEmpty()) {
                            lore.add("§7" + String.join(", ", members));
                        }
                        return ItemBuilder.of(teamId.woolMaterial())
                                .name(nameMsg.getString(player))
                                .lore(lore)
                                .build();
                    },
                    (player, click) -> Team.joinTeam(player, teamId.id(), true)
            ));
        }

        setButton(20, toggleButton(
                Setting::isEnableMediumBlock,
                Setting::toggleMediumBlock,
                Message.MENU_MEDIUM_BLOCKS
        ));
        setButton(21, toggleButton(
                Setting::isEnableHardBlock,
                Setting::toggleHardBlock,
                Message.MENU_HARD_BLOCKS
        ));
        setButton(24, MenuButton.of(
                player -> ItemBuilder.of(Material.NAME_TAG)
                        .name(Message.MENU_BLOCK_AMOUNT.getString(player) + Setting.getBlockAmount())
                        .lore(Message.MENU_BLOCK_AMOUNT_LORE.getStringList(player))
                        .build(),
                (player, click) -> {
                    player.closeInventory();
                    editVictoryPercentPlayer.remove(player.getName());
                    editAmountPlayer.add(player.getName());
                    player.sendMessage(Message.NOTICE_SET_BLOCKS.getString(player));
                }
        ));
        setButton(23, MenuButton.of(
                player -> ItemBuilder.of(Material.GOLD_INGOT)
                        .name(Message.MENU_VICTORY_PERCENT.getString(player) + Setting.getVictoryScorePercent() + "%")
                        .lore(Message.MENU_VICTORY_PERCENT_LORE.getStringList(player))
                        .build(),
                (player, click) -> {
                    player.closeInventory();
                    editAmountPlayer.remove(player.getName());
                    editVictoryPercentPlayer.add(player.getName());
                    player.sendMessage(Message.NOTICE_SET_VICTORY_PERCENT.getString(player));
                }
        ));
        setButton(29, MenuButton.of(this::normalModeItem, (player, click) -> {
            Setting.setCurrentGameMode(Setting.GameMode.NORMAL);
            refreshSettings();
        }));
        setButton(30, MenuButton.of(this::racingModeItem, (player, click) -> {
            Setting.setCurrentGameMode(Setting.GameMode.RACING);
            refreshSettings();
        }));
        setButton(32, MenuButton.of(
                player -> ItemBuilder.of(Setting.isMutualExclusion() ? Material.IRON_CHAIN : Material.SHEARS)
                        .name(Setting.isMutualExclusion()
                                ? Message.MENU_MUTUAL_EXCLUSION_ENABLED.getString(player)
                                : Message.MENU_MUTUAL_EXCLUSION_DISABLED.getString(player))
                        .lore(Message.MENU_MUTUAL_EXCLUSION_LORE.getStringList(player))
                        .build(),
                (player, click) -> {
                    Setting.toggleMutualExclusion();
                    sendAll(Setting.isMutualExclusion()
                            ? Message.NOTICE_SET_MUTUAL_EXCLUSION_ENABLED
                            : Message.NOTICE_SET_MUTUAL_EXCLUSION_DISABLED);
                    refreshSettings();
                }
        ));
        setButton(33, MenuButton.of(
                player -> ItemBuilder.of(Setting.isSpeedMode() ? Material.GREEN_CONCRETE : Material.YELLOW_CONCRETE)
                        .name(Setting.isSpeedMode()
                                ? Message.MENU_SPEED_MODE_ENABLED.getString(player)
                                : Message.MENU_SPEED_MODE_DISABLED.getString(player))
                        .lore(Message.MENU_SPEED_MODE_LORE.getStringList(player))
                        .build(),
                (player, click) -> {
                    Setting.toggleSpeedMode();
                    refreshSettings();
                }
        ));
        setButton(38, MenuButton.of(
                player -> ItemBuilder.of(Material.EMERALD)
                        .name(Message.MENU_READY.getString(player))
                        .lore(Message.MENU_READY_LORE.getStringList(player))
                        .build(),
                (player, click) -> Game.playerReady(player)
        ));
        setButton(39, MenuButton.of(
                player -> ItemBuilder.of(Material.DIAMOND)
                        .name(Message.MENU_START.getString(player))
                        .lore(Message.MENU_START_LORE.getStringList(player))
                        .build(),
                (player, click) -> Game.checkStartDemands(player)
        ));
        setButton(41, MenuButton.of(
                player -> ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(Message.MENU_RANDOM_TEAM.getString(player))
                        .lore(Message.MENU_RANDOM_TEAM_LORE.getStringList(player))
                        .build(),
                (player, click) -> RandomTeam.requestConfirmation(player)
        ));
        setButton(42, MenuButton.of(
                () -> ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                        .name("§bLanguage / 语言")
                        .build(),
                (player, click) -> new LanguageMenu().open(player)
        ));
    }

    @Override
    protected ItemStack getBackgroundItem(int slot, Player player) {
        if (GREEN_BACKGROUND.contains(slot)) {
            return item(Material.LIME_STAINED_GLASS_PANE, " ");
        }
        if (BLUE_BACKGROUND.contains(slot)) {
            return item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        }
        if (slot == 10 || slot == 16) {
            return item(Material.YELLOW_STAINED_GLASS_PANE, Message.MENU_SELECT_TEAM.getString(player));
        }
        if (slot == 19 || slot == 25) {
            return item(Material.YELLOW_STAINED_GLASS_PANE, Message.MENU_BLOCK_SETTING.getString(player));
        }
        if (slot == 28 || slot == 34) {
            return item(Material.YELLOW_STAINED_GLASS_PANE, Message.MENU_SELECT_MODE.getString(player));
        }
        if (slot == 37 || slot == 43) {
            return item(Material.YELLOW_STAINED_GLASS_PANE, Message.MENU_READY_AND_START.getString(player));
        }
        return null;
    }

    private MenuButton toggleButton(BooleanSupplier enabled, Runnable toggle, Message label) {
        return MenuButton.of(
                player -> item(
                        enabled.getAsBoolean() ? Material.GREEN_CONCRETE : Material.RED_CONCRETE,
                        label.getString(player) + (enabled.getAsBoolean()
                                ? Message.MENU_ENABLED.getString(player)
                                : Message.MENU_DISABLED.getString(player))
                ),
                (player, click) -> {
                    toggle.run();
                    Block.refreshAvailableBlocksAndClampAmount();
                    refreshSettings();
                }
        );
    }

    private ItemStack normalModeItem(Player player) {
        boolean selected = Setting.getCurrentGameMode().equals(Setting.GameMode.NORMAL);
        return ItemBuilder.of(selected ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(Message.MENU_NORMAL_MODE.getString(player) + " " + (selected
                        ? Message.MENU_CURRENT_MODE.getString(player)
                        : Message.MENU_SWITCH_TO.getString(player)))
                .lore(Message.MENU_NORMAL_MODE_LORE.getStringList(player))
                .build();
    }

    private ItemStack racingModeItem(Player player) {
        boolean selected = Setting.getCurrentGameMode().equals(Setting.GameMode.RACING);
        return ItemBuilder.of(selected ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                .name(Message.MENU_RACING_MODE.getString(player) + " " + (selected
                        ? Message.MENU_CURRENT_MODE.getString(player)
                        : Message.MENU_SWITCH_TO.getString(player)))
                .lore(Message.MENU_RACING_MODE_LORE.getStringList(player))
                .build();
    }

    private void refreshSettings() {
        updateMenu(new PreGameMenu());
        updateScoreboard();
    }

    private static ItemStack item(Material material, String name) {
        return ItemBuilder.of(material).name(name).build();
    }
}
