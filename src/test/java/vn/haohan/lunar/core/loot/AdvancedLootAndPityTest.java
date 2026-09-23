package vn.haohan.lunar.core.loot;

import vn.haohan.lunar.api.system.loot.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.loot.pity.PityManager;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedLootAndPityTest {

    private static class DummyItemStack extends ItemStack {
        private final Material type;
        private int amount;

        public DummyItemStack(Material type, int amount) {
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

    private HaoHanItemBridge itemBridge;

    @BeforeEach
    void setUp() {
        itemBridge = HaoHanItemBridge.get();
        itemBridge.setItemStackFactory(DummyItemStack::new);
        itemBridge.setCustomResolver((id, amount) -> {
            if ("haohan:rare_sword".equalsIgnoreCase(id)) {
                return Optional.of(new DummyItemStack(Material.NETHERITE_SWORD, amount));
            }
            if ("haohan:common_gem".equalsIgnoreCase(id)) {
                return Optional.of(new DummyItemStack(Material.EMERALD, amount));
            }
            return Optional.empty();
        });
    }

    @Test
    void pitySystemTriggersGuaranteedDropAtThreshold() {
        PityManager pityManager = new PityManager();
        UUID playerUuid = UUID.randomUUID();
        String rareId = "haohan:rare_sword";

        assertEquals(0, pityManager.getPity(playerUuid, rareId));

        for (int i = 0; i < 49; i++) {
            pityManager.recordMiss(playerUuid, rareId);
        }
        assertEquals(49, pityManager.getPity(playerUuid, rareId));
        assertFalse(pityManager.isPityTriggered(playerUuid, rareId, 50));

        pityManager.recordMiss(playerUuid, rareId);
        assertEquals(50, pityManager.getPity(playerUuid, rareId));
        assertTrue(pityManager.isPityTriggered(playerUuid, rareId, 50));

        pityManager.recordSuccess(playerUuid, rareId);
        assertEquals(0, pityManager.getPity(playerUuid, rareId));
        assertFalse(pityManager.isPityTriggered(playerUuid, rareId, 50));
    }

    @Test
    void perPlayerDropsDistributedAccordingToContribution() {
        MobDefinitionRegistry definitions = new MobDefinitionRegistry();
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("boss_titan"), EntityType.IRON_GOLEM,
                "Titan", null, Map.of(), Map.of(), List.of(), "boss_drops", Set.of());
        definitions.register(mobDef);

        LunarMobManager mobManager = new LunarMobManager();
        World world = mockWorld("world");
        LivingEntity entity = mockLivingEntity(world, 0, 64, 0);
        ActiveLunarMob mob = new ActiveLunarMob(entity, mobDef, new LunarMobIdentity("boss_titan", "1"));
        mobManager.register(mob);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        mob.threatTable().addThreat(p1, 80.0);
        mob.threatTable().addThreat(p2, 20.0);
        mob.threatTable().addThreat(p3, 2.0);

        DropOptions options = DropOptions.builder()
                .dropsPerPlayer(true, 5.0) // Requires at least 5% damage
                .build();

        DropTableDefinition table = new DropTableDefinition("boss_drops", DropTableDefinition.RollMode.INDEPENDENT,
                List.of(new DropEntry("haohan:common_gem", 1.0, 2, 2)), 1, options);

        DropManager dropManager = new DropManager(definitions, mobManager, itemBridge, new ConditionRegistry());
        dropManager.register(table);

        List<ItemStack> allDrops = dropManager.handleDeathDrops(mob, null, entity.getLocation(), new Random(42));
        assertEquals(2, allDrops.size());
        assertEquals(2, allDrops.get(0).getAmount());
        assertEquals(2, allDrops.get(1).getAmount());
    }

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "dropItemNaturally" -> mockItem();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Item mockItem() {
        return (Item) Proxy.newProxyInstance(Item.class.getClassLoader(), new Class<?>[]{Item.class},
                (proxy, method, args) -> null);
    }

    private static LivingEntity mockLivingEntity(World world, double x, double y, double z) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getHealth" -> 100.0;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
