package vn.haohan.lunar.core.system.variable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe container for named variables within a specific scope.
 */
public final class VariableHolder {

    private final Map<String, VariableValue> variables = new ConcurrentHashMap<>();

    public Optional<VariableValue> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(variables.get(normalize(name)));
    }

    public void set(String name, VariableValue value) {
        if (name == null || name.isBlank() || value == null) {
            return;
        }
        variables.put(normalize(name), value);
    }

    public void set(String name, String value) {
        set(name, VariableValue.parse(value));
    }

    public void set(String name, int value) {
        set(name, VariableValue.of(value));
    }

    public void set(String name, double value) {
        set(name, VariableValue.of(value));
    }

    public void set(String name, boolean value) {
        set(name, VariableValue.of(value));
    }

    public Optional<VariableValue> remove(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(variables.remove(normalize(name)));
    }

    public boolean has(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return variables.containsKey(normalize(name));
    }

    public int size() {
        return variables.size();
    }

    public void clear() {
        variables.clear();
    }

    public Map<String, VariableValue> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    public VariableHolder copy() {
        VariableHolder holder = new VariableHolder();
        variables.forEach(holder::set);
        return holder;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
