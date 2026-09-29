package top.lqsnow.blockracing.managers;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RandomTeleportManagerTest {

    @BeforeEach
    public void setUp() {
        RandomTeleportManager.clearPool();
    }

    @Test
    public void testPoolLifecycle() {
        assertEquals(0, RandomTeleportManager.getPoolSize());
        assertEquals(0, RandomTeleportManager.getReadyCount());
        assertNull(RandomTeleportManager.pollCandidate());
    }

    @Test
    public void onlyInitialTeleportWaitsForCandidate() {
        assertTrue(RandomTeleportManager.waitsForCandidate(RandomTeleportManager.RequestReason.INITIAL));
        assertFalse(RandomTeleportManager.waitsForCandidate(RandomTeleportManager.RequestReason.USER));
    }

    @Test
    public void testEpochAdvancement() {
        long epochBefore = RandomTeleportManager.getGameEpoch();
        RandomTeleportManager.nextGameEpoch();
        long epochAfter = RandomTeleportManager.getGameEpoch();
        assertEquals(epochBefore + 1, epochAfter);
        assertEquals(0, RandomTeleportManager.getReadyCount());
    }

    @Test
    public void fullCoverageIsNotReadyWhileFinalAsyncChunksAreInFlight() {
        assertFalse(RandomTeleportManager.isCoverageComplete(625, 621, 4));
        assertFalse(RandomTeleportManager.isCoverageComplete(625, 625, 1));
        assertTrue(RandomTeleportManager.isCoverageComplete(625, 625, 0));
    }
}
