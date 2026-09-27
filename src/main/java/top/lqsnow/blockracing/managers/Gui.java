package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import top.lqsnow.blockracing.menus.GameMenu;
import top.lqsnow.blockracing.menus.PreGameMenu;
import top.lqsnow.blockracing.toolkit.menu.MenuManager;
import top.lqsnow.blockracing.toolkit.menu.MenuView;

import java.util.List;

import static top.lqsnow.blockracing.managers.Game.redTeamChest;
import static top.lqsnow.blockracing.managers.Game.blueTeamChest;
import static top.lqsnow.blockracing.managers.Game.currentGameState;
import static top.lqsnow.blockracing.managers.Team.blueTeamPlayers;
import static top.lqsnow.blockracing.managers.Team.redTeamPlayers;

public class Gui {
    public static void openMenu(Player player) {
        if (currentGameState.equals(Game.GameState.PREGAME)) new PreGameMenu().open(player);
        if (currentGameState.equals(Game.GameState.INGAME)) new GameMenu().open(player);
    }

    @SuppressWarnings("deprecation") // InventoryView has no component-based title setter in Paper 26.2.
    public static void openTeamChest(Player player, int index) {
        String team = Team.getTeam(player);
        if (team.isEmpty()) {
            player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
            return;
        }
        List<org.bukkit.inventory.Inventory> chests = Game.getTeamChests(team);
        if (index >= 0 && index < chests.size()) {
            player.openInventory(chests.get(index));
            Message titleMsg = Team.getMenuTeamChestMessage(team);
            player.getOpenInventory().setTitle(titleMsg.getString(player) + " " + (index + 1));
        }
    }

    public static void closeAllPlayersMenu() {
        Bukkit.getOnlinePlayers().forEach((Player player) -> {
            player.closeInventory();
        });
    }

    // Update menu
    public static void updateMenu(MenuView menu) {
        MenuManager.refreshOpenMenus(menu.getClass());
    }
}
