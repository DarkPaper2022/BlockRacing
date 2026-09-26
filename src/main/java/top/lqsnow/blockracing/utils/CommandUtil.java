package top.lqsnow.blockracing.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.Team;

import java.util.List;
import java.util.function.BiFunction;

import static top.lqsnow.blockracing.utils.ColorUtil.t;

public class CommandUtil {
    public static void sendAll(String message) {
        Bukkit.getOnlinePlayers().forEach((Player player) -> {
            player.sendMessage(t(message));
        });
    }

    public static void sendAll(Message message) {
        sendAll(message, (player, text) -> text);
    }

    public static void sendAll(Message message, BiFunction<Player, String, String> formatter) {
        Bukkit.getOnlinePlayers().forEach(player ->
                player.sendMessage(t(formatter.apply(player, message.getString(player)))));
    }

    public static void sendTeam(String teamId, String message) {
        List<String> players = Team.getPlayers(teamId);
        players.forEach(playerName -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) player.sendMessage(t(message));
        });
    }

    public static void sendTeam(String teamId, Message message, BiFunction<Player, String, String> formatter) {
        List<String> players = Team.getPlayers(teamId);
        players.forEach(playerName -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) player.sendMessage(t(formatter.apply(player, message.getString(player))));
        });
    }

    public static void sendRed(String message) {
        sendTeam("red", message);
    }

    public static void sendRed(Message message, BiFunction<Player, String, String> formatter) {
        sendTeam("red", message, formatter);
    }

    public static void sendBlue(String message) {
        sendTeam("blue", message);
    }

    public static void sendBlue(Message message, BiFunction<Player, String, String> formatter) {
        sendTeam("blue", message, formatter);
    }
}
