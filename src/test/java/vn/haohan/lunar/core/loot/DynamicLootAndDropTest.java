package vn.haohan.lunar.core.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.mob.equipment.EquipmentApplier;
import vn.haohan.lunar.api.system.mob.equipment.EquipmentSlot;
import vn.haohan.lunar.api.system.mob.equipment.ItemProviderRegistry;
import vn.haohan.lunar.api.system.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.system.mob.scaling.LevelScalingDefinition;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.loot.*;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicLootAndDropTest {

    private HaoHanItemBridge itemBridge;
    private ConditionRegistry conditionRegistry;

    private static class MockItemStack extends ItemStack {
        private final Material material;
        private int amount;

        public MockItemStack(Material material, int amount) {
            super();
            this.material = material;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return material;
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

    @BeforeEach
    void setUp() {
        itemBridge = HaoHanItemBridge.get();
        itemBridge.setItemStackFactory(MockItemStack::new);
        itemBridge.setCustomResolver((id, amount) -> {
            String norm = id.toLowerCase(Locale.ROOT);
            if (norm.contains("diamond_sword")) return Optional.of(new MockItemStack(Material.DIAMOND_SWORD, amount));
            if (norm.contains("diamond")) return Optional.of(new MockItemStack(Material.DIAMOND, amount));
            if (norm.contains("emerald")) return Optional.of(new MockItemStack(Material.EMERALD, amount));
            if (norm.contains("gold")) return Optional.of(new MockItemStack(Material.GOLD_INGOT, amount));
            return Optional.empty();
        });
        conditionRegistry = new ConditionRegistry();
    }

    @AfterEach
    void tearDown() {
        itemBridge.setCustomResolver(null);
    }

    @Test
    @DisplayName("DropTableDefinition.fromMap parses YAML formats with string shorthand and structured maps")
    void testDropTableYamlParsing() {
        Map<String, Object> yamlMap = new HashMap<>();
        yamlMap.put("RollMode", "INDEPENDENT");
        yamlMap.put("Rolls", 2);

        Map<String, Object> opts = new HashMap<>();
        opts.put("DropsPerPlayer", true);
        opts.put("DropsPerPlayerRequiredDamagePercent", 15.0);
        opts.put("DropsDoLootsplosion", true);
        opts.put("DropsGlowByDefault", true);
        opts.put("BonusLuck", 0.25);
        opts.put("BonusLevel", 0.05);
        yamlMap.put("Options", opts);

        List<Object> drops = new ArrayList<>();
        drops.add("DIAMOND 1-3 1.0"); // Shorthand
        drops.add(Map.of("item", "EMERALD", "amount", "2-5", "chance", 1.0)); // Structured map
        yamlMap.put("Drops", drops);

        DropTableDefinition table = DropTableDefinition.fromMap("test_table", yamlMap, conditionRegistry);
        assertNotNull(table);
        assertEquals("test_table", table.id());
        assertEquals(DropTableDefinition.RollMode.INDEPENDENT, table.mode());
        assertEquals(2, table.rolls());
        assertTrue(table.options().dropsPerPlayer());
        assertEquals(15.0, table.options().dropsPerPlayerRequiredDamagePercent(), 0.001);
        assertTrue(table.options().dropsDoLootsplosion());
        assertTrue(table.options().dropsGlowByDefault());
        assertEquals(0.25, table.options().bonusLuckMultiplier(), 0.001);
        assertEquals(0.05, table.options().bonusLevelMultiplier(), 0.001);
        assertEquals(2, table.entries().size());

        DropEntry entry1 = table.entries().get(0);
        assertEquals("DIAMOND", entry1.itemId());
        assertEquals(1.0, entry1.chance(), 0.001);
        assertEquals(1, entry1.minAmount());
        assertEquals(3, entry1.maxAmount());

        DropEntry entry2 = table.entries().get(1);
        assertEquals("EMERALD", entry2.itemId());
        assertEquals(1.0, entry2.chance(), 0.001);
        assertEquals(2, entry2.minAmount());
        assertEquals(5, entry2.maxAmount());
    }

    @Test
    @DisplayName("DropTableDefinition independent vs weighted roll execution")
    void testDropRollModes() {
        // Independent table: always drops 100% chance items
        DropEntry entry1 = new DropEntry("DIAMOND", 1.0, 2, 2);
        DropEntry entry2 = new DropEntry("EMERALD", 1.0, 3, 3);
        DropTableDefinition independent = new DropTableDefinition("indep", List.of(entry1, entry2));

        DropMetadata meta = new DropMetadata(null, null, null, 1.0, 0L);
        List<ItemStack> indepResults = independent.roll(meta, new Random(), itemBridge, conditionRegistry);
        assertEquals(2, indepResults.size());

        // Weighted table: picks exactly 1 entry per roll
        DropTableDefinition weighted = new DropTableDefinition("weight", DropTableDefinition.RollMode.WEIGHTED, List.of(entry1, entry2), 1, DropOptions.DEFAULT);
        List<ItemStack> weightResults = weighted.roll(meta, new Random(), itemBridge, conditionRegistry);
        assertEquals(1, weightResults.size());
    }

    @Test
    @DisplayName("MobEquipmentDefinition parses from map and resolves equipment drops")
    void testEquipmentParsingAndDrops() {
        Map<String, Object> eqMap = new HashMap<>();
        eqMap.put("HAND", "DIAMOND_SWORD:1.0"); // 100% drop chance
        eqMap.put("HEAD", Map.of("item", "NETHERITE_HELMET", "dropChance", 0.0)); // 0% drop chance

        MobEquipmentDefinition def = MobEquipmentDefinition.fromMap(eqMap);
        assertNotNull(def);
        assertFalse(def.isEmpty());
        assertTrue(def.get(EquipmentSlot.MAIN_HAND).isPresent());
        assertEquals("DIAMOND_SWORD", def.get(EquipmentSlot.MAIN_HAND).get().itemId());
        assertEquals(1.0, def.get(EquipmentSlot.MAIN_HAND).get().dropChance(), 0.001);

        assertTrue(def.get(EquipmentSlot.HEAD).isPresent());
        assertEquals("NETHERITE_HELMET", def.get(EquipmentSlot.HEAD).get().itemId());
        assertEquals(0.0, def.get(EquipmentSlot.HEAD).get().dropChance(), 0.001);

        ItemProviderRegistry registry = new ItemProviderRegistry();
        List<ItemStack> drops = EquipmentApplier.resolveEquipmentDrops(def, registry);
        // Only HAND should drop (HEAD has 0.0 dropChance)
        assertEquals(1, drops.size());
        assertEquals(Material.DIAMOND_SWORD, drops.get(0).getType());
    }

    @Test
    @DisplayName("LevelScalingDefinition parses level and modifiers, correctly calculates scaled stats")
    void testLevelScalingStats() {
        Map<String, Object> map = new HashMap<>();
        map.put("Level", "5-10");
        Map<String, Object> mods = new HashMap<>();
        mods.put("Health", 20.0);
        mods.put("Damage", 4.0);
        mods.put("Armor", 2.0);
        mods.put("Power", 0.1);
        map.put("LevelModifiers", mods);

        LevelScalingDefinition scaling = LevelScalingDefinition.fromMap(map);
        assertEquals(5, scaling.minLevel());
        assertEquals(10, scaling.maxLevel());
        assertEquals(20.0, scaling.perLevelHealth(), 0.001);
        assertEquals(4.0, scaling.perLevelDamage(), 0.001);

        // At level 1: no bonus
        assertEquals(100.0, scaling.calculateHealth(100.0, 1), 0.001);
        // At level 5: base + 4 * 20.0 = 180.0
        assertEquals(180.0, scaling.calculateHealth(100.0, 5), 0.001);
        // At level 5: damage base + 4 * 4.0 = 26.0
        assertEquals(26.0, scaling.calculateDamage(10.0, 5), 0.001);
    }

    @Test
    @DisplayName("DropManager.generateDrops generates loot using registered table")
    void testDropManagerGenerateDrops() {
        DropEntry entry = new DropEntry("DIAMOND", 1.0, 5, 5);
        DropTableDefinition table = new DropTableDefinition("boss_table", List.of(entry));

        DropManager manager = new DropManager(new MobDefinitionRegistry(), new LunarMobManager(), itemBridge, conditionRegistry);
        manager.register(table);

        List<ItemStack> generated = manager.generateDrops("boss_table", null, null, 1.0);
        assertNotNull(generated);
        assertEquals(1, generated.size());
        assertEquals(Material.DIAMOND, generated.get(0).getType());
        assertEquals(5, generated.get(0).getAmount());
    }

    @Test
    @DisplayName("Multithreaded drop rolling performs safely under high concurrency")
    void testConcurrentLootGeneration() throws Exception {
        DropEntry entry1 = new DropEntry("DIAMOND", 0.5, 1, 3);
        DropEntry entry2 = new DropEntry("EMERALD", 0.5, 1, 3);
        DropTableDefinition table = new DropTableDefinition("concurrent_table", List.of(entry1, entry2));

        DropMetadata metadata = new DropMetadata(null, null, null, 1.0, 0L);
        int threads = 8;
        int rollsPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                int totalGenerated = 0;
                Random rnd = ThreadLocalRandom.current();
                for (int r = 0; r < rollsPerThread; r++) {
                    List<ItemStack> items = table.roll(metadata, rnd, itemBridge, conditionRegistry);
                    totalGenerated += items.size();
                }
                return totalGenerated;
            });
        }

        List<Future<Integer>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<Integer> f : futures) {
            assertTrue(f.get() > 0);
        }
    }

    @Test
    @DisplayName("DropTableDefinition parses inline conditions and DropsHaveBeamByDefault option")
    void testInlineConditionsAndBeamOption() {
        Map<String, Object> yamlMap = new HashMap<>();
        yamlMap.put("RollMode", "INDEPENDENT");
        yamlMap.put("Rolls", 1);

        Map<String, Object> opts = new HashMap<>();
        opts.put("DropsHaveBeamByDefault", true);
        yamlMap.put("Options", opts);

        List<Object> drops = new ArrayList<>();
        drops.add("DIAMOND 1-3 1.0 ?health{<50%} ?playerwithin{d=30}");
        yamlMap.put("Drops", drops);

        DropTableDefinition table = DropTableDefinition.fromMap("beam_table", yamlMap, conditionRegistry);
        assertNotNull(table);
        assertTrue(table.options().dropsHaveBeamByDefault());
        assertEquals(1, table.entries().size());

        DropEntry entry = table.entries().get(0);
        assertEquals("DIAMOND", entry.itemId());
        assertEquals(2, entry.conditions().size());
        assertEquals("health", entry.conditions().get(0).id());
        assertTrue(entry.conditions().get(0).parameters().containsKey("<50%"));
        assertEquals("playerwithin", entry.conditions().get(1).id());
        assertEquals(30, entry.conditions().get(1).parameters().get("d"));
    }

    @Test
    @DisplayName("LootGenerateEvent can be cancelled or its item list modified")
    void testLootGenerateEventContract() {
        ItemStack item1 = new MockItemStack(Material.DIAMOND, 2);
        ItemStack item2 = new MockItemStack(Material.EMERALD, 5);
        List<ItemStack> original = new ArrayList<>(List.of(item1, item2));

        vn.haohan.lunar.api.system.mob.Mob mockMob = (vn.haohan.lunar.api.system.mob.Mob) java.lang.reflect.Proxy.newProxyInstance(
                vn.haohan.lunar.api.system.mob.Mob.class.getClassLoader(),
                new Class<?>[]{vn.haohan.lunar.api.system.mob.Mob.class},
                (p, m, a) -> null);
        vn.haohan.lunar.api.event.LootGenerateEvent event = new vn.haohan.lunar.api.event.LootGenerateEvent(mockMob, null, original);
        assertFalse(event.isCancelled());
        assertEquals(2, event.drops().size());

        // Cancel
        event.setCancelled(true);
        assertTrue(event.isCancelled());

        // Modify drop list
        event.setCancelled(false);
        ItemStack item3 = new MockItemStack(Material.GOLD_INGOT, 10);
        event.drops().clear();
        event.drops().add(item3);
        assertEquals(1, event.drops().size());
        assertEquals(Material.GOLD_INGOT, event.drops().get(0).getType());
    }
}
