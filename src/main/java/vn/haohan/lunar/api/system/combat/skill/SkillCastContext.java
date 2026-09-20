package vn.haohan.lunar.api.system.combat.skill;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.api.system.variable.VariableHolder;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-cast execution state. Supports hierarchical cancellation tokens, deep cloning for composite subskills,
 * dynamic parameters, and depth/duration bounds.
 */
public final class SkillCastContext {

    public static final int MAX_DEPTH = 5;
    public static final long MAX_DURATION_TICKS = 600L;

    private final ActiveMob caster;
    private final SkillDefinition skill;
    private final SkillTrigger trigger;
    private final long startedAtTick;
    private final Entity triggerEntity;
    private final Location origin;
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final VariableHolder castVariables;
    private final AtomicBoolean cancellationToken;
    private final Map<String, Object> parameters = new ConcurrentHashMap<>();
    private final int depth;
    private volatile boolean lastStepSuccess = true;

    public SkillCastContext(ActiveMob caster, SkillDefinition skill,
                            SkillTrigger trigger, long startedAtTick) {
        this(caster, skill, trigger, startedAtTick, null, null);
    }

    public SkillCastContext(ActiveMob caster, SkillDefinition skill,
                            SkillTrigger trigger, long startedAtTick,
                            Entity triggerEntity, Location origin) {
        this(caster, skill, trigger, startedAtTick, triggerEntity, origin,
                new VariableHolder(), new AtomicBoolean(false), 0, Map.of());
    }

    public SkillCastContext(ActiveMob caster, SkillDefinition skill,
                            SkillTrigger trigger, long startedAtTick,
                            Entity triggerEntity, Location origin,
                            VariableHolder castVariables,
                            AtomicBoolean cancellationToken,
                            int depth,
                            Map<String, Object> parameters) {
        this.caster = Objects.requireNonNull(caster, "Caster must not be null");
        this.skill = Objects.requireNonNull(skill, "Skill must not be null");
        this.trigger = Objects.requireNonNull(trigger, "Trigger must not be null");
        if (!skill.triggers().contains(trigger)) {
            throw new IllegalArgumentException("Trigger is not configured for skill " + skill.id());
        }
        this.startedAtTick = startedAtTick;
        this.triggerEntity = triggerEntity;
        this.origin = origin != null ? origin : safeGetLocation(caster.entity());
        this.castVariables = castVariables != null ? castVariables : new VariableHolder();
        this.cancellationToken = cancellationToken != null ? cancellationToken : new AtomicBoolean(false);
        this.depth = Math.max(0, depth);
        if (parameters != null) {
            this.parameters.putAll(parameters);
        }
    }

    public ActiveMob caster() { return caster; }
    public UUID casterId() { return caster.entityId(); }
    public LivingEntity casterEntity() { return caster.entity(); }
    public SkillDefinition skill() { return skill; }
    public SkillTrigger trigger() { return trigger; }
    public long startedAtTick() { return startedAtTick; }
    public Entity triggerEntity() { return triggerEntity; }
    public Location origin() { return origin; }
    public VariableHolder castVariables() { return castVariables; }
    public AtomicBoolean cancellationToken() { return cancellationToken; }
    public int depth() { return depth; }

    public boolean isCancelled() {
        if (cancellationToken.get()) return true;
        if (caster != null && caster.entity() != null) {
            try {
                return !caster.entity().isValid() || caster.entity().isDead();
            } catch (Exception ignored) {
                // Mock or test entity environment
            }
        }
        return false;
    }

    public void cancel() {
        cancellationToken.set(true);
    }

    public boolean isExpired(long currentTick) {
        return (currentTick - startedAtTick) > MAX_DURATION_TICKS;
    }

    public boolean lastStepSuccess() {
        return lastStepSuccess;
    }

    public void setLastStepSuccess(boolean success) {
        this.lastStepSuccess = success;
    }

    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public Object getParameter(String key) {
        if (key == null) return null;
        return parameters.get(key);
    }

    public void putParameter(String key, Object value) {
        Objects.requireNonNull(key, "Parameter key must not be null");
        if (value == null) {
            parameters.remove(key);
        } else {
            parameters.put(key, value);
        }
    }

    public void putAllParameters(Map<String, ?> params) {
        if (params != null) {
            params.forEach((k, v) -> {
                if (k != null && v != null) {
                    parameters.put(k, v);
                }
            });
        }
    }

    public void put(String key, Object value) {
        Objects.requireNonNull(key, "Context value key must not be null");
        Objects.requireNonNull(value, "Context value must not be null");
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }

    /**
     * Creates a child execution context sharing the parent's cancellation token,
     * with an incremented depth and deep-copied variables and parameters.
     */
    public SkillCastContext deepClone() {
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("Max skill recursion depth (" + MAX_DEPTH + ") exceeded");
        }
        Location clonedOrigin = origin != null ? origin.clone() : null;
        SkillCastContext clone = new SkillCastContext(
                caster, skill, trigger, startedAtTick,
                triggerEntity, clonedOrigin,
                castVariables.copy(), cancellationToken,
                depth + 1, parameters
        );
        clone.values.putAll(this.values);
        clone.lastStepSuccess = this.lastStepSuccess;
        return clone;
    }

    private static Location safeGetLocation(LivingEntity entity) {
        if (entity == null) return null;
        try {
            return entity.getLocation();
        } catch (Exception ignored) {
            return null;
        }
    }
}
