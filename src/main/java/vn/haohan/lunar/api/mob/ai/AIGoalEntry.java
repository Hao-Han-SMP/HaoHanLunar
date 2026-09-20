package vn.haohan.lunar.api.mob.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parsed AI goal entry holding the goal type, parameters, and original raw configuration string.
 */
public record AIGoalEntry(AIGoalType type, List<String> args, String rawLine) {

    public AIGoalEntry {
        Objects.requireNonNull(type, "type must not be null");
        args = args != null ? List.copyOf(args) : List.of();
        rawLine = rawLine != null ? rawLine.trim() : "";
    }

    public static AIGoalEntry parse(String line) {
        if (line == null || line.isBlank()) {
            return new AIGoalEntry(AIGoalType.CUSTOM, List.of(), "");
        }

        String trimmed = line.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return new AIGoalEntry(AIGoalType.CUSTOM, List.of(), trimmed);
        }

        AIGoalType type = AIGoalType.fromString(parts[0]);
        List<String> args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            args.add(parts[i]);
        }

        return new AIGoalEntry(type, args, trimmed);
    }

    public double getDoubleArg(int index, double fallback) {
        if (index < 0 || index >= args.size()) return fallback;
        try {
            return Double.parseDouble(args.get(index));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean isClear() {
        return type == AIGoalType.CLEAR;
    }
}
