package vn.haohan.lunar.api.spawner.cluster;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable definition for spawning a clustered mob pack (leader + minions).
 */
public record ClusterDefinition(
        String id,
        String leaderMobId,
        String minionMobId,
        int minMinions,
        int maxMinions,
        double radius,
        double chance,
        boolean enabled
) {
    public ClusterDefinition {
        Objects.requireNonNull(id, "Cluster id must not be null");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) throw new IllegalArgumentException("Cluster id cannot be empty");

        Objects.requireNonNull(leaderMobId, "Leader mob id must not be null");
        leaderMobId = leaderMobId.trim().toLowerCase(Locale.ROOT);
        if (leaderMobId.isEmpty()) throw new IllegalArgumentException("Leader mob id cannot be empty");

        minionMobId = (minionMobId != null && !minionMobId.isBlank())
                ? minionMobId.trim().toLowerCase(Locale.ROOT) : leaderMobId;

        minMinions = Math.max(0, minMinions);
        maxMinions = Math.max(minMinions, maxMinions);
        radius = Math.max(1.0, Math.min(radius, 64.0));
        chance = Math.max(0.0, Math.min(chance, 1.0));
    }

    public static Builder builder(String id, String leaderMobId) {
        return new Builder(id, leaderMobId);
    }

    public static final class Builder {
        private final String id;
        private final String leaderMobId;
        private String minionMobId;
        private int minMinions = 2;
        private int maxMinions = 5;
        private double radius = 8.0;
        private double chance = 1.0;
        private boolean enabled = true;

        public Builder(String id, String leaderMobId) {
            this.id = id;
            this.leaderMobId = leaderMobId;
            this.minionMobId = leaderMobId;
        }

        public Builder minionMobId(String minionMobId) {
            this.minionMobId = minionMobId;
            return this;
        }

        public Builder minMinions(int minMinions) {
            this.minMinions = minMinions;
            return this;
        }

        public Builder maxMinions(int maxMinions) {
            this.maxMinions = maxMinions;
            return this;
        }

        public Builder radius(double radius) {
            this.radius = radius;
            return this;
        }

        public Builder chance(double chance) {
            this.chance = chance;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ClusterDefinition build() {
            return new ClusterDefinition(id, leaderMobId, minionMobId, minMinions, maxMinions, radius, chance, enabled);
        }
    }
}
