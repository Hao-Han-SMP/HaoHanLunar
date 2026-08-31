package vn.haohan.lunar.mechanics.boss.warden.ai;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WardenTargeting {
    private WardenTargeting() {}

    public static Player selectBestTarget(IronGolem golem, WardenState state) {
        Location golemLoc = golem.getLocation();
        if (golemLoc.getWorld() == null) return null;

        state.damageThreatTable.keySet().removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            return p == null || !p.isValid() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getWorld() != golemLoc.getWorld();
        });

        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player p : golemLoc.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
            if (p.getLocation().distanceSquared(golemLoc) <= 50.0 * 50.0) {
                nearbyPlayers.add(p);
            }
        }

        if (nearbyPlayers.isEmpty()) return null;

        List<Player> lowHealthPlayers = new ArrayList<>();
        for (Player p : nearbyPlayers) {
            double maxHp = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
            double hp = p.getHealth();
            double hpRatio = hp / maxHp;

            if ((hp <= 7.0 || hpRatio <= 0.35) && p.getLocation().distanceSquared(golemLoc) <= 35.0 * 35.0) {
                lowHealthPlayers.add(p);
            }
        }

        if (!lowHealthPlayers.isEmpty()) {
            lowHealthPlayers.sort((p1, p2) -> {
                int hpCmp = Double.compare(p1.getHealth(), p2.getHealth());
                if (hpCmp != 0) return hpCmp;
                return Double.compare(p1.getLocation().distanceSquared(golemLoc), p2.getLocation().distanceSquared(golemLoc));
            });

            Player executeTarget = lowHealthPlayers.get(0);
            state.currentTargetUUID = executeTarget.getUniqueId();
            return executeTarget;
        }

        if (state.damageThreatTable.isEmpty()) {
            nearbyPlayers.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(golemLoc)));
            Player target = nearbyPlayers.get(0);
            state.currentTargetUUID = target.getUniqueId();
            return target;
        }

        nearbyPlayers.sort((p1, p2) -> {
            double d1 = state.damageThreatTable.getOrDefault(p1.getUniqueId(), 0.0);
            double d2 = state.damageThreatTable.getOrDefault(p2.getUniqueId(), 0.0);
            return Double.compare(d2, d1);
        });

        Player topDamager = nearbyPlayers.get(0);
        double topDmg = state.damageThreatTable.getOrDefault(topDamager.getUniqueId(), 0.0);
        double secondDmg = nearbyPlayers.size() > 1 ? state.damageThreatTable.getOrDefault(nearbyPlayers.get(1).getUniqueId(), 0.0) : 0.0;
        double topDist = golemLoc.distance(topDamager.getLocation());

        if (topDist <= 14.0 || nearbyPlayers.size() == 1 || topDmg >= secondDmg * 1.5) {
            state.currentTargetUUID = topDamager.getUniqueId();
            return topDamager;
        }

        for (Player other : nearbyPlayers) {
            if (other.equals(topDamager)) continue;
            double otherDist = golemLoc.distance(other.getLocation());
            if (otherDist <= 10.0) {
                state.currentTargetUUID = other.getUniqueId();
                return other;
            }
        }

        state.currentTargetUUID = topDamager.getUniqueId();
        return topDamager;
    }
}
