package top.lqsnow.blockracing.managers;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RandomTeleportManagerTest {

    @Test
    public void testPoolLifecycle() {
        RandomTeleportManager.clearPool();
        assertEquals(0, RandomTeleportManager.getPoolSize());
        assertNull(RandomTeleportManager.pollCandidate());
    }
}
