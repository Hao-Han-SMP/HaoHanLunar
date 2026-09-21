package vn.haohan.lunar.api.system.combat;

import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Immutable table of damage multipliers based on DamageCause and attacker EntityType.
 * Maps directly to MythicMobs DamageModifiers and EntityDamageModifiers configurations.
 */
public final class DamageModifierTable {

    private final Map<DamageCause, Double> causeModifiers;
    private final Map<EntityType, Double> entityModifiers;

    public DamageModifierTable(Map<DamageCause, Double> causeModifiers, Map<EntityType, Double> entityModifiers) {
        this.causeModifiers = causeModifiers != null ? Map.copyOf(causeModifiers) : Map.of();
        this.entityModifiers = entityModifiers != null ? Map.copyOf(entityModifiers) : Map.of();
    }

    public static DamageModifierTable empty() {
        return new DamageModifierTable(Map.of(), Map.of());
    }

    /**
     * Parses DamageModifierTable from configuration representations (Lists, Maps, or Strings).
     */
    public static DamageModifierTable fromConfig(Object causeModsObj, Object entityModsObj) {
        Map<DamageCause, Double> causes = parseCauseModifiers(causeModsObj);
        Map<EntityType, Double> entities = parseEntityModifiers(entityModsObj);
        if (causes.isEmpty() && entities.isEmpty()) {
            return empty();
        }
        return new DamageModifierTable(causes, entities);
    }

    private static Map<DamageCause, Double> parseCauseModifiers(Object raw) {
        if (raw == null) return Map.of();
        Map<DamageCause, Double> result = new EnumMap<>(DamageCause.class);
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    try {
                        DamageCause cause = DamageCause.valueOf(entry.getKey().toString().trim().toUpperCase(Locale.ROOT));
                        double val = Double.parseDouble(entry.getValue().toString().trim());
                        result.put(cause, Math.max(0.0, val));
                    }
                    catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } else if (raw instanceof Iterable<?> list) {
            for (Object item : list) {
                parseModifierEntry(item, (key, val) -> {
                    try {
                        DamageCause cause = DamageCause.valueOf(key.toUpperCase(Locale.ROOT));
                        result.put(cause, Math.max(0.0, val));
                    }
                    catch (IllegalArgumentException ignored) {
                    }
                });
            }
        } else if (raw instanceof String str) {
            for (String entry : str.split("[,;\\n]")) {
                if (!entry.isBlank()) {
                    parseModifierEntry(entry, (key, val) -> {
                        try {
                            DamageCause cause = DamageCause.valueOf(key.toUpperCase(Locale.ROOT));
                            result.put(cause, Math.max(0.0, val));
                        }
                        catch (IllegalArgumentException ignored) {
                        }
                    });
                }
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static Map<EntityType, Double> parseEntityModifiers(Object raw) {
        if (raw == null) return Map.of();
        Map<EntityType, Double> result = new EnumMap<>(EntityType.class);
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    try {
                        EntityType type = EntityType.valueOf(entry.getKey().toString().trim().toUpperCase(Locale.ROOT));
                        double val = Double.parseDouble(entry.getValue().toString().trim());
                        result.put(type, Math.max(0.0, val));
                    }
                    catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } else if (raw instanceof Iterable<?> list) {
            for (Object item : list) {
                parseModifierEntry(item, (key, val) -> {
                    try {
                        EntityType type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                        result.put(type, Math.max(0.0, val));
                    }
                    catch (IllegalArgumentException ignored) {
                    }
                });
            }
        } else if (raw instanceof String str) {
            for (String entry : str.split("[,;\\n]")) {
                if (!entry.isBlank()) {
                    parseModifierEntry(entry, (key, val) -> {
                        try {
                            EntityType type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                            result.put(type, Math.max(0.0, val));
                        }
                        catch (IllegalArgumentException ignored) {
                        }
                    });
                }
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static void parseModifierEntry(Object item, BiConsumer<String, Double> consumer) {
        if (item == null) return;
        String s = item.toString().trim();
        if (s.isEmpty()) return;
        String[] parts = s.split("[:\\s]+", 2);
        if (parts.length == 2) {
            try {
                double val = Double.parseDouble(parts[1].trim());
                consumer.accept(parts[0].trim(), val);
            }
            catch (NumberFormatException ignored) {
            }
        }
    }

    public double getCauseModifier(DamageCause cause) {
        if (cause == null) return 1.0;
        return causeModifiers.getOrDefault(cause, 1.0);
    }

    public double getEntityModifier(EntityType entityType) {
        if (entityType == null) return 1.0;
        return entityModifiers.getOrDefault(entityType, 1.0);
    }

    /**
     * Calculates combined multiplier for the given cause and attacker entity type.
     * Result is clamped to non-negative (>= 0.0).
     */
    public double calculateMultiplier(DamageCause cause, EntityType attackerType) {
        double causeMod = getCauseModifier(cause);
        double entityMod = attackerType != null ? getEntityModifier(attackerType) : 1.0;
        return Math.max(0.0, causeMod * entityMod);
    }

    /**
     * Applies this modifier table to a DamageContext.
     */
    public void apply(DamageContext context) {
        Objects.requireNonNull(context, "DamageContext must not be null");
        EntityType attackerType = context.attacker() != null ? context.attacker().getType() : null;
        double multiplier = calculateMultiplier(context.cause(), attackerType);
        if (multiplier != 1.0) {
            context.multiplyDamage(multiplier);
        }
    }

    public Map<DamageCause, Double> causeModifiers() {
        return causeModifiers;
    }

    public Map<EntityType, Double> entityModifiers() {
        return entityModifiers;
    }
}
