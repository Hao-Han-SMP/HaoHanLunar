package vn.haohan.lunar.api.system.combat.skill;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Central tick-based cooldown store with support for Cooldown Groups; no per-mob scheduler tasks are created. */
public final class CooldownRegistry {

    private final Map<Key, Long> expiryTicks = new ConcurrentHashMap<>();
    private final Map<Key, Long> groupExpiryTicks = new ConcurrentHashMap<>();

    public boolean isReady(UUID entityId, String skillId, long currentTick) {
        Key key = new Key(entityId, skillId);
        Long expiry = expiryTicks.get(key);
        if (expiry == null) {
            return true;
        }
        if (currentTick >= expiry) {
            expiryTicks.remove(key, expiry);
            return true;
        }
        return false;
    }

    public boolean isGroupReady(UUID entityId, String group, long currentTick) {
        if (group == null || group.isBlank()) return true;
        Key key = new Key(entityId, group.trim().toUpperCase(Locale.ROOT));
        Long expiry = groupExpiryTicks.get(key);
        if (expiry == null) {
            return true;
        }
        if (currentTick >= expiry) {
            groupExpiryTicks.remove(key, expiry);
            return true;
        }
        return false;
    }

    public void setGroupCooldown(UUID entityId, String group, long cooldownTicks, long currentTick) {
        if (entityId == null || group == null || group.isBlank() || cooldownTicks <= 0) return;
        Key key = new Key(entityId, group.trim().toUpperCase(Locale.ROOT));
        groupExpiryTicks.put(key, Math.addExact(currentTick, cooldownTicks));
    }

    /** Atomically checks and starts a skill cooldown. */
    public boolean tryAcquire(UUID entityId, SkillDefinition skill, long currentTick) {
        return tryAcquire(entityId, skill, currentTick, 0.0);
    }

    /**
     * Atomically checks and starts a skill cooldown applying Cooldown Reduction (CDR) and Cooldown Groups.
     *
     * @param entityId entity UUID
     * @param skill skill definition
     * @param currentTick current engine tick
     * @param cooldownReduction CDR ratio in range [0.0, 0.40]
     * @return true if acquired, false if still on cooldown or group cooldown
     */
    public boolean tryAcquire(UUID entityId, SkillDefinition skill, long currentTick, double cooldownReduction) {
        Objects.requireNonNull(skill, "Skill must not be null");
        Objects.requireNonNull(entityId, "Entity UUID must not be null");

        // Check group cooldown first if defined
        String group = skill.cooldownGroup();
        if (group != null && !isGroupReady(entityId, group, currentTick)) {
            return false;
        }

        if (skill.cooldownTicks() == 0) {
            if (group != null && skill.groupCooldownTicks() > 0) {
                setGroupCooldown(entityId, group, skill.groupCooldownTicks(), currentTick);
            }
            return true;
        }

        double cdr = Math.max(0.0, Math.min(0.40, cooldownReduction));
        long cd = Math.max(1L, Math.round(skill.cooldownTicks() * (1.0 - cdr)));
        Key key = new Key(entityId, skill.id());
        final boolean[] acquired = {false};
        expiryTicks.compute(key, (ignored, expiry) -> {
            if (expiry == null || currentTick >= expiry) {
                acquired[0] = true;
                return Math.addExact(currentTick, cd);
            }
            return expiry;
        });

        if (acquired[0] && group != null && skill.groupCooldownTicks() > 0) {
            setGroupCooldown(entityId, group, skill.groupCooldownTicks(), currentTick);
        }

        return acquired[0];
    }

    public void cancel(UUID entityId, String skillId) {
        expiryTicks.remove(new Key(entityId, skillId));
    }

    public void cancelGroup(UUID entityId, String group) {
        if (group != null) {
            groupExpiryTicks.remove(new Key(entityId, group.trim().toUpperCase(Locale.ROOT)));
        }
    }

    public void clear(UUID entityId) {
        if (entityId != null) {
            expiryTicks.keySet().removeIf(key -> key.entityId().equals(entityId));
            groupExpiryTicks.keySet().removeIf(key -> key.entityId().equals(entityId));
        }
    }

    public int size() {
        return expiryTicks.size();
    }

    private record Key(UUID entityId, String skillId) {
        private Key {
            Objects.requireNonNull(entityId, "Entity UUID must not be null");
            Objects.requireNonNull(skillId, "Skill ID must not be null");
        }
    }
}
