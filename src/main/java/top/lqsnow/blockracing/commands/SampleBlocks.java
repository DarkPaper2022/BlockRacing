package top.lqsnow.blockracing.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.lqsnow.blockracing.managers.Block;
import top.lqsnow.blockracing.managers.Game;

import java.util.ArrayList;
import java.util.List;

public class SampleBlocks implements CommandExecutor, TabCompleter {
    private static final int DEFAULT_SAMPLE_BLOCK_AMOUNT = 64;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        int blockAmount = DEFAULT_SAMPLE_BLOCK_AMOUNT;

        if (args.length >= 1) {
            try {
                blockAmount = Integer.parseInt(args[0]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Invalid block amount: " + args[0]);
                return true;
            }
        }

        if (blockAmount <= 0) {
            sender.sendMessage("Block amount must be greater than 0.");
            return true;
        }

        Block.reloadBlock();
        List<String> sampleBlocks = Block.generateSampleBlocks(blockAmount);

        sender.sendMessage("[BlockRacing] Sample block list (" + blockAmount + "):");
        for (int i = 0; i < sampleBlocks.size(); i++) {
            String blockName = sampleBlocks.get(i);
            String translatedName = Game.getTargetDisplayName(blockName);
            sender.sendMessage(String.format("%02d. %s (%s)", i + 1, blockName, translatedName));
        }
        Bukkit.getLogger().info("[BlockRacing] Sample block list generated: " + sampleBlocks);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add(Integer.toString(DEFAULT_SAMPLE_BLOCK_AMOUNT));
            completions.add("32");
            completions.add("128");
        }

        String prefix = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return completions;
    }
}
