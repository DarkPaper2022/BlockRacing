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

    @Test void milkCauldronAndCompostEventsRecordActionsOnlyWhenConditionsHold() throws Exception {
        var oldState = top.lqsnow.blockracing.managers.Game.currentGameState;
        var oldRed = java.util.List.copyOf(top.lqsnow.blockracing.managers.Team.redTeamPlayers);
        try {
            top.lqsnow.blockracing.managers.Goal.clearDefinitions();
            top.lqsnow.blockracing.managers.Goal.resetProgress();
            top.lqsnow.blockracing.managers.Goal.registerDefinition("USE_MILK", "Milk", "actions:MILK_CLEANSE");
            top.lqsnow.blockracing.managers.Goal.registerDefinition("USE_CAULDRON", "Cauldron", "actions:CAULDRON_CLEAN");
            top.lqsnow.blockracing.managers.Goal.registerDefinition("USE_COMPOSTER", "Composter", "actions:COMPOST_FILL,COMPOST_COLLECT");
            top.lqsnow.blockracing.managers.Game.currentGameState = top.lqsnow.blockracing.managers.Game.GameState.INGAME;
            top.lqsnow.blockracing.managers.Team.redTeamPlayers.clear();
            top.lqsnow.blockracing.managers.Team.redTeamPlayers.add("Alice");
            top.lqsnow.blockracing.managers.Team.redTeamPlayers.add("Bob");

            org.bukkit.entity.Player alice = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.entity.Player.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.entity.Player.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getName" -> "Alice";
                        case "getUniqueId" -> java.util.UUID.fromString("00000000-0000-0000-0000-000000000011");
                        default -> null;
                    });
            org.bukkit.entity.Player bob = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.entity.Player.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.entity.Player.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getName" -> "Bob";
                        case "getUniqueId" -> java.util.UUID.fromString("00000000-0000-0000-0000-000000000012");
                        default -> null;
                    });

            TaskActionListener listener = new TaskActionListener();
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            org.bukkit.potion.PotionEffect dummyEffect = (org.bukkit.potion.PotionEffect) unsafe.allocateInstance(
                    org.bukkit.potion.PotionEffect.class);

            // Non-milk cause must not count
            listener.onMilkClear(new org.bukkit.event.entity.EntityPotionEffectEvent(
                    alice, dummyEffect, null, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.EXPIRATION,
                    org.bukkit.event.entity.EntityPotionEffectEvent.Action.REMOVED, false));
            assertEquals(0, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_MILK", alice)[0]);

            // Real milk cleanse counts
            listener.onMilkClear(new org.bukkit.event.entity.EntityPotionEffectEvent(
                    alice, dummyEffect, null, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.MILK,
                    org.bukkit.event.entity.EntityPotionEffectEvent.Action.CLEARED, false));
            assertEquals(1, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_MILK", alice)[0]);

            // Cauldron: non-lowering level or empty cauldron must not count
            org.bukkit.block.data.Levelled level3 = (org.bukkit.block.data.Levelled) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.block.data.Levelled.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.block.data.Levelled.class},
                    (p2, m2, a2) -> "getLevel".equals(m2.getName()) ? 3 : null);
            org.bukkit.block.data.Levelled level2 = (org.bukkit.block.data.Levelled) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.block.data.Levelled.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.block.data.Levelled.class},
                    (p2, m2, a2) -> "getLevel".equals(m2.getName()) ? 2 : null);
            org.bukkit.block.Block waterCauldron = (org.bukkit.block.Block) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.block.Block.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.block.Block.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getType" -> org.bukkit.Material.WATER_CAULDRON;
                        case "getBlockData" -> level3;
                        default -> null;
                    });
            org.bukkit.block.BlockState newState2 = (org.bukkit.block.BlockState) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.block.BlockState.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.block.BlockState.class},
                    (p, m, a) -> "getBlockData".equals(m.getName()) ? level2 : null);

            listener.onCauldronClean(new org.bukkit.event.block.CauldronLevelChangeEvent(
                    waterCauldron, alice, ChangeReason.BOTTLE_FILL, newState2));
            assertEquals(0, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_CAULDRON", alice)[0]);

            listener.onCauldronClean(new org.bukkit.event.block.CauldronLevelChangeEvent(
                    waterCauldron, alice, ChangeReason.BANNER_WASH, newState2));
            assertEquals(1, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_CAULDRON", alice)[0]);

            // Composter team cooperation: Alice fills, Bob collects tagged bone meal
            top.lqsnow.blockracing.managers.Goal.recordAction(alice, top.lqsnow.blockracing.managers.Goal.TaskAction.COMPOST_FILL);
            assertEquals(1, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_COMPOSTER", alice)[0]);
            assertEquals(2, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_COMPOSTER", alice)[1]);

            java.util.Map<org.bukkit.NamespacedKey, String> pdcMap = new java.util.HashMap<>();
            org.bukkit.persistence.PersistentDataContainer pdc = (org.bukkit.persistence.PersistentDataContainer) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.persistence.PersistentDataContainer.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.persistence.PersistentDataContainer.class},
                    (p, m, a) -> {
                        if ("get".equals(m.getName())) return pdcMap.get(a[0]);
                        if ("set".equals(m.getName())) { pdcMap.put((org.bukkit.NamespacedKey) a[0], (String) a[2]); return null; }
                        return null;
                    });
            org.bukkit.inventory.ItemStack boneMealStack = new org.bukkit.inventory.ItemStack() {
                @Override public org.bukkit.Material getType() { return org.bukkit.Material.BONE_MEAL; }
                @Override public int getAmount() { return 1; }
            };
            org.bukkit.entity.Item boneMealDrop = (org.bukkit.entity.Item) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.entity.Item.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.entity.Item.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getItemStack" -> boneMealStack;
                        case "getPersistentDataContainer" -> pdc;
                        default -> null;
                    });
            // Untagged bone meal pickup must not count
            listener.onCompostPickup(new org.bukkit.event.entity.EntityPickupItemEvent(bob, boneMealDrop, 0));
            assertEquals(1, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_COMPOSTER", bob)[0]);

            // Tagged with current epoch counts and completes 2/2 across Alice + Bob
            pdcMap.put(new org.bukkit.NamespacedKey("blockracing", "compost_output_round"),
                    top.lqsnow.blockracing.managers.Goal.progressEpoch());
            listener.onCompostPickup(new org.bukkit.event.entity.EntityPickupItemEvent(bob, boneMealDrop, 0));
            assertEquals(2, top.lqsnow.blockracing.managers.Goal.getBoardProgress("USE_COMPOSTER", bob)[0]);
        } finally {
            top.lqsnow.blockracing.managers.Game.currentGameState = oldState;
            top.lqsnow.blockracing.managers.Team.redTeamPlayers.clear();
            top.lqsnow.blockracing.managers.Team.redTeamPlayers.addAll(oldRed);
        }
    }
}
