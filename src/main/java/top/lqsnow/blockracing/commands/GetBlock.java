package top.lqsnow.blockracing.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.lqsnow.blockracing.managers.Game;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.utils.TranslationUtil;

import java.util.ArrayList;
import java.util.List;

import static top.lqsnow.blockracing.managers.Game.getCurrentBlocks;
import static top.lqsnow.blockracing.managers.Game.getCurrentGameState;
public class GetBlock implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            Bukkit.getLogger().info("This command can only be run by a player.");
            return true;
        }

        if (getCurrentGameState().equals(Game.GameState.PREGAME)) {
            player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }

        String team = args[0].toLowerCase();
        if (!top.lqsnow.blockracing.managers.TeamId.isValid(team)) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }
        List<String> currentBlocks = getCurrentBlocks(team);
        if (index < 1 || index > currentBlocks.size()) {
            player.sendMessage(Message.NOTICE_ERROR_COMMAND.getString(player));
            return true;
        }

        String block = currentBlocks.get(index - 1);
        player.sendMessage(Game.getTargetDisplayName(block, player));
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (top.lqsnow.blockracing.managers.TeamId t : top.lqsnow.blockracing.managers.TeamId.ALL) {
                completions.add(t.id());
            }
        } else if (args.length == 2) {
            String team = args[0].toLowerCase();
            if (top.lqsnow.blockracing.managers.TeamId.isValid(team)) {
                List<String> currentBlocks = getCurrentBlocks(team);
                for (int i = 1; i <= currentBlocks.size(); i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return completions;
    }
}
