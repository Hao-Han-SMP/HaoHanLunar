package vn.haohan.lunar.api.system.combat.reward;

/**
 * Tier categorization for boss kill participation rewards based on damage contribution.
 */
public enum RewardTier {
    /** Top damager (Rank 1) receiving exclusive MVP chest and title. */
    TIER_1_MVP,

    /** Major contributor with at least 10% total boss damage. */
    TIER_2_MAJOR,

    /** Participation contributor who dealt damage under 10%. */
    TIER_3_PARTICIPATION;

    /**
     * Determines reward tier from MVP status and damage percentage (0 - 100).
     */
    public static RewardTier of(boolean isMvp, double percentage) {
        if (isMvp) {
            return TIER_1_MVP;
        }
        if (percentage >= 10.0) {
            return TIER_2_MAJOR;
        }
        return TIER_3_PARTICIPATION;
    }
}
