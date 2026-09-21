package vn.haohan.lunar.api.system.combat;

import java.util.*;

/**
 * Immutable snapshot of combat damage contribution against an ActiveMob.
 */
public record DamageContributionSnapshot(UUID mobId,
                                         double totalDamage,
                                         Map<UUID, Double> damageMap,
                                         Map<UUID, Double> percentageMap,
                                         UUID topDamager,
                                         double topDamage,
                                         UUID killer) {

    public DamageContributionSnapshot {
        Objects.requireNonNull(mobId, "Mob entity UUID must not be null");
        damageMap = Collections.unmodifiableMap(new LinkedHashMap<>(damageMap != null ? damageMap : Map.of()));
        percentageMap = Collections.unmodifiableMap(new LinkedHashMap<>(percentageMap != null ? percentageMap : Map.of()));
    }

    public static DamageContributionSnapshot create(UUID mobId, Map<UUID, Double> rawDamage, UUID killer) {
        Objects.requireNonNull(mobId, "Mob entity UUID must not be null");

        double total = 0.0;
        UUID top = null;
        double maxDmg = 0.0;

        Map<UUID, Double> sortedDamage = new LinkedHashMap<>();
        if (rawDamage != null) {
            // Filter non-positive, sort descending by damage
            List<Map.Entry<UUID, Double>> sortedEntries = rawDamage.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0.0)
                    .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                    .toList();

            for (Map.Entry<UUID, Double> entry : sortedEntries) {
                double dmg = entry.getValue();
                total += dmg;
                sortedDamage.put(entry.getKey(), dmg);
                if (top == null || dmg > maxDmg) {
                    top = entry.getKey();
                    maxDmg = dmg;
                }
            }
        }

        Map<UUID, Double> percentages = new LinkedHashMap<>();
        if (total > 0.0) {
            for (Map.Entry<UUID, Double> entry : sortedDamage.entrySet()) {
                double pct = (entry.getValue() / total) * 100.0;
                percentages.put(entry.getKey(), pct);
            }
        }

        return new DamageContributionSnapshot(
                mobId,
                total,
                sortedDamage,
                percentages,
                top,
                maxDmg,
                killer
        );
    }

    public double getDamage(UUID playerId) {
        if (playerId == null) return 0.0;
        return damageMap.getOrDefault(playerId, 0.0);
    }

    public double getPercentage(UUID playerId) {
        if (playerId == null) return 0.0;
        return percentageMap.getOrDefault(playerId, 0.0);
    }

    /**
     * Checks if a player contributed at least the required percentage of damage.
     *
     * @param playerId player UUID
     * @param requiredPercent minimum percentage required (0 to 100)
     */
    public boolean meetsRequiredPercentage(UUID playerId, double requiredPercent) {
        if (playerId == null) return false;
        return getPercentage(playerId) >= Math.max(0.0, requiredPercent);
    }

    /**
     * Returns a list of player UUIDs who contributed at least the minimum percentage, sorted descending by damage.
     */
    public List<UUID> getEligiblePlayers(double minPercentage) {
        double threshold = Math.max(0.0, minPercentage);
        return damageMap.entrySet().stream()
                .filter(e -> getPercentage(e.getKey()) >= threshold)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Optional<UUID> getTopDamager() {
        return Optional.ofNullable(topDamager);
    }

    public Optional<UUID> getKiller() {
        return Optional.ofNullable(killer);
    }
}
