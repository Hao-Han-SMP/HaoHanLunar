package vn.haohan.lunar.mechanics.boss.warden;

public final class WardenConstants {
    private WardenConstants() {}

    public static final String MODEL_ID = "thelunarwarden";

    // All registered attack animation names for TheLunarWarden
    public static final String[] ATTACK_ANIMATIONS = {
        "attack_slash_left",
        "attack_sweep_right",
        "attack_slash_straight",
        "attack_thrust_fling",
        "skill_charge_summon",
        "skill_shield_charge",
        "skill_shield_sword_slam",
        "skill_shield_block_push",
        "skill_shield_block"
    };

    // Movement speed constants (Enhanced responsiveness & fluid footwork)
    public static final double WALK_FORWARD_SPEED = 0.125;
    public static final double WALK_CHASE_SPEED = 0.180;
    public static final double WALK_STRAFE_SPEED = 0.135;
    public static final double WALK_BACKWARD_SPEED = 0.130;
    public static final double BOSS_HEAD_HEIGHT = 5.2;

    public static final double BOSS_MAX_HEALTH = 1000.0;
}
