package vn.haohan.lunar.api.system.combat.skill;

/**
 * Events that may start or trigger a custom mob skill.
 */
public enum SkillTrigger {
    ON_SPAWN("onSpawn"),
    ON_TIMER("onTimer"),
    ON_ATTACK("onAttack"),
    ON_COMBAT("onCombat"),
    ON_DAMAGED("onDamaged"),
    ON_DEATH("onDeath"),
    ON_KILL("onKill"),
    ON_INTERACT("onInteract"),
    ON_SIGNAL("onSignal"),
    ON_LUNAR_PHASE_CHANGE("onLunarPhaseChange"),
    ON_MOONRISE("onMoonrise"),
    ON_MOONSET("onMoonset"),
    ON_ENTER_BOUNDS("onEnterBounds"), ON_EXIT_BOUNDS("onExitBounds"), ON_SHOOT("onShoot"), ON_TARGET_CHANGE("onTargetChange"), ON_HEAL("onHeal"), ON_TELEPORT("onTeleport");

    private final String configName;

    SkillTrigger(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }
}
