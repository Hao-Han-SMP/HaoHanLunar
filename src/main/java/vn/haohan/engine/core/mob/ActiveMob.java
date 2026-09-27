package vn.haohan.engine.core.mob;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import vn.haohan.engine.api.system.mob.IMob;
import vn.haohan.engine.api.system.mob.MobAttributeDefinition;
import vn.haohan.engine.api.system.mob.MobDefinition;
import vn.haohan.engine.api.system.mob.MobDefinitionId;
import vn.haohan.engine.api.system.mob.MobOptionDefinition;
import vn.haohan.engine.api.system.mob.ai.antistuck.AntiStuckController;
import vn.haohan.engine.api.system.mob.disguise.DisguiseData;
import vn.haohan.engine.api.system.mob.options.MobOptions;
import vn.haohan.engine.api.system.mob.phase.MobPhaseMachine;
import vn.haohan.engine.api.system.mob.stat.StatHolder;
import vn.haohan.engine.api.presentation.display.bossbar.LunarBossBarTracker;
import vn.haohan.engine.api.system.combat.DamageModifierTable;
import vn.haohan.engine.api.system.combat.ImmunityTable;
import vn.haohan.engine.api.system.combat.cc.CrowdControlTracker;
import vn.haohan.engine.api.system.combat.skill.interrupt.CancellationToken;
import vn.haohan.engine.api.system.combat.skill.interrupt.InterruptReason;
import vn.haohan.engine.api.system.combat.threat.ThreatTable;
import vn.haohan.engine.core.mob.MobIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actively managed in-game instance of a custom Lunar mob.
 */
public class ActiveMob implements IMob {

    private final UUID entityId;
    private final MobDefinitionId definitionId;
    private volatile MobDefinition definition;
    private final LivingEntity entity;
    private final MobIdentity identity;
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
    private volatile MobPhaseMachine phaseMachine;
    private final StatHolder stats = new StatHolder();

    private volatile double baseMaxHealth = -1;
    private volatile double baseDamage = -1;
    private volatile double currentHealthMultiplier = 1.0;
    private volatile double currentDamageMultiplier = 1.0;
    private volatile double dynamicCooldownReduction = 0.0;
    private volatile int lastTrackedPlayerCount = 0;
    private volatile boolean softLeashed = false;
    private volatile long invulnerableUntilTick = 0L;

    @Override
    public boolean isInvulnerable(long currentTick) {
        if (currentTick < invulnerableUntilTick) return true;
        if (entity != null) {
            try {
                return entity.isInvulnerable();
            } catch (Throwable ignored) {}
        }
        return false;
    }

    @Override
    public void setInvulnerableTicks(int ticks, long currentTick) {
        this.invulnerableUntilTick = currentTick + Math.max(0, ticks);
        if (entity != null) {
            try {
                entity.setInvulnerable(ticks > 0);
            } catch (Throwable ignored) {}
        }
    }
    private volatile Location spawnLocation = null;
    private volatile long damageCapWindowStartTick = 0L;
    private volatile double damageAccumulatedInWindow = 0.0;
    private final java.util.Set<CancellationToken> activeTokens = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String activeChannelingSkill = null;
    private volatile CancellationToken activeChannelingToken = null;

    public ActiveMob(LivingEntity entity, MobDefinition definition, MobIdentity identity) {
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
        try {
            this.spawnLocation = entity.getLocation() != null ? entity.getLocation().clone() : null;
        } catch (Throwable ignored) {
            this.spawnLocation = null;
        }
        initDamageModifiers(definition);
    }

    @Override
    public UUID entityId() { return entityId; }
    public MobDefinitionId definitionId() { return definitionId; }
    public MobDefinition definition() { return definition; }
    @Override
    public LivingEntity entity() { return entity; }
    public MobIdentity identity() { return identity; }
    @Override
    public AntiStuckController antiStuckController() { return antiStuckController; }
    @Override
    public StatHolder stats() { return stats; }

    @Override
    public String faction() {
        return options != null ? options.faction() : null;
    }

    public boolean isSameFaction(ActiveMob other) {
        if (other == null) return false;
        String f1 = this.faction();
        String f2 = other.faction();
        return f1 != null && !f1.isBlank() && f1.equalsIgnoreCase(f2);
    }

    public static Object findOption(Map<String, ?> options, String... candidateKeys) {
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
        if (def == null || def.options() == null) {
            this.damageModifiers = DamageModifierTable.empty();
            return;
        }
        Object causeMods = findOption(def.options(), "damagemodifiers", "damagemodifier", "damagemods");
        Object entityMods = findOption(def.options(), "entitydamagemodifiers", "entitydamagemodifier", "entitydamagemods");
        this.damageModifiers = DamageModifierTable.fromConfig(causeMods, entityMods);
    }

    @Override
    public String stance() { return stance; }
    @Override
    public void setStance(String stance) { this.stance = stance != null ? stance : "default"; }

    public boolean isUsingDamageSkill() { return usingDamageSkill; }
    public void setUsingDamageSkill(boolean usingDamageSkill) { this.usingDamageSkill = usingDamageSkill; }

    @Override
    public ImmunityTable immunityTable() { return immunityTable; }
    @Override
    public CrowdControlTracker crowdControl() { return crowdControl; }

    @Override
    public DamageModifierTable damageModifiers() { return damageModifiers; }
    @Override
    public void setDamageModifiers(DamageModifierTable damageModifiers) {
        this.damageModifiers = Objects.requireNonNull(damageModifiers, "Damage modifier table must not be null");
    }

    @Override
    public ThreatTable threatTable() { return threatTable; }

    public MobOptions options() { return options; }
    public void setOptions(MobOptions options) {
        this.options = options != null ? options : MobOptions.DEFAULT;
    }

    @Override
    public UUID parentUUID() { return parentUUID; }
    @Override
    public void setParentUUID(UUID parentUUID) { this.parentUUID = parentUUID; }

    @Override
    public LunarBossBarTracker bossBars() { return bossBars; }

    @Override
    public UUID mountUUID() { return mountUUID; }
    @Override
    public void setMountUUID(UUID mountUUID) { this.mountUUID = mountUUID; }

    @Override
    public Set<UUID> riderUUIDs() { return riderUUIDs; }

    @Override
    public boolean isMounted() { return mountUUID != null; }

    @Override
    public boolean hasRiders() { return !riderUUIDs.isEmpty(); }

    @Override
    public boolean isBerserk() { return berserk; }
    @Override
    public void setBerserk(boolean berserk) { this.berserk = berserk; }

    @Override
    public DisguiseData disguise() { return disguise; }
    public DisguiseData getDisguise() { return disguise; }
    @Override
    public void setDisguise(DisguiseData disguise) { this.disguise = disguise; }
    @Override
    public boolean isDisguised() { return disguise != null; }

    @Override
    public String activeModelId() { return activeModelId != null ? activeModelId : (definition != null ? definition.modelId().orElse(null) : null); }
    @Override
    public void setActiveModelId(String activeModelId) { this.activeModelId = activeModelId; }

    @Override
    public String activeModelState() { return activeModelState; }
    @Override
    public void setActiveModelState(String activeModelState) { this.activeModelState = activeModelState; }

    public vn.haohan.engine.api.system.mob.phase.MobPhaseMachine phaseMachine() { return phaseMachine; }
    public void setPhaseMachine(vn.haohan.engine.api.system.mob.phase.MobPhaseMachine phaseMachine) { this.phaseMachine = phaseMachine; }

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

    @Override
    public boolean isSoftLeashed() { return softLeashed; }
    @Override
    public void setSoftLeashed(boolean softLeashed) { this.softLeashed = softLeashed; }

    @Override
    public Location spawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation != null ? spawnLocation.clone() : null;
    }

    /**
     * Resolves the maximum health of this mob, inspecting live attributes first
     * and falling back to definition attributes if not initialized.
     */
    @Override
    public double maxHealth() {
        try {
            var attr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                return attr.getValue();
            }
        } catch (Throwable ignored) {}
        if (definition != null && definition.attributes() != null) {
            MobAttributeDefinition def = definition.attributes().get("max_health");
            if (def != null) {
                return def.baseValue();
            }
        }
        return entity != null ? entity.getHealth() : 20.0;
    }

    /**
     * Thread-safely records damage and clamps to a rolling cap per second (20 ticks).
     */
    public synchronized double applyDamageCap(double damage, double capPerSecond, long currentTick) {
        if (capPerSecond <= 0.0) {
            return damage;
        }
        if (currentTick - damageCapWindowStartTick >= 20L || currentTick < damageCapWindowStartTick) {
            damageCapWindowStartTick = currentTick;
            damageAccumulatedInWindow = 0.0;
        }
        double remaining = Math.max(0.0, capPerSecond - damageAccumulatedInWindow);
        double allowed = Math.min(damage, remaining);
        damageAccumulatedInWindow += allowed;
        return allowed;
    }

    /**
     * Fully resets a boss/mob back to its spawn location, clearing threat, soft leash,
     * restoring full health, and cancelling active channeling abilities.
     */
    @Override
    public void resetToSpawn() {
        long currentTick;
        try {
            currentTick = org.bukkit.Bukkit.getCurrentTick();
        } catch (Throwable t) {
            currentTick = System.currentTimeMillis() / 50L;
        }
        resetToSpawn(currentTick);
    }

    @Override
    public void resetToSpawn(long currentTick) {
        if (spawnLocation == null || entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        // 1. Interrupt active and channeling skills
        interruptActiveSkills(InterruptReason.COMMAND);

        // 2. Clear threat & target if configured
        if (options != null && options.resetThreatOnLeash()) {
            if (threatTable != null) {
                threatTable.clear();
            }
            if (entity instanceof org.bukkit.entity.Mob m) {
                try {
                    m.setTarget(null);
                } catch (Throwable ignored) {}
            }
        }

        // 3. Heal to max health if configured
        if (options != null && options.healOnLeash()) {
            double targetHealth = maxHealth();
            try {
                entity.setHealth(targetHealth);
            } catch (Throwable ignored) {}
        }

        // 4. Temporary invulnerability
        int invulTicks = options != null ? options.leashInvulnerableTicks() : 60;
        if (invulTicks > 0) {
            setInvulnerableTicks(invulTicks, currentTick);
        }

        // 5. Clear soft leash state
        setSoftLeashed(false);

        // 6. Teleport back to spawn location
        try {
            entity.teleport(spawnLocation);
        } catch (Throwable ignored) {}
    }

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

    @Override
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

    @Override
    public boolean isChanneling() {
        return activeChannelingSkill != null && activeChannelingToken != null && !activeChannelingToken.isCancelled();
    }

    @Override
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
    public void trigger(vn.haohan.engine.api.system.combat.skill.SkillTrigger trigger) {
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
