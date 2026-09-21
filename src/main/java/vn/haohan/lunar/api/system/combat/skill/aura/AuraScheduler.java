package vn.haohan.lunar.api.system.combat.skill.aura;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, high-performance tick scheduler for active auras.
 * Prevents Bukkit task proliferation by managing all active auras in a single tick pass.
 */
public final class AuraScheduler {

    private final Map<String, ActiveAura> auras = new ConcurrentHashMap<>();

    /**
     * Applies an aura to the specified attachment point according to the aura's configured StackMode.
     */
    public ActiveAura applyAura(AuraDefinition definition, AuraAttachment attachment, UUID ownerId, long currentTick) {
        Objects.requireNonNull(definition, "AuraDefinition must not be null");
        Objects.requireNonNull(attachment, "AuraAttachment must not be null");

        String key = buildKey(attachment.attachmentKey(), definition.id());
        ActiveAura existing = auras.get(key);

        if (existing != null && !existing.isExpired()) {
            switch (definition.stackMode()) {
                case REFRESH -> existing.refresh(currentTick);
                case ADD_STACK -> existing.addStack(currentTick);
                case IGNORE -> { /* Ignore new application */ }
            }
            return existing;
        }

        ActiveAura newAura = new ActiveAura(definition, attachment, ownerId, currentTick);
        auras.put(key, newAura);
        newAura.start();
        return newAura;
    }

    /**
     * Main tick dispatch invoked once per server tick by the plugin tick loop.
     */
    public void tick(long currentTick) {
        for (ActiveAura aura : auras.values()) {
            aura.tick(currentTick);
            if (aura.isExpired()) {
                auras.remove(buildKey(aura.attachment().attachmentKey(), aura.definition().id()), aura);
            }
        }
    }

    public void dispatchAttack(LivingEntity attacker, LivingEntity target, double damage) {
        if (attacker == null) return;
        String prefix = "entity:" + attacker.getUniqueId();
        for (ActiveAura aura : auras.values()) {
            if (!aura.isExpired() && (aura.attachment().attachmentKey().startsWith(prefix)
                    || (aura.ownerId() != null && aura.ownerId().equals(attacker.getUniqueId())))) {
                aura.onHit(target, damage);
            }
        }
    }

    public void dispatchDamaged(LivingEntity victim, Entity attacker, double damage) {
        if (victim == null) return;
        String prefix = "entity:" + victim.getUniqueId();
        for (ActiveAura aura : auras.values()) {
            if (!aura.isExpired() && (aura.attachment().attachmentKey().startsWith(prefix)
                    || (aura.ownerId() != null && aura.ownerId().equals(victim.getUniqueId())))) {
                aura.onDamaged(attacker, damage);
            }
        }
    }

    public Optional<ActiveAura> getActive(AuraAttachment attachment, String auraId) {
        if (attachment == null || auraId == null) return Optional.empty();
        return Optional.ofNullable(auras.get(buildKey(attachment.attachmentKey(), auraId)));
    }

    private static String buildKey(String attachmentKey, String auraId) {
        return attachmentKey + ":" + auraId.toLowerCase(Locale.ROOT);
    }

    public int cancelAll(UUID entityId) {
        if (entityId == null) return 0;
        String prefix = "entity:" + entityId;
        int count = 0;
        Iterator<ActiveAura> iterator = auras.values().iterator();
        while (iterator.hasNext()) {
            ActiveAura aura = iterator.next();
            if (aura.attachment().attachmentKey().startsWith(prefix)
                    || (aura.ownerId() != null && aura.ownerId().equals(entityId))) {
                aura.expire();
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    public int size() {
        return auras.size();
    }

    public void clear() {
        for (ActiveAura aura : auras.values()) {
            aura.expire();
        }
        auras.clear();
    }

    public boolean hasAura(UUID entityId, String auraId) {
        if (entityId == null || auraId == null) return false;
        String key = "entity:" + entityId + ":" + auraId.toLowerCase(Locale.ROOT);
        ActiveAura existing = auras.get(key);
        return existing != null && !existing.isExpired();
    }
}
