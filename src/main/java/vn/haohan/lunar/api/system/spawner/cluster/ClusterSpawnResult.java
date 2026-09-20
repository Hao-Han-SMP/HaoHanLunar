package vn.haohan.lunar.api.spawner.cluster;

import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.List;

/**
 * Result of a cluster spawn execution.
 */
public record ClusterSpawnResult(
        boolean success,
        ActiveMob leader,
        List<ActiveMob> minions,
        String message
) {
    public static ClusterSpawnResult successful(ActiveMob leader, List<ActiveMob> minions) {
        return new ClusterSpawnResult(true, leader, minions != null ? List.copyOf(minions) : List.of(), "SUCCESS");
    }

    public static ClusterSpawnResult failed(String message) {
        return new ClusterSpawnResult(false, null, List.of(), message);
    }
}
