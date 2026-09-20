package vn.haohan.lunar.core.features.boss.warden;

import org.bukkit.entity.EntityType;
import vn.haohan.lunar.api.system.config.ConfigLoadException;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.config.LunarYamlLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** YAML-backed Warden values with legacy WardenConstants as backward-compatible defaults. */
public record LunarWardenConfig(EntityType entityType, String displayName, String modelId,
                                double maxHealth, double walkForwardSpeed, double walkChaseSpeed,
                                double walkStrafeSpeed, double walkBackwardSpeed, double headHeight,
                                List<String> attackAnimations, List<String> skillReferences,
                                List<String> tags, String dropTable) {

    public LunarWardenConfig {
        entityType = Objects.requireNonNull(entityType, "Entity type must not be null");
        displayName = requireText(displayName, "Display name");
        modelId = requireText(modelId, "Model ID");
        if (!Double.isFinite(maxHealth) || maxHealth <= 0) throw new IllegalArgumentException("Max health must be positive");
        attackAnimations = List.copyOf(attackAnimations);
        skillReferences = List.copyOf(skillReferences);
        tags = List.copyOf(tags);
        dropTable = dropTable == null || dropTable.isBlank() ? null : dropTable.trim();
    }

    public static LunarWardenConfig defaults() {
        return new LunarWardenConfig(EntityType.IRON_GOLEM, "The Lunar Warden", WardenConstants.MODEL_ID,
                WardenConstants.BOSS_MAX_HEALTH, WardenConstants.WALK_FORWARD_SPEED, WardenConstants.WALK_CHASE_SPEED,
                WardenConstants.WALK_STRAFE_SPEED, WardenConstants.WALK_BACKWARD_SPEED, WardenConstants.BOSS_HEAD_HEIGHT,
                List.of(WardenConstants.ATTACK_ANIMATIONS), List.of(), List.of("boss", "lunar"), null);
    }

    public static LunarWardenConfig load(LunarYamlLoader loader, Path configRoot) throws ConfigLoadException {
        Map<String, Object> values = loader.load(configRoot).directories().getOrDefault("mobs", Map.of()).get("lunar_warden");
        if (values == null) throw new ConfigLoadException(new ConfigValidationReport(List.of(
                new ConfigValidationReport.Issue(configRoot.toString(), "$.mobs.lunar_warden", "Missing Lunar Warden definition", 0, 0))));
        return fromMap(values);
    }

    public static LunarWardenConfig fromMap(Map<String, Object> values) {
        LunarWardenConfig fallback = defaults();
        EntityType type = enumValue(values, "entity-type", EntityType.class, fallback.entityType());
        Map<String, Object> attributes = map(values.get("attributes"));
        Map<String, Object> movement = map(values.get("movement"));
        return new LunarWardenConfig(type,
                stringValue(values, "display-name", fallback.displayName()),
                stringValue(values, "model-id", fallback.modelId()),
                number(attributes, "max_health", fallback.maxHealth()),
                number(movement, "walk-forward-speed", fallback.walkForwardSpeed()),
                number(movement, "walk-chase-speed", fallback.walkChaseSpeed()),
                number(movement, "walk-strafe-speed", fallback.walkStrafeSpeed()),
                number(movement, "walk-backward-speed", fallback.walkBackwardSpeed()),
                number(movement, "head-height", fallback.headHeight()),
                strings(values.get("attack-animations"), fallback.attackAnimations()),
                strings(values.get("skills"), fallback.skillReferences()),
                strings(values.get("tags"), fallback.tags()),
                values.get("drop-table") instanceof String drop ? drop : fallback.dropTable());
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, item) -> { if (key instanceof String text) result.put(text, item); });
        return result;
    }
    private static double number(Map<String, Object> values, String key, double fallback) {
        return values.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }
    private static String stringValue(Map<String, Object> values, String key, String fallback) {
        return values.get(key) instanceof String value && !value.isBlank() ? value.trim() : fallback;
    }
    private static <E extends Enum<E>> E enumValue(Map<String, Object> values, String key, Class<E> type, E fallback) {
        if (!(values.get(key) instanceof String value)) return fallback;
        try { return Enum.valueOf(type, value.trim().toUpperCase()); } catch (IllegalArgumentException exception) { return fallback; }
    }
    private static List<String> strings(Object value, List<String> fallback) {
        if (!(value instanceof List<?> list)) return fallback;
        return list.stream().filter(String.class::isInstance).map(String.class::cast).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
