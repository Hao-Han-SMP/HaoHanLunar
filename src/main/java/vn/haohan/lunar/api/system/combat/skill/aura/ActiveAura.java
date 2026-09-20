package vn.haohan.lunar.api.system.combat.skill.aura;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime state of an instantiated aura attached to an entity, location, or bone.
 */
public final class ActiveAura {

    private final UUID instanceId;
    private final AuraDefinition definition;
    private final AuraAttachment attachment;
    private final UUID ownerId;
    private final long startTick;
    private long endTick;
    private int currentStacks;
    private volatile boolean expired;

    public ActiveAura(AuraDefinition definition, AuraAttachment attachment, UUID ownerId, long currentTick) {
        this.instanceId = UUID.randomUUID();
        this.definition = Objects.requireNonNull(definition, "Aura definition must not be null");
        this.attachment = Objects.requireNonNull(attachment, "Attachment must not be null");
        this.ownerId = ownerId;
        this.startTick = currentTick;
        this.endTick = currentTick + definition.durationTicks();
        this.currentStacks = 1;
        this.expired = false;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public AuraDefinition definition() {
        return definition;
    }

    public String auraId() {
        return definition.id();
    }

    public AuraAttachment attachment() {
        return attachment;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public long startTick() {
        return startTick;
    }

    public long endTick() {
        return endTick;
    }

    public int currentStacks() {
        return currentStacks;
    }

    public boolean isExpired() {
        return expired;
    }

    public void refresh(long currentTick) {
        this.endTick = currentTick + definition.durationTicks();
    }

    public void addStack(long currentTick) {
        if (currentStacks < definition.maxStacks()) {
            currentStacks++;
        }
        refresh(currentTick);
    }

    public void start() {
        for (AuraComponent component : definition.components()) {
            try {
                component.onStart(this);
            } catch (Exception ignored) {}
        }
    }

    public void tick(long currentTick) {
        if (expired) return;
        if (!attachment.isValid() || currentTick >= endTick) {
            expire();
            return;
        }
        long elapsed = currentTick - startTick;
        if (elapsed % definition.intervalTicks() == 0) {
            for (AuraComponent component : definition.components()) {
                try {
                    component.onTick(this, currentTick);
                } catch (Exception ignored) {}
            }
        }
    }

    public void onHit(LivingEntity target, double damage) {
        if (expired) return;
        for (AuraComponent component : definition.components()) {
            try {
                component.onHit(this, target, damage);
            } catch (Exception ignored) {}
        }
    }

    public void onDamaged(Entity attacker, double damage) {
        if (expired) return;
        for (AuraComponent component : definition.components()) {
            try {
                component.onDamaged(this, attacker, damage);
            } catch (Exception ignored) {}
        }
    }

    public void expire() {
        if (expired) return;
        expired = true;
        for (AuraComponent component : definition.components()) {
            try {
                component.onExpire(this);
            } catch (Exception ignored) {}
        }
    }
}
