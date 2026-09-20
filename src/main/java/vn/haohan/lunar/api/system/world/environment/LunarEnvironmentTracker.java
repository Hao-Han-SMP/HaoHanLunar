package vn.haohan.lunar.api.system.world.environment;

import org.bukkit.World;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.subsystem.mob.LunarMobManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Tracks celestial world progression, detecting lunar phase transitions,
 * moonrise (13,000 ticks), and moonset (23,000 ticks), and dispatching skill triggers.
 */
public final class LunarEnvironmentTracker {

    public static final long MOONRISE_TICK = 13000L;
    public static final long MOONSET_TICK = 23000L;

    private final Map<String, LunarPhase> lastPhases = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDayTimes = new ConcurrentHashMap<>();

    /**
     * Ticks celestial observation for a world and executes skill triggers on living mobs.
     */
    public void tickWorld(World world, LunarMobManager mobManager, BiConsumer<ActiveMob, SkillTrigger> dispatcher) {
        if (world == null || mobManager == null || dispatcher == null) return;

        String worldName = world.getName();
        long fullTime = world.getFullTime();
        long dayTime = world.getTime() % 24000L;
        if (dayTime < 0) dayTime += 24000L;

        // 1. Lunar Phase Check
        LunarPhase currentPhase = LunarPhase.fromFullTime(fullTime);
        LunarPhase prevPhase = lastPhases.put(worldName, currentPhase);
        if (prevPhase != null && prevPhase != currentPhase) {
            dispatchToWorld(world, mobManager, SkillTrigger.ON_LUNAR_PHASE_CHANGE, dispatcher);
        }

        // 2. Moonrise & Moonset Check
        Long prevDayTimeObj = lastDayTimes.put(worldName, dayTime);
        if (prevDayTimeObj != null) {
            long prevDayTime = prevDayTimeObj;

            // Check moonrise transition
            if (prevDayTime < MOONRISE_TICK && dayTime >= MOONRISE_TICK) {
                dispatchToWorld(world, mobManager, SkillTrigger.ON_MOONRISE, dispatcher);
            }

            // Check moonset transition
            if (prevDayTime < MOONSET_TICK && dayTime >= MOONSET_TICK) {
                dispatchToWorld(world, mobManager, SkillTrigger.ON_MOONSET, dispatcher);
            }
        }
    }

    private void dispatchToWorld(World world, LunarMobManager mobManager, SkillTrigger trigger, BiConsumer<ActiveMob, SkillTrigger> dispatcher) {
        for (ActiveMob mob : mobManager.snapshot()) {
            if (mob != null && mob.entity() != null && mob.entity().isValid() && !mob.entity().isDead()) {
                if (world.equals(mob.entity().getWorld())) {
                    dispatcher.accept(mob, trigger);
                }
            }
        }
    }

    public LunarPhase getCurrentPhase(World world) {
        if (world == null) return LunarPhase.FULL_MOON;
        return LunarPhase.fromFullTime(world.getFullTime());
    }

    public void clear() {
        lastPhases.clear();
        lastDayTimes.clear();
    }
}
