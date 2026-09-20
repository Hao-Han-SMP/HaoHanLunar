package vn.haohan.lunar.api.system.world.totem;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Live instance of a summonable totem entity in the world.
 */
public final class ActiveTotem {

    private final UUID totemId;
    private final UUID casterId;
    private final TotemDefinition definition;
    private final Location location;
    private final Entity entity;
    private double currentHealth;
    private final long spawnTick;
    private long lastPulseTick;
    private boolean isDestroyed = false;
    private boolean isExpired = false;

    public ActiveTotem(
            UUID totemId,
            UUID casterId,
            TotemDefinition definition,
            Location location,
            Entity entity,
            long spawnTick
    ) {
        this.totemId = totemId != null ? totemId : UUID.randomUUID();
        this.casterId = casterId;
        this.definition = Objects.requireNonNull(definition, "Totem definition must not be null");
        this.location = location != null ? location.clone() : null;
        this.entity = entity;
        this.currentHealth = definition.maxHealth();
        this.spawnTick = spawnTick;
        this.lastPulseTick = spawnTick;
        updateDisplay();
    }

    public UUID totemId() {
        return totemId;
    }

    public UUID casterId() {
        return casterId;
    }

    public TotemDefinition definition() {
        return definition;
    }

    public Location location() {
        return location != null ? location.clone() : null;
    }

    public Entity entity() {
        return entity;
    }

    public double currentHealth() {
        return currentHealth;
    }

    public double maxHealth() {
        return definition.maxHealth();
    }

    public long spawnTick() {
        return spawnTick;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public boolean isExpired() {
        return isExpired;
    }

    public boolean isActive() {
        return !isDestroyed && !isExpired;
    }

    /**
     * Ticks the totem: checks expiration and interval-based pulse skills.
     */
    public void tick(long currentTick, BiConsumer<String, TargetRef> skillDispatcher) {
        if (!isActive()) return;

        // Check duration expiration
        if (currentTick >= spawnTick + definition.durationTicks()) {
            expire(skillDispatcher);
            return;
        }

        // Check pulse interval
        if (currentTick - lastPulseTick >= definition.intervalTicks()) {
            lastPulseTick = currentTick;
            pulse(skillDispatcher);
        }
    }

    public void pulse(BiConsumer<String, TargetRef> skillDispatcher) {
        if (!isActive() || definition.onPulseSkill() == null || definition.onPulseSkill().isBlank()) {
            return;
        }
        if (skillDispatcher != null) {
            Location loc = location();
            TargetRef targetRef = loc != null ? TargetRef.of(loc) : (entity != null ? TargetRef.of(entity) : null);
            if (targetRef != null) {
                skillDispatcher.accept(definition.onPulseSkill(), targetRef);
            }
        }
    }

    /**
     * Applies damage to the totem.
     */
    public boolean damage(double amount, LivingEntity damager, BiConsumer<String, TargetRef> skillDispatcher) {
        if (!isActive() || amount <= 0) return false;

        this.currentHealth = Math.max(0.0, this.currentHealth - amount);
        updateDisplay();

        if (this.currentHealth <= 0.0) {
            destroy(damager, skillDispatcher);
            return true;
        }
        return false;
    }

    public void destroy(LivingEntity killer, BiConsumer<String, TargetRef> skillDispatcher) {
        if (!isActive()) return;
        this.isDestroyed = true;

        if (definition.onDestroySkill() != null && !definition.onDestroySkill().isBlank() && skillDispatcher != null) {
            Location loc = location();
            TargetRef targetRef = killer != null ? TargetRef.of(killer) : (loc != null ? TargetRef.of(loc) : null);
            if (targetRef != null) {
                skillDispatcher.accept(definition.onDestroySkill(), targetRef);
            }
        }

        removeEntity();
    }

    public void expire(BiConsumer<String, TargetRef> skillDispatcher) {
        if (!isActive()) return;
        this.isExpired = true;
        removeEntity();
    }

    public void updateDisplay() {
        if (entity == null) return;
        try {
            int percent = (int) Math.ceil((currentHealth / definition.maxHealth()) * 100);
            String name = "\u00a7c" + definition.type() + " \u00a7f[" + percent + "%]";
            entity.setCustomName(name);
            entity.setCustomNameVisible(true);
        } catch (Throwable ignored) {}
    }

    public void removeEntity() {
        if (entity != null) {
            try {
                if (entity.isValid() && !entity.isDead()) {
                    entity.remove();
                }
            } catch (Throwable ignored) {}
        }
    }
}
