package vn.haohan.lunar.api.system.combat.skill;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable metadata for one skill; execution details are supplied by later runtime tasks. */
public final class SkillDefinition {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9](?:[a-z0-9_.:-]*[a-z0-9])?");

    private final String id;
    private final Set<SkillTrigger> triggers;
    private final long cooldownTicks;
    private final List<String> mechanicReferences;
    private final String cooldownGroup;
    private final long groupCooldownTicks;

    public SkillDefinition(String id, Set<SkillTrigger> triggers, long cooldownTicks,
                           List<String> mechanicReferences, String cooldownGroup, long groupCooldownTicks) {
        this.id = normalizeId(id);
        this.triggers = Set.copyOf(Objects.requireNonNull(triggers, "Skill triggers must not be null"));
        if (this.triggers.isEmpty()) {
            throw new IllegalArgumentException("Skill must have at least one trigger");
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("Skill cooldown must not be negative");
        }
        this.cooldownTicks = cooldownTicks;
        Objects.requireNonNull(mechanicReferences, "Mechanic references must not be null");
        this.mechanicReferences = List.copyOf(mechanicReferences.stream()
                .map(value -> requireText(value, "Mechanic reference"))
                .toList());
        this.cooldownGroup = cooldownGroup != null && !cooldownGroup.isBlank() ? cooldownGroup.trim().toUpperCase(Locale.ROOT) : null;
        this.groupCooldownTicks = Math.max(0, groupCooldownTicks);
    }

    public SkillDefinition(String id, Set<SkillTrigger> triggers, long cooldownTicks,
                           List<String> mechanicReferences) {
        this(id, triggers, cooldownTicks, mechanicReferences, null, 0);
    }

    public SkillDefinition(String id, Set<SkillTrigger> triggers, long cooldownTicks) {
        this(id, triggers, cooldownTicks, List.of(), null, 0);
    }

    public String id() { return id; }
    public Set<SkillTrigger> triggers() { return triggers; }
    public long cooldownTicks() { return cooldownTicks; }
    public List<String> mechanicReferences() { return mechanicReferences; }
    public String cooldownGroup() { return cooldownGroup; }
    public long groupCooldownTicks() { return groupCooldownTicks; }

    private static String normalizeId(String value) {
        Objects.requireNonNull(value, "Skill ID must not be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid skill ID: " + value);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
