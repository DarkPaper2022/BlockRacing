package top.lqsnow.blockracing.menus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.button.ButtonMenu;
import org.mineacademy.fo.menu.button.annotation.Position;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompMaterial;

import top.lqsnow.blockracing.managers.Game;
import static top.lqsnow.blockracing.managers.Game.blueTeamScore;
import static top.lqsnow.blockracing.managers.Game.blueWaypoint;
import static top.lqsnow.blockracing.managers.Game.blueWaypointIconCache;
import static top.lqsnow.blockracing.managers.Game.freeRandomTPList;
import static top.lqsnow.blockracing.managers.Game.getCoords;
import static top.lqsnow.blockracing.managers.Game.randomTeleport;
import static top.lqsnow.blockracing.managers.Game.redTeamScore;
import static top.lqsnow.blockracing.managers.Game.redWaypoint;
import static top.lqsnow.blockracing.managers.Game.redWaypointIconCache;
import static top.lqsnow.blockracing.managers.Game.waypoint;
import static top.lqsnow.blockracing.managers.Gui.openTeamChest;
import static top.lqsnow.blockracing.managers.Gui.updateMenu;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.Scoreboard;
import top.lqsnow.blockracing.managers.Setting;
import top.lqsnow.blockracing.managers.Goal;
import static top.lqsnow.blockracing.managers.Team.blueTeamPlayers;
import static top.lqsnow.blockracing.managers.Team.redTeamPlayers;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public class GameMenu extends Menu {
    private static final int TARGET_LIST_ITEMS_PER_PAGE = 45;

    @Position(0)
    private final Button teamChest;

    @Position(1)
    private final Button targetList;

    @Position(2)
    private final Button roll;

    @Position(4)
    private final Button locate;

    @Position(6)
    private final Button waypoint;

    @Position(8)
    private final Button randomTP;

    public GameMenu() {
        setTitle(Message.MENU_GAME_TITLE.getString());
        setSize(2 * 9);

        // Open team chest menu
        this.teamChest = new ButtonMenu(new TeamChestSelectMenu(), ItemCreator.of(CompMaterial.CHEST, Message.MENU_TEAM_CHEST.getString(), Message.MENU_TEAM_CHEST_LORE.getStringList()).make());

        // Open target list menu
        this.targetList = new Button() {
            @Override
            public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                new TargetListMenu(player, 0).displayTo(player);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(CompMaterial.WRITABLE_BOOK, Message.MENU_TARGET_LIST.getString(), Message.MENU_TARGET_LIST_LORE.getStringList()).make();
            }
        };

        // Roll
        this.roll = new Button() {
            @Override
            public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                Game.roll(player);
                player.closeInventory();
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(CompMaterial.TOTEM_OF_UNDYING, Message.MENU_ROLL.getString(), Message.MENU_ROLL_LORE.getString()).make();
            }
        };

        // Locate
        this.locate = new Button() {
            @Override
            public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                Game.locate(player);
                player.closeInventory();
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(CompMaterial.COMPASS, Message.MENU_LOCATE.getString(), replacePlaceholders(Message.MENU_LOCATE_LORE.getStringList())).make();
            }
        };

        // Open waypoint menu
        this.waypoint = new Button() {
            @Override
            public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                if (redTeamPlayers.contains(player.getName())) new WayPointMenu(redWaypoint,redWaypointIconCache).displayTo(player);
                else if (blueTeamPlayers.contains(player.getName())) new WayPointMenu(blueWaypoint,blueWaypointIconCache).displayTo(player);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(CompMaterial.PAPER, Message.MENU_WAYPOINTS.getString(), Message.MENU_WAYPOINTS_LORE.getStringList()).make();
            }
        };

        // Random tp
        this.randomTP = new Button() {
            @Override
            public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                if (freeRandomTPList.contains(player.getName())) {
                    Game.randomTeleport(player, false);
                    freeRandomTPList.remove(player.getName());
                } else {
                    if (redTeamPlayers.contains(player.getName())) {
                        if (redTeamScore < Setting.getRandomTeleportCost()) {
                            player.sendMessage(Message.NOTICE_NOT_ENOUGH_SCORE.getString());
                            return;
                        }
                    } else if (blueTeamPlayers.contains(player.getName())) {
                        if (blueTeamScore < Setting.getRandomTeleportCost()) {
                            player.sendMessage(Message.NOTICE_NOT_ENOUGH_SCORE.getString());
                            return;
                        }
                    }
                    player.closeInventory();
                    randomTeleport(player, false);
                    if (redTeamPlayers.contains(player.getName())) {
                        redTeamScore -= Setting.getRandomTeleportCost();
                        sendAll(Message.NOTICE_RANDOM_TP.getString()
                                .replace("%player%", Message.TEAM_RED_COLOR.getString() + player.getName())
                                .replace("%score%", String.valueOf(Setting.getRandomTeleportCost())));
                    } else if (blueTeamPlayers.contains(player.getName())) {
                        blueTeamScore -= Setting.getRandomTeleportCost();
                        sendAll(Message.NOTICE_RANDOM_TP.getString()
                                .replace("%player%", Message.TEAM_BLUE_COLOR.getString() + player.getName())
                                .replace("%score%", String.valueOf(Setting.getRandomTeleportCost())));
                    }
                    Scoreboard.updateScoreboard();
                }
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(CompMaterial.ENDER_PEARL, Message.MENU_RANDOM_TP.getString(), replacePlaceholders(Message.MENU_RANDOM_TP_LORE.getStringList())).make();
            }
        };

    }

    public class TargetListMenu extends Menu {
        private final String team;
        private final int page;

        public TargetListMenu(Player player, int page) {
            super(GameMenu.this);
            this.team = redTeamPlayers.contains(player.getName()) ? "red"
                    : (blueTeamPlayers.contains(player.getName()) ? "blue" : "");
            List<String> targets = getTargets();
            int maxPage = Math.max(0, (targets.size() - 1) / TARGET_LIST_ITEMS_PER_PAGE);
            this.page = Math.max(0, Math.min(page, maxPage));

            setTitle(Message.MENU_TARGET_LIST_TITLE.getString()
                    .replace("%page%", String.valueOf(this.page + 1))
                    .replace("%total_page%", String.valueOf(maxPage + 1)));
            setSize(6 * 9);

            int start = this.page * TARGET_LIST_ITEMS_PER_PAGE;
            int end = Math.min(targets.size(), start + TARGET_LIST_ITEMS_PER_PAGE);
            for (int i = start; i < end; i++) {
                int slot = i - start;
                String target = targets.get(i);
                int index = i + 1;
                Button button = new Button(slot) {
                    @Override
                    public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                    }

                    @Override
                    public ItemStack getItem() {
                        return ItemCreator.of(getTargetIcon(target), "&e" + index + ". &f" + Game.getTargetDisplayName(target),
                                getTargetLore(player, index, target)).make();
                    }
                };
                this.registerButton(button);
            }

            Button previous = new Button(45) {
                @Override
                public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                    if (TargetListMenu.this.page > 0) {
                        new TargetListMenu(player, TargetListMenu.this.page - 1).displayTo(player);
                    }
                }

                @Override
                public ItemStack getItem() {
                    return ItemCreator.of(CompMaterial.ARROW, Message.MENU_TARGET_LIST_PREVIOUS.getString()).make();
                }
            };
            this.registerButton(previous);

            Button pageInfo = new Button(49) {
                @Override
                public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                }

                @Override
                public ItemStack getItem() {
                    return ItemCreator.of(CompMaterial.PAPER, Message.MENU_TARGET_LIST_PAGE.getString()
                            .replace("%page%", String.valueOf(TargetListMenu.this.page + 1))
                            .replace("%total_page%", String.valueOf(maxPage + 1))
                            .replace("%amount%", String.valueOf(targets.size()))).make();
                }
            };
            this.registerButton(pageInfo);

            Button next = new Button(53) {
                @Override
                public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                    if (TargetListMenu.this.page < maxPage) {
                        new TargetListMenu(player, TargetListMenu.this.page + 1).displayTo(player);
                    }
                }

                @Override
                public ItemStack getItem() {
                    return ItemCreator.of(CompMaterial.ARROW, Message.MENU_TARGET_LIST_NEXT.getString()).make();
                }
            };
            this.registerButton(next);

            Button back = new Button(48) {
                @Override
                public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                    new GameMenu().displayTo(player);
                }

                @Override
                public ItemStack getItem() {
                    return ItemCreator.of(CompMaterial.BARRIER, Message.MENU_ALL_RETURN_BACK.getString()).make();
                }
            };
            this.registerButton(back);
        }

        private List<String> getTargets() {
            return switch (team) {
                case "red" -> Game.getCurrentBlocks("red");
                case "blue" -> Game.getCurrentBlocks("blue");
                default -> List.of();
            };
        }

        @Override
        protected boolean addReturnButton() {
            return false;
        }
    }

    // Team chest select menu
    public class TeamChestSelectMenu extends Menu {

        public TeamChestSelectMenu() {
            super(GameMenu.this);

            setTitle(Message.MENU_TEAM_CHEST_SELECT_TITLE.getString());

            int teamChestNum = Setting.getMaxTeamChestNum();
            int teamChestMenuSize = ((teamChestNum) / 9 + 1) * 9;
            setSize(teamChestMenuSize);

            for (int i = 0; i < teamChestNum; i++) {
                Button button = new Button(i) {
                    @Override
                    public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                        openTeamChest(player, this.getSlot());
                    }

                    @Override
                    public ItemStack getItem() {
                        return ItemCreator.of(CompMaterial.CHEST, Message.MENU_TEAM_CHEST_SELECT_CHEST.getString() + (this.getSlot() + 1)).make();
                    }
                };

                this.registerButton(button);
            }

            Button back = new Button(teamChestMenuSize - 1) {
                @Override
                public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                    new GameMenu().displayTo(player);
                }

                @Override
                public ItemStack getItem() {
                    return ItemCreator.of(CompMaterial.ARROW, Message.MENU_ALL_RETURN_BACK.getString()).make();
                }
            };

            this.registerButton(back);

        }

        @Override
        protected boolean addReturnButton() {
            return false;
        }
    }

    public class WayPointMenu extends Menu {
        public WayPointMenu(HashMap<Integer, Location> wayPointMap,HashMap<Integer,CompMaterial> wayPointIconCache) {
            super(GameMenu.this);

            setTitle(Message.MENU_WAYPOINT_TITLE.getString());

            int teamWaypointNum = Setting.getMaxTeamWaypointNum();
            int teamWaypointMenuNum = ((teamWaypointNum) / 9 + 1) * 9;
            setSize(teamWaypointMenuNum);

            for (int i = 1; i <= teamWaypointNum; i++) {
                Button button = new Button(i-1) {
                    @Override
                    public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                        boolean isChanged = waypoint(player, this.getSlot() + 1, click); 
                        if (isChanged) {
                            updateMenu(WayPointMenu.this);
                        }
                    }

                    @Override
                    public ItemStack getItem() {
                        int ith = this.getSlot() + 1;
                        Location wayPoint = wayPointMap.get(ith);

                        if (wayPoint != null) {
                            CompMaterial icon = wayPointIconCache.get(ith);
                            
                            if(icon == null){
                                Block block = wayPoint.getBlock();
                                while (block.isEmpty()&&block.getY()>-64) {
                                    block = block.getRelative(0, -1, 0);
                                }
                                icon = CompMaterial.fromBlock(block);
                                if(block.isEmpty()){
                                    switch(block.getWorld().getEnvironment()){
                                        case NORMAL:icon = CompMaterial.GRASS_BLOCK;break;
                                        case NETHER:icon = CompMaterial.NETHERRACK;break;
                                        case THE_END:icon = CompMaterial.END_STONE;break;
                                        default:icon = CompMaterial.FILLED_MAP;break;
                                    }
                                }
                                wayPointIconCache.put(ith,icon);
                            }
                            
                            ItemStack itemStack;
                            try {
                                itemStack = ItemCreator.of(icon, Message.MENU_WAYPOINT_FILLED.getString() + ith, replacePlaceholders(Message.MENU_WAYPOINT_FILLED_LORE.getStringList(), wayPoint.getWorld().getName(), getCoords(wayPoint), wayPoint.getBlock().getBiome().getKey().getKey())).make();
                            } catch (Exception e) {
                                itemStack = ItemCreator.of(CompMaterial.FILLED_MAP, Message.MENU_WAYPOINT_FILLED.getString() + ith, replacePlaceholders(Message.MENU_WAYPOINT_FILLED_LORE.getStringList(), wayPoint.getWorld().getName(), getCoords(wayPoint), wayPoint.getBlock().getBiome().getKey().getKey())).make();
                            }
                            return itemStack;
                        } else {
                            wayPointIconCache.remove(ith);
                            return ItemCreator.of(CompMaterial.MAP, Message.MENU_WAYPOINT_EMPTY.getString() + ith, Message.MENU_WAYPOINT_EMPTY_LORE.getStringList()).make();
                        }
                    }

                };

                this.registerButton(button);
            }

            Button back = new Button(teamWaypointMenuNum - 1) {
                @Override
                public void onClickedInMenu(Player player, Menu menu, ClickType click) {
                    new GameMenu().displayTo(player);
                }

                @Override
                public ItemStack getItem() {
                    return ItemCreator.of(CompMaterial.ARROW, Message.MENU_ALL_RETURN_BACK.getString()).make();
                }
            };
            this.registerButton(back);
        }

        @Override
        protected boolean addReturnButton() {
            return false;
        }
    }

    private Collection<String> replacePlaceholders(Collection<String> lore) {
        List<String> modifiedLore = new ArrayList<>();

        for (String line : lore) {
            line = line
                    .replace("%score%", String.valueOf(Setting.getLocateCost()))
                    .replace("%random_tp_score%", String.valueOf(Setting.getRandomTeleportCost()));

            modifiedLore.add(line);
        }
        return modifiedLore;
    }

    private Collection<String> replacePlaceholders(Collection<String> lore, String dimension, String coords, String biome) {
        List<String> modifiedLore = new ArrayList<>();

        for (String line : lore) {
            line = line
                    .replace("%dimension%", dimension)
                    .replace("%coords%", coords)
                    .replace("%biome%", biome);

            modifiedLore.add(line);
        }
        return modifiedLore;
    }

    private Collection<String> replaceTargetPlaceholders(Collection<String> lore, int index, String target) {
        List<String> modifiedLore = new ArrayList<>();

        for (String line : lore) {
            line = line
                    .replace("%index%", String.valueOf(index))
                    .replace("%target%", Game.getTargetDisplayName(target))
                    .replace("%score%", String.valueOf(top.lqsnow.blockracing.managers.Block.getTargetScore(target)));

            modifiedLore.add(line);
        }
        return modifiedLore;
    }

    private Collection<String> getTargetLore(Player player, int index, String target) {
        List<String> lore = new ArrayList<>(replaceTargetPlaceholders(Message.MENU_TARGET_LIST_ITEM_LORE.getStringList(), index, target));
        lore.addAll(Goal.getProgressLore(target, player));
        return lore;
    }

    private CompMaterial getTargetIcon(String target) {
        if (target == null || target.startsWith("DRAFTOUT:")) {
            return CompMaterial.WRITABLE_BOOK;
        }

        try {
            return CompMaterial.fromMaterial(org.bukkit.Material.valueOf(target));
        } catch (Exception exception) {
            return CompMaterial.PAPER;
        }
    }


}
