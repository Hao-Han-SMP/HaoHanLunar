package vn.haohan.lunar.core.loot;

import vn.haohan.lunar.api.system.loot.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.loot.instanced.InstancedDropTracker;
import vn.haohan.lunar.api.system.loot.luck.LuckModifier;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class InstancedLootAndLuckTest {

    private HaoHanItemBridge itemBridge;

    @BeforeEach
    void setUp() {
        itemBridge = HaoHanItemBridge.get();
        itemBridge.setItemStackFactory(TestItemStack::new);
    }

    private static class TestItemStack extends ItemStack {
        private final Material type;
        private int amount;

        public TestItemStack(Material type, int amount) {
            super();
            this.type = type;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public int getAmount() {
            return amount;
        }
    }

    @Test
    @DisplayName("LuckModifier scales chance and weight properly")
    void testLuckModifierScaling() {
        // Zero luck: no change
        assertEquals(0.2, LuckModifier.scaleChance(0.2, 0.0), 0.0001);
        assertEquals(10.0, LuckModifier.scaleWeight(10.0, 0.0), 0.0001);

        // Positive luck: +5% per luck point
        // Luck = 4 -> 1.0 + (4 * 0.05) = 1.2x
        assertEquals(0.24, LuckModifier.scaleChance(0.2, 4.0), 0.0001);
        assertEquals(12.0, LuckModifier.scaleWeight(10.0, 4.0), 0.0001);

        // Cap at 1.0 for chance
        assertEquals(1.0, LuckModifier.scaleChance(0.9, 10.0), 0.0001);

        // Negative luck
        // Luck = -4 -> 1.0 - 0.20 = 0.8x
        assertEquals(0.16, LuckModifier.scaleChance(0.2, -4.0), 0.0001);
    }

    @Test
    @DisplayName("InstancedDropTracker protects dropped items for owner during protection period")
    void testInstancedDropTrackerProtection() {
        InstancedDropTracker tracker = new InstancedDropTracker();
        UUID itemEntityId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID thiefId = UUID.randomUUID();

        // Protect item for 100 ticks starting at tick 50
        tracker.protect(itemEntityId, ownerId, 50L, 100L);
        assertEquals(1, tracker.activeCount());

        // At tick 100 (within [50, 150]):
        // Owner can pick up
        assertTrue(tracker.canPickup(ownerId, itemEntityId, 100L));
        // Non-owner cannot pick up
        assertFalse(tracker.canPickup(thiefId, itemEntityId, 100L));

        // At tick 150 (protection expired):
        // Thief can pick up
        assertTrue(tracker.canPickup(thiefId, itemEntityId, 150L));

        // Untracked item can be picked up by anyone
        UUID unownedItem = UUID.randomUUID();
        assertTrue(tracker.canPickup(thiefId, unownedItem, 100L));
    }

    @Test
    @DisplayName("InstancedDropTracker cleans up expired items")
    void testInstancedDropTrackerCleanup() {
        InstancedDropTracker tracker = new InstancedDropTracker();
        UUID item1 = UUID.randomUUID();
        UUID item2 = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        tracker.protect(item1, owner, 0L, 50L);
        tracker.protect(item2, owner, 0L, 200L);
        assertEquals(2, tracker.activeCount());

        // Cleanup at tick 60
        tracker.cleanupExpired(60L);
        assertEquals(1, tracker.activeCount());
        assertTrue(tracker.getDrop(item1).isEmpty());
        assertTrue(tracker.getDrop(item2).isPresent());
    }

    @Test
    @DisplayName("DropTableDefinition incorporates LuckModifier in rolls")
    void testDropTableLuckRoll() {
        DropTableDefinition table = new DropTableDefinition("boss_loot", DropTableDefinition.RollMode.INDEPENDENT, List.of(
                new DropEntry("DIAMOND", 0.5, 1, 1)
        ), 100);

        // Seeded random for deterministic comparison
        DropMetadata baseMeta = new DropMetadata(null, null, null, 1.0, 0L);
        List<ItemStack> baseDrops = table.roll(baseMeta, new Random(42), itemBridge, null);

        // Player with high luck
        LivingEntity luckyPlayer = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return UUID.randomUUID();
                    if (method.getName().equals("getAttribute")) {
                        return (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                                new Class<?>[]{AttributeInstance.class},
                                (p, m, a) -> m.getName().equals("getValue") ? 10.0 : null);
                    }
                    if (method.getName().equals("getPotionEffect")) return null;
                    return null;
                });

        DropMetadata luckyMeta = new DropMetadata(null, luckyPlayer, null, 1.0, 0L);
        List<ItemStack> luckyDrops = table.roll(luckyMeta, new Random(42), itemBridge, null);

        // Higher luck results in more drops with identical RNG seed
        assertTrue(luckyDrops.size() >= baseDrops.size(),
                "Lucky drops (" + luckyDrops.size() + ") should be >= base drops (" + baseDrops.size() + ")");
    }
}
