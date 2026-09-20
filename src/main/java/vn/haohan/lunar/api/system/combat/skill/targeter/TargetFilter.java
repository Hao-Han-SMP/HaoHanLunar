package vn.haohan.lunar.api.system.combat.skill.targeter;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Advanced post-selection filter and sorting pipeline for targeter outputs.
 * Supports limit caps, exclusion filters (CASTER, CREATIVE, SPECTATOR, SAME_TYPE),
 * and priority sorting (NEAREST, FURTHEST, HEALTH, THREAT, RANDOM).
 */
public final class TargetFilter {

    public enum SortOrder {
        NEAREST,
        FURTHEST,
        HIGHEST_HEALTH,
        LOWEST_HEALTH,
        HIGHEST_THREAT,
        RANDOM
    }

    private TargetFilter() {}

    public static List<LivingEntity> filterAndSort(
            Collection<LivingEntity> candidates,
            SkillCastContext context,
            Map<String, Object> parameters
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        ActiveMob casterMob = context != null ? context.caster() : null;
        LivingEntity casterEntity = casterMob != null ? casterMob.entity() : null;
        UUID casterId = casterEntity != null ? casterEntity.getUniqueId() : null;
        EntityType casterType = casterEntity != null ? casterEntity.getType() : null;
        Location origin = context != null && context.origin() != null
                ? context.origin()
                : (casterEntity != null ? casterEntity.getLocation() : null);

        Set<String> ignores = parseIgnores(parameters);
        boolean ignoreCaster = ignores.contains("caster") || !parameters.containsKey("ignore-caster") || Boolean.TRUE.equals(parameters.get("ignore-caster"));
        boolean ignoreSameType = ignores.contains("same_type") || ignores.contains("sametype");
        boolean ignoreCreative = !ignores.contains("allow_creative");
        boolean ignoreSpectator = !ignores.contains("allow_spectator");

        List<LivingEntity> filtered = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            if (candidate == null || !candidate.isValid() || candidate.isDead()) {
                continue;
            }
            if (ignoreCaster && casterId != null && casterId.equals(candidate.getUniqueId())) {
                continue;
            }
            if (ignoreSameType && casterType != null && casterType == candidate.getType()) {
                continue;
            }
            if (candidate instanceof Player player) {
                if (!player.isOnline()) continue;
                GameMode gm = player.getGameMode();
                if (ignoreCreative && gm == GameMode.CREATIVE) continue;
                if (ignoreSpectator && gm == GameMode.SPECTATOR) continue;
            }
            filtered.add(candidate);
        }

        SortOrder sortOrder = parseSortOrder(parameters);
        if (sortOrder != null) {
            applySorting(filtered, sortOrder, origin, casterMob);
        }

        int limit = TargeterFilter.parseLimit(parameters);
        if (filtered.size() > limit) {
            return List.copyOf(filtered.subList(0, limit));
        }
        return List.copyOf(filtered);
    }

    private static void applySorting(List<LivingEntity> list, SortOrder order, Location origin, ActiveMob casterMob) {
        switch (order) {
            case NEAREST -> {
                if (origin != null) {
                    list.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));
                }
            }
            case FURTHEST -> {
                if (origin != null) {
                    list.sort((a, b) -> Double.compare(b.getLocation().distanceSquared(origin), a.getLocation().distanceSquared(origin)));
                }
            }
            case HIGHEST_HEALTH -> list.sort((a, b) -> Double.compare(b.getHealth(), a.getHealth()));
            case LOWEST_HEALTH -> list.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case HIGHEST_THREAT -> {
                if (casterMob != null) {
                    list.sort((a, b) -> Double.compare(
                            casterMob.threatTable().getThreat(b.getUniqueId()),
                            casterMob.threatTable().getThreat(a.getUniqueId())
                    ));
                }
            }
            case RANDOM -> Collections.shuffle(list);
        }
    }

    private static SortOrder parseSortOrder(Map<String, Object> params) {
        if (params == null) return null;
        Object raw = params.get("sort");
        if (raw == null) raw = params.get("order");
        if (raw instanceof String text) {
            try {
                return SortOrder.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private static Set<String> parseIgnores(Map<String, Object> params) {
        if (params == null) return Set.of();
        Object raw = params.get("ignore");
        if (raw instanceof String s) {
            return Set.of(s.trim().toLowerCase(Locale.ROOT).split("[,;\\s]+"));
        }
        if (raw instanceof Collection<?> coll) {
            return coll.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).map(x -> x.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }
}
