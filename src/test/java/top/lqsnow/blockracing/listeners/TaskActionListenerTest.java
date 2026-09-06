package top.lqsnow.blockracing.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskActionListenerTest {
    @Test void onlyActualCauldronWashReasonsQualify() {
        for (ChangeReason reason : ChangeReason.values()) {
            assertEquals(java.util.Set.of(ChangeReason.ARMOR_WASH, ChangeReason.BANNER_WASH, ChangeReason.SHULKER_WASH).contains(reason),
                    TaskActionListener.isCleaning(reason), reason.name());
        }
    }
    @Test void noOpPreviewPlacementAndCreativeCloneAreNotCraftedResults() {
        assertFalse(TaskActionListener.takesResult(InventoryAction.NOTHING));
        assertFalse(TaskActionListener.takesResult(InventoryAction.CLONE_STACK));
        assertFalse(TaskActionListener.takesResult(InventoryAction.PLACE_ALL));
        assertTrue(TaskActionListener.takesResult(InventoryAction.PICKUP_ALL));
        assertTrue(TaskActionListener.takesResult(InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertTrue(TaskActionListener.takesResult(InventoryAction.HOTBAR_SWAP));
    }
    @Test void allActionHandlersObserveFinalUncancelledEvents() {
        int count = 0;
        for (var method : TaskActionListener.class.getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null) continue;
            count++;
            assertEquals(EventPriority.MONITOR, handler.priority());
            assertTrue(handler.ignoreCancelled());
        }
        assertEquals(7, count);
    }
    @Test void loomRequiresConsumedDyeAndAnActuallyReceivedOrDroppedResult() {
        assertFalse(TaskActionListener.completedLoomTake(2, 2, 0, 0, false)); // preview/full inventory
        assertFalse(TaskActionListener.completedLoomTake(2, 0, 0, 0, false)); // GUI closed, inputs returned
        assertFalse(TaskActionListener.completedLoomTake(2, 2, 0, 1, false)); // unrelated item transfer
        assertTrue(TaskActionListener.completedLoomTake(2, 1, 3, 4, false));
        assertTrue(TaskActionListener.completedLoomTake(2, 1, 0, 0, true));
    }
}
