package vn.haohan.lunar.core.mob.stat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.combat.DamageContext;
import vn.haohan.lunar.core.combat.DamagePipeline;
import vn.haohan.lunar.core.combat.DamageResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;
import vn.haohan.lunar.core.integration.placeholder.LunarPlaceholderResolver;
import vn.haohan.lunar.core.skill.CooldownRegistry;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StatSystemTest {

    private World mockWorld;

    @BeforeEach
    void setUp() {
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "hashCode" -> 12345;
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    default -> null;
                }
        );
    }

    private LivingEntity mockLivingEntity(double initialHealth, double maxHealth) {
        UUID uuid = UUID.randomUUID();
        final double[] health = {initialHealth};

        AttributeInstance maxHealthAttr = (AttributeInstance) Proxy.newProxyInstance(
                AttributeInstance.class.getClassLoader(),
                new Class<?>[]{AttributeInstance.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getValue" -> maxHealth;
                    case "getBaseValue" -> maxHealth;
                    default -> null;
                }
        );

        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHealth" -> health[0];
                    case "setHealth" -> {
                        health[0] = (double) args[0];
                        yield null;
                    }
                    case "getAttribute" -> (args.length > 0 && (Objects.equals(args[0], Attribute.MAX_HEALTH) || String.valueOf(args[0]).contains("MAX_HEALTH"))) ? maxHealthAttr : null;
                    case "getLocation" -> new Location(mockWorld, 0, 64, 0);
                    case "getWorld" -> mockWorld;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "isInvulnerable" -> false;
                    case "getType" -> EntityType.IRON_GOLEM;
                    case "getUniqueId" -> uuid;
                    case "damage" -> null;
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }

    private ActiveLunarMob createMob(String mobId, double initialHealth, double maxHealth) {
        LivingEntity entity = mockLivingEntity(initialHealth, maxHealth);
        MobDefinition definition = new MobDefinition(new MobDefinitionId(mobId),
                EntityType.IRON_GOLEM, mobId, null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity(mobId, "1"));
    }

    @Test
    void testStatModifierOperationsAndBounds() {
        StatHolder holder = new StatHolder();
        holder.setBase(StatType.DAMAGE, 100.0);

        // FLAT: +20
        holder.addModifier(StatModifier.permanent("gear_weapon", StatType.DAMAGE, ModifierOperation.FLAT, 20.0));
        // PERCENT_ADD: +50% (+0.50)
        holder.addModifier(StatModifier.permanent("buff_fury", StatType.DAMAGE, ModifierOperation.PERCENT_ADD, 0.50));
        // PERCENT_MULT: *1.10 (+0.10)
        holder.addModifier(StatModifier.permanent("blessing", StatType.DAMAGE, ModifierOperation.PERCENT_MULT, 0.10));

        // Calculation: (100 + 20) * (1 + 0.50) * (1 + 0.10) = 120 * 1.5 * 1.10 = 198.0
        StatSnapshot snapshot = holder.snapshot(0);
        assertEquals(198.0, snapshot.damage(), 0.001);

        // Clamping bounds test: CDR capped at 40% (0.40)
        holder.setBase(StatType.COOLDOWN_REDUCTION, 0.10);
        holder.addModifier(StatModifier.permanent("cdr_overflow", StatType.COOLDOWN_REDUCTION, ModifierOperation.FLAT, 0.80));
        StatSnapshot cdrSnap = holder.snapshot(0);
        assertEquals(0.40, cdrSnap.cooldownReduction(), 0.001);

        // Clamping bounds test: CRIT_CHANCE capped at 1.0 (100%)
        holder.setBase(StatType.CRIT_CHANCE, 0.50);
        holder.addModifier(StatModifier.permanent("crit_overflow", StatType.CRIT_CHANCE, ModifierOperation.FLAT, 1.50));
        StatSnapshot critSnap = holder.snapshot(0);
        assertEquals(1.0, critSnap.critChance(), 0.001);

        // Clamping bounds test: Negative damage cannot go below 0.0
        holder.addModifier(StatModifier.permanent("massive_curse", StatType.DAMAGE, ModifierOperation.FLAT, -1000.0));
        assertEquals(0.0, holder.snapshot(0).damage(), 0.001);
    }

    @Test
    void testStatModifierExpirationByTick() {
        StatHolder holder = new StatHolder();
        holder.setBase(StatType.ARMOR, 50.0);

        // Permanent: +10 armor
        holder.addModifier(StatModifier.permanent("perm_ring", StatType.ARMOR, ModifierOperation.FLAT, 10.0));
        // Timed: +40 armor until tick 100
        holder.addModifier(StatModifier.timed("iron_skin_potion", StatType.ARMOR, ModifierOperation.FLAT, 40.0, 100L));

        // At tick 50 -> 50 + 10 + 40 = 100
        assertEquals(100.0, holder.snapshot(50).armor(), 0.001);

        // At tick 100 -> timed modifier expires -> 50 + 10 = 60
        assertEquals(60.0, holder.snapshot(100).armor(), 0.001);
        // Ensure expired modifier is purged from active list
        assertEquals(1, holder.modifiers().size());

        // Remove modifier by source ID
        assertTrue(holder.removeModifier("perm_ring"));
        assertEquals(50.0, holder.snapshot(100).armor(), 0.001);
    }

    @Test
    void testDamagePipelineCriticalStrikeAndLifesteal() {
        DamagePipeline pipeline = new DamagePipeline();

        ActiveLunarMob attacker = createMob("vampire_boss", 50.0, 100.0);
        ActiveLunarMob victim = createMob("target_dummy", 500.0, 500.0);

        // Attacker stats: 100% crit chance, 2.0x crit damage, 25% lifesteal
        attacker.stats().setBase(StatType.CRIT_CHANCE, 1.0);
        attacker.stats().setBase(StatType.CRIT_DAMAGE, 2.0);
        attacker.stats().setBase(StatType.LIFESTEAL, 0.25);

        DamageContext ctx = DamageContext.builder()
                .attacker(attacker.entity())
                .attackerMob(attacker)
                .victim(victim.entity())
                .victimMob(victim)
                .baseDamage(50.0)
                .build();

        DamageResult result = pipeline.execute(ctx);
        assertTrue(result.executed());

        // 1. Critical Strike applied: 50.0 * 2.0 = 100.0 damage
        assertTrue(ctx.isCritical());
        assertEquals(100.0, result.appliedDamage(), 0.001);

        // 2. Lifesteal applied: healed = 100.0 * 0.25 = 25.0 HP
        // Initial health was 50.0 -> becomes 75.0
        assertEquals(75.0, attacker.entity().getHealth(), 0.001);
    }

    @Test
    void testDamagePipelineArmorMitigation() {
        DamagePipeline pipeline = new DamagePipeline();

        ActiveLunarMob attacker = createMob("attacker", 100.0, 100.0);
        ActiveLunarMob victim = createMob("tank_boss", 500.0, 500.0);

        // Victim armor: 100 -> 100 / (100 + 100) = 50% mitigation
        victim.stats().setBase(StatType.ARMOR, 100.0);

        DamageContext normalCtx = DamageContext.builder()
                .attacker(attacker.entity())
                .attackerMob(attacker)
                .victim(victim.entity())
                .victimMob(victim)
                .baseDamage(100.0)
                .build();

        DamageResult normalResult = pipeline.execute(normalCtx);
        assertTrue(normalResult.executed());
        assertEquals(50.0, normalResult.appliedDamage(), 0.001);

        // Ignore Armor test
        DamageContext trueDamageCtx = DamageContext.builder()
                .attacker(attacker.entity())
                .attackerMob(attacker)
                .victim(victim.entity())
                .victimMob(victim)
                .baseDamage(100.0)
                .ignoreArmor(true)
                .build();

        DamageResult trueResult = pipeline.execute(trueDamageCtx);
        assertTrue(trueResult.executed());
        assertEquals(100.0, trueResult.appliedDamage(), 0.001);
    }

    @Test
    void testCooldownRegistryCooldownReduction() {
        CooldownRegistry cooldowns = new CooldownRegistry();
        UUID entityId = UUID.randomUUID();
        SkillDefinition skill = new SkillDefinition("meteor", Set.of(SkillTrigger.ON_COMBAT), 100);

        // 1. 20% CDR -> 100 * (1 - 0.20) = 80 ticks cooldown
        assertTrue(cooldowns.tryAcquire(entityId, skill, 0L, 0.20));
        assertFalse(cooldowns.isReady(entityId, "meteor", 79L));
        assertTrue(cooldowns.isReady(entityId, "meteor", 80L));

        // 2. 50% CDR requested -> capped at 40% (0.40) -> 100 * (1 - 0.40) = 60 ticks
        assertTrue(cooldowns.tryAcquire(entityId, skill, 100L, 0.50));
        assertFalse(cooldowns.isReady(entityId, "meteor", 159L));
        assertTrue(cooldowns.isReady(entityId, "meteor", 160L));
    }

    @Test
    void testStatPlaceholders() {
        ActiveLunarMob mob = createMob("stat_boss", 100.0, 100.0);
        mob.stats().setBase(StatType.DAMAGE, 150.0);
        mob.stats().setBase(StatType.CRIT_CHANCE, 0.35);
        mob.stats().setBase(StatType.LIFESTEAL, 0.15);

        String template = "Boss Info: DMG=<caster.stat.damage>, CRIT=<caster.stat.crit_chance>, STEAL=<caster.stat.lifesteal>";
        String resolved = LunarPlaceholderResolver.resolve(template, mob, null, null);

        assertEquals("Boss Info: DMG=150, CRIT=0.35, STEAL=0.15", resolved);
    }
}
