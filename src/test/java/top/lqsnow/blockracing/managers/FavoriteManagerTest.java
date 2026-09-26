package top.lqsnow.blockracing.managers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FavoriteManagerTest {

    @BeforeEach
    void setUp() {
        FavoriteManager.reset();
        Setting.setMaxFavoriteTargets(5);
        Block.redTeamRemainingBlocks = new ArrayList<>(List.of("APPLE", "BREAD", "DIAMOND", "IRON_INGOT", "GOLD_INGOT", "EMERALD"));
        Block.blueTeamRemainingBlocks = new ArrayList<>(List.of("APPLE", "BREAD", "DIAMOND", "IRON_INGOT", "GOLD_INGOT", "EMERALD"));
    }

    @Test
    void testRestoreAndLimit() {
        FavoriteManager.restore(List.of("APPLE", "BREAD", "DIAMOND", "IRON_INGOT", "GOLD_INGOT", "EMERALD"), List.of("BREAD"));
        List<String> red = FavoriteManager.getFavorites("red");
        assertEquals(5, red.size());
        assertTrue(red.contains("APPLE"));
        assertFalse(red.contains("EMERALD")); // Exceeded default max 5

        List<String> blue = FavoriteManager.getFavorites("blue");
        assertEquals(1, blue.size());
        assertTrue(blue.contains("BREAD"));
    }

    @Test
    void testTaskCompletedRemovesFavorite() {
        FavoriteManager.restore(List.of("APPLE", "DIAMOND"), List.of("DIAMOND"));
        assertTrue(FavoriteManager.isFavorited("red", "DIAMOND"));
        assertTrue(FavoriteManager.isFavorited("blue", "DIAMOND"));

        // Red completes DIAMOND -> removes from both teams if mutual
        FavoriteManager.onTaskCompletedOrRemoved("red", "DIAMOND");
        assertFalse(FavoriteManager.isFavorited("red", "DIAMOND"));
        assertTrue(FavoriteManager.isFavorited("blue", "DIAMOND"));

        FavoriteManager.onTaskCompletedOrRemoved("blue", "DIAMOND");
        assertFalse(FavoriteManager.isFavorited("blue", "DIAMOND"));
    }
}
