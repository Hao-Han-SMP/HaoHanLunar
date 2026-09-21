package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargetFilter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;

/**
 * Selects all hostile targets currently present in the caster mob's threat table with threat > 0.
 * Syntax: {@code @ThreatTableTargets} or {@code @ThreatTargets{sort=HIGHEST_THREAT;limit=5}}
 */
public final class ThreatTableTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        ActiveMob casterMob = context.caster();
        ThreatTable table = casterMob.threatTable();
        if (table == null || table.isEmpty()) return List.of();

        Map<UUID, Double> entries = table.snapshot();
        List<LivingEntity> candidates = new ArrayList<>();

        for (Map.Entry<UUID, Double> entry : entries.entrySet()) {
            if (entry.getValue() <= 0.0) continue;
            try {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living && TargeterFilter.isTargetable(living)) {
                    candidates.add(living);
                }
            } catch (Exception ignored) {}
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }
}
