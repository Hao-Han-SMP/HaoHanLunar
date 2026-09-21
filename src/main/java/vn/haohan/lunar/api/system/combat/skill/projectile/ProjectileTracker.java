package vn.haohan.lunar.api.system.combat.skill.projectile;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized tracker for in-flight projectiles.
 * Advances all active projectiles in a single tick pass without spawning per-projectile tasks.
 */
public final class ProjectileTracker {

    private final Set<ActiveProjectile> projectiles = ConcurrentHashMap.newKeySet();

    public ActiveProjectile spawn(ActiveProjectile projectile) {
        Objects.requireNonNull(projectile, "Projectile must not be null");
        projectiles.add(projectile);
        return projectile;
    }

    public void tick(long currentTick) {
        Iterator<ActiveProjectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            ActiveProjectile p = iterator.next();
            p.tick();
            if (p.isDead()) {
                iterator.remove();
            }
        }
    }

    public int size() {
        return projectiles.size();
    }

    public void cleanupShooter(UUID shooterId) {
        if (shooterId == null) return;
        Iterator<ActiveProjectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            ActiveProjectile p = iterator.next();
            if (shooterId.equals(p.shooterId())) {
                p.terminate();
                iterator.remove();
            } else if (p.isDead()) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        for (ActiveProjectile p : projectiles) {
            p.terminate();
        }
        projectiles.clear();
    }
}
