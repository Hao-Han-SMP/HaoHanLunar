package vn.haohan.lunar.core.combat;

import vn.haohan.lunar.api.system.combat.*;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobAttributeDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobOptionDefinition;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamagePipelineTest {

    @Test
    void normalDamageExecutionAppliesDamageAndNotifiesPostHandlers() {
        World world = mockWorld();
        AtomicReference<Double> damageReceived = new AtomicReference<>(0.0);
        LivingEntity attacker = mockLiving(world, true, false, false, d -> {});
        LivingEntity victim = mockLiving(world, true, false, false, damageReceived::set);

        DamagePipeline pipeline = new DamagePipeline();

        AtomicBoolean postHandlerCalled = new AtomicBoolean(false);
        pipeline.registerPostHandler((ctx, amount) -> {
            postHandlerCalled.set(true);
            assertEquals(15.0, amount, 0.001);
        });

        DamageContext context = DamageContext.builder()
                .attacker(attacker)
                .victim(victim)
                .baseDamage(15.0)
                .cause(EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                .damageType(DamageType.PHYSICAL)
                .build();

        DamageResult result = pipeline.execute(context);

        assertTrue(result.executed());
        assertFalse(result.cancelled());
        assertEquals(15.0, result.appliedDamage(), 0.001);
        assertEquals(15.0, damageReceived.get(), 0.001);
        assertTrue(postHandlerCalled.get());
    }

    @Test
    void preChecksBlockDeadInvulnerableAndCancelledVictims() {
        World world = mockWorld();
        DamagePipeline pipeline = new DamagePipeline();

        // 1. Dead victim
        LivingEntity deadVictim = mockLiving(world, true, true, false, d -> {});
        DamageContext deadContext = DamageContext.builder().victim(deadVictim).baseDamage(10.0).build();
        DamageResult deadResult = pipeline.execute(deadContext);
        assertFalse(deadResult.executed());
        assertTrue(deadResult.cancelled());
        assertTrue(deadResult.cancellationReason().orElse("").contains("Dead or invalid"));

        // 2. Invulnerable victim
        LivingEntity invulnerableVictim = mockLiving(world, true, false, true, d -> {});
        DamageContext invulContext = DamageContext.builder().victim(invulnerableVictim).baseDamage(10.0).build();
        DamageResult invulResult = pipeline.execute(invulContext);
        assertFalse(invulResult.executed());
        assertTrue(invulResult.cancelled());
        assertTrue(invulResult.cancellationReason().orElse("").contains("invulnerable"));

        // 3. Pre-cancelled context
        LivingEntity aliveVictim = mockLiving(world, true, false, false, d -> {});
        DamageContext cancelledContext = DamageContext.builder()
                .victim(aliveVictim)
                .baseDamage(10.0)
                .cancelled(true)
                .cancelReason("Player in spawn protected region")
                .build();
        DamageResult cancelledResult = pipeline.execute(cancelledContext);
        assertFalse(cancelledResult.executed());
        assertTrue(cancelledResult.cancelled());
        assertEquals("Player in spawn protected region", cancelledResult.cancellationReason().orElse(""));
    }

    @Test
    void modifiersAdjustFinalDamageCorrectly() {
        World world = mockWorld();
        AtomicReference<Double> damageReceived = new AtomicReference<>(0.0);
        LivingEntity victim = mockLiving(world, true, false, false, damageReceived::set);

        DamagePipeline pipeline = new DamagePipeline();
        // Register a 50% damage reduction modifier for fire
        pipeline.registerModifier(ctx -> {
            if (ctx.cause() == EntityDamageEvent.DamageCause.FIRE) {
                ctx.multiplyDamage(0.5);
            }
        });

        DamageContext context = DamageContext.builder()
                .victim(victim)
                .baseDamage(20.0)
                .cause(EntityDamageEvent.DamageCause.FIRE)
                .build();

        DamageResult result = pipeline.execute(context);
        assertTrue(result.executed());
        assertEquals(10.0, result.appliedDamage(), 0.001);
        assertEquals(10.0, damageReceived.get(), 0.001);

        // Modifier reducing to 0 cancels the damage
        DamagePipeline zeroPipeline = new DamagePipeline();
        zeroPipeline.registerModifier(ctx -> ctx.setFinalDamage(0.0));
        DamageResult zeroResult = zeroPipeline.execute(DamageContext.builder().victim(victim).baseDamage(10.0).build());
        assertFalse(zeroResult.executed());
        assertTrue(zeroResult.cancelled());
    }

    @Test
    void immunityCheckersBlockDamage() {
        World world = mockWorld();
        LivingEntity victim = mockLiving(world, true, false, false, d -> {});

        DamagePipeline pipeline = new DamagePipeline();
        pipeline.registerImmunityChecker(ctx -> {
            if (ctx.cause() == EntityDamageEvent.DamageCause.FALL) {
                return "Immune to fall damage";
            }
            return null;
        });

        DamageContext context = DamageContext.builder()
                .victim(victim)
                .baseDamage(30.0)
                .cause(EntityDamageEvent.DamageCause.FALL)
                .build();

        DamageResult result = pipeline.execute(context);
        assertFalse(result.executed());
        assertTrue(result.cancelled());
        assertEquals("Immune to fall damage", result.cancellationReason().orElse(""));
    }

    @Test
    void trueDamageAutomaticallySetsIgnoreArmorAndAbsorption() {
        World world = mockWorld();
        LivingEntity victim = mockLiving(world, true, false, false, d -> {});

        DamageContext trueCtx = DamageContext.builder()
                .victim(victim)
                .baseDamage(12.0)
                .damageType(DamageType.TRUE)
                .build();

        assertTrue(trueCtx.isIgnoreArmor());
        assertTrue(trueCtx.isIgnoreAbsorption());
        assertEquals(DamageType.TRUE, trueCtx.damageType());
    }

    @Test
    void recursionGuardPreventsInfiniteLoops() {
        World world = mockWorld();
        DamagePipeline pipeline = new DamagePipeline();

        AtomicInteger recursiveCalls = new AtomicInteger(0);
        AtomicInteger loopPrevented = new AtomicInteger(0);

        LivingEntity attackerEntity = mockLiving(world, true, false, false, d -> {});
        ActiveLunarMob attackerMob = mockActiveMob(attackerEntity, "boss_mob");

        // The victim triggers a counter-attack damage when damaged!
        LivingEntity counterAttackerVictim = mockLiving(world, true, false, false, damage -> {
            recursiveCalls.incrementAndGet();
            // Nested call attempting to damage while the mob is in the middle of executing a damage skill
            DamageContext nestedContext = DamageContext.builder()
                    .attackerMob(attackerMob)
                    .victim(attackerEntity)
                    .baseDamage(5.0)
                    .build();
            DamageResult nestedResult = pipeline.execute(nestedContext);
            if (nestedResult.cancelled() && nestedResult.cancellationReason().orElse("").contains("Recursion guard")) {
                loopPrevented.incrementAndGet();
            }
        });

        DamageContext primaryContext = DamageContext.builder()
                .attackerMob(attackerMob)
                .victim(counterAttackerVictim)
                .baseDamage(25.0)
                .build();

        DamageResult result = pipeline.execute(primaryContext);

        assertTrue(result.executed());
        assertEquals(1, recursiveCalls.get(), "Victim damage listener was triggered once");
        assertEquals(1, loopPrevented.get(), "Nested damage call was safely intercepted by recursion guard");
        assertFalse(attackerMob.isUsingDamageSkill(), "Recursion guard lock must be released after pipeline finishes");
    }

    @Test
    void validationGuardsOnContextBuilding() {
        World world = mockWorld();
        LivingEntity victim = mockLiving(world, true, false, false, d -> {});

        assertThrows(IllegalArgumentException.class, () ->
                DamageContext.builder().victim(victim).baseDamage(-5.0).build());

        assertThrows(NullPointerException.class, () ->
                DamageContext.builder().baseDamage(10.0).build());

        assertThrows(NullPointerException.class, () ->
                new DamagePipeline().execute(null));
    }

    // --- Helpers & Mocks ---

    private static World mockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    case "getName" -> "mock_world";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LivingEntity mockLiving(World world, boolean valid, boolean dead, boolean invulnerable, java.util.function.Consumer<Double> onDamage) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getUniqueId" -> uuid;
                    case "isValid" -> valid;
                    case "isDead" -> dead;
                    case "isInvulnerable" -> invulnerable;
                    case "damage" -> {
                        if (args != null && args.length > 0 && args[0] instanceof Double amt) {
                            onDamage.accept(amt);
                        }
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "MockLivingEntity[" + uuid + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ActiveLunarMob mockActiveMob(LivingEntity entity, String id) {
        MobDefinition def = new MobDefinition(
                new MobDefinitionId(id),
                EntityType.IRON_GOLEM,
                "Boss",
                null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 100.0)),
                Map.of("silent", new MobOptionDefinition("silent", "false")),
                List.of(),
                null,
                Set.of()
        );
        LunarMobIdentity identity = new LunarMobIdentity(id, "1.0.0");
        return new ActiveLunarMob(entity, def, identity);
    }
}
