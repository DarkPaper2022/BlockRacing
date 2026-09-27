package top.lqsnow.blockracing.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.lqsnow.blockracing.managers.Game;
import top.lqsnow.blockracing.managers.GameProgressStore;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.Setting;
import top.lqsnow.blockracing.managers.Team;
import top.lqsnow.blockracing.utils.CommandUtil;

public class WayPoint implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Bukkit.getLogger().info("This command can only be run by a player.");
            return true;
        }
        if (Game.getCurrentGameState().equals(Game.GameState.PREGAME)) {
            player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("remove")) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }
        String team = Team.getTeam(player);
        if (team.isEmpty()) {
            player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }
        if (index < 1 || index > Setting.getMaxTeamWaypointNum()) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }

        var waypoints = Game.getTeamWaypoints(team);
        if (waypoints.remove(index) != null) {
            Game.getTeamWaypointIcons(team).remove(index);
            GameProgressStore.saveNow();
            Message removeMsg = Team.getRemoveWaypointMessage(team);
            CommandUtil.sendTeam(team, removeMsg, (viewer, text) -> text
                    .replace("%player%", player.getName()).replace("%index%", String.valueOf(index)));
        } else {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
        }
        return true;
    }
}
