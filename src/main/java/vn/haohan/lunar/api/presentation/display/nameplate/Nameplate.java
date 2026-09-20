package vn.haohan.lunar.core.presentation.display.nameplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.core.mob.ActiveLunarMob;

/**
 * Utility for formatting and applying rich MiniMessage dynamic nameplates to Lunar mobs.
 */
public final class LunarNameplate {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private LunarNameplate() {}

    /**
     * Renders a template string into an Adventure Component using mob state placeholders.
     */
    public static Component render(String template, ActiveLunarMob mob) {
        if (template == null || template.isBlank()) {
            return Component.empty();
        }
        if (mob == null) {
            return MINI_MESSAGE.deserialize(template);
        }

        LivingEntity entity = mob.entity();
        double health = entity != null ? entity.getHealth() : 0.0;
        double maxHealth = entity != null ? entity.getMaxHealth() : 1.0;
        int healthPercent = (int) Math.round((health / Math.max(1.0, maxHealth)) * 100.0);
        String mobName = mob.definition() != null ? mob.definition().displayName() : mob.definitionId().value();
        String stance = mob.stance() != null ? mob.stance() : "default";

        String formatted = template
                .replace("<name>", mobName)
                .replace("<health>", String.format("%.0f", health))
                .replace("<max_health>", String.format("%.0f", maxHealth))
                .replace("<health_percent>", String.valueOf(healthPercent) + "%")
                .replace("<stance>", stance);

        return MINI_MESSAGE.deserialize(formatted);
    }

    /**
     * Updates the entity's custom name with the rendered nameplate template.
     */
    public static void apply(ActiveLunarMob mob, String template) {
        if (mob == null || mob.entity() == null) return;
        Component component = render(template, mob);
        try {
            mob.entity().customName(component);
            mob.entity().setCustomNameVisible(true);
        } catch (Throwable ignored) {
        }
    }
}
