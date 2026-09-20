package vn.haohan.lunar.api.system.world.environment;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages spatial combat fields spawned by boss skills:
 * - GravityZone: alters player movement/fall speed within radius.
 * - OxygenField: drains or replenishes player air/oxygen tanks within radius.
 */
public final class EnvironmentalFieldTracker {

    public enum OxygenMode {
        DRAIN,
        RESTORE
    }

    public record GravityZone(
            Location center,
            double radius,
            double multiplier,
            int maxDurationTicks,
            long spawnTick
    ) {
        public boolean isExpired(long currentTick) {
            return currentTick >= spawnTick + maxDurationTicks;
        }

        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || center.getWorld() == null) return false;
            if (!loc.getWorld().equals(center.getWorld())) return false;
            return loc.distanceSquared(center) <= radius * radius;
        }
    }

    public record OxygenField(
            Location center,
            double radius,
            OxygenMode mode,
            int amount,
            int maxDurationTicks,
            long spawnTick
    ) {
        public boolean isExpired(long currentTick) {
            return currentTick >= spawnTick + maxDurationTicks;
        }

        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || center.getWorld() == null) return false;
            if (!loc.getWorld().equals(center.getWorld())) return false;
            return loc.distanceSquared(center) <= radius * radius;
        }
    }

    private final List<GravityZone> gravityZones = new CopyOnWriteArrayList<>();
    private final List<OxygenField> oxygenFields = new CopyOnWriteArrayList<>();

    public void addGravityZone(Location center, double radius, double multiplier, int durationTicks, long currentTick) {
        if (center == null) return;
        gravityZones.add(new GravityZone(center.clone(), Math.max(1.0, radius), multiplier, Math.max(1, durationTicks), currentTick));
    }

    public void addOxygenField(Location center, double radius, OxygenMode mode, int amount, int durationTicks, long currentTick) {
        if (center == null) return;
        oxygenFields.add(new OxygenField(center.clone(), Math.max(1.0, radius), mode != null ? mode : OxygenMode.DRAIN, amount, Math.max(1, durationTicks), currentTick));
    }

    public List<GravityZone> getActiveGravityZones() {
        return List.copyOf(gravityZones);
    }

    public List<OxygenField> getActiveOxygenFields() {
        return List.copyOf(oxygenFields);
    }

    /**
     * Executes the environmental field tick.
     *
     * @param currentTick current world tick count
     * @param onlinePlayers online players provider
     * @param oxygenConsumer optional consumer to apply custom oxygen logic to players (e.g. PlayerLunarData)
     */
    public void tick(long currentTick, Iterable<? extends Player> onlinePlayers, Consumer<PlayerOxygenAdjustment> oxygenConsumer) {
        // 1. Process and prune Gravity Zones
        gravityZones.removeIf(zone -> zone.isExpired(currentTick));
        for (GravityZone zone : gravityZones) {
            if (onlinePlayers == null) continue;
            for (Player player : onlinePlayers) {
                if (player != null && player.isOnline() && !player.isDead() && zone.contains(player.getLocation())) {
                    applyGravityEffect(player, zone.multiplier());
                }
            }
        }

        // 2. Process and prune Oxygen Fields
        oxygenFields.removeIf(field -> field.isExpired(currentTick));
        for (OxygenField field : oxygenFields) {
            if (onlinePlayers == null) continue;
            for (Player player : onlinePlayers) {
                if (player != null && player.isOnline() && !player.isDead() && field.contains(player.getLocation())) {
                    applyOxygenEffect(player, field, oxygenConsumer);
                }
            }
        }
    }

    private void applyGravityEffect(Player player, double multiplier) {
        // If multiplier < 1.0 (e.g. low gravity), apply gentle upward lift to simulate low gravity
        try {
            Vector vel = player.getVelocity();
            if (vel != null && multiplier < 1.0 && vel.getY() < 0) {
                // Counteract gravity partially
                double lift = Math.min(0.06, (1.0 - multiplier) * 0.05);
                player.setVelocity(vel.clone().add(new Vector(0, lift, 0)));
            }
        } catch (Throwable ignored) {}
    }

    private void applyOxygenEffect(Player player, OxygenField field, Consumer<PlayerOxygenAdjustment> oxygenConsumer) {
        int adjustment = field.mode() == OxygenMode.RESTORE ? field.amount() : -field.amount();
        try {
            int currentAir = player.getRemainingAir();
            int newAir = Math.max(0, Math.min(player.getMaximumAir(), currentAir + (adjustment * 15)));
            player.setRemainingAir(newAir);
        } catch (Throwable ignored) {}

        if (oxygenConsumer != null) {
            oxygenConsumer.accept(new PlayerOxygenAdjustment(player, field.mode(), field.amount()));
        }
    }

    public void clear() {
        gravityZones.clear();
        oxygenFields.clear();
    }

    public record PlayerOxygenAdjustment(Player player, OxygenMode mode, int amount) {}
}
