package vn.haohan.lunar.core.system.item;

import java.util.Locale;

/**
 * Triggers specifically bound to custom items.
 */
public enum ItemSkillTrigger {
    ON_USE("~onUse"),
    ON_SWING("~onSwing"),
    ON_DAMAGE("~onDamage"),
    ON_CONSUME("~onConsume"),
    ON_EQUIP("~onEquip"),
    ON_UNEQUIP("~onUnequip");

    private final String mythicNotation;

    ItemSkillTrigger(String mythicNotation) {
        this.mythicNotation = mythicNotation;
    }

    public String mythicNotation() {
        return mythicNotation;
    }

    public static ItemSkillTrigger fromString(String input) {
        if (input == null || input.isBlank()) {
            return ON_USE;
        }
        String clean = input.trim().replace("~", "").toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "onuse", "use", "rightclick" -> ON_USE;
            case "onswing", "swing", "leftclick" -> ON_SWING;
            case "ondamage", "damage", "attack", "hit" -> ON_DAMAGE;
            case "onconsume", "consume", "eat", "drink" -> ON_CONSUME;
            case "onequip", "equip" -> ON_EQUIP;
            case "onunequip", "unequip" -> ON_UNEQUIP;
            default -> ON_USE;
        };
    }
}
