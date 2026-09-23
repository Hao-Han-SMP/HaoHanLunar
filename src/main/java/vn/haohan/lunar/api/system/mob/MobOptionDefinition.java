package vn.haohan.lunar.api.system.mob;

import java.util.Objects;

/** Immutable named option. Values stay textual until a config layer gives them a type. */
public record MobOptionDefinition(String name, String value) {

    public MobOptionDefinition {
        Objects.requireNonNull(name, "Option name must not be null");
        Objects.requireNonNull(value, "Option value must not be null");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Option name must not be blank");
        }
    }
}
