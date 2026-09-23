package vn.haohan.lunar.api.system.mob.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed AI goal entry holding the goal type, parameters, and original raw configuration string.
 * Supports both space-delimited arguments and curly-brace key-value pairs (e.g. circle{radius=8;speed=1.2}).
 */
public record AIGoalEntry(AIGoalType type, List<String> args, Map<String, Object> params, String rawLine) {

    public AIGoalEntry {
        Objects.requireNonNull(type, "type must not be null");
        args = args != null ? List.copyOf(args) : List.of();
        params = params != null ? Map.copyOf(params) : Map.of();
        rawLine = rawLine != null ? rawLine.trim() : "";
    }

    public AIGoalEntry(AIGoalType type, List<String> args, String rawLine) {
        this(type, args, Map.of(), rawLine);
    }

    public static AIGoalEntry parse(String line) {
        if (line == null || line.isBlank()) {
            return new AIGoalEntry(AIGoalType.CUSTOM, List.of(), Map.of(), "");
        }

        String trimmed = line.trim();
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');

        if (braceStart > 0 && braceEnd > braceStart) {
            String typeName = trimmed.substring(0, braceStart).trim();
            AIGoalType type = AIGoalType.fromString(typeName);
            String content = trimmed.substring(braceStart + 1, braceEnd).trim();
            Map<String, Object> params = parseParameters(content);
            return new AIGoalEntry(type, List.of(), params, trimmed);
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return new AIGoalEntry(AIGoalType.CUSTOM, List.of(), Map.of(), trimmed);
        }

        AIGoalType type = AIGoalType.fromString(parts[0]);
        List<String> args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            args.add(parts[i]);
        }

        return new AIGoalEntry(type, args, Map.of(), trimmed);
    }

    private static Map<String, Object> parseParameters(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        String[] tokens = raw.split("[;,]");
        for (String token : tokens) {
            String item = token.trim();
            if (item.isEmpty()) continue;
            int eq = item.indexOf('=');
            if (eq > 0) {
                String key = item.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = item.substring(eq + 1).trim();
                map.put(key, tryParseNumber(val));
            } else {
                map.put(item.toLowerCase(Locale.ROOT), true);
            }
        }
        return map;
    }

    private static Object tryParseNumber(String val) {
        try {
            if (val.contains(".")) {
                return Double.parseDouble(val);
            }
            return Long.parseLong(val);
        } catch (NumberFormatException ignored) {
            if ("true".equalsIgnoreCase(val)) return true;
            if ("false".equalsIgnoreCase(val)) return false;
            return val;
        }
    }

    public double getDoubleArg(int index, double fallback) {
        if (index < 0 || index >= args.size()) return fallback;
        try {
            return Double.parseDouble(args.get(index));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public double getParamOrArg(String key, int argIndex, double fallback) {
        if (params != null && key != null) {
            Object val = params.get(key.toLowerCase(Locale.ROOT));
            if (val instanceof Number n) return n.doubleValue();
            if (val != null) {
                try {
                    return Double.parseDouble(String.valueOf(val).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return getDoubleArg(argIndex, fallback);
    }

    public String getStringArg(int index, String fallback) {
        if (index < 0 || index >= args.size()) return fallback;
        return args.get(index);
    }

    public String getStringParamOrArg(String key, int argIndex, String fallback) {
        if (params != null && key != null) {
            Object val = params.get(key.toLowerCase(Locale.ROOT));
            if (val != null) {
                return String.valueOf(val).trim();
            }
        }
        return getStringArg(argIndex, fallback);
    }

    public boolean getBooleanParamOrArg(String key, int argIndex, boolean fallback) {
        if (params != null && key != null) {
            Object val = params.get(key.toLowerCase(Locale.ROOT));
            if (val instanceof Boolean b) return b;
            if (val != null) {
                return Boolean.parseBoolean(String.valueOf(val).trim());
            }
        }
        if (argIndex >= 0 && argIndex < args.size()) {
            return Boolean.parseBoolean(args.get(argIndex).trim());
        }
        return fallback;
    }

    public boolean isClear() {
        return type == AIGoalType.CLEAR;
    }
}
