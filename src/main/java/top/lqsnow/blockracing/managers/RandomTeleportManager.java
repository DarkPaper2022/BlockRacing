package top.lqsnow.blockracing.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import top.lqsnow.blockracing.Main;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Manages background pre-generation and safe-location validation for random teleports.
 * Prevents main thread stutter and eliminates repeated synchronous ocean retries.
 */
public final class RandomTeleportManager {
    private static final Logger LOGGER = Logger.getLogger("BlockRacing");
    private static final int TARGET_POOL_SIZE = 24;
    private static final int MAX_COORD_RANGE = 10000;
    private static final Random RANDOM = new Random();

    // FIFO candidate queue
    private static final Deque<Location> POOL = new ArrayDeque<>();
    // Prevents duplicate candidate generation in the exact same chunk
    private static final Set<Long> PENDING_CHUNKS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean IS_PRODUCING = new AtomicBoolean(false);

    private static BukkitTask warmerTask;

    private static final Set<Material> DANGEROUS_BLOCKS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.VOID_AIR
    );

    private RandomTeleportManager() {}

    /**
     * Start the periodic background warmer task.
     */
    public static synchronized void startWarmer() {
        if (warmerTask != null && !warmerTask.isCancelled()) {
            return;
        }
        // Runs every 20 ticks (1 second) to maintain the pool without spiking server tick time
        warmerTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), RandomTeleportManager::tick, 40L, 20L);
    }

    /**
     * Stop the warmer task.
     */
    public static synchronized void stopWarmer() {
        if (warmerTask != null) {
            warmerTask.cancel();
            warmerTask = null;
        }
    }

    /**
     * Clear all cached candidates.
     */
    public static synchronized void clearPool() {
        POOL.clear();
        PENDING_CHUNKS.clear();
    }

    public static synchronized int getPoolSize() {
        return POOL.size();
    }

    public static synchronized List<Location> getPoolSnapshot() {
        return List.copyOf(POOL);
    }

    /**
     * Atomically fetch a verified safe candidate location.
     * Returns null if pool is empty.
     */
    public static synchronized Location pollCandidate() {
        Location loc = POOL.pollFirst();
        // Trigger a top-up immediately if pool is low
        triggerProduce();
        return loc;
    }

    /**
     * Periodic check called by Bukkit scheduler.
     */
    private static void tick() {
        if (getPoolSize() < TARGET_POOL_SIZE) {
            triggerProduce();
        }
    }

    /**
     * Trigger generation of one or more candidate locations asynchronously.
     */
    private static void triggerProduce() {
        if (Bukkit.getServer() == null) {
            return;
        }
        if (getPoolSize() >= TARGET_POOL_SIZE) {
            return;
        }
        if (!IS_PRODUCING.compareAndSet(false, true)) {
            return;
        }

        World world = Game.getPrimaryWorld();
        if (world == null) {
            IS_PRODUCING.set(false);
            return;
        }

        int blockX = RANDOM.nextInt(MAX_COORD_RANGE * 2) - MAX_COORD_RANGE;
        int blockZ = RANDOM.nextInt(MAX_COORD_RANGE * 2) - MAX_COORD_RANGE;
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long chunkKey = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);

        if (!PENDING_CHUNKS.add(chunkKey)) {
            IS_PRODUCING.set(false);
            return;
        }

        // Asynchronously load/generate chunk
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) -> {
            PENDING_CHUNKS.remove(chunkKey);
            IS_PRODUCING.set(false);

            if (error != null || chunk == null) {
                return;
            }

            // Inspect candidate on the main thread safely
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                Location safeLoc = findSafeLandingLocation(world, chunk, blockX, blockZ);
                if (safeLoc != null) {
                    synchronized (RandomTeleportManager.class) {
                        if (POOL.size() < TARGET_POOL_SIZE) {
                            POOL.addLast(safeLoc);
                        }
                    }
                }
            });
        });
    }

    /**
     * Execute random teleport for a player.
     * Prioritizes pre-warmed pool candidates. If pool is depleted, falls back to safe async search.
     */
    public static void executeTeleport(Player player, boolean avoidOcean) {
        Location cached = pollCandidate();
        if (cached != null) {
            // Instant teleport with 0 chunk generation delay
            performTeleport(player, cached, avoidOcean);
            return;
        }

        // Fallback: asynchronous generation if pool was empty
        World world = Game.getPrimaryWorld();
        startFallbackAsync(player, world, avoidOcean, 1, avoidOcean ? 8 : 1);
    }

    private static void startFallbackAsync(Player player, World world, boolean avoidOcean,
                                           int attempt, int maxAttempts) {
        int blockX = RANDOM.nextInt(MAX_COORD_RANGE * 2) - MAX_COORD_RANGE;
        int blockZ = RANDOM.nextInt(MAX_COORD_RANGE * 2) - MAX_COORD_RANGE;

        world.getChunkAtAsync(blockX >> 4, blockZ >> 4, true).whenComplete((chunk, error) -> {
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (!player.isOnline()) return;

                if (error == null && chunk != null) {
                    Location safeLoc = findSafeLandingLocation(world, chunk, blockX, blockZ);
                    if (safeLoc != null) {
                        if (!avoidOcean || !isOcean(safeLoc.getBlock().getBiome())) {
                            performTeleport(player, safeLoc, avoidOcean);
                            return;
                        }
                    }
                }

                if (attempt < maxAttempts) {
                    startFallbackAsync(player, world, avoidOcean, attempt + 1, maxAttempts);
                } else {
                    // Last resort fallback
                    Location highest = world.getHighestBlockAt(blockX, blockZ).getLocation().add(0.5, 1, 0.5);
                    performTeleport(player, highest, avoidOcean);
                }
            });
        });
    }

    /**
     * Inspect a chunk to find a safe solid surface without drowning or hazards.
     */
    private static Location findSafeLandingLocation(World world, Chunk chunk, int targetX, int targetZ) {
        int highestY = world.getHighestBlockYAt(targetX, targetZ);
        if (highestY <= world.getMinHeight() || highestY >= world.getMaxHeight()) {
            return null;
        }

        Material ground = world.getBlockAt(targetX, highestY, targetZ).getType();
        if (ground.isAir() || DANGEROUS_BLOCKS.contains(ground)) {
            return null;
        }
        if (isOcean(world.getBiome(targetX, highestY, targetZ))) {
            return null;
        }

        // Ensure feet and head are air/passable
        Material feet = world.getBlockAt(targetX, highestY + 1, targetZ).getType();
        Material head = world.getBlockAt(targetX, highestY + 2, targetZ).getType();
        if (!feet.isAir() || !head.isAir()) {
            return null;
        }

        return new Location(world, targetX + 0.5, highestY + 1.0, targetZ + 0.5);
    }

    public static boolean isOcean(Biome biome) {
        if (biome == null) return false;
        String key = biome.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
        return key.contains("ocean");
    }

    private static void performTeleport(Player player, Location target, boolean avoidOcean) {
        player.teleport(target);
        String x = String.format("%.1f", target.getX());
        String y = String.format("%.1f", target.getY());
        String z = String.format("%.1f", target.getZ());
        player.sendMessage(Message.NOTICE_TP_SUCCESS.getString(player)
                .replace("%x%", x).replace("%y%", y).replace("%z%", z));
        if (avoidOcean && isOcean(target.getBlock().getBiome())) {
            player.sendMessage(Message.NOTICE_TP_OCEAN.getString(player));
        }
    }
}
