package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;

/**
 * Selects the highest threat living entity from the caster mob's threat table.
 * Syntax: {@code @ThreatTop} or {@code @ThreatTableTop}
 */
public final class ThreatTopTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        ActiveMob casterMob = context.caster();
        ThreatTable table = casterMob.threatTable();
        if (table == null || table.isEmpty()) return List.of();

        Optional<UUID> topOpt = table.evaluateTarget(context.startedAtTick());
        if (topOpt.isEmpty()) {
            topOpt = table.topTarget();
        }

        if (topOpt.isEmpty()) return List.of();

        try {
            Entity entity = Bukkit.getEntity(topOpt.get());
            if (entity instanceof LivingEntity living && TargeterFilter.isTargetable(living)) {
                return List.of(living);
            }
        }
        catch (Exception ignored) {
        }

        return List.of();
    }
}
