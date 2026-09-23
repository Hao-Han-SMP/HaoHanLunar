package vn.haohan.lunar.api.system.mob.phase;

import java.util.*;

/**
 * Immutable phase definition supporting health thresholds, composite conditions,
 * enter/exit skill sequences, once-only enforcement, and transition cooldowns.
 */
public record MobPhase(
        String id,
        int priority,
        double minimumHealthRatio,
        List<PhaseCondition> conditions,
        List<String> onEnterSkills,
        List<String> onExitSkills,
        boolean resetCooldowns,
        boolean onceOnly,
        long transitionCooldownTicks
) {
    public MobPhase {
        Objects.requireNonNull(id, "Phase ID must not be null");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("Phase ID must not be blank");
        if (!Double.isFinite(minimumHealthRatio) || minimumHealthRatio < 0 || minimumHealthRatio > 1) {
            throw new IllegalArgumentException("Phase health ratio must be between 0 and 1");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        onEnterSkills = onEnterSkills == null ? List.of() : List.copyOf(onEnterSkills);
        onExitSkills = onExitSkills == null ? List.of() : List.copyOf(onExitSkills);
    }

    /**
     * Backward-compatible constructor for existing callers and simple single-skill transitions.
     */
    public MobPhase(String id, double minimumHealthRatio, String enterSkill,
                    String exitSkill, boolean resetCooldowns) {
        this(id, 0, minimumHealthRatio,
                List.of(PhaseCondition.healthLessThanOrEqual(minimumHealthRatio)),
                enterSkill != null && !enterSkill.isBlank() ? List.of(enterSkill.trim().toLowerCase(Locale.ROOT)) : List.of(),
                exitSkill != null && !exitSkill.isBlank() ? List.of(exitSkill.trim().toLowerCase(Locale.ROOT)) : List.of(),
                resetCooldowns, false, 0L);
    }

    public Optional<String> enterSkillOptional() {
        return onEnterSkills.isEmpty() ? Optional.empty() : Optional.of(onEnterSkills.getFirst());
    }

    public Optional<String> exitSkillOptional() {
        return onExitSkills.isEmpty() ? Optional.empty() : Optional.of(onExitSkills.getFirst());
    }

    public boolean matches(PhaseContext context) {
        if (conditions.isEmpty()) {
            return context.healthRatio() <= minimumHealthRatio;
        }
        for (PhaseCondition condition : conditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static List<MobPhase> parsePhases(Object raw) {
        if (raw == null) return List.of();
        List<MobPhase> result = new ArrayList<>();

        if (raw instanceof List<?> list) {
            int autoIndex = 1;
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String id = map.containsKey("id") ? String.valueOf(map.get("id")) : String.valueOf(autoIndex);
                    MobPhase phase = parseSinglePhase(id, (Map<String, Object>)map, autoIndex);
                    if (phase != null) result.add(phase);
                    autoIndex++;
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            int autoIndex = 1;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String id = String.valueOf(entry.getKey());
                Object val = entry.getValue();
                if (val instanceof Map<?, ?> pMap) {
                    MobPhase phase = parseSinglePhase(id, (Map<String, Object>)pMap, autoIndex);
                    if (phase != null) result.add(phase);
                } else if (val instanceof Number n) {
                    double hp = n.doubleValue();
                    if (hp > 1.0) hp = hp / 100.0;
                    result.add(MobPhase.builder(id).priority(autoIndex).minimumHealthRatio(Math.max(0.0, Math.min(1.0, hp))).build());
                }
                autoIndex++;
            }
        }

        return List.copyOf(result);
    }

    private static MobPhase parseSinglePhase(String id, Map<String, Object> map, int defaultPriority) {
        Builder builder = builder(id);

        int priority = map.containsKey("priority") ? ((Number)map.get("priority")).intValue() : defaultPriority;
        builder.priority(priority);

        Object rawHealth = map.get("health");
        if (rawHealth == null) rawHealth = map.get("hp");
        if (rawHealth == null) rawHealth = map.get("minimumHealthRatio");
        if (rawHealth != null) {
            double hp = 1.0;
            if (rawHealth instanceof Number n) {
                hp = n.doubleValue();
            } else if (rawHealth instanceof String s) {
                String cleaned = s.replace("%", "").trim();
                try {
                    hp = Double.parseDouble(cleaned);
                }
                catch (NumberFormatException ignored) {
                }
                if (s.contains("%")) hp = hp / 100.0;
            }
            if (hp > 1.0) hp = hp / 100.0;
            builder.minimumHealthRatio(Math.max(0.0, Math.min(1.0, hp)));
        }

        addSkills(map.get("skills"), builder::onEnterSkill);
        addSkills(map.get("onEnterSkills"), builder::onEnterSkill);
        addSkills(map.get("enterSkills"), builder::onEnterSkill);
        addSkills(map.get("exitSkills"), builder::onExitSkill);
        addSkills(map.get("onExitSkills"), builder::onExitSkill);

        boolean resetCd = Boolean.parseBoolean(String.valueOf(map.getOrDefault("resetCooldowns", map.getOrDefault("reset-cooldowns", false))));
        builder.resetCooldowns(resetCd);

        boolean once = Boolean.parseBoolean(String.valueOf(map.getOrDefault("onceOnly", map.getOrDefault("once-only", false))));
        builder.onceOnly(once);

        long cd = map.containsKey("cooldown") ? ((Number)map.get("cooldown")).longValue() : map.containsKey("transitionCooldownTicks") ? ((Number)map.get("transitionCooldownTicks")).longValue() : 0L;
        builder.transitionCooldownTicks(cd);

        Object rawConds = map.get("conditions");
        if (rawConds instanceof Collection<?> condCol) {
            for (Object c : condCol) {
                if (c != null) {
                    PhaseCondition parsed = parseConditionString(c.toString());
                    if (parsed != null) builder.condition(parsed);
                }
            }
        }

        return builder.build();
    }

    private static void addSkills(Object raw, java.util.function.Consumer<String> consumer) {
        if (raw instanceof Collection<?> col) {
            for (Object s : col) if (s != null) consumer.accept(s.toString());
        } else if (raw instanceof String s && !s.isBlank()) {
            consumer.accept(s);
        }
    }

    public static PhaseCondition parseConditionString(String condStr) {
        if (condStr == null || condStr.isBlank()) return null;
        String trimmed = condStr.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("health") || lower.startsWith("hp")) {
            String numPart = lower.replaceAll("[^0-9.]", "");
            if (!numPart.isEmpty()) {
                double val = Double.parseDouble(numPart);
                if (trimmed.contains("%") || val > 1.0) val = val / 100.0;
                return PhaseCondition.healthLessThanOrEqual(Math.max(0.0, Math.min(1.0, val)));
            }
        } else if (lower.startsWith("alive") || lower.startsWith("alivetime")) {
            String numPart = lower.replaceAll("[^0-9]", "");
            if (!numPart.isEmpty()) return PhaseCondition.aliveTimeGreaterThanOrEqual(Long.parseLong(numPart));
        } else if (lower.startsWith("target") || lower.startsWith("targets") || lower.startsWith("targetcount")) {
            String numPart = lower.replaceAll("[^0-9]", "");
            if (!numPart.isEmpty()) return PhaseCondition.targetCountGreaterThanOrEqual(Integer.parseInt(numPart));
        } else if (lower.contains("signal") || lower.startsWith("~onsignal")) {
            int idx = trimmed.indexOf(':');
            if (idx == -1) idx = trimmed.indexOf("==");
            if (idx != -1) {
                String sig = trimmed.substring(idx + (trimmed.charAt(idx) == ':' ? 1 : 2)).trim();
                return PhaseCondition.signalEquals(sig);
            }
        } else if (trimmed.contains("==")) {
            String[] parts = trimmed.split("==", 2);
            String key = parts[0].trim().replace("<caster.var.", "").replace(">", "").replace("var.", "");
            String expected = parts[1].trim();
            return PhaseCondition.variableEquals(key, expected);
        }
        return null;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private int priority = 0;
        private double minimumHealthRatio = 1.0;
        private final List<PhaseCondition> conditions = new ArrayList<>();
        private final List<String> onEnterSkills = new ArrayList<>();
        private final List<String> onExitSkills = new ArrayList<>();
        private boolean resetCooldowns = false;
        private boolean onceOnly = false;
        private long transitionCooldownTicks = 0L;

        private Builder(String id) {
            this.id = id;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder minimumHealthRatio(double ratio) {
            this.minimumHealthRatio = ratio;
            this.conditions.add(PhaseCondition.healthLessThanOrEqual(ratio));
            return this;
        }

        public Builder condition(PhaseCondition condition) {
            if (condition != null) this.conditions.add(condition);
            return this;
        }

        public Builder onEnterSkill(String skillId) {
            if (skillId != null && !skillId.isBlank()) this.onEnterSkills.add(skillId.trim().toLowerCase(Locale.ROOT));
            return this;
        }

        public Builder onExitSkill(String skillId) {
            if (skillId != null && !skillId.isBlank()) this.onExitSkills.add(skillId.trim().toLowerCase(Locale.ROOT));
            return this;
        }

        public Builder resetCooldowns(boolean resetCooldowns) {
            this.resetCooldowns = resetCooldowns;
            return this;
        }

        public Builder onceOnly(boolean onceOnly) {
            this.onceOnly = onceOnly;
            return this;
        }

        public Builder transitionCooldownTicks(long ticks) {
            this.transitionCooldownTicks = Math.max(0L, ticks);
            return this;
        }

        public MobPhase build() {
            return new MobPhase(id, priority, minimumHealthRatio, conditions, onEnterSkills, onExitSkills,
                    resetCooldowns, onceOnly, transitionCooldownTicks);
        }
    }
}
