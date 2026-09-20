package vn.haohan.lunar.api.system.loot;

/**
 * Configuration options for loot drop behavior and visual effects.
 */
public record DropOptions(
        boolean dropsPerPlayer,
        double dropsPerPlayerRequiredDamagePercent,
        boolean dropsDoLootsplosion,
        boolean dropsGlowByDefault,
        boolean dropsHaveBeamByDefault,
        double bonusLuckMultiplier,
        double bonusLevelMultiplier
) {
    public DropOptions {
        dropsPerPlayerRequiredDamagePercent = Math.max(0.0, Math.min(100.0, dropsPerPlayerRequiredDamagePercent));
        bonusLuckMultiplier = Math.max(0.0, bonusLuckMultiplier);
        bonusLevelMultiplier = Math.max(0.0, bonusLevelMultiplier);
    }

    public static final DropOptions DEFAULT = new DropOptions(
            false,
            0.0,
            false,
            false,
            false,
            0.0,
            0.0
    );

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean dropsPerPlayer = false;
        private double dropsPerPlayerRequiredDamagePercent = 0.0;
        private boolean dropsDoLootsplosion = false;
        private boolean dropsGlowByDefault = false;
        private boolean dropsHaveBeamByDefault = false;
        private double bonusLuckMultiplier = 0.0;
        private double bonusLevelMultiplier = 0.0;

        public Builder dropsPerPlayer(boolean enable, double requiredDamagePercent) {
            this.dropsPerPlayer = enable;
            this.dropsPerPlayerRequiredDamagePercent = requiredDamagePercent;
            return this;
        }

        public Builder lootsplosion(boolean enable) {
            this.dropsDoLootsplosion = enable;
            return this;
        }

        public Builder glowByDefault(boolean enable) {
            this.dropsGlowByDefault = enable;
            return this;
        }

        public Builder beamByDefault(boolean enable) {
            this.dropsHaveBeamByDefault = enable;
            return this;
        }

        public Builder bonusLuck(double multiplier) {
            this.bonusLuckMultiplier = multiplier;
            return this;
        }

        public Builder bonusLevel(double multiplier) {
            this.bonusLevelMultiplier = multiplier;
            return this;
        }

        public DropOptions build() {
            return new DropOptions(
                    dropsPerPlayer,
                    dropsPerPlayerRequiredDamagePercent,
                    dropsDoLootsplosion,
                    dropsGlowByDefault,
                    dropsHaveBeamByDefault,
                    bonusLuckMultiplier,
                    bonusLevelMultiplier
            );
        }
    }
}
