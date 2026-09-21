package vn.haohan.lunar.api.system.combat.skill.aura;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for configured aura templates.
 */
public final class AuraRegistry {

    private final Map<String, AuraDefinition> auras = new ConcurrentHashMap<>();

    public void register(AuraDefinition aura) {
        Objects.requireNonNull(aura, "AuraDefinition must not be null");
        auras.put(normalize(aura.id()), aura);
    }

    public Optional<AuraDefinition> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(auras.get(normalize(id)));
    }

    public boolean contains(String id) {
        if (id == null || id.isBlank()) return false;
        return auras.containsKey(normalize(id));
    }

    public int size() {
        return auras.size();
    }

    public Map<String, AuraDefinition> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(auras));
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
