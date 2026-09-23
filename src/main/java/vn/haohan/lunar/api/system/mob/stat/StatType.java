package vn.haohan.lunar.api.system.mob.stat;

/**
 * Standard RPG statistics supported by HaoHanLunar custom mobs and players.
 */
public enum StatType {
    DAMAGE(0.0, Double.MAX_VALUE),
    ARMOR(0.0, Double.MAX_VALUE),
    CRIT_CHANCE(0.0, 1.0),
    CRIT_DAMAGE(1.0, Double.MAX_VALUE),
    LIFESTEAL(0.0, 1.0),
    COOLDOWN_REDUCTION(0.0, 0.40),
    LUCK(-100.0, 100.0),
    KNOCKBACK_RESISTANCE(0.0, 1.0);

    private final double min;
    private final double max;

    StatType(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }
}
