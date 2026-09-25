package top.lqsnow.blockracing.network;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import top.lqsnow.blockracing.managers.*;
import top.lqsnow.blockracing.menus.TargetIconCatalog;

import java.util.*;

/** Opt-in read-only bridge. No task mutation, inventory write, or client-supplied team/score. */
public final class TaskBoardBridge implements PluginMessageListener, Listener {
    private static final long LEASE_NANOS = 12_000_000_000L;
    private static final long IMMEDIATE_COOLDOWN_NANOS = 200_000_000L;
    private static TaskBoardBridge activeBridge;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> leases = new HashMap<>();
    private final Map<UUID, Long> lastImmediateSend = new HashMap<>();

    public TaskBoardBridge(JavaPlugin plugin) { this.plugin = plugin; }

    public void start() {
        activeBridge = this;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, BoardWire.REQUEST, this);
        messenger.registerOutgoingPluginChannel(plugin, BoardWire.SNAPSHOT);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    public static void pushAll() {
        if (activeBridge != null) activeBridge.broadcastSnapshots();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BoardWire.REQUEST.equals(channel) || !BoardWire.validRequest(message)) return;
        UUID id = player.getUniqueId();
        if (message[0] == 0) { leases.remove(id); return; }
        long now = System.nanoTime();
        leases.put(id, now + LEASE_NANOS);
        // Send an immediate snapshot if outside the 200ms per-player cooldown so Tab open is instant,
        // while spamming packets remains rate-limited in O(1).
        if (now - lastImmediateSend.getOrDefault(id, 0L) >= IMMEDIATE_COOLDOWN_NANOS) {
            lastImmediateSend.put(id, now);
            sendTo(player, new HashMap<>());
        }
    }

    @EventHandler
    public void onRegisterChannel(org.bukkit.event.player.PlayerRegisterChannelEvent event) {
        if (BoardWire.SNAPSHOT.equals(event.getChannel())) {
            sendTo(event.getPlayer(), new HashMap<>());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        leases.remove(id);
        lastImmediateSend.remove(id);
    }

    private void broadcastSnapshots() {
        Map<String, byte[]> cache = new HashMap<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendTo(viewer, cache);
        }
    }

    private void update() {
        long now = System.nanoTime();
        leases.entrySet().removeIf(e -> e.getValue() < now || Bukkit.getPlayer(e.getKey()) == null);
        Map<String, byte[]> cache = new HashMap<>();
        for (UUID id : leases.keySet()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) sendTo(viewer, cache);
        }
    }

    private void sendTo(Player viewer, Map<String, byte[]> cache) {
        if (viewer == null || !viewer.getListeningPluginChannels().contains(BoardWire.SNAPSHOT)) return;
        String key = team(viewer) + ":" + LanguageManager.usesChinese(viewer);
        byte[] data = cache.computeIfAbsent(key, ignored -> {
            try { return BoardWire.encode(snapshot(viewer)); }
            catch (RuntimeException ex) {
                plugin.getLogger().warning("Task board snapshot unavailable: " + ex.getClass().getSimpleName());
                return BoardWire.encode(Map.of("version", 1, "error", "目标面板暂不可用 / Board unavailable", "tasks", List.of()));
            }
        });
        viewer.sendPluginMessage(plugin, BoardWire.SNAPSHOT, data);
    }

    static String team(Player viewer) {
        return Team.redTeamPlayers.contains(viewer.getName()) ? "red"
                : Team.blueTeamPlayers.contains(viewer.getName()) ? "blue" : "";
    }

    static String status(boolean remaining, boolean available) {
        // Both sides lose a mutually claimed task. Absence is NOT proof this team won it.
        return !remaining ? "resolved" : available ? "active" : "queued";
    }

    public static Map<String, Object> snapshot(Player viewer) {
        String team = team(viewer);
        boolean red = team.equals("red");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", 1);
        data.put("team", team);
        data.put("state", Game.getCurrentGameState().name());
        data.put("chinese", LanguageManager.usesChinese(viewer));
        data.put("score", team.isEmpty() ? 0 : red ? Game.redTeamProgressScore : Game.blueTeamProgressScore);
        data.put("winScore", team.isEmpty() ? 0 : red ? Game.redTeamWinScore : Game.blueTeamWinScore);
        data.put("totalScore", team.isEmpty() ? 0 : red ? Game.redTeamTotalScore : Game.blueTeamTotalScore);
        List<Map<String, Object>> tasks = new ArrayList<>();
        data.put("tasks", tasks);
        if (team.isEmpty() || Game.getCurrentGameState() == Game.GameState.PREGAME) return data;
        List<String> original = red ? Block.redTeamBlocks : Block.blueTeamBlocks;
        if (original.size() > 512) {
            data.put("error", "目标超过 512 项，使用 /menu targets / Too many tasks; use /menu targets");
            return data;
        }
        Set<String> remaining = new HashSet<>(red ? Block.redTeamRemainingBlocks : Block.blueTeamRemainingBlocks);
        Set<String> available = new HashSet<>(Game.getCurrentBlocks(team));
        for (int index = 0; index < original.size(); index++) {
            String target = original.get(index);
            String requirement = Goal.getRawRequirement(target);
            var visual = TargetIconCatalog.matching(target, requirement);
            Material material = visual != null ? visual.material()
                    : Goal.isGoal(target) ? Goal.getGoalIcon(target) : Material.getMaterial(target);
            if (material == null || !material.isItem()) material = Material.PAPER;
            String status = status(remaining.contains(target), available.contains(target));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", target);
            row.put("index", index + 1);
            row.put("title", plain(Game.getTargetDisplayName(target, viewer), 240));
            row.put("requirement", plain(requirement, 8192));
            row.put("icon", material.getKey().toString());
            row.put("model", visual != null && !visual.vanilla() ? visual.modelKey() : "");
            row.put("glint", Block.isBonusTarget(target) || (visual != null && visual.glint()));
            row.put("score", Block.getTargetScore(target));
            row.put("bonus", Block.isBonusTarget(target));
            row.put("status", status);
            int[] progress = Goal.isGoal(target) && status.equals("active")
                    ? Goal.getBoardProgress(target, viewer) : new int[]{0, 1};
            row.put("current", progress[0]);
            row.put("required", progress[1]);
            row.put("progressKnown", Goal.isGoal(target) && status.equals("active"));
            row.put("individual", Goal.isBoardProgressIndividual(target));
            tasks.add(row);
        }
        return data;
    }

    private static String plain(String text, int limit) {
        if (text == null) return "";
        String clean = text.replaceAll("§.", "");
        return clean.length() <= limit ? clean : clean.substring(0, limit) + "…";
    }
}
