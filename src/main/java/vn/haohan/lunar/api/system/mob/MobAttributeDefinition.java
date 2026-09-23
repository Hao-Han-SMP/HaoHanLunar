package vn.haohan.lunar.api.system.mob;

import java.util.Objects;

/** Immutable numeric attribute override for a mob definition. */
public record MobAttributeDefinition(String attribute, double baseValue) {

    public MobAttributeDefinition {
        Objects.requireNonNull(attribute, "Attribute name must not be null");
        attribute = attribute.trim();
        if (attribute.isEmpty()) {
            throw new IllegalArgumentException("Attribute name must not be blank");
        }
        if (!Double.isFinite(baseValue)) {
            throw new IllegalArgumentException("Attribute value must be finite");
        }
    }
}
