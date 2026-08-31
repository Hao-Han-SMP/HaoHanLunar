package vn.haohan.lunar.mechanics.boss.warden;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.ai.WardenAITask;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldBlockSkill;
import vn.haohan.lunar.mechanics.boss.warden.ui.WardenBGMManager;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenTrailCaptureSystem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LunarWardenMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private final Map<UUID, WardenState> bossStates = new ConcurrentHashMap<>();

    public LunarWardenMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        new WardenAITask(plugin, this).runTaskTimer(plugin, 1L, 1L);
    }

    public Map<UUID, WardenState> getBossStates() {
        return bossStates;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof IronGolem golem && bossStates.containsKey(golem.getUniqueId())) {
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof IronGolem golem && bossStates.containsKey(golem.getUniqueId())) {
            WardenState state = bossStates.get(golem.getUniqueId());

            // Always clear no-damage ticks to ensure consecutive melee/projectile strikes land cleanly
            golem.setNoDamageTicks(0);

            // 1. PURE SHIELD BLOCK PARRY COUNTER: Boss still takes 10% damage and triggers counter parry
            if (state.currentBehavior == WardenBehavior.ATTACKING && "skill_shield_block".equals(state.currentAttack)) {
                e.setDamage(e.getDamage() * 0.1);
                Location hitLoc = golem.getLocation().add(golem.getLocation().getDirection().multiply(1.6)).add(0, 2.0, 0);
                ShieldBlockSkill.handleParryCounter(plugin, golem, state, e.getDamager(), hitLoc);
                return;
            }

            // Audio & VFX feedback when damaged by arrows or projectiles directly
            if (e.getDamager() instanceof Projectile) {
                Location hitLoc = golem.getLocation().add(0, 3.2, 0);
                golem.getWorld().playSound(hitLoc, Sound.ENTITY_ARROW_HIT_PLAYER, 1.4f, 1.1f);
                golem.getWorld().spawnParticle(Particle.DUST, hitLoc, 15, 0.3, 0.4, 0.3, 0.0,
                        new Particle.DustOptions(Color.fromRGB(200, 35, 35), 1.6f));
            }

            // Damage reduction when casting celestial charge summon
            if (state.currentBehavior == WardenBehavior.ATTACKING && "skill_charge_summon".equals(state.currentAttack)) {
                e.setDamage(e.getDamage() * 0.1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();
        Entity hitEntity = e.getHitEntity();
        Location hitLoc = projectile.getLocation();
        World world = hitLoc.getWorld();
        if (world == null) return;

        IronGolem targetBoss = null;
        WardenState targetState = null;

        // 1. Direct hit on the IronGolem boss
        if (hitEntity instanceof IronGolem golem && bossStates.containsKey(golem.getUniqueId())) {
            targetBoss = golem;
            targetState = bossStates.get(golem.getUniqueId());
        }

        // 2. Hit on a sub-entity / Interaction hitbox belonging to ModelEngine or near the boss
        if (targetBoss == null && hitEntity != null) {
            for (Map.Entry<UUID, WardenState> entry : bossStates.entrySet()) {
                Entity bossEnt = Bukkit.getEntity(entry.getKey());
                if (bossEnt instanceof IronGolem golem && bossEnt.isValid()) {
                    Location bossLoc = bossEnt.getLocation();
                    if (bossLoc.getWorld().equals(hitEntity.getWorld())) {
                        double dx = Math.abs(hitEntity.getLocation().getX() - bossLoc.getX());
                        double dz = Math.abs(hitEntity.getLocation().getZ() - bossLoc.getZ());
                        double dy = hitEntity.getLocation().getY() - bossLoc.getY();
                        // 3D expanded bounding box check (radius 3.0m, height up to 8.5m)
                        if (dx <= 3.0 && dz <= 3.0 && dy >= -0.5 && dy <= 8.5) {
                            targetBoss = golem;
                            targetState = entry.getValue();
                            break;
                        }
                    }
                }
            }
        }

        // 3. Arrow grazed near the boss model in 3D space when hitting block/air
        if (targetBoss == null) {
            for (Map.Entry<UUID, WardenState> entry : bossStates.entrySet()) {
                Entity bossEnt = Bukkit.getEntity(entry.getKey());
                if (bossEnt instanceof IronGolem golem && bossEnt.isValid()) {
                    Location bossLoc = bossEnt.getLocation();
                    if (bossLoc.getWorld().equals(hitLoc.getWorld())) {
                        double dx = Math.abs(hitLoc.getX() - bossLoc.getX());
                        double dz = Math.abs(hitLoc.getZ() - bossLoc.getZ());
                        double dy = hitLoc.getY() - bossLoc.getY();
                        if (dx <= 2.6 && dz <= 2.6 && dy >= -0.2 && dy <= 8.0) {
                            targetBoss = golem;
                            targetState = entry.getValue();
                            break;
                        }
                    }
                }
            }
        }

        if (targetBoss == null || targetState == null) return;

        // Determine shooter / damager
        LivingEntity shooter = null;
        if (projectile.getShooter() instanceof LivingEntity livingShooter) {
            shooter = livingShooter;
        }

        // Calculate accurate projectile damage
        double damage = 8.0;
        if (projectile instanceof AbstractArrow arrow) {
            double speed = projectile.getVelocity().length();
            damage = Math.max(7.0, arrow.getDamage() * Math.max(1.0, speed * 2.6));
            if (arrow.isCritical()) {
                damage *= 1.4;
            }
        } else if (projectile instanceof Trident) {
            damage = 16.0;
        }

        // 1. PURE SHIELD BLOCK: Parry projectile and boss takes 10% damage
        if (targetState.currentBehavior == WardenBehavior.ATTACKING && "skill_shield_block".equals(targetState.currentAttack)) {
            damage *= 0.1;
            targetBoss.setNoDamageTicks(0);
            if (shooter != null) {
                targetBoss.damage(damage, shooter);
            } else {
                targetBoss.damage(damage, projectile);
            }
            ShieldBlockSkill.handleParryCounter(plugin, targetBoss, targetState, shooter != null ? shooter : projectile, hitLoc);
            projectile.remove();
            return;
        }

        // 2. Shield / Blocking check: Boss deflects arrows when in other shield skills
        boolean isShieldBlocking = targetState.currentBehavior == WardenBehavior.ATTACKING &&
                ("skill_shield_block_push".equals(targetState.currentAttack) || "skill_shield_sword_slam".equals(targetState.currentAttack));

        if (isShieldBlocking) {
            world.playSound(hitLoc, Sound.ITEM_SHIELD_BLOCK, 1.8f, 1.1f);
            WardenAudio.playCustomSound(hitLoc, "haohan:boss.parry", 1.8f, 1.2f);
            world.spawnParticle(Particle.CRIT, hitLoc, 12, 0.2, 0.2, 0.2, 0.15);
            world.spawnParticle(Particle.DUST, hitLoc, 10, 0.2, 0.2, 0.2, 0.0,
                    new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.6f));
            projectile.remove();
            return;
        }

        // Celestial Charge summon 90% resistance
        if (targetState.currentBehavior == WardenBehavior.ATTACKING && "skill_charge_summon".equals(targetState.currentAttack)) {
            damage *= 0.1;
        }

        // Apply direct damage to the boss
        targetBoss.setNoDamageTicks(0);
        if (shooter != null) {
            targetBoss.damage(damage, shooter);
        } else {
            targetBoss.damage(damage, projectile);
        }

        // Impact audio & particles
        world.playSound(hitLoc, Sound.ENTITY_ARROW_HIT_PLAYER, 1.4f, 1.0f);
        world.playSound(hitLoc, Sound.ENTITY_IRON_GOLEM_HURT, 1.2f, 0.85f);
        WardenAudio.playCustomSound(hitLoc, "haohan:boss.slash_light", 1.4f, 1.5f);

        world.spawnParticle(Particle.DUST, hitLoc, 18, 0.25, 0.35, 0.25, 0.0,
                new Particle.DustOptions(Color.fromRGB(190, 30, 30), 1.8f));
        world.spawnParticle(Particle.CRIT, hitLoc, 8, 0.2, 0.2, 0.2, 0.1);

        // Remove projectile to prevent ghost arrows or duplicate hits
        projectile.remove();
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (bossStates.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (bossStates.containsKey(e.getEntity().getUniqueId())) {
            IronGolem golem = (IronGolem) e.getEntity();
            WardenState state = bossStates.get(golem.getUniqueId());

            if (state.bossBar != null) {
                state.bossBar.name(net.kyori.adventure.text.Component.empty());
                for (Player player : golem.getWorld().getPlayers()) {
                    player.hideBossBar(state.bossBar);
                }
            }

            // Stop all BGM for this boss
            WardenBGMManager.stopBGMForBoss(golem.getUniqueId());

            if (state.impaledTargetUUID != null) {
                Entity victim = org.bukkit.Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p) {
                    p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                }
            }

            WardenTrailCaptureSystem.clearHistory(golem.getUniqueId());
            bossStates.remove(golem.getUniqueId());

            e.getDrops().clear();
            e.setDroppedExp(5000);

            try {
                var itemFactory = HaoHanItemCore.get().getItemFactory();
                ItemStack swordLoot = itemFactory.create("haohan:lunar_claymore", 1);
                golem.getWorld().dropItemNaturally(golem.getLocation(), swordLoot);
            } catch (Throwable t) {
                golem.getWorld().dropItemNaturally(golem.getLocation(), new ItemStack(Material.NETHERITE_SWORD));
            }

            golem.getWorld().dropItemNaturally(golem.getLocation(), new ItemStack(Material.NETHERITE_INGOT, 8));
        }
    }
}
