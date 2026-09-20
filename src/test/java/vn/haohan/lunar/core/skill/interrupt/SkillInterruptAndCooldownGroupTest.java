package vn.haohan.lunar.core.skill.interrupt;

import vn.haohan.lunar.api.system.combat.skill.interrupt.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkillInterruptAndCooldownGroupTest {

    @Test
    void testCancellationTokenLifecycle() {
        CancellationToken token = new CancellationToken();
        assertFalse(token.isCancelled());
        assertNull(token.reason());

        assertTrue(token.cancel(InterruptReason.STUNNED));
        assertTrue(token.isCancelled());
        assertEquals(InterruptReason.STUNNED, token.reason());

        // Second cancel call should return false (already cancelled)
        assertFalse(token.cancel(InterruptReason.SILENCED));
        assertEquals(InterruptReason.STUNNED, token.reason());
    }

    @Test
    void testChannelingLockAndOverrideBehavior() {
        ActiveLunarMob mob = createMockMob();

        // 1. Start Channeling Skill 1
        CancellationToken token1 = mob.registerSkillExecution("lunar_beam", true, false);
        assertNotNull(token1);
        assertFalse(token1.isCancelled());
        assertTrue(mob.isChanneling());
        assertEquals("lunar_beam", mob.activeChannelingSkill());

        // 2. Attempt to start second Channeling Skill without override -> Should be rejected (CHANNEL_BUSY)
        CancellationToken token2 = mob.registerSkillExecution("star_fall", true, false);
        assertNull(token2);
        assertTrue(mob.isChanneling());
        assertEquals("lunar_beam", mob.activeChannelingSkill());

        // 3. Start third Channeling Skill WITH override -> interrupts skill 1
        CancellationToken token3 = mob.registerSkillExecution("supernova", true, true);
        assertNotNull(token3);
        assertTrue(token1.isCancelled());
        assertEquals(InterruptReason.COMMAND, token1.reason());
        assertTrue(mob.isChanneling());
        assertEquals("supernova", mob.activeChannelingSkill());

        // 4. Stun the mob -> interrupts all active skills
        int interrupted = mob.interruptActiveSkills(InterruptReason.STUNNED);
        assertTrue(interrupted >= 1);
        assertTrue(token3.isCancelled());
        assertEquals(InterruptReason.STUNNED, token3.reason());
        assertFalse(mob.isChanneling());
        assertNull(mob.activeChannelingSkill());
    }

    @Test
    void testCooldownGroupSharedLockout() {
        CooldownRegistry registry = new CooldownRegistry();
        UUID entityId = UUID.randomUUID();

        // Skill A: 20 ticks individual CD, 100 ticks group CD (ULTIMATE)
        SkillDefinition skillA = new SkillDefinition("apocalypse", Set.of(SkillTrigger.ON_COMBAT), 20L,
                List.of(), "ULTIMATE", 100L);

        // Skill B: 10 ticks individual CD, 100 ticks group CD (ULTIMATE)
        SkillDefinition skillB = new SkillDefinition("meteor_shower", Set.of(SkillTrigger.ON_COMBAT), 10L,
                List.of(), "ULTIMATE", 100L);

        long currentTick = 1000L;

        // Both skills ready initially
        assertTrue(registry.isReady(entityId, skillA.id(), currentTick));
        assertTrue(registry.isReady(entityId, skillB.id(), currentTick));
        assertTrue(registry.isGroupReady(entityId, "ULTIMATE", currentTick));

        // Acquire Skill A
        assertTrue(registry.tryAcquire(entityId, skillA, currentTick));

        // Skill A is on cooldown
        assertFalse(registry.tryAcquire(entityId, skillA, currentTick));

        // Skill B is ALSO locked out by the group cooldown, even though never acquired!
        assertFalse(registry.isGroupReady(entityId, "ULTIMATE", currentTick));
        assertFalse(registry.tryAcquire(entityId, skillB, currentTick));

        // At currentTick + 30: Skill A individual CD (20) has passed, but group CD (100) still active!
        currentTick += 30L;
        assertFalse(registry.tryAcquire(entityId, skillA, currentTick));
        assertFalse(registry.tryAcquire(entityId, skillB, currentTick));

        // At currentTick + 80 (total 110 ticks elapsed since cast): group CD has expired!
        currentTick += 80L;
        assertTrue(registry.isGroupReady(entityId, "ULTIMATE", currentTick));
        // Now Skill B can be acquired!
        assertTrue(registry.tryAcquire(entityId, skillB, currentTick));
    }

    private ActiveLunarMob createMockMob() {
        UUID uuid = UUID.randomUUID();
        World mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (p, m, a) -> "world");
        LivingEntity mockEntity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> new Location(mockWorld, 0, 64, 0);
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "equals" -> p == a[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                });
        MobDefinition def = new MobDefinition(new MobDefinitionId("test_boss"), EntityType.ZOMBIE, "Test Boss",
                null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(mockEntity, def, new LunarMobIdentity("test_boss", "1.0"));
    }
}
