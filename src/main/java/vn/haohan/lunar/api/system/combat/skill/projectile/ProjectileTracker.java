package vn.haohan.lunar.api.system.combat.skill.projectile;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
        Iterator<ActiveProjectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            ActiveProjectile p = iterator.next();
            p.tick();
            if (p.isDead()) {
                projectiles.remove(p);
            }
        }
    }

    public int size() {
        return projectiles.size();
    }

    public void clear() {
        for (ActiveProjectile p : projectiles) {
            p.terminate();
        }
        projectiles.clear();
    }
}
