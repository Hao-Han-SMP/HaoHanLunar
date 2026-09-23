package vn.haohan.lunar.api.system.mob.stat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe container and calculator for base stats and dynamic stat modifiers.
 */
public final class StatHolder {

    private final Map<StatType, Double> baseStats = new ConcurrentHashMap<>();
    private final List<StatModifier> modifiers = new CopyOnWriteArrayList<>();

    public StatHolder() {
        baseStats.put(StatType.CRIT_DAMAGE, 1.5);
    }

    public void setBase(StatType type, double value) {
        Objects.requireNonNull(type, "StatType must not be null");
        baseStats.put(type, value);
    }

    public double getBase(StatType type) {
        if (type == null) return 0.0;
        return baseStats.getOrDefault(type, type == StatType.CRIT_DAMAGE ? 1.5 : 0.0);
    }

    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "Modifier must not be null");
        modifiers.add(modifier);
    }

    public boolean removeModifier(String sourceId) {
        if (sourceId == null) return false;
        return modifiers.removeIf(m -> m.sourceId().equalsIgnoreCase(sourceId));
    }

    public void cleanupExpired(long currentTick) {
        modifiers.removeIf(m -> m.isExpired(currentTick));
    }

    public List<StatModifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    /**
     * Calculates the immutable StatSnapshot applying the 3 modifier operations:
     * {@code result = (base + sum(FLAT)) * (1.0 + sum(PERCENT_ADD)) * product(1.0 + PERCENT_MULT)}
     * clamped to each stat's valid bounds.
     */
    public StatSnapshot snapshot(long currentTick) {
        cleanupExpired(currentTick);
        Map<StatType, Double> computed = new EnumMap<>(StatType.class);

        for (StatType type : StatType.values()) {
            double base = getBase(type);
            double flat = 0.0;
            double pctAdd = 0.0;
            double mult = 1.0;

            for (StatModifier m : modifiers) {
                if (m.statType() == type && !m.isExpired(currentTick)) {
                    switch (m.operation()) {
                        case FLAT -> flat += m.value();
                        case PERCENT_ADD -> pctAdd += m.value();
                        case PERCENT_MULT -> mult *= (1.0 + m.value());
                    }
                }
            }

            double total = (base + flat) * (1.0 + pctAdd) * mult;
            computed.put(type, type.clamp(total));
        }

        return new StatSnapshot(computed);
    }
}
