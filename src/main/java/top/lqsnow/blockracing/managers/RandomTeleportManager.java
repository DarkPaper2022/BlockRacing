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
import top.lqsnow.blockracing.utils.CommandUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Implements BlockRacing RTP Design v1.0:
 * - Single pending RTP request per player (D1)
 * - Complete destination view-distance pregeneration (D2)
 * - Center first, then ring outward (D3)
 * - Pre-generate only, no permanent keep-alive tickets (D4)
 * - Best-effort landing verification at generation time (D5)
 * - Centralized fee reservation and idempotent settlement (D8, 07)
 */
public final class RandomTeleportManager {
    private static final Logger LOGGER = Logger.getLogger("BlockRacing");

    public enum RequestReason {
        INITIAL,
        USER
    }

    public enum RequestStatus {
        ACCEPTED,
        RESERVED,
        TELEPORTING,
        SETTLING,
        DONE
    }

    public enum RejectReason {
        BUSY,
        NOT_READY,
        THROTTLED,
        NOT_IN_GAME,
        SPECTATOR,
        NOT_ENOUGH_SCORE
    }

    public record Candidate(
            long candidateId,
            long gameEpoch,
            long generationEpoch,
            UUID worldId,
            Location landingLocation,
            int centerChunkX,
            int centerChunkZ,
            int viewDistanceRadius
    ) {}

    public static final class RtpRequest {
        final UUID requestId = UUID.randomUUID();
        final UUID playerId;
        final RequestReason reason;
        final long gameEpoch;
        final boolean avoidOcean;
        RequestStatus status = RequestStatus.ACCEPTED;
        Candidate candidate;
        boolean freeUsed = false;
        String reservedTeam = null;
        int reservedScore = 0;
        final AtomicBoolean settled = new AtomicBoolean(false);

        RtpRequest(UUID playerId, RequestReason reason, long gameEpoch, boolean avoidOcean) {
            this.playerId = playerId;
            this.reason = reason;
            this.gameEpoch = gameEpoch;
            this.avoidOcean = avoidOcean;
        }
    }

    // --- Epochs & Lifecycle ---
    private static final AtomicLong GAME_EPOCH = new AtomicLong(1);
    private static final AtomicLong GENERATION_EPOCH = new AtomicLong(1);
    private static final AtomicLong CANDIDATE_SEQ = new AtomicLong(1);

    // --- Single Pending Request per Player (D1) ---
    private static final Map<UUID, RtpRequest> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

    // --- Free RTP list (stored by UUID string or playerName for compatibility) ---
    private static final Set<UUID> FREE_RTP_PLAYERS = ConcurrentHashMap.newKeySet();

    // --- Ready Candidates Pool (FIFO) ---
    private static final Deque<Candidate> READY_POOL = new ArrayDeque<>();

    // --- Active Candidate Generation Job ---
    private static final class GenerationJob {
        final long candidateId;
        final long gEpoch;
        final long genEpoch;
        final World world;
        final Location landing;
        final int centerChunkX;
        final int centerChunkZ;
        final int radius;
        final List<long[]> chunkCoords = new ArrayList<>();
        final AtomicInteger completedChunks = new AtomicInteger(0);
        final AtomicBoolean failed = new AtomicBoolean(false);

        GenerationJob(long candidateId, long gEpoch, long genEpoch, World world, Location landing,
                      int centerChunkX, int centerChunkZ, int radius) {
            this.candidateId = candidateId;
            this.gEpoch = gEpoch;
            this.genEpoch = genEpoch;
            this.world = world;
            this.landing = landing;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.radius = radius;
        }
    }

    // --- Budget & Config ---
    private static final int MAX_COORD_RANGE = 10000;
    private static final int TARGET_POOL_SIZE = 24;
    private static final int START_RESERVE = 4;
    private static final int MAX_INFLIGHT_CHUNKS = 4;
    private static final int MAX_ACTIVE_CANDIDATES = 2;
    private static final Random RANDOM = new Random();

    private static final AtomicInteger INFLIGHT_CHUNKS = new AtomicInteger(0);
    private static final Map<Long, GenerationJob> ACTIVE_JOBS = new ConcurrentHashMap<>();
    private static final Set<Long> PENDING_CHUNK_KEYS = ConcurrentHashMap.newKeySet();

    private static BukkitTask warmerTask;

    private static final Set<Material> DANGEROUS_BLOCKS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.VOID_AIR
    );

    private RandomTeleportManager() {}

    // =========================================================================
    // Epoch and Lifecycle Management (08)
    // =========================================================================

    public static long getGameEpoch() {
        return GAME_EPOCH.get();
    }

    public static synchronized void nextGameEpoch() {
        GAME_EPOCH.incrementAndGet();
        GENERATION_EPOCH.incrementAndGet();
        clearAllJobsAndPool();
        ACTIVE_REQUESTS.clear();
        FREE_RTP_PLAYERS.clear();
    }

    public static synchronized void resetGenerationEpoch() {
        GENERATION_EPOCH.incrementAndGet();
        clearAllJobsAndPool();
    }

    private static synchronized void clearAllJobsAndPool() {
        READY_POOL.clear();
        ACTIVE_JOBS.clear();
        PENDING_CHUNK_KEYS.clear();
        // Notice: do NOT reset INFLIGHT_CHUNKS to 0 blindly, let ongoing futures complete and decrement it
    }

    public static synchronized void startWarmer() {
        if (warmerTask != null && !warmerTask.isCancelled()) {
            return;
        }
        warmerTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), RandomTeleportManager::tickWarmer, 20L, 10L);
    }

    public static synchronized void stopWarmer() {
        if (warmerTask != null) {
            warmerTask.cancel();
            warmerTask = null;
        }
        resetGenerationEpoch();
    }

    public static void grantFreeRtp(Player player) {
        if (player != null) {
            FREE_RTP_PLAYERS.add(player.getUniqueId());
        }
    }

    public static boolean hasFreeRtp(Player player) {
        return player != null && FREE_RTP_PLAYERS.contains(player.getUniqueId());
    }

    public static synchronized int getReadyCount() {
        return READY_POOL.size();
    }

    public static synchronized int getStartRequirement(int participatingPlayerCount) {
        return participatingPlayerCount + START_RESERVE;
    }

    public static synchronized boolean isStartThresholdMet(int participatingPlayerCount) {
        return getReadyCount() >= getStartRequirement(participatingPlayerCount);
    }

    // =========================================================================
    // Public Request & Settlement Workflow (06, 07)
    // =========================================================================

    /**
     * Centralized RTP request entry point.
     * Checks eligibility, active request slot, reserves funds/free-quota, and triggers async teleport.
     */
    public static void requestRtp(Player player, RequestReason reason, boolean avoidOcean) {
        if (player == null || !player.isOnline()) return;

        // 1. Check game state & team eligibility
        if (reason == RequestReason.USER) {
            if (Game.getCurrentGameState() != Game.GameState.INGAME) {
                player.sendMessage(Message.NOTICE_GAME_NOT_START.getString(player));
                return;
            }
            String team = Team.getTeam(player);
            if (team.isEmpty()) {
                player.sendMessage(Message.NOTICE_SPECTATOR.getString(player));
                return;
            }
        }

        UUID playerId = player.getUniqueId();
        long currentGEpoch = GAME_EPOCH.get();

        // 2. Check if player has an active pending request (D1)
        RtpRequest request = new RtpRequest(playerId, reason, currentGEpoch, avoidOcean);
        if (ACTIVE_REQUESTS.putIfAbsent(playerId, request) != null) {
            // Already busy
            player.sendMessage("§c您正在进行随机传送，请稍候...");
            return;
        }

        // 3. Obtain a verified READY candidate from the pool
        Candidate candidate = pollReadyCandidate(currentGEpoch);
        if (candidate == null) {
            ACTIVE_REQUESTS.remove(playerId, request);
            player.sendMessage("§e目的地生成准备中，请稍后再试...");
            triggerWarmup();
            return;
        }
        request.candidate = candidate;

        // 4. Reserve payment in main thread (07)
        if (reason == RequestReason.USER) {
            if (FREE_RTP_PLAYERS.remove(playerId)) {
                request.freeUsed = true;
            } else {
                String team = Team.getTeam(player);
                int cost = Setting.getRandomTeleportCost();
                int currentScore = Game.getTeamScore(team);
                if (currentScore < cost) {
                    // Not enough score: rollback candidate and release request
                    returnCandidate(candidate);
                    ACTIVE_REQUESTS.remove(playerId, request);
                    player.sendMessage(Message.NOTICE_NOT_ENOUGH_SCORE.getString(player));
                    return;
                }
                // Reserve funds
                Game.setTeamScore(team, currentScore - cost);
                request.reservedTeam = team;
                request.reservedScore = cost;
                Scoreboard.updateScoreboard();
                GameProgressStore.saveNow();
            }
        }

        request.status = RequestStatus.RESERVED;
        player.closeInventory();

        // 5. Submit teleportAsync (P3, P4, D7)
        request.status = RequestStatus.TELEPORTING;
        Location targetLoc = candidate.landingLocation;

        try {
            CompletableFuture<Boolean> tpFuture = player.teleportAsync(targetLoc);
            tpFuture.whenComplete((success, throwable) -> {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    settleRequest(request, player, Boolean.TRUE.equals(success) && throwable == null);
                });
            });
        } catch (Throwable t) {
            settleRequest(request, player, false);
        }
    }

    /**
     * Idempotent final settlement (07).
     */
    private static void settleRequest(RtpRequest request, Player player, boolean success) {
        if (!request.settled.compareAndSet(false, true)) {
            return;
        }
        request.status = RequestStatus.SETTLING;

        try {
            if (success && player.isOnline() && request.gameEpoch == GAME_EPOCH.get()) {
                // Succeeded: finalize reservation
                if (request.reservedScore > 0 && request.reservedTeam != null) {
                    Message colorMsg = Team.getTeamColorMessage(request.reservedTeam);
                    CommandUtil.sendAll(Message.NOTICE_RANDOM_TP,
                            (viewer, text) -> text
                                    .replace("%score%", String.valueOf(request.reservedScore))
                                    .replace("%player%", colorMsg.getString(viewer) + player.getName()));
                }
                String x = String.format("%.1f", request.candidate.landingLocation.getX());
                String y = String.format("%.1f", request.candidate.landingLocation.getY());
                String z = String.format("%.1f", request.candidate.landingLocation.getZ());
                player.sendMessage(Message.NOTICE_TP_SUCCESS.getString(player)
                        .replace("%x%", x).replace("%y%", y).replace("%z%", z));
                if (request.avoidOcean && isOcean(request.candidate.landingLocation.getBlock().getBiome())) {
                    player.sendMessage(Message.NOTICE_TP_OCEAN.getString(player));
                }
            } else {
                // Failed or cancelled: refund reservation exactly once
                if (request.freeUsed) {
                    FREE_RTP_PLAYERS.add(request.playerId);
                } else if (request.reservedScore > 0 && request.reservedTeam != null) {
                    int currentScore = Game.getTeamScore(request.reservedTeam);
                    Game.setTeamScore(request.reservedTeam, currentScore + request.reservedScore);
                    Scoreboard.updateScoreboard();
                    GameProgressStore.saveNow();
                }
                if (player.isOnline()) {
                    player.sendMessage("§c传送未成功完成，积分或免费次数已返还。");
                }
            }
        } finally {
            request.status = RequestStatus.DONE;
            ACTIVE_REQUESTS.remove(request.playerId, request);
            triggerWarmup();
        }
    }

    public static void onPlayerQuit(Player player) {
        if (player == null) return;
        UUID pid = player.getUniqueId();
        RtpRequest req = ACTIVE_REQUESTS.get(pid);
        if (req != null && req.status == RequestStatus.RESERVED) {
            // Cancel before teleport if possible
            settleRequest(req, player, false);
        }
    }

    // =========================================================================
    // Candidate Pool (D6)
    // =========================================================================

    private static synchronized Candidate pollReadyCandidate(long currentGEpoch) {
        while (!READY_POOL.isEmpty()) {
            Candidate c = READY_POOL.pollFirst();
            if (c.gameEpoch == currentGEpoch && c.generationEpoch == GENERATION_EPOCH.get()) {
                return c;
            }
        }
        return null;
    }

    private static synchronized void returnCandidate(Candidate candidate) {
        if (candidate != null && candidate.gameEpoch == GAME_EPOCH.get()
                && candidate.generationEpoch == GENERATION_EPOCH.get()) {
            READY_POOL.addFirst(candidate);
        }
    }

    // =========================================================================
    // Background Warmer & View-Distance Pregeneration (04, 05, D2, D3, D4)
    // =========================================================================

    private static void tickWarmer() {
        if (Bukkit.getServer() == null) return;
        if (READY_POOL.size() + ACTIVE_JOBS.size() < TARGET_POOL_SIZE) {
            triggerWarmup();
        }
        advanceActiveJobs();
    }

    private static void triggerWarmup() {
        if (ACTIVE_JOBS.size() >= MAX_ACTIVE_CANDIDATES) return;
        if (READY_POOL.size() + ACTIVE_JOBS.size() >= TARGET_POOL_SIZE) return;

        World world = Game.getPrimaryWorld();
        if (world == null) return;

        long cId = CANDIDATE_SEQ.incrementAndGet();
        long gEpoch = GAME_EPOCH.get();
        long genEpoch = GENERATION_EPOCH.get();

        // Sample center coordinates
        int blockX = RANDOM.nextInt(MAX_COORD_RANGE * 2) - MAX_COORD_RANGE;
        int blockZ = RANDOM.nextInt(MAX_COORD_RANGE * 2) - MAX_COORD_RANGE;
        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;

        long centerKey = (((long) centerChunkX) << 32) | (centerChunkZ & 0xFFFFFFFFL);
        if (!PENDING_CHUNK_KEYS.add(centerKey)) {
            return;
        }

        INFLIGHT_CHUNKS.incrementAndGet();
        // 1. Center chunk check first (D3)
        world.getChunkAtAsync(centerChunkX, centerChunkZ, true, false).whenComplete((chunk, err) -> {
            PENDING_CHUNK_KEYS.remove(centerKey);
            INFLIGHT_CHUNKS.decrementAndGet();

            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (gEpoch != GAME_EPOCH.get() || genEpoch != GENERATION_EPOCH.get()) return;
                if (err != null || chunk == null) return;

                Location landing = findSafeLandingLocation(world, blockX, blockZ);
                if (landing == null) {
                    // Center check failed: discard immediately (D3)
                    return;
                }

                // Center valid! Create full view-distance expanding job (05)
                int radius = world.getViewDistance();
                GenerationJob job = new GenerationJob(cId, gEpoch, genEpoch, world, landing, centerChunkX, centerChunkZ, radius);

                // Build square coverage list in outward concentric rings (5.1, 5.2)
                buildConcentricRingCoords(centerChunkX, centerChunkZ, radius, job.chunkCoords);
                // Center chunk is already loaded/generated
                job.completedChunks.incrementAndGet();

                ACTIVE_JOBS.put(cId, job);
                advanceActiveJobs();
            });
        });
    }

    private static void buildConcentricRingCoords(int cx, int cz, int r, List<long[]> outList) {
        // Ring 0: center chunk
        outList.add(new long[]{cx, cz});
        // Rings 1 to r
        for (int d = 1; d <= r; d++) {
            for (int x = cx - d; x <= cx + d; x++) {
                outList.add(new long[]{x, cz - d});
                outList.add(new long[]{x, cz + d});
            }
            for (int z = cz - d + 1; z <= cz + d - 1; z++) {
                outList.add(new long[]{cx - d, z});
                outList.add(new long[]{cx + d, z});
            }
        }
    }

    private static void advanceActiveJobs() {
        if (ACTIVE_JOBS.isEmpty()) return;

        for (GenerationJob job : ACTIVE_JOBS.values()) {
            if (job.failed.get() || job.gEpoch != GAME_EPOCH.get() || job.genEpoch != GENERATION_EPOCH.get()) {
                ACTIVE_JOBS.remove(job.candidateId);
                continue;
            }

            int totalRequired = job.chunkCoords.size();
            while (INFLIGHT_CHUNKS.get() < MAX_INFLIGHT_CHUNKS) {
                int nextIndex = job.completedChunks.get();
                if (nextIndex >= totalRequired) {
                    // All chunks in coverage finished! Publish as READY (D6, 05)
                    ACTIVE_JOBS.remove(job.candidateId);
                    Candidate candidate = new Candidate(
                            job.candidateId, job.gEpoch, job.genEpoch,
                            job.world.getUID(), job.landing,
                            job.centerChunkX, job.centerChunkZ, job.radius
                    );
                    synchronized (RandomTeleportManager.class) {
                        READY_POOL.addLast(candidate);
                    }
                    break;
                }

                long[] coord = job.chunkCoords.get(nextIndex);
                int chunkX = (int) coord[0];
                int chunkZ = (int) coord[1];
                long key = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);

                if (!PENDING_CHUNK_KEYS.add(key)) {
                    // Already in flight by another request; count as progressed
                    job.completedChunks.incrementAndGet();
                    continue;
                }

                INFLIGHT_CHUNKS.incrementAndGet();
                job.world.getChunkAtAsync(chunkX, chunkZ, true, false).whenComplete((ch, ex) -> {
                    PENDING_CHUNK_KEYS.remove(key);
                    INFLIGHT_CHUNKS.decrementAndGet();

                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        if (ex != null) {
                            job.failed.set(true);
                            ACTIVE_JOBS.remove(job.candidateId);
                            return;
                        }
                        job.completedChunks.incrementAndGet();
                        advanceActiveJobs();
                    });
                });
            }
        }
    }

    // =========================================================================
    // Safe Landing Location Validation (04)
    // =========================================================================

    private static Location findSafeLandingLocation(World world, int targetX, int targetZ) {
        int highestY = world.getHighestBlockYAt(targetX, targetZ);
        if (highestY <= world.getMinHeight() || highestY >= world.getMaxHeight() - 2) {
            return null;
        }

        Material ground = world.getBlockAt(targetX, highestY, targetZ).getType();
        if (ground.isAir() || DANGEROUS_BLOCKS.contains(ground)) {
            return null;
        }
        if (isOcean(world.getBiome(targetX, highestY, targetZ))) {
            return null;
        }

        Material feet = world.getBlockAt(targetX, highestY + 1, targetZ).getType();
        Material head = world.getBlockAt(targetX, highestY + 2, targetZ).getType();
        if (!feet.isAir() || !head.isAir()) {
            return null;
        }

        return new Location(world, targetX + 0.5, highestY + 1.0, targetZ + 0.5);
    }

    public static boolean isOcean(Biome biome) {
        if (biome == null) return false;
        String key = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        return key.contains("ocean");
    }

    // Compatibility facade methods
    public static synchronized Location pollCandidate() {
        Candidate c = pollReadyCandidate(GAME_EPOCH.get());
        return c != null ? c.landingLocation : null;
    }

    public static synchronized int getPoolSize() {
        return READY_POOL.size();
    }

    public static synchronized List<Location> getPoolSnapshot() {
        List<Location> list = new ArrayList<>();
        for (Candidate c : READY_POOL) {
            list.add(c.landingLocation);
        }
        return list;
    }

    public static synchronized void clearPool() {
        clearAllJobsAndPool();
    }
}
