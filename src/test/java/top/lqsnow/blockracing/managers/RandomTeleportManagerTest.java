package top.lqsnow.blockracing.managers;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RandomTeleportManagerTest {

    @BeforeEach
    public void setUp() {
        RandomTeleportManager.clearPool();
    }

    @AfterEach
    public void clearConfigurationProperties() {
        System.clearProperty(RandomTeleportManager.MAX_INFLIGHT_PROPERTY);
        System.clearProperty(RandomTeleportManager.TARGET_POOL_SIZE_PROPERTY);
    }

    @Test
    public void testPoolLifecycle() {
        assertEquals(0, RandomTeleportManager.getPoolSize());
        assertEquals(0, RandomTeleportManager.getReadyCount());
        assertNull(RandomTeleportManager.pollCandidate());
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

    @Test
    public void positiveIntegerPropertyUsesDefaultAndConfiguredValue() {
        assertEquals(4, RandomTeleportManager.positiveIntProperty(
                RandomTeleportManager.MAX_INFLIGHT_PROPERTY, 4));

        System.setProperty(RandomTeleportManager.MAX_INFLIGHT_PROPERTY, "7");
        assertEquals(7, RandomTeleportManager.positiveIntProperty(
                RandomTeleportManager.MAX_INFLIGHT_PROPERTY, 4));
    }

    @Test
    public void positiveIntegerPropertyRejectsInvalidValues() {
        System.setProperty(RandomTeleportManager.MAX_INFLIGHT_PROPERTY, "0");
        assertThrows(IllegalArgumentException.class, () -> RandomTeleportManager.positiveIntProperty(
                RandomTeleportManager.MAX_INFLIGHT_PROPERTY, 4));

        System.setProperty(RandomTeleportManager.MAX_INFLIGHT_PROPERTY, "many");
        assertThrows(IllegalArgumentException.class, () -> RandomTeleportManager.positiveIntProperty(
                RandomTeleportManager.MAX_INFLIGHT_PROPERTY, 4));
    }

    @Test
    public void nonNegativePropertyAllowsDisablingTheTestPool() {
        System.setProperty(RandomTeleportManager.TARGET_POOL_SIZE_PROPERTY, "0");
        assertEquals(0, RandomTeleportManager.nonNegativeIntProperty(
                RandomTeleportManager.TARGET_POOL_SIZE_PROPERTY, 24));

        System.setProperty(RandomTeleportManager.TARGET_POOL_SIZE_PROPERTY, "-1");
        assertThrows(IllegalArgumentException.class, () -> RandomTeleportManager.nonNegativeIntProperty(
                RandomTeleportManager.TARGET_POOL_SIZE_PROPERTY, 24));
    }
}
