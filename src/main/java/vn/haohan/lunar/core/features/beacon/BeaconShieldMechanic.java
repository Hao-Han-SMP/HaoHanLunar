package vn.haohan.lunar.core.features.beacon;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Manages active lunar beacon shields, ticking their visual fields and lifecycle events.
 */
public final class BeaconShieldMechanic implements Listener, LunarSubSystem {

    private static final String LUNAR_WORLD = "haohan:lunar";
    private final List<BeaconShield> shields = new ArrayList<>();
    private final List<ItemDisplay> displays = new ArrayList<>();

    private final double maxRadius;
    private final double expansionPerTick;
    private final double edgeParticleSpacing;
    private final int renderInterval;
    private final double boundaryParticleDistance;
    private final double rotationSpeed;
    private final int collapseDuration;
    private long animationTick;

    public BeaconShieldMechanic(HaoHanLunarPlugin plugin) {
        this.maxRadius = Math.max(4.0, plugin.getConfig().getDouble("beacon-shield.radius", 48.0));
        this.expansionPerTick = Math.max(0.25, plugin.getConfig().getDouble("beacon-shield.expansion-speed", 3.0));
        this.edgeParticleSpacing = Math.max(0.75,
                plugin.getConfig().getDouble("beacon-shield.edge-particle-spacing", 1.0));
        this.renderInterval = Math.max(1,
                plugin.getConfig().getInt("beacon-shield.render-interval", 2));
        this.boundaryParticleDistance = Math.max(1.0,
                plugin.getConfig().getDouble("beacon-shield.boundary-particle-distance", 8.0));
        this.rotationSpeed = plugin.getConfig().getDouble("beacon-shield.rotation-speed", 0.012);
        this.collapseDuration = Math.max(6,
                plugin.getConfig().getInt("beacon-shield.collapse-duration", 24));
    }

    @Override
    public String name() {
        return "BeaconShield";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        removeAll();
    }

    @Override
    public void tick() {
        animationTick++;
        double rotationAngle = animationTick * rotationSpeed;
        Iterator<BeaconShield> iterator = shields.iterator();

        while (iterator.hasNext()) {
            BeaconShield shield = iterator.next();
            if (shield.isCollapsing()) {
                if (tickCollapse(shield)) {
                    BeaconShieldRenderer.removeDisplays(shield, displays);
                    iterator.remove();
                }
                continue;
            }
            if (!shield.isValid(LUNAR_WORLD)) {
                BeaconShieldRenderer.removeDisplays(shield, displays);
                iterator.remove();
                continue;
            }

            shield.setRadius(Math.min(maxRadius, shield.getRadius() + expansionPerTick));
            BeaconShieldRenderer.updateDisplays(shield, animationTick, rotationSpeed);

            if (shield.getRadius() < maxRadius) {
                if (animationTick % renderInterval == 0) {
                    BeaconShieldRenderer.renderExpansionWireframe(shield, edgeParticleSpacing, rotationAngle);
                }
            } else {
                BeaconShieldRenderer.renderInteriorParticles(shield, boundaryParticleDistance, rotationAngle);
                if (animationTick % 10 == 0) {
                    BeaconShieldRenderer.updateGroundContacts(shield, rotationAngle);
                }
                if (animationTick % 2 == 0) {
                    BeaconShieldRenderer.renderGroundContacts(shield);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType().name().equals("BEACON") && isLunar(block.getWorld())) {
            shields.removeIf(shield -> shield.getBeacon().equals(block.getLocation()));
            BeaconShield shield = new BeaconShield(block.getLocation().clone().add(0.5, 0.0, 0.5));
            shields.add(shield);
            BeaconShieldRenderer.createDisplays(shield, displays, animationTick, rotationSpeed);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.8f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType().name().equals("BEACON")) {
            for (BeaconShield shield : shields) {
                if (shield.getBeacon().getBlock().equals(event.getBlock())) {
                    shield.beginCollapse();
                }
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        removeShields(shield -> shield.getBeacon().getWorld() == event.getWorld());
    }

    public void removeAll() {
        removeShields(shield -> true);
    }

    public boolean isInShield(Location location) {
        if (location == null || !isLunar(location.getWorld())) return false;
        for (BeaconShield shield : shields) {
            if (shield.isInShield(location)) {
                return true;
            }
        }
        return false;
    }

    private boolean tickCollapse(BeaconShield shield) {
        shield.incrementCollapseTick();
        double progress = Math.min(1.0, (double) shield.getCollapseTick() / collapseDuration);
        double remaining = 1.0 - progress;
        shield.setRadius(shield.getCollapseStartRadius() * remaining * remaining);

        BeaconShieldRenderer.updateDisplays(shield, animationTick, rotationSpeed);
        if (shield.getRadius() > 0.05) {
            BeaconShieldRenderer.renderCollapseParticles(shield, progress, animationTick * rotationSpeed);
        }
        return shield.getCollapseTick() >= collapseDuration;
    }

    private void removeShields(Predicate<BeaconShield> predicate) {
        Iterator<BeaconShield> iterator = shields.iterator();
        while (iterator.hasNext()) {
            BeaconShield shield = iterator.next();
            if (!predicate.test(shield)) continue;
            BeaconShieldRenderer.removeDisplays(shield, displays);
            iterator.remove();
        }
    }

    private boolean isLunar(World world) {
        return world != null && world.getKey().toString().equals(LUNAR_WORLD);
    }
}
