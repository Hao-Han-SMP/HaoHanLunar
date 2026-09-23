package vn.haohan.lunar.core.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.DamageContext;
import vn.haohan.lunar.api.system.combat.DamagePipeline;
import vn.haohan.lunar.api.system.combat.DamageResult;
import vn.haohan.lunar.api.system.combat.DamageType;
import vn.haohan.lunar.api.system.mob.MobAttributeDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobOptionDefinition;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class BossLeashAndDpsCapTest {

    @Test
    @DisplayName("ActiveMob rolling DPS cap strictly limits damage accumulated within a 20-tick sliding window")
    void testRollingDpsCapSlidingWindow() {
        LivingEntity mockEntity = createMockEntity(UUID.randomUUID(), 500.0);
        MobDefinition def = new MobDefinition(
                new MobDefinitionId("boss_guardian"),
                EntityType.IRON_GOLEM,
                "Guardian Boss",
                null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 500.0)),
                Map.of("damage-cap", new MobOptionDefinition("damage-cap", "50.0")),
                List.of(),
                null,
                Set.of()
        );
        LunarMobIdentity identity = new LunarMobIdentity("boss_guardian", "1.0", Optional.empty());
        ActiveMob mob = new ActiveMob(mockEntity, def, identity);

        long startTick = 100L;

        // Hit 1: 30 damage within window -> full 30 allowed
        double allowed1 = mob.applyDamageCap(30.0, 50.0, startTick);
        assertEquals(30.0, allowed1, 0.001);

        // Hit 2: 30 damage at tick 105 (same window) -> remaining cap is 20
        double allowed2 = mob.applyDamageCap(30.0, 50.0, startTick + 5);
        assertEquals(20.0, allowed2, 0.001);

        // Hit 3: 25 damage at tick 115 (same window) -> remaining cap is 0
        double allowed3 = mob.applyDamageCap(25.0, 50.0, startTick + 15);
        assertEquals(0.0, allowed3, 0.001);

        // Hit 4: 40 damage at tick 125 (new 20-tick window >= startTick + 20) -> cap resets to full 50
        double allowed4 = mob.applyDamageCap(40.0, 50.0, startTick + 25);
        assertEquals(40.0, allowed4, 0.001);
    }

    @Test
    @DisplayName("DamagePipeline enforces rolling damageCap modifier across consecutive strikes")
    void testDamagePipelineDpsCapModifier() {
        LivingEntity mockVictim = createMockEntity(UUID.randomUUID(), 1000.0);
        MobDefinition def = new MobDefinition(
                new MobDefinitionId("raid_boss"),
                EntityType.IRON_GOLEM,
                "Raid Boss",
                null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 1000.0)),
                Map.of("damage-cap", new MobOptionDefinition("damage-cap", "100.0")),
                List.of(),
                null,
                Set.of()
        );
        LunarMobIdentity identity = new LunarMobIdentity("raid_boss", "1.0", Optional.empty());
        ActiveMob boss = new ActiveMob(mockVictim, def, identity);

        DamagePipeline pipeline = new DamagePipeline();

        // First hit: 80 damage
        DamageContext ctx1 = DamageContext.builder()
                .victim(mockVictim)
                .victimMob(boss)
                .baseDamage(80.0)
                .cause(DamageCause.CUSTOM)
                .damageType(DamageType.PHYSICAL)
                .build();
        DamageResult res1 = pipeline.execute(ctx1);
        assertTrue(res1.executed());
        assertEquals(80.0, res1.appliedDamage(), 0.001);

        // Second hit: 50 damage -> only 20 remaining in cap
        DamageContext ctx2 = DamageContext.builder()
                .victim(mockVictim)
                .victimMob(boss)
                .baseDamage(50.0)
                .cause(DamageCause.CUSTOM)
                .damageType(DamageType.PHYSICAL)
                .build();
        DamageResult res2 = pipeline.execute(ctx2);
        assertTrue(res2.executed());
        assertEquals(20.0, res2.appliedDamage(), 0.001);

        // Third hit: 40 damage -> 0 remaining in cap -> damage reduced to 0 and cancelled
        DamageContext ctx3 = DamageContext.builder()
                .victim(mockVictim)
                .victimMob(boss)
                .baseDamage(40.0)
                .cause(DamageCause.CUSTOM)
                .damageType(DamageType.PHYSICAL)
                .build();
        DamageResult res3 = pipeline.execute(ctx3);
        assertFalse(res3.executed());
        assertTrue(res3.cancelled());
        assertEquals(0.0, res3.appliedDamage(), 0.001);
    }

    @Test
    @DisplayName("ActiveMob resetToSpawn clears soft leash, resets threat, heals to full, and teleports back")
    void testBossResetToSpawn() {
        UUID mobId = UUID.randomUUID();
        AtomicReference<Double> currentHealth = new AtomicReference<>(150.0);
        AtomicReference<Location> currentLoc = new AtomicReference<>(null);
        AtomicBoolean teleportCalled = new AtomicBoolean(false);

        World mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (p, m, a) -> null);

        Location spawnLoc = new Location(mockWorld, 100, 64, 100);
        currentLoc.set(new Location(mockWorld, 250, 64, 250));

        AttributeInstance attrInst = (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                new Class<?>[]{AttributeInstance.class}, (p, m, a) -> {
                    if (m.getName().equals("getValue")) return 500.0;
                    return null;
                });

        Mob mockMob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return mobId;
                    if (m.getName().equals("isValid")) return true;
                    if (m.getName().equals("isDead")) return false;
                    if (m.getName().equals("getHealth")) return currentHealth.get();
                    if (m.getName().equals("setHealth")) {
                        currentHealth.set((Double) a[0]);
                        return null;
                    }
                    if (m.getName().equals("getAttribute")) {
                        return attrInst;
                    }
                    if (m.getName().equals("getLocation")) return currentLoc.get();
                    if (m.getName().equals("teleport")) {
                        teleportCalled.set(true);
                        currentLoc.set((Location) a[0]);
                        return true;
                    }
                    if (m.getName().equals("setTarget")) return null;
                    return null;
                });

        MobDefinition def = new MobDefinition(
                new MobDefinitionId("altar_boss"),
                EntityType.IRON_GOLEM,
                "Altar Boss",
                null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 500.0)),
                Map.of(
                        "leash-range", new MobOptionDefinition("leash-range", "40.0"),
                        "soft-leash-radius", new MobOptionDefinition("soft-leash-radius", "30.0"),
                        "heal-on-leash", new MobOptionDefinition("heal-on-leash", "true"),
                        "reset-threat-on-leash", new MobOptionDefinition("reset-threat-on-leash", "true")
                ),
                List.of(),
                null,
                Set.of()
        );
        LunarMobIdentity identity = new LunarMobIdentity("altar_boss", "1.0", Optional.empty());
        ActiveMob activeMob = new ActiveMob(mockMob, def, identity);
        activeMob.setSpawnLocation(spawnLoc);

        // Add threat and soft leash
        activeMob.threatTable().addThreat(UUID.randomUUID(), 200.0, 100L);
        activeMob.setSoftLeashed(true);
        assertFalse(activeMob.threatTable().isEmpty());
        assertTrue(activeMob.isSoftLeashed());

        // Perform leash reset at tick 100
        activeMob.resetToSpawn(100L);

        // Verify full heal
        assertEquals(500.0, currentHealth.get(), 0.001);
        // Verify threat cleared
        assertTrue(activeMob.threatTable().isEmpty());
        // Verify soft leash cleared
        assertFalse(activeMob.isSoftLeashed());
        // Verify teleport executed back to spawn location
        assertTrue(teleportCalled.get());
        assertEquals(spawnLoc, currentLoc.get());

        // Verify temporary invulnerability (default 60 ticks)
        assertTrue(activeMob.isInvulnerable(100L));
        assertTrue(activeMob.isInvulnerable(159L));
        assertFalse(activeMob.isInvulnerable(160L));
    }

    @Test
    @DisplayName("DamagePipeline cancels all incoming damage when victimMob is invulnerable from leash reset")
    void testInvulnerableMobCancelsDamagePipeline() {
        LivingEntity mockVictim = createMockEntity(UUID.randomUUID(), 1000.0);
        MobDefinition def = new MobDefinition(
                new MobDefinitionId("invul_boss"),
                EntityType.IRON_GOLEM,
                "Invul Boss",
                null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 1000.0)),
                Map.of("leash-invulnerable-ticks", new MobOptionDefinition("leash-invulnerable-ticks", "80")),
                List.of(),
                null,
                Set.of()
        );
        ActiveMob boss = new ActiveMob(mockVictim, def, new LunarMobIdentity("invul_boss", "1.0", Optional.empty()));
        long currentTick = System.currentTimeMillis() / 50L;
        boss.setInvulnerableTicks(80, currentTick);

        DamagePipeline pipeline = new DamagePipeline();
        DamageContext ctx = DamageContext.builder()
                .victim(mockVictim)
                .victimMob(boss)
                .baseDamage(200.0)
                .cause(DamageCause.ENTITY_ATTACK)
                .damageType(DamageType.PHYSICAL)
                .build();

        DamageResult res = pipeline.execute(ctx);
        assertFalse(res.executed());
        assertTrue(res.cancelled());
        assertEquals(0.0, res.appliedDamage(), 0.001);
    }

    private LivingEntity createMockEntity(UUID id, double maxHealth) {
        AtomicReference<Double> health = new AtomicReference<>(maxHealth);
        AttributeInstance attr = (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                new Class<?>[]{AttributeInstance.class}, (p, m, a) -> {
                    if (m.getName().equals("getValue")) return maxHealth;
                    return null;
                });

        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return id;
                    if (m.getName().equals("isValid")) return true;
                    if (m.getName().equals("isDead")) return false;
                    if (m.getName().equals("isInvulnerable")) return false;
                    if (m.getName().equals("getHealth")) return health.get();
                    if (m.getName().equals("setHealth")) {
                        health.set((Double) a[0]);
                        return null;
                    }
                    if (m.getName().equals("getAttribute")) {
                        return attr;
                    }
                    if (m.getName().equals("damage")) return null;
                    return null;
                });
    }
}
