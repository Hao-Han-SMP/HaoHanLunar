package vn.haohan.lunar.api.system.world.totem;

import java.util.Locale;
import java.util.Map;

/**
 * Immutable definition for a summonable destructible totem entity.
 */
public record TotemDefinition(
        String type,
        double maxHealth,
        long durationTicks,
        long intervalTicks,
        String onPulseSkill,
        String onDestroySkill,
        String modelId
) {
    public TotemDefinition {
        type = type == null || type.isBlank() ? "LUNAR_CRYSTAL" : type.trim().toUpperCase(Locale.ROOT);
        maxHealth = Math.max(1.0, maxHealth);
        durationTicks = Math.max(1L, durationTicks);
        intervalTicks = Math.max(1L, intervalTicks);
    }

    public static TotemDefinition fromMap(Map<String, Object> map) {
        if (map == null) {
            return new TotemDefinition("LUNAR_CRYSTAL", 100.0, 400L, 40L, null, null, null);
        }
        String type = (String) map.getOrDefault("type", map.getOrDefault("entity_type", "LUNAR_CRYSTAL"));
        double health = 100.0;
        Object healthObj = map.getOrDefault("health", map.get("max_health"));
        if (healthObj instanceof Number n) {
            health = n.doubleValue();
        }

        long duration = 400L;
        Object durObj = map.getOrDefault("duration", map.get("durationTicks"));
        if (durObj instanceof Number n) {
            duration = n.longValue();
        }

        long interval = 40L;
        Object intObj = map.getOrDefault("interval", map.get("intervalTicks"));
        if (intObj instanceof Number n) {
            interval = n.longValue();
        }

        String onPulse = (String) map.getOrDefault("onPulseSkill", map.getOrDefault("on-pulse-skill", map.get("onpulse")));
        String onDestroy = (String) map.getOrDefault("onDestroySkill", map.getOrDefault("on-destroy-skill", map.get("ondestroy")));
        String model = (String) map.getOrDefault("model", map.getOrDefault("model_id", map.get("modelId")));

        return new TotemDefinition(type, health, duration, interval, onPulse, onDestroy, model);
    }
}
