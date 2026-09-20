package vn.haohan.lunar.core.system.variable;

import org.bukkit.Location;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable strongly-typed value container for Lunar variables.
 */
public record VariableValue(VariableType type, Object rawValue) {

    public VariableValue {
        Objects.requireNonNull(type, "Variable type must not be null");
        Objects.requireNonNull(rawValue, "Raw value must not be null");
    }

    public String asString() {
        return rawValue.toString();
    }

    public int asInt() {
        if (rawValue instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(asString().trim());
        } catch (NumberFormatException e) {
            try {
                return (int) Double.parseDouble(asString().trim());
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }

    public float asFloat() {
        if (rawValue instanceof Number n) {
            return n.floatValue();
        }
        try {
            return Float.parseFloat(asString().trim());
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    public double asDouble() {
        if (rawValue instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(asString().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public boolean asBoolean() {
        if (rawValue instanceof Boolean b) {
            return b;
        }
        String s = asString().trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    public Location asLocation() {
        if (rawValue instanceof Location loc) {
            return loc;
        }
        return null;
    }

    public static VariableValue of(String value) {
        return new VariableValue(VariableType.STRING, value != null ? value : "");
    }

    public static VariableValue of(int value) {
        return new VariableValue(VariableType.INT, value);
    }

    public static VariableValue of(long value) {
        return new VariableValue(VariableType.INT, (int) value);
    }

    public static VariableValue of(float value) {
        return new VariableValue(VariableType.FLOAT, value);
    }

    public static VariableValue of(double value) {
        return new VariableValue(VariableType.DOUBLE, value);
    }

    public static VariableValue of(boolean value) {
        return new VariableValue(VariableType.BOOLEAN, value);
    }

    public static VariableValue of(Location location) {
        return new VariableValue(VariableType.LOCATION, location);
    }

    /**
     * Automatically parses an untyped string into the most appropriate VariableValue.
     */
    public static VariableValue parse(String raw) {
        if (raw == null) {
            return of("");
        }
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return of(Boolean.parseBoolean(trimmed));
        }
        try {
            return of(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
        }
        try {
            return of(Double.parseDouble(trimmed));
        } catch (NumberFormatException ignored) {
        }
        return of(raw);
    }
}
