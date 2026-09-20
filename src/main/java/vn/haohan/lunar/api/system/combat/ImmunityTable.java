package vn.haohan.lunar.api.system.combat;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe table tracking active temporary immunities for custom mobs.
 * Tracks immunity by DamageCause, DamageType, or Skill identifier with tick expiration.
 */
public final class ImmunityTable {

    private final Map<DamageCause, Long> causeImmunities = new ConcurrentHashMap<>();
    private final Map<DamageType, Long> typeImmunities = new ConcurrentHashMap<>();
    private final Map<String, Long> skillImmunities = new ConcurrentHashMap<>();
    private final LongSupplier tickSupplier;

    public ImmunityTable(LongSupplier tickSupplier) {
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "Tick supplier must not be null");
    }

    public ImmunityTable() {
        this(System::currentTimeMillis);
    }

    public void addCauseImmunity(DamageCause cause, long durationTicks) {
        addCauseImmunity(cause, durationTicks, tickSupplier.getAsLong());
    }

    public void addCauseImmunity(DamageCause cause, long durationTicks, long currentTick) {
        if (cause != null && durationTicks > 0) {
            causeImmunities.put(cause, currentTick + durationTicks);
        }
    }

    public void addTypeImmunity(DamageType type, long durationTicks) {
        addTypeImmunity(type, durationTicks, tickSupplier.getAsLong());
    }

    public void addTypeImmunity(DamageType type, long durationTicks, long currentTick) {
        if (type != null && durationTicks > 0) {
            typeImmunities.put(type, currentTick + durationTicks);
        }
    }

    public void addSkillImmunity(String skillId, long durationTicks) {
        addSkillImmunity(skillId, durationTicks, tickSupplier.getAsLong());
    }

    public void addSkillImmunity(String skillId, long durationTicks, long currentTick) {
        if (skillId != null && !skillId.isBlank() && durationTicks > 0) {
            skillImmunities.put(skillId.trim().toLowerCase(Locale.ROOT), currentTick + durationTicks);
        }
    }

    public boolean hasCauseImmunity(DamageCause cause, long currentTick) {
        if (cause == null) return false;
        Long expire = causeImmunities.get(cause);
        if (expire == null) return false;
        if (currentTick <= expire) return true;
        causeImmunities.remove(cause, expire);
        return false;
    }

    public boolean hasTypeImmunity(DamageType type, long currentTick) {
        if (type == null) return false;
        Long expire = typeImmunities.get(type);
        if (expire == null) return false;
        if (currentTick <= expire) return true;
        typeImmunities.remove(type, expire);
        return false;
    }

    public boolean hasSkillImmunity(String skillId, long currentTick) {
        Long allExpire = skillImmunities.get("all");
        if (allExpire != null) {
            if (currentTick <= allExpire) return true;
            skillImmunities.remove("all", allExpire);
        }
        if (skillId == null || skillId.isBlank()) return false;
        Long expire = skillImmunities.get(skillId.trim().toLowerCase(Locale.ROOT));
        if (expire == null) return false;
        if (currentTick <= expire) return true;
        skillImmunities.remove(skillId.trim().toLowerCase(Locale.ROOT), expire);
        return false;
    }

    /**
     * Checks if the given damage context is blocked by this immunity table.
     *
     * @param context the context to test
     * @param currentTick current tick or timestamp
     * @return Optional containing the reason for immunity if immune, or empty if vulnerable
     */
    public Optional<String> checkImmunity(DamageContext context, long currentTick) {
        Objects.requireNonNull(context, "DamageContext must not be null");

        if (hasCauseImmunity(context.cause(), currentTick)) {
            return Optional.of("Victim is immune to " + context.cause());
        }
        if (hasTypeImmunity(context.damageType(), currentTick)) {
            return Optional.of("Victim is immune to " + context.damageType() + " damage");
        }
        if (context.skillId().isPresent()) {
            String skill = context.skillId().get();
            if (hasSkillImmunity(skill, currentTick)) {
                return Optional.of("Victim is immune to skill " + skill);
            }
        }
        if (hasSkillImmunity("all", currentTick) && context.skillId().isPresent()) {
            return Optional.of("Victim is immune to all skills");
        }
        return Optional.empty();
    }

    public Optional<String> checkImmunity(DamageContext context) {
        return checkImmunity(context, tickSupplier.getAsLong());
    }

    public void cleanExpired(long currentTick) {
        causeImmunities.entrySet().removeIf(e -> e.getValue() < currentTick);
        typeImmunities.entrySet().removeIf(e -> e.getValue() < currentTick);
        skillImmunities.entrySet().removeIf(e -> e.getValue() < currentTick);
    }

    public void clear() {
        causeImmunities.clear();
        typeImmunities.clear();
        skillImmunities.clear();
    }
}
