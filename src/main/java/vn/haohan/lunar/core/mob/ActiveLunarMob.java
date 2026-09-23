package vn.haohan.lunar.core.mob;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

/**
 * Compatibility subclass for {@link ActiveMob} preserving legacy class naming across tests and integrations.
 */
public class ActiveLunarMob extends ActiveMob {

    public ActiveLunarMob(LivingEntity entity, MobDefinition definition, LunarMobIdentity identity) {
        super(entity, definition, identity);
    }
}
