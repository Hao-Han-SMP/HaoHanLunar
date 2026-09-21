package vn.haohan.lunar.core.subsystem.mob;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.mob.Mob;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobOptionDefinition;
import vn.haohan.lunar.api.mob.ai.antistuck.AntiStuckController;
import vn.haohan.lunar.api.mob.disguise.DisguiseData;
import vn.haohan.lunar.api.mob.options.MobOptions;
import vn.haohan.lunar.api.mob.stat.StatHolder;
import vn.haohan.lunar.api.presentation.display.bossbar.LunarBossBarTracker;
import vn.haohan.lunar.api.system.combat.DamageModifierTable;
import vn.haohan.lunar.api.system.combat.ImmunityTable;
import vn.haohan.lunar.api.system.combat.cc.CrowdControlTracker;
import vn.haohan.lunar.api.system.combat.skill.interrupt.CancellationToken;
import vn.haohan.lunar.api.system.combat.skill.interrupt.InterruptReason;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.core.mob.LunarMobIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actively managed in-game instance of a custom Lunar mob.
 */
public class ActiveMob implements Mob {

    private final UUID entityId;
    private final MobDefinitionId definitionId;
    private volatile MobDefinition definition;
    private final LivingEntity entity;
    private final LunarMobIdentity identity;
    private volatile String stance = "default";
    private volatile boolean usingDamageSkill;
    private final ImmunityTable immunityTable = new ImmunityTable();
    private final CrowdControlTracker crowdControl = new CrowdControlTracker();
    private final AntiStuckController antiStuckController = new AntiStuckController();
    private volatile DamageModifierTable damageModifiers = DamageModifierTable.empty();
    private final ThreatTable threatTable;
    private volatile MobOptions options;
    private volatile UUID parentUUID;
    private final LunarBossBarTracker bossBars = new LunarBossBarTracker();
    private volatile UUID mountUUID;
    private final Set<UUID> riderUUIDs = ConcurrentHashMap.newKeySet();
    private volatile boolean berserk;
    private volatile DisguiseData disguise;
    private volatile String activeModelId = null;
    private volatile String activeModelState = null;
    private volatile vn.haohan.lunar.api.mob.phase.MobPhaseMachine phaseMachine;
    private final StatHolder stats = new StatHolder();

    private volatile double baseMaxHealth = -1;
    private volatile double baseDamage = -1;
    private volatile double currentHealthMultiplier = 1.0;
    private volatile double currentDamageMultiplier = 1.0;
    private volatile double dynamicCooldownReduction = 0.0;
    private volatile int lastTrackedPlayerCount = 0;
    private volatile boolean softLeashed = false;
    private final java.util.Set<CancellationToken> activeTokens = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String activeChannelingSkill = null;
    private volatile CancellationToken activeChannelingToken = null;

    public ActiveMob(LivingEntity entity, MobDefinition definition, LunarMobIdentity identity) {
        this.entity = Objects.requireNonNull(entity, "Entity must not be null");
        this.entityId = Objects.requireNonNull(entity.getUniqueId(), "Entity UUID must not be null");
        this.definition = Objects.requireNonNull(definition, "Mob definition must not be null");
        this.definitionId = definition.id();
        this.identity = Objects.requireNonNull(identity, "Mob identity must not be null");
        if (!definitionId.equals(new MobDefinitionId(identity.mobId()))) {
            throw new IllegalArgumentException("Mob definition ID does not match persistent identity");
        }
        this.threatTable = new ThreatTable(this.entityId);
        this.options = MobOptions.fromMap(definition.options());
        initDamageModifiers(definition);
    }

    public UUID entityId() { return entityId; }
    public MobDefinitionId definitionId() { return definitionId; }
    public MobDefinition definition() { return definition; }
    public LivingEntity entity() { return entity; }
    public LunarMobIdentity identity() { return identity; }
    public AntiStuckController antiStuckController() { return antiStuckController; }
    public StatHolder stats() { return stats; }

    static Object findOption(Map<String, ?> options, String... candidateKeys) {
        if (options == null) return null;
        for (String key : candidateKeys) {
            Object obj = options.get(key);
            if (obj != null) {
                return obj instanceof MobOptionDefinition mod ? mod.value() : obj;
            }
        }
        for (Map.Entry<String, ?> entry : options.entrySet()) {
            String entryKey = entry.getKey().replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
            for (String cand : candidateKeys) {
                String cleanCand = cand.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
                if (entryKey.equals(cleanCand)) {
                    Object val = entry.getValue();
                    return val instanceof MobOptionDefinition mod ? mod.value() : val;
                }
            }
        }
        return null;
    }

    public void updateDefinition(MobDefinition newDefinition) {
        if (newDefinition != null && newDefinition.id().equals(this.definitionId)) {
            this.definition = newDefinition;
            this.options = MobOptions.fromMap(newDefinition.options());
            initDamageModifiers(newDefinition);
        }
    }

    private void initDamageModifiers(MobDefinition def) {
        if (def == null || def.options() == null) return;
        Object dm = findOption(def.options(), "damagemodifiers", "damage_modifiers", "damage-modifiers");
        Object edm = findOption(def.options(), "entitydamagemodifiers", "entity_damage_modifiers", "entity-damage-modifiers");
        if (dm != null || edm != null) {
            this.damageModifiers = DamageModifierTable.fromConfig(dm, edm);
        }
    }

    public String stance() { return stance; }
    public void setStance(String stance) { this.stance = stance != null ? stance : "default"; }

    public boolean isUsingDamageSkill() { return usingDamageSkill; }
    public void setUsingDamageSkill(boolean usingDamageSkill) { this.usingDamageSkill = usingDamageSkill; }

    public ImmunityTable immunityTable() { return immunityTable; }
    public CrowdControlTracker crowdControl() { return crowdControl; }

    public DamageModifierTable damageModifiers() { return damageModifiers; }
    public void setDamageModifiers(DamageModifierTable damageModifiers) {
        this.damageModifiers = Objects.requireNonNull(damageModifiers, "Damage modifier table must not be null");
    }

    public ThreatTable threatTable() { return threatTable; }

    public vn.haohan.lunar.api.mob.phase.MobPhaseMachine phaseMachine() {
        return phaseMachine;
    }

    public void setPhaseMachine(vn.haohan.lunar.api.mob.phase.MobPhaseMachine phaseMachine) {
        this.phaseMachine = phaseMachine;
    }

    public MobOptions options() { return options; }
    public void setOptions(MobOptions options) {
        this.options = options != null ? options : MobOptions.DEFAULT;
    }

    public UUID parentUUID() { return parentUUID; }
    public void setParentUUID(UUID parentUUID) { this.parentUUID = parentUUID; }

    public LunarBossBarTracker bossBars() { return bossBars; }

    public UUID mountUUID() { return mountUUID; }
    public void setMountUUID(UUID mountUUID) { this.mountUUID = mountUUID; }

    public Set<UUID> riderUUIDs() { return riderUUIDs; }

    public boolean isBerserk() { return berserk; }
    public void setBerserk(boolean berserk) { this.berserk = berserk; }

    public DisguiseData getDisguise() { return disguise; }
    public void setDisguise(DisguiseData disguise) { this.disguise = disguise; }
    public boolean isDisguised() { return disguise != null; }

    public String activeModelId() { return activeModelId != null ? activeModelId : (definition != null ? definition.modelId().orElse(null) : null); }
    public void setActiveModelId(String activeModelId) { this.activeModelId = activeModelId; }

    public String activeModelState() { return activeModelState; }
    public void setActiveModelState(String activeModelState) { this.activeModelState = activeModelState; }

    public double baseMaxHealth() { return baseMaxHealth; }
    public void setBaseMaxHealth(double baseMaxHealth) { this.baseMaxHealth = baseMaxHealth; }

    public double baseDamage() { return baseDamage; }
    public void setBaseDamage(double baseDamage) { this.baseDamage = baseDamage; }

    public double currentHealthMultiplier() { return currentHealthMultiplier; }
    public void setCurrentHealthMultiplier(double currentHealthMultiplier) { this.currentHealthMultiplier = currentHealthMultiplier; }

    public double currentDamageMultiplier() { return currentDamageMultiplier; }
    public void setCurrentDamageMultiplier(double currentDamageMultiplier) { this.currentDamageMultiplier = currentDamageMultiplier; }

    public double dynamicCooldownReduction() { return dynamicCooldownReduction; }
    public void setDynamicCooldownReduction(double dynamicCooldownReduction) { this.dynamicCooldownReduction = dynamicCooldownReduction; }

    public int lastTrackedPlayerCount() { return lastTrackedPlayerCount; }
    public void setLastTrackedPlayerCount(int lastTrackedPlayerCount) { this.lastTrackedPlayerCount = lastTrackedPlayerCount; }

    public boolean isSoftLeashed() { return softLeashed; }
    public void setSoftLeashed(boolean softLeashed) { this.softLeashed = softLeashed; }

    public CancellationToken registerSkillExecution(String skillId, boolean isChanneling, boolean overrideChanneling) {
        if (isChanneling) {
            if (activeChannelingSkill != null && activeChannelingToken != null && !activeChannelingToken.isCancelled()) {
                if (overrideChanneling) {
                    interruptActiveSkills(InterruptReason.COMMAND);
                } else {
                    return null;
                }
            }
        }
        CancellationToken token = new CancellationToken();
        activeTokens.add(token);
        if (isChanneling) {
            this.activeChannelingSkill = skillId;
            this.activeChannelingToken = token;
        }
        return token;
    }

    public int interruptActiveSkills(InterruptReason reason) {
        int count = 0;
        for (CancellationToken token : java.util.List.copyOf(activeTokens)) {
            if (token.cancel(reason)) {
                count++;
            }
        }
        activeTokens.clear();
        activeChannelingSkill = null;
        activeChannelingToken = null;
        return count;
    }

    public boolean isChanneling() {
        return activeChannelingSkill != null && activeChannelingToken != null && !activeChannelingToken.isCancelled();
    }

    public String activeChannelingSkill() {
        return activeChannelingSkill;
    }

    // --- Mob API Implementation ---
    private volatile int mobLevel = 1;

    @Override
    public String mobId() {
        return identity != null ? identity.mobId() : (definitionId != null ? definitionId.value() : "unknown");
    }

    @Override
    public int level() {
        return mobLevel;
    }

    @Override
    public void setLevel(int level) {
        this.mobLevel = Math.max(1, level);
    }

    @Override
    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    @Override
    public void trigger(vn.haohan.lunar.api.system.combat.skill.SkillTrigger trigger) {
        // Trigger hook for public API
    }

    @Override
    public void castSkill(String skillName) {
        // Cast hook for public API
    }

    @Override
    public void despawn() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }
}
