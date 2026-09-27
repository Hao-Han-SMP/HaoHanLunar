package vn.haohan.engine.api.system.mob;

import vn.haohan.engine.core.util.PatternUtil;

import java.util.Locale;
import java.util.Objects;

/** Stable, normalized identifier for a configured mob definition. */
public final class MobDefinitionId {

    private final String value;

    public MobDefinitionId(String value) {
        Objects.requireNonNull(value, "Mob definition ID must not be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!PatternUtil.isValidNamespacedId(normalized)) {
            throw new IllegalArgumentException("Invalid mob definition ID: " + value);
        }
        this.value = normalized;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MobDefinitionId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
