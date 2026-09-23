package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import vn.haohan.lunar.api.system.combat.ThreatTable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper Goal connecting ActiveMob's ThreatTable directly to Minecraft Mob AI navigation.
 * Automatically evaluates the highest threat score and synchronizes target switching.
 */
public class ThreatTargetGoal implements Goal<Mob> {

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "threat_target"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.TARGET);

    private final Mob mob;
    private final ThreatTable threatTable;
    private LivingEntity target;
    private int tickCounter = 0;

    public ThreatTargetGoal(Mob mob, ThreatTable threatTable) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.threatTable = Objects.requireNonNull(threatTable, "ThreatTable must not be null");
    }

    @Override
    public boolean shouldActivate() {
        if (!mob.isValid() || threatTable.isEmpty()) {
            return false;
        }
        LivingEntity top = resolveTopTarget();
        if (top != null && top.isValid() && !top.isDead()) {
            this.target = top;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldStayActive() {
        if (!mob.isValid() || threatTable.isEmpty()) {
            return false;
        }
        if (target == null || !target.isValid() || target.isDead()) {
            return false;
        }
        if (!Objects.equals(target.getWorld(), mob.getWorld())) {
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        if (target != null && mob.isValid()) {
            mob.setTarget(target);
        }
    }

    @Override
    public void stop() {
        if (target == null || !target.isValid() || target.isDead()) {
            if (mob.isValid()) {
                mob.setTarget(null);
            }
        }
        this.target = null;
    }

    @Override
    public void tick() {
        // Periodically verify top target every 5 ticks
        if (++tickCounter % 5 == 0) {
            LivingEntity top = resolveTopTarget();
            if (top == null || !top.isValid() || top.isDead()) {
                this.target = null;
                if (mob.isValid()) {
                    mob.setTarget(null);
                }
            } else if (!top.equals(mob.getTarget())) {
                this.target = top;
                if (mob.isValid()) {
                    mob.setTarget(top);
                }
            }
        }
    }

    public LivingEntity resolveTopTarget() {
        return threatTable.topTarget()
                .map(this::findEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(e -> e.isValid() && !e.isDead() && Objects.equals(e.getWorld(), mob.getWorld()))
                .orElse(null);
    }

    private Entity findEntity(UUID uuid) {
        try {
            return Bukkit.getEntity(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return TYPES;
    }

    public Mob getMob() {
        return mob;
    }

    public ThreatTable getThreatTable() {
        return threatTable;
    }

    public LivingEntity getCurrentTarget() {
        return target;
    }
}
