package vn.haohan.lunar.api.system.mob;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, normalized identifier for a configured mob definition. */
public final class MobDefinitionId {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9](?:[a-z0-9_.:-]*[a-z0-9])?");

    private final String value;

    public MobDefinitionId(String value) {
        Objects.requireNonNull(value, "Mob definition ID must not be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(normalized).matches()) {
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
