package vn.haohan.lunar.api.system.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Safe YAML batch loader for the public configuration directories.
 * Parsing happens before the active snapshot is swapped, so a bad reload keeps the last good data.
 */
public final class LunarYamlLoader {

    private static final List<String> DIRECTORIES = List.of("mobs", "skills", "drops", "spawners");
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9](?:[a-z0-9_.-]*[a-z0-9])?");

    private final AtomicReference<LoadedConfigBatch> activeSnapshot = new AtomicReference<>(LoadedConfigBatch.empty());

    public LoadedConfigBatch snapshot() {
        return activeSnapshot.get();
    }

    /** Load and atomically activate a complete configuration tree. */
    public ConfigValidationReport reload(Path configRoot) {
        try {
            LoadedConfigBatch loaded = load(configRoot);
            activeSnapshot.set(loaded);
            return new ConfigValidationReport(List.of());
        } catch (ConfigLoadException exception) {
            return exception.report();
        }
    }

    public LoadedConfigBatch load(Path configRoot) throws ConfigLoadException {
        Objects.requireNonNull(configRoot, "Config root must not be null");
        List<ConfigValidationReport.Issue> issues = new ArrayList<>();
        Map<String, Map<String, Map<String, Object>>> directories = new LinkedHashMap<>();

        if (!Files.isDirectory(configRoot)) {
            issues.add(issue(configRoot, "$", "Configuration directory does not exist"));
            throw new ConfigLoadException(new ConfigValidationReport(issues));
        }

        for (String directoryName : DIRECTORIES) {
            Path directory = configRoot.resolve(directoryName);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(LunarYamlLoader::isYamlFile)
                        .sorted()
                        .forEach(file -> loadFile(file, entries, issues));
            } catch (IOException exception) {
                issues.add(issue(directory, "$", "Could not read directory: " + exception.getMessage()));
            }
            directories.put(directoryName, entries);
        }

        if (!issues.isEmpty()) {
            throw new ConfigLoadException(new ConfigValidationReport(issues));
        }
        return new LoadedConfigBatch(directories);
    }

    private static void loadFile(Path file, Map<String, Map<String, Object>> entries,
                                 List<ConfigValidationReport.Issue> issues) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream input = Files.newInputStream(file)) {
            Object raw = new Yaml(new SafeConstructor(options)).load(input);
            if (!(raw instanceof Map<?, ?> rawMap)) {
                issues.add(issue(file, "$", "Root YAML value must be a mapping"));
                return;
            }
            Map<String, Object> values = stringKeyedMap(rawMap, file, issues);
            String id = readId(file, values, issues);
            if (id != null && entries.putIfAbsent(id, values) != null) {
                issues.add(issue(file, "$.id", "Duplicate configuration ID: " + id));
            }
        } catch (MarkedYAMLException exception) {
            var mark = exception.getProblemMark();
            issues.add(new ConfigValidationReport.Issue(file.toString(), "$", exception.getProblem(),
                    mark == null ? 0 : mark.getLine() + 1, mark == null ? 0 : mark.getColumn() + 1));
        } catch (IOException | RuntimeException exception) {
            issues.add(issue(file, "$", "Could not load YAML: " + exception.getMessage()));
        }
    }

    private static Map<String, Object> stringKeyedMap(Map<?, ?> rawMap, Path file,
                                                       List<ConfigValidationReport.Issue> issues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                issues.add(issue(file, "$", "All YAML keys must be strings"));
                continue;
            }
            values.put(key, entry.getValue());
        }
        return values;
    }

    private static String readId(Path file, Map<String, Object> values,
                                 List<ConfigValidationReport.Issue> issues) {
        Object configuredId = values.get("id");
        String id;
        if (configuredId == null) {
            String filename = file.getFileName().toString();
            id = filename.substring(0, filename.lastIndexOf('.')).trim().toLowerCase(Locale.ROOT);
        } else if (configuredId instanceof String stringId) {
            id = stringId.trim().toLowerCase(Locale.ROOT);
        } else {
            issues.add(issue(file, "$.id", "Expected a string"));
            return null;
        }
        if (!VALID_ID.matcher(id).matches()) {
            issues.add(issue(file, "$.id", "Invalid configuration ID: " + id));
            return null;
        }
        return id;
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private static ConfigValidationReport.Issue issue(Path file, String path, String message) {
        return new ConfigValidationReport.Issue(file.toString(), path, message, 0, 0);
    }

    public record LoadedConfigBatch(Map<String, Map<String, Map<String, Object>>> directories) {
        public LoadedConfigBatch {
            Map<String, Map<String, Map<String, Object>>> copied = new LinkedHashMap<>();
            directories.forEach((directory, entries) -> copied.put(directory, immutableEntries(entries)));
            directories = Collections.unmodifiableMap(copied);
        }

        public static LoadedConfigBatch empty() {
            return new LoadedConfigBatch(Map.of());
        }

        private static Map<String, Map<String, Object>> immutableEntries(Map<String, Map<String, Object>> entries) {
            Map<String, Map<String, Object>> copied = new LinkedHashMap<>();
            entries.forEach((id, values) -> copied.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
            return Collections.unmodifiableMap(copied);
        }
    }
}
