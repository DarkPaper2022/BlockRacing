package top.lqsnow.blockracing.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.Team;

import java.util.List;

import static top.lqsnow.blockracing.managers.Game.*;
import static top.lqsnow.blockracing.managers.Team.*;


public class Teleport implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Bukkit.getLogger().info("This command can only be run by a player.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }
        if (getCurrentGameState().equals(GameState.PREGAME)) {
            player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
            return true;
        }

        String senderTeam = Team.getTeam(player);
        if (senderTeam.isEmpty()) {
            // Spectator
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) {
                player.teleport(target);
                player.sendMessage(Message.NOTICE_SPECTATOR_TP_PLAYER_SUCCESS.getString(player).replace("%player%", target.getName()));
            } else {
                player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            }
            return true;
        }

        // Active Team Member: only teleport to teammates
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Message.NOTICE_PLAYER_NOT_EXIST.getString(player));
            return true;
        }
        if (senderTeam.equalsIgnoreCase(Team.getTeam(target))) {
            player.teleport(target);
            player.sendMessage(Message.NOTICE_TP_PLAYER_SUCCESS.getString(player).replace("%player%", target.getName()));
        } else {
            player.sendMessage(Message.NOTICE_PLAYER_NOT_IN_SAME_TEAM.getString(player));
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String team = Team.getTeam(sender.getName());
        if (!team.isEmpty()) {
            return List.copyOf(Team.getPlayers(team));
        }
        return getOnlinePlayersString();
    }
}
