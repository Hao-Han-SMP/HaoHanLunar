package vn.haohan.lunar.core.loot;

import vn.haohan.lunar.api.system.loot.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropTableEngineTest {

    private HaoHanItemBridge itemBridge;

    @BeforeEach
    void setUp() {
        itemBridge = HaoHanItemBridge.get();
        itemBridge.setItemStackFactory(TestItemStack::new);
        // Setup mock custom item provider for tests
        itemBridge.setCustomResolver((id, amount) -> {
            if ("haohan:aero_compound".equalsIgnoreCase(id)) {
                return Optional.of(new TestItemStack(Material.PAPER, amount));
            }
            if ("haohan:steel_ingot".equalsIgnoreCase(id)) {
                return Optional.of(new TestItemStack(Material.IRON_INGOT, amount));
            }
            return Optional.empty();
        });
    }

    @Test
    void dropEntryValidatesBoundsAndClampsChance() {
        DropEntry entry = new DropEntry("diamond", 1.5, 5, 2);
        assertEquals(1.0, entry.chance());
        assertEquals(5, entry.minAmount());
        assertEquals(5, entry.maxAmount()); // maxAmount clamped to minAmount

        assertThrows(IllegalArgumentException.class, () -> new DropEntry("", 0.5, 1, 1));
    }

    @Test
    void dropEntryDeterministicWithSeededRandom() {
        DropEntry entry = new DropEntry("diamond", 0.5, 1, 4);
        Random seededRandom1 = new Random(12345);
        Random seededRandom2 = new Random(12345);

        boolean drop1 = entry.shouldDrop(null, seededRandom1, null);
        boolean drop2 = entry.shouldDrop(null, seededRandom2, null);
        assertEquals(drop1, drop2);

        int amount1 = entry.rollAmount(null, seededRandom1);
        int amount2 = entry.rollAmount(null, seededRandom2);
        assertEquals(amount1, amount2);
    }

    @Test
    void dropEntryRespectsConditions() {
        ConditionRegistry conditions = new ConditionRegistry();
        var tagCondition = new ConditionRegistry.ConditionCall("tag", Map.of("value", "killer_vip"));
        DropEntry entry = new DropEntry("haohan:aero_compound", 1.0, 1, 1, List.of(tagCondition));

        World world = mockWorld("world");
        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, Set.of());
        LivingEntity killerWithoutTag = mockLivingEntity(world, 0, 64, 0, Set.of("normal"));
        LivingEntity killerWithTag = mockLivingEntity(world, 0, 64, 0, Set.of("killer_vip"));

        ActiveLunarMob mob = activeMob(caster, "lunar_warden");
        DropMetadata metaWithout = DropMetadata.of(mob, killerWithoutTag, caster.getLocation());
        DropMetadata metaWith = DropMetadata.of(mob, killerWithTag, caster.getLocation());

        Random random = new Random(42);
        assertFalse(entry.shouldDrop(metaWithout, random, conditions));
        assertTrue(entry.shouldDrop(metaWith, random, conditions));
    }

    @Test
    void independentDropTableRollsAllGuaranteedItems() {
        DropEntry entry1 = new DropEntry("haohan:aero_compound", 1.0, 2, 2);
        DropEntry entry2 = new DropEntry("haohan:steel_ingot", 1.0, 3, 3);
        DropTableDefinition table = new DropTableDefinition("boss_drops", List.of(entry1, entry2));

        World world = mockWorld("world");
        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, Set.of());
        ActiveLunarMob mob = activeMob(caster, "boss");
        DropMetadata metadata = DropMetadata.of(mob, null, caster.getLocation());

        List<ItemStack> items = table.roll(metadata, new Random(1), itemBridge, null);
        assertEquals(2, items.size());
        assertEquals(2, items.get(0).getAmount());
        assertEquals(3, items.get(1).getAmount());
    }

    @Test
    void weightedDropTablePicksSingleWinningItem() {
        DropEntry common = new DropEntry("haohan:steel_ingot", 10.0, 1, 1);
        DropEntry rare = new DropEntry("haohan:aero_compound", 1.0, 1, 1);
        DropTableDefinition table = new DropTableDefinition("weighted_box",
                DropTableDefinition.RollMode.WEIGHTED, List.of(common, rare), 1);

        World world = mockWorld("world");
        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, Set.of());
        ActiveLunarMob mob = activeMob(caster, "box");
        DropMetadata metadata = DropMetadata.of(mob, null, caster.getLocation());

        List<ItemStack> items = table.roll(metadata, new Random(100), itemBridge, null);
        assertEquals(1, items.size());
    }

    @Test
    void dropManagerHandlesDeathDropsAndSpawnsIntoWorld() {
        MobDefinitionRegistry definitions = new MobDefinitionRegistry();
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), "warden_drops", Set.of());
        definitions.register(mobDef);

        LunarMobManager mobManager = new LunarMobManager();
        List<ItemStack> spawnedInWorld = new ArrayList<>();
        World world = mockWorldWithDrops("lunar", spawnedInWorld);
        LivingEntity entity = mockLivingEntity(world, 10, 64, -10, Set.of());
        ActiveLunarMob activeMob = new ActiveLunarMob(entity, mobDef, new LunarMobIdentity("warden", "1"));
        mobManager.register(activeMob);

        DropManager dropManager = new DropManager(definitions, mobManager, itemBridge, new ConditionRegistry());
        DropTableDefinition dropTable = new DropTableDefinition("warden_drops",
                List.of(new DropEntry("haohan:aero_compound", 1.0, 5, 5)));
        dropManager.register(dropTable);

        List<ItemStack> dropped = dropManager.handleDeathDrops(activeMob, null, entity.getLocation(), new Random(42));
        assertEquals(1, dropped.size());
        assertEquals(5, dropped.getFirst().getAmount());
        assertEquals(1, spawnedInWorld.size());
    }

    @Test
    void fallbackToVanillaMaterialWorksSafely() {
        HaoHanItemBridge bridge = HaoHanItemBridge.get();
        // Clear custom resolver to test fallback
        bridge.setCustomResolver(null);

        // Vanilla material
        Optional<ItemStack> diamond = bridge.createItemStack("DIAMOND", 3);
        assertTrue(diamond.isPresent());
        assertEquals(3, diamond.get().getAmount());
        assertEquals(Material.DIAMOND, diamond.get().getType());

        // Namespaced vanilla
        Optional<ItemStack> iron = bridge.createItemStack("minecraft:iron_ingot", 2);
        assertTrue(iron.isPresent());
        assertEquals(2, iron.get().getAmount());
        assertEquals(Material.IRON_INGOT, iron.get().getType());

        // Invalid item
        Optional<ItemStack> invalid = bridge.createItemStack("non_existent_item_xyz", 1);
        assertFalse(invalid.isPresent());
    }

    // --- Helpers ---

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

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    private static ActiveLunarMob activeMob(LivingEntity entity, String id) {
        MobDefinition definition = new MobDefinition(new MobDefinitionId(id), EntityType.IRON_GOLEM,
                "Mob", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity(id, "1"));
    }

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static World mockWorldWithDrops(String name, List<ItemStack> droppedList) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "dropItemNaturally" -> {
                        if (args.length > 1 && args[1] instanceof ItemStack stack) {
                            droppedList.add(stack);
                        }
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LivingEntity mockLivingEntity(World world, double x, double y, double z, Set<String> tags) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "getScoreboardTags" -> tags;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getHealth" -> 20.0;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
