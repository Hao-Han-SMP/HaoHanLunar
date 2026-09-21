package vn.haohan.lunar.api.system.combat.skill.projectile;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized tracker for in-flight projectiles.
 * Advances all active projectiles in a single tick pass without spawning per-projectile tasks.
 */
public final class ProjectileTracker {

    private final List<ActiveProjectile> projectiles = new CopyOnWriteArrayList<>();

    public ActiveProjectile spawn(ActiveProjectile projectile) {
        Objects.requireNonNull(projectile, "Projectile must not be null");
        projectiles.add(projectile);
        return projectile;
    }

    public void tick(long currentTick) {
        projectiles.removeIf(p -> {
            p.tick();
            return p.isDead();
        });
    }

    public int size() {
        return projectiles.size();
    }

    public void cleanupShooter(UUID shooterId) {
        if (shooterId == null) return;
        projectiles.removeIf(p -> {
            if (shooterId.equals(p.shooterId())) {
                p.terminate();
                return true;
            }
            return p.isDead();
        });
    }

    public void clear() {
        for (ActiveProjectile p : projectiles) {
            p.terminate();
        }
        projectiles.clear();
    }
}
