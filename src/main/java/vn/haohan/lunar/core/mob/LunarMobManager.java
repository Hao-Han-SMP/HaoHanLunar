package vn.haohan.lunar.core.mob;

import org.bukkit.entity.LivingEntity;
import java.util.function.Consumer;

/**
 * Compatibility alias for LunarMobManager in core.mob.
 */
public class LunarMobManager extends vn.haohan.lunar.core.subsystem.mob.LunarMobManager {

    public LunarMobManager() {
        super();
    }

    public LunarMobManager(Consumer<LivingEntity> modelCleanup) {
        super(modelCleanup);
    }
}
