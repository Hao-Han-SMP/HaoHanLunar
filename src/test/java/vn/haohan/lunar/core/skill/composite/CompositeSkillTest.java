package vn.haohan.lunar.core.skill.composite;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.skill.mechanic.MechanicContext;
import vn.haohan.lunar.core.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;
import vn.haohan.lunar.core.skill.SkillCastContext;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;
import vn.haohan.lunar.core.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeSkillTest {

    @Test
    void sequenceExecutesChildrenInOrderAndStopsOnFailure() {
        List<String> log = new ArrayList<>();
        CompositeSkillNode node1 = (ctx, targets) -> {
            log.add("step1");
            return CompositeResult.success();
        };
        CompositeSkillNode node2 = (ctx, targets) -> {
            log.add("step2");
            return CompositeResult.failure("failed at 2");
        };
        CompositeSkillNode node3 = (ctx, targets) -> {
            log.add("step3");
            return CompositeResult.success();
        };

        SequenceNode sequence = new SequenceNode(List.of(node1, node2, node3), true, false);
        SkillCastContext context = sampleContext();

        CompositeResult result = sequence.execute(context, List.of());
        assertFalse(result.successful());
        assertEquals(List.of("step1", "step2"), log);
        assertFalse(context.lastStepSuccess());
    }

    @Test
    void cancellationTokenTripsAndHaltsChildExecution() {
        List<String> log = new ArrayList<>();
        SkillCastContext context = sampleContext();

        CompositeSkillNode step1 = (ctx, targets) -> {
            log.add("step1");
            return CompositeResult.success();
        };
        CompositeSkillNode cancelStep = new CancelNode();
        CompositeSkillNode step3 = (ctx, targets) -> {
            log.add("step3");
            return CompositeResult.success();
        };

        SequenceNode sequence = new SequenceNode(List.of(step1, cancelStep, step3));
        sequence.execute(context, List.of());

        assertTrue(context.isCancelled());
        assertEquals(List.of("step1"), log);

        // Cloned contexts share cancellation token
        SkillCastContext childClone = context.deepClone();
        assertTrue(childClone.isCancelled());
    }

    @Test
    void parallelExecutesAllBranchesWithClonedContexts() {
        AtomicInteger counter = new AtomicInteger();
        List<Integer> depths = new ArrayList<>();

        CompositeSkillNode branch1 = (ctx, targets) -> {
            counter.incrementAndGet();
            depths.add(ctx.depth());
            return CompositeResult.success();
        };
        CompositeSkillNode branch2 = (ctx, targets) -> {
            counter.incrementAndGet();
            depths.add(ctx.depth());
            return CompositeResult.success();
        };

        ParallelNode parallel = new ParallelNode(List.of(branch1, branch2));
        SkillCastContext context = sampleContext();

        CompositeResult result = parallel.execute(context, List.of());
        assertTrue(result.successful());
        assertEquals(2, counter.get());
        assertEquals(List.of(1, 1), depths);
    }

    @Test
    void chanceNodeExecutesOnlyWhenRollPasses() {
        AtomicInteger ranCount = new AtomicInteger();
        CompositeSkillNode action = (ctx, targets) -> {
            ranCount.incrementAndGet();
            return CompositeResult.success();
        };

        ChanceNode zeroChance = new ChanceNode(0.0, action);
        ChanceNode fullChance = new ChanceNode(1.0, action);

        SkillCastContext context = sampleContext();
        zeroChance.execute(context, List.of());
        assertEquals(0, ranCount.get());

        fullChance.execute(context, List.of());
        assertEquals(1, ranCount.get());
    }

    @Test
    void repeatUntilLoopsUntilConditionOrSafetyBound() {
        AtomicInteger iterations = new AtomicInteger();
        CompositeSkillNode action = (ctx, targets) -> {
            iterations.incrementAndGet();
            ctx.put("counter", iterations.get());
            return CompositeResult.success();
        };

        // Stop when counter >= 3
        RepeatUntilNode loop = new RepeatUntilNode(action, ctx -> {
            Object val = ctx.get("counter");
            return val instanceof Integer i && i >= 3;
        }, 10);

        SkillCastContext context = sampleContext();
        CompositeResult result = loop.execute(context, List.of());
        assertTrue(result.successful());
        assertEquals(3, iterations.get());

        // Safety limit bounded
        RepeatUntilNode infiniteLoop = new RepeatUntilNode(action, ctx -> false, 100);
        infiniteLoop.execute(context, List.of());
        // Max safety iterations is 50
        assertEquals(3 + 50, iterations.get());
    }

    @Test
    void onSuccessAndOnFailBranching() {
        List<String> log = new ArrayList<>();
        OnSuccessNode successBranch = new OnSuccessNode((ctx, targets) -> {
            log.add("success_ran");
            return CompositeResult.success();
        });
        OnFailNode failBranch = new OnFailNode((ctx, targets) -> {
            log.add("fail_ran");
            return CompositeResult.success();
        });

        SkillCastContext context = sampleContext();
        context.setLastStepSuccess(true);
        successBranch.execute(context, List.of());
        failBranch.execute(context, List.of());
        assertEquals(List.of("success_ran"), log);

        log.clear();
        context.setLastStepSuccess(false);
        successBranch.execute(context, List.of());
        failBranch.execute(context, List.of());
        assertEquals(List.of("fail_ran"), log);
    }

    @Test
    void maxRecursionDepthEnforced() {
        SkillCastContext ctx = sampleContext();
        SkillCastContext d1 = ctx.deepClone();
        SkillCastContext d2 = d1.deepClone();
        SkillCastContext d3 = d2.deepClone();
        SkillCastContext d4 = d3.deepClone();
        SkillCastContext d5 = d4.deepClone();
        assertEquals(5, d5.depth());

        // Exceeding MAX_DEPTH (5) throws IllegalStateException
        assertThrows(IllegalStateException.class, d5::deepClone);
    }

    @Test
    void maxDurationExpirationDetected() {
        SkillCastContext ctx = sampleContext();
        assertFalse(ctx.isExpired(ctx.startedAtTick() + 500));
        assertTrue(ctx.isExpired(ctx.startedAtTick() + 601));
    }

    @Test
    void mechanicRegistryCompositeIntegration() {
        MechanicRegistry registry = new MechanicRegistry();
        List<String> subskillsInvoked = new ArrayList<>();

        registry.setSubskillInvoker((skillId, ctx) -> {
            subskillsInvoked.add(skillId);
            return true;
        });

        ActiveLunarMob mob = sampleMob();
        SkillCastContext context = sampleContext();
        MechanicContext mechContext = new MechanicContext(context, List.of(TargetRef.entity(mob.entity())));

        // 1. Single subskill
        MechanicResult skillRes = registry.execute("skill", mechContext, Map.of("skill", "sub_fire"));
        assertTrue(skillRes.valid());
        assertEquals(List.of("sub_fire"), subskillsInvoked);

        // 2. Sequence mechanic
        subskillsInvoked.clear();
        MechanicResult seqRes = registry.execute("sequence", mechContext, Map.of("skills", List.of("slam", "shockwave")));
        assertTrue(seqRes.valid());
        assertEquals(List.of("slam", "shockwave"), subskillsInvoked);

        // 3. Chance mechanic
        subskillsInvoked.clear();
        registry.execute("chance", mechContext, Map.of("chance", 1.0, "skill", "lucky_crit"));
        assertEquals(List.of("lucky_crit"), subskillsInvoked);

        // 4. Cancel mechanic
        registry.execute("cancel", mechContext, Map.of());
        assertTrue(context.isCancelled());
    }

    private static SkillCastContext sampleContext() {
        ActiveLunarMob mob = sampleMob();
        SkillDefinition skill = new SkillDefinition("meta_skill", Set.of(SkillTrigger.ON_COMBAT), 20);
        return new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100);
    }

    private static ActiveLunarMob sampleMob() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }
}
