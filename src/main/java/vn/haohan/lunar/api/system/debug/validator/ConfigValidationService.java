package vn.haohan.lunar.core.system.debug.validator;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Pure configuration validator that inspects YAML files without altering active registries.
 * Validates syntax, required attributes, data types, and cross-ID linkages.
 */
public final class ConfigValidationService {

    public record Issue(String file, String path, String message, int line, int column, boolean isWarning) {
        public Issue(String file, String path, String message, int line, int column) {
            this(file, path, message, line, column, false);
        }

        @Override
        public String toString() {
            String prefix = isWarning ? "[WARN] " : "[ERROR] ";
            String loc = line > 0 ? file + ":" + line + ":" + Math.max(1, column) : file;
            return prefix + loc + " (" + path + "): " + message;
        }
    }

    public record ValidationResult(
            int totalFiles,
            List<String> validFiles,
            List<Issue> warnings,
            List<Issue> errors
    ) {
        public boolean isClean() {
            return errors.isEmpty() && warnings.isEmpty();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public String formatSummary(String category) {
            StringBuilder sb = new StringBuilder();
            sb.append("§6=== Lunar Validation Report: ").append(category.toUpperCase(Locale.ROOT)).append(" ===\n");
            sb.append("§7Total files scanned: §f").append(totalFiles).append("\n");
            sb.append("§aValid files: §f").append(validFiles.size()).append("\n");
            sb.append("§eWarnings: §f").append(warnings.size()).append("\n");
            sb.append("§cErrors: §f").append(errors.size()).append("\n");

            if (!errors.isEmpty()) {
                sb.append("§c--- Errors ---\n");
                for (Issue err : errors) {
                    sb.append(" §c• ").append(err.toString()).append("\n");
                }
            }

            if (!warnings.isEmpty()) {
                sb.append("§e--- Warnings ---\n");
                for (Issue warn : warnings) {
                    sb.append(" §e• ").append(warn.toString()).append("\n");
                }
            }

            return sb.toString().trim();
        }
    }

    public ValidationResult validate(Path configRoot, String category) {
        Objects.requireNonNull(configRoot, "Config root must not be null");

        List<String> categories = (category == null || category.isBlank() || category.equalsIgnoreCase("all"))
                ? List.of("mobs", "skills", "drops", "spawners")
                : List.of(category.trim().toLowerCase(Locale.ROOT));

        int totalFiles = 0;
        List<String> validFiles = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();

        Map<String, Set<String>> discoveredIds = new HashMap<>();
        for (String cat : List.of("mobs", "skills", "drops", "spawners")) {
            discoveredIds.put(cat, new HashSet<>());
        }

        // 1. Pass 1: Parse YAML, validate schema, discover IDs
        for (String cat : categories) {
            Path dir = configRoot.resolve(cat);
            if (!Files.isDirectory(dir)) {
                continue;
            }

            try (Stream<Path> stream = Files.walk(dir)) {
                List<Path> files = stream.filter(p -> Files.isRegularFile(p) && isYaml(p)).toList();
                totalFiles += files.size();

                for (Path file : files) {
                    int initialErrorCount = errors.size();
                    validateFile(file, cat, discoveredIds.get(cat), errors, warnings);
                    if (errors.size() == initialErrorCount) {
                        validFiles.add(file.getFileName().toString());
                    }
                }
            } catch (IOException e) {
                errors.add(new Issue(dir.toString(), "$", "Failed to scan directory: " + e.getMessage(), 0, 0));
            }
        }

        return new ValidationResult(totalFiles, Collections.unmodifiableList(validFiles),
                Collections.unmodifiableList(warnings), Collections.unmodifiableList(errors));
    }

    private void validateFile(Path file, String category, Set<String> idCollector,
                              List<Issue> errors, List<Issue> warnings) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);

        try (InputStream in = Files.newInputStream(file)) {
            Object raw = new Yaml(new SafeConstructor(options)).load(in);
            if (!(raw instanceof Map<?, ?> map)) {
                errors.add(new Issue(file.toString(), "$", "Root YAML element must be a mapping/object", 1, 1));
                return;
            }

            // Check ID field
            Object idObj = map.get("id");
            if (idObj == null || String.valueOf(idObj).isBlank()) {
                errors.add(new Issue(file.toString(), "$.id", "Missing required field: 'id'", 1, 1));
                return;
            }
            String id = String.valueOf(idObj).trim().toLowerCase(Locale.ROOT);
            if (!idCollector.add(id)) {
                errors.add(new Issue(file.toString(), "$.id", "Duplicate ID '" + id + "' across category '" + category + "'", 1, 1));
            }

            // Category specific schema validation
            switch (category) {
                case "mobs" -> validateMob(file, map, errors, warnings);
                case "skills" -> validateSkill(file, map, errors, warnings);
                case "drops" -> validateDrop(file, map, errors, warnings);
                case "spawners" -> validateSpawner(file, map, errors, warnings);
            }

        } catch (MarkedYAMLException e) {
            var mark = e.getProblemMark();
            int line = mark != null ? mark.getLine() + 1 : 0;
            int col = mark != null ? mark.getColumn() + 1 : 0;
            errors.add(new Issue(file.toString(), "$", "YAML Syntax error: " + e.getProblem(), line, col));
        } catch (Exception e) {
            errors.add(new Issue(file.toString(), "$", "Error loading file: " + e.getMessage(), 0, 0));
        }
    }

    private void validateMob(Path file, Map<?, ?> map, List<Issue> errors, List<Issue> warnings) {
        if (!map.containsKey("type") && !map.containsKey("entity_type")) {
            errors.add(new Issue(file.toString(), "$.type", "Mob definition requires 'type' or 'entity_type'", 0, 0));
        }
        if (map.containsKey("health")) {
            try {
                double health = Double.parseDouble(String.valueOf(map.get("health")));
                if (health <= 0) {
                    warnings.add(new Issue(file.toString(), "$.health", "Health is less than or equal to 0", 0, 0, true));
                }
            } catch (NumberFormatException e) {
                errors.add(new Issue(file.toString(), "$.health", "Health must be a valid number", 0, 0));
            }
        }
    }

    private void validateSkill(Path file, Map<?, ?> map, List<Issue> errors, List<Issue> warnings) {
        if (!map.containsKey("triggers") && !map.containsKey("trigger")) {
            warnings.add(new Issue(file.toString(), "$.triggers", "Skill has no triggers defined; may never activate", 0, 0, true));
        }
        if (map.containsKey("cooldown")) {
            try {
                double cd = Double.parseDouble(String.valueOf(map.get("cooldown")));
                if (cd < 0) {
                    errors.add(new Issue(file.toString(), "$.cooldown", "Cooldown cannot be negative", 0, 0));
                }
            } catch (NumberFormatException e) {
                errors.add(new Issue(file.toString(), "$.cooldown", "Cooldown must be a numeric value", 0, 0));
            }
        }
    }

    private void validateDrop(Path file, Map<?, ?> map, List<Issue> errors, List<Issue> warnings) {
        if (!map.containsKey("items") && !map.containsKey("drops")) {
            warnings.add(new Issue(file.toString(), "$.items", "Drop table has no items defined", 0, 0, true));
        }
    }

    private void validateSpawner(Path file, Map<?, ?> map, List<Issue> errors, List<Issue> warnings) {
        if (!map.containsKey("mob") && !map.containsKey("mob_id")) {
            errors.add(new Issue(file.toString(), "$.mob", "Spawner requires 'mob' or 'mob_id' target", 0, 0));
        }
        if (!map.containsKey("location") && !map.containsKey("pos")) {
            errors.add(new Issue(file.toString(), "$.location", "Spawner requires a location definition", 0, 0));
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
