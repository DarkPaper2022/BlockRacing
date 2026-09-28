package top.lqsnow.blockracing.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.lqsnow.blockracing.managers.Game;
import top.lqsnow.blockracing.managers.Gui;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.RandomTeleportManager;
import top.lqsnow.blockracing.managers.Scoreboard;
import top.lqsnow.blockracing.managers.Setting;
import top.lqsnow.blockracing.managers.Team;
import top.lqsnow.blockracing.menus.GameMenu;

import java.util.ArrayList;
import java.util.List;

import static top.lqsnow.blockracing.managers.Game.*;
import static top.lqsnow.blockracing.managers.Team.*;
import static top.lqsnow.blockracing.utils.CommandUtil.sendAll;

public class Menu implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Bukkit.getLogger().info("This command can only be run by a player.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("targetpreview")) {
            if (player.isOp() && Game.getCurrentGameState() == Game.GameState.PREGAME) {
                top.lqsnow.blockracing.menus.TargetListMenu.preview(player).open(player);
            } else {
                player.sendMessage("UI preview requires an operator in pregame.");
            }
            return true;
        }
        if (Game.getCurrentGameState().equals(Game.GameState.END)) return true;

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("main"))) {
            Gui.openMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("chest")) {
            if (args.length > 2) {
                player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
                return true;
            }
            if (Game.getCurrentGameState().equals(Game.GameState.PREGAME)) {
                player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
                return true;
            }
            String team = Team.getTeam(player);
            if (team.isEmpty()) {
                player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
                return true;
            }
            if (args.length == 1) {
                new GameMenu.TeamChestSelectMenu().open(player);
                return true;
            }
            Integer ith = parseIndex(args[1], Setting.getMaxTeamChestNum());
            if (ith == null) {
                player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
                return true;
            }
            Gui.openTeamChest(player, ith - 1);
            return true;
        }

        if (args[0].equalsIgnoreCase("waypoints")) {
            if (Game.getCurrentGameState().equals(Game.GameState.PREGAME)) {
                player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
                return true;
            }
            String team = Team.getTeam(player);
            if (team.isEmpty()) {
                player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
                return true;
            }

            if (args.length == 1) {
                new GameMenu.WayPointMenu(Game.getTeamWaypoints(team), Game.getTeamWaypointIcons(team)).open(player);
                return true;
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("use")) {
                Integer index = parseIndex(args[2], Setting.getMaxTeamWaypointNum());
                if (index == null) {
                    player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
                    return true;
                }
                waypoint(player, index, ClickType.LEFT);
            } else {
                player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("targets") && args.length == 1) {
            if (Game.getCurrentGameState() != Game.GameState.INGAME) {
                player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
            } else {
                new top.lqsnow.blockracing.menus.TargetListMenu(player, 0).open(player);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("roll") && args.length == 1) {
            if (Game.getCurrentGameState().equals(Game.GameState.PREGAME)) {
                player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
                return true;
            }
            roll(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("locate") && args.length == 1) {
            if (Game.getCurrentGameState().equals(Game.GameState.PREGAME)) {
                player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
                return true;
            }
            locate(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("randomTP") && args.length == 1) {
            RandomTeleportManager.requestRtp(player, RandomTeleportManager.RequestReason.USER, false);
            return true;
        }

        player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
        return true;
    }

    private static Integer parseIndex(String value, int maximum) {
        try {
            int index = Integer.parseInt(value);
            return index >= 1 && index <= maximum ? index : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("main");
            completions.add("targets");
            if (sender.isOp() && Game.getCurrentGameState() == Game.GameState.PREGAME) completions.add("targetpreview");
            completions.add("chest");
            completions.add("waypoints");
            completions.add("roll");
            completions.add("locate");
            completions.add("randomTP");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("chest")) {
            for(int i = 1; i <= Setting.getMaxTeamChestNum(); i++){
                completions.add(Integer.toString(i));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("waypoints")) {
            completions.add("use");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("waypoints")) {
            for(int i = 1; i <= Setting.getMaxTeamWaypointNum(); i++){
                completions.add(Integer.toString(i));
            }
        }

        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));

        return completions;
    }
}
