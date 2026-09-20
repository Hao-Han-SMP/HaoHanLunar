package vn.haohan.lunar.api.world.hazard;

import java.util.Map;

/**
 * Configuration definition for persistent AoE hazard zones or ground traps.
 */
public record HazardZoneDefinition(
        double radius,
        long durationTicks,
        long tickInterval,
        String particle,
        String onEnterSkill,
        String onTickSkill,
        String onExitSkill
) {
    public HazardZoneDefinition {
        radius = Math.max(0.5, radius);
        durationTicks = Math.max(1L, durationTicks);
        tickInterval = Math.max(1L, tickInterval);
    }

    public static HazardZoneDefinition fromMap(Map<String, Object> map) {
        if (map == null) {
            return new HazardZoneDefinition(5.0, 200L, 20L, "DUST_PLUME", null, null, null);
        }
        double radius = 5.0;
        Object radObj = map.getOrDefault("radius", map.get("r"));
        if (radObj instanceof Number n) {
            radius = n.doubleValue();
        }

        long duration = 200L;
        Object durObj = map.getOrDefault("duration", map.get("durationTicks"));
        if (durObj instanceof Number n) {
            duration = n.longValue();
        }

        long interval = 20L;
        Object intObj = map.getOrDefault("interval", map.getOrDefault("tickInterval", map.get("rate")));
        if (intObj instanceof Number n) {
            interval = n.longValue();
        }

        String particle = (String) map.getOrDefault("particle", "DUST_PLUME");
        String onEnter = (String) map.getOrDefault("onEnterSkill", map.getOrDefault("on-enter-skill", map.get("onenter")));
        String onTick = (String) map.getOrDefault("onTickSkill", map.getOrDefault("on-tick-skill", map.get("ontick")));
        String onExit = (String) map.getOrDefault("onExitSkill", map.getOrDefault("on-exit-skill", map.get("onexit")));

        return new HazardZoneDefinition(radius, duration, interval, particle, onEnter, onTick, onExit);
    }
}
