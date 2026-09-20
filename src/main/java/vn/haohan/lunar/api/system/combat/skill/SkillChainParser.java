package vn.haohan.lunar.api.system.combat.skill;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed parser for skill chain YAML. */
public final class SkillChainParser {

    public ParseResult parse(Path file) {
        List<String> errors = new ArrayList<>();
        Map<String, SkillChainDefinition> parsed = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(file)) {
            Object raw = new Yaml(new SafeConstructor(options())).load(input);
            if (!(raw instanceof Map<?, ?> root)) {
                errors.add(error(file, "<unknown>", "root", "must be a mapping"));
            } else if (root.containsKey("mechanics")) {
                String id = text(root, "id", file.getFileName().toString());
                if (id != null) {
                    try {
                        SkillChainDefinition definition = parseDefinition(id, root, file, errors);
                        if (definition != null) parsed.put(definition.definition().id(), definition);
                    } catch (RuntimeException exception) {
                        errors.add(error(file, id, "skill", exception.getMessage()));
                    }
                }
            } else {
                for (Map.Entry<?, ?> entry : root.entrySet()) {
                    if (entry.getKey() instanceof String skillId && entry.getValue() instanceof Map<?, ?> skillMap) {
                        try {
                            SkillChainDefinition definition = parseDefinition(skillId, skillMap, file, errors);
                            if (definition != null) parsed.put(definition.definition().id(), definition);
                        } catch (RuntimeException exception) {
                            errors.add(error(file, skillId, "skill", exception.getMessage()));
                        }
                    }
                }
            }
        } catch (Exception exception) {
            errors.add(error(file, "<unknown>", "yaml", exception.getMessage()));
        }
        return new ParseResult(parsed, errors);
    }

    public ParseResult parseDirectory(Path directory) {
        Map<String, SkillChainDefinition> definitions = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            errors.add(error(directory, "<unknown>", "directory", "does not exist"));
            return new ParseResult(definitions, errors);
        }
        try (var files = Files.list(directory)) {
            files.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .sorted().forEach(path -> {
                        ParseResult result = parse(path);
                        result.definitions().forEach((id, value) -> {
                            if (definitions.putIfAbsent(id, value) != null) {
                                errors.add(error(path, id, "id", "duplicate skill ID"));
                            }
                        });
                        errors.addAll(result.errors());
                    });
        } catch (IOException exception) {
            errors.add(error(directory, "<unknown>", "directory", exception.getMessage()));
        }
        return new ParseResult(definitions, errors);
    }

    public SkillChainDefinition parseMap(String id, Map<?, ?> root, Path file, List<String> errors) {
        return parseDefinition(id, root, file, errors);
    }

    private static SkillChainDefinition parseDefinition(String id, Map<?, ?> root, Path file, List<String> errors) {
        Set<SkillTrigger> triggers = parseTriggers(root.get("trigger"), file, id, errors);
        long cooldown = longValue(valueOrDefault(root, "cooldown", 0), file, id, "cooldown", errors, 0);
        String targeter = root.get("targeter") == null ? "self" : textValue(root.get("targeter"), file, id, "targeter", errors);
        List<ConditionRegistry.ConditionCall> conditions = parseConditions(root.get("conditions"), file, id, errors);
        List<SkillChainDefinition.MechanicStep> mechanics = parseMechanics(root.get("mechanics"), file, id, errors);
        if (!errorsFor(errors, id)) {
            SkillDefinition skill = new SkillDefinition(id, triggers, cooldown,
                    mechanics.stream().map(SkillChainDefinition.MechanicStep::id).toList());
            return new SkillChainDefinition(skill, conditions, targeter, mechanics);
        }
        return null;
    }

    private static Set<SkillTrigger> parseTriggers(Object raw, Path file, String id, List<String> errors) {
        List<?> values = raw instanceof List<?> list ? list : raw == null ? List.of() : List.of(raw);
        java.util.EnumSet<SkillTrigger> triggers = java.util.EnumSet.noneOf(SkillTrigger.class);
        for (Object value : values) {
            if (!(value instanceof String text)) { errors.add(error(file, id, "trigger", "must contain strings")); continue; }
            String configured = text.trim();
            SkillTrigger trigger = java.util.Arrays.stream(SkillTrigger.values())
                    .filter(candidate -> candidate.configName().equalsIgnoreCase(configured)
                            || candidate.name().equalsIgnoreCase(configured.replace('-', '_')))
                    .findFirst().orElse(null);
            if (trigger == null) errors.add(error(file, id, "trigger", "unknown trigger '" + text + "'"));
            else triggers.add(trigger);
        }
        if (triggers.isEmpty()) errors.add(error(file, id, "trigger", "must not be empty"));
        return Set.copyOf(triggers);
    }

    private static List<ConditionRegistry.ConditionCall> parseConditions(Object raw, Path file, String id, List<String> errors) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) { errors.add(error(file, id, "conditions", "must be a list")); return List.of(); }
        List<ConditionRegistry.ConditionCall> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> map)) { errors.add(error(file, id, "conditions[" + i + "]", "must be a mapping")); continue; }
            String conditionId = textValue(map.get("id"), file, id, "conditions[" + i + "].id", errors);
            if (conditionId != null) result.add(new ConditionRegistry.ConditionCall(conditionId, parameters(map, "id")));
        }
        return result;
    }

    private static List<SkillChainDefinition.MechanicStep> parseMechanics(Object raw, Path file, String id, List<String> errors) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) { errors.add(error(file, id, "mechanics", "must be a non-empty list")); return List.of(); }
        List<SkillChainDefinition.MechanicStep> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String path = "mechanics[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> map)) { errors.add(error(file, id, path, "must be a mapping")); continue; }
            String mechanicId = map.get("type") != null ? textValue(map.get("type"), file, id, path + ".type", errors)
                    : textValue(map.get("mechanic"), file, id, path + ".mechanic", errors);
            long delay = longValue(valueOrDefault(map, "delay", 0), file, id, path + ".delay", errors, 0);
            long repeatLong = longValue(valueOrDefault(map, "repeat", 1), file, id, path + ".repeat", errors, 1);
            String castSkill = map.get("cast-skill") == null ? null : textValue(map.get("cast-skill"), file, id, path + ".cast-skill", errors);
            if (repeatLong > Integer.MAX_VALUE) errors.add(error(file, id, path + ".repeat", "is too large"));
            if (mechanicId != null && delay >= 0 && repeatLong > 0 && repeatLong <= Integer.MAX_VALUE) {
                result.add(new SkillChainDefinition.MechanicStep(mechanicId, parameters(map, "type", "mechanic", "delay", "repeat", "cast-skill"), delay, (int) repeatLong, castSkill));
            }
        }
        return result;
    }

    private static Map<String, Object> parameters(Map<?, ?> source, String... excluded) {
        Set<String> ignored = Set.of(excluded);
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (key instanceof String string && !ignored.contains(string)) result.put(string, value); });
        return result;
    }

    private static Object valueOrDefault(Map<?, ?> map, String key, Object fallback) {
        return map.containsKey(key) ? map.get(key) : fallback;
    }

    private static String text(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback.substring(0, fallback.lastIndexOf('.')) : String.valueOf(value);
    }

    private static String textValue(Object value, Path file, String id, String field, List<String> errors) {
        if (!(value instanceof String string) || string.isBlank()) { errors.add(error(file, id, field, "must be a non-blank string")); return null; }
        return string.trim();
    }

    private static long longValue(Object value, Path file, String id, String field, List<String> errors, long fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.longValue() < 0) { errors.add(error(file, id, field, "must be a non-negative integer")); return fallback; }
        return number.longValue();
    }

    private static boolean errorsFor(List<String> errors, String id) { return errors.stream().anyMatch(error -> error.contains("[" + id + "]")); }
    private static String error(Path file, String id, String field, String message) { return file + " [" + id + "] at " + field + ": " + message; }
    private static LoaderOptions options() { LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); return options; }

    public record ParseResult(Map<String, SkillChainDefinition> definitions, List<String> errors) {
        public ParseResult { definitions = Map.copyOf(definitions); errors = List.copyOf(errors); }
        public boolean isValid() { return errors.isEmpty(); }
    }
}
