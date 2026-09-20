package vn.haohan.lunar.api.mob;

import vn.haohan.lunar.api.system.config.ConfigLoadException;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.config.LunarYamlLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Thread-safe registry whose readers always observe one complete immutable snapshot. */
public final class MobDefinitionRegistry {

    private volatile Map<String, MobDefinition> definitions = Map.of();

    public Optional<MobDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(normalize(id)));
    }

    public Optional<MobDefinition> get(MobDefinitionId id) {
        return id == null ? Optional.empty() : get(id.value());
    }

    public boolean contains(String id) {
        return definitions.containsKey(normalize(id));
    }

    public boolean contains(MobDefinitionId id) {
        return id != null && contains(id.value());
    }

    public Map<String, MobDefinition> snapshot() {
        return definitions;
    }

    public synchronized void register(MobDefinition definition) {
        Objects.requireNonNull(definition, "Mob definition must not be null");
        String id = normalize(definition.id().value());
        if (definitions.containsKey(id)) {
            throw new IllegalArgumentException("Mob definition ID already registered: " + id);
        }
        Map<String, MobDefinition> updated = new LinkedHashMap<>(definitions);
        updated.put(id, definition);
        definitions = Map.copyOf(updated);
    }

    /** Atomically replaces the whole registry after validating every entry. */
    public synchronized void replaceAll(Collection<MobDefinition> replacements) {
        Objects.requireNonNull(replacements, "Mob definitions must not be null");
        Map<String, MobDefinition> updated = new LinkedHashMap<>();
        for (MobDefinition definition : replacements) {
            Objects.requireNonNull(definition, "Mob definition must not be null");
            String id = normalize(definition.id().value());
            if (updated.putIfAbsent(id, definition) != null) {
                throw new IllegalArgumentException("Duplicate mob definition ID: " + id);
            }
        }
        definitions = Map.copyOf(updated);
    }

    /** Convenience overload for callers that already keyed definitions by normalized ID. */
    public synchronized void replaceAll(Map<String, MobDefinition> replacements) {
        Objects.requireNonNull(replacements, "Mob definitions must not be null");
        List<MobDefinition> values = new ArrayList<>(replacements.size());
        for (Map.Entry<String, MobDefinition> entry : replacements.entrySet()) {
            String key = normalize(entry.getKey());
            MobDefinition definition = Objects.requireNonNull(entry.getValue(), "Mob definition must not be null");
            if (!key.equals(definition.id().value())) {
                throw new IllegalArgumentException("Registry key does not match definition ID: " + key);
            }
            values.add(definition);
        }
        replaceAll(values);
    }

    public synchronized boolean unregister(String id) {
        if (id == null) return false;
        String norm = normalize(id);
        if (!definitions.containsKey(norm)) return false;
        Map<String, MobDefinition> updated = new LinkedHashMap<>(definitions);
        updated.remove(norm);
        definitions = Map.copyOf(updated);
        return true;
    }

    public synchronized boolean unregister(MobDefinitionId id) {
        return id != null && unregister(id.value());
    }

    public synchronized void clear() {
        definitions = Map.of();
    }

    /**
     * Loads YAML first and only applies the decoded definitions after the complete batch succeeds.
     * The decoder must not mutate this registry or call Bukkit APIs off the main thread.
     */
    public ConfigValidationReport reload(
            Path configRoot,
            LunarYamlLoader loader,
            Function<LunarYamlLoader.LoadedConfigBatch, ? extends Collection<MobDefinition>> decoder) {
        Objects.requireNonNull(loader, "YAML loader must not be null");
        Objects.requireNonNull(decoder, "Definition decoder must not be null");
        try {
            Collection<MobDefinition> decoded = decoder.apply(loader.load(configRoot));
            replaceAll(decoded);
            return new ConfigValidationReport(List.of());
        } catch (ConfigLoadException exception) {
            return exception.report();
        } catch (RuntimeException exception) {
            return new ConfigValidationReport(List.of(new ConfigValidationReport.Issue(
                    configRoot == null ? "<unknown>" : configRoot.toString(), "$",
                    exception.getMessage() == null ? "Could not apply mob definitions" : exception.getMessage(), 0, 0)));
        }
    }

    private static String normalize(String id) {
        return new MobDefinitionId(id).value();
    }
}
