package vn.haohan.lunar.mechanics.boss.warden.skills;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenEntityManager;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.util.MathUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.BlockWaveRenderer;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

import java.util.Optional;
import java.util.Random;

public final class ShieldSwordSlamSkill {
    private ShieldSwordSlamSkill() {}

    public static void triggerShieldSwordSlamSkill(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, Random random) {
        state.currentBehavior = WardenBehavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.isMovingAttack = false;
        state.shieldThrownDone = false;
        state.swordThrownDone = false;
        state.slamImpactDone = false;
        state.weaponPickupDone = false;
        state.shieldSwordSlamCooldown = MathUtil.secondsToTicks(20.0 + random.nextDouble() * 6.0);

        Location golemLoc = golem.getLocation();
        Location targetLoc = target.getLocation();
        World world = golem.getWorld();

        // 1. TACTICAL REPOSITION: Teleport backward to keep safe distance before casting
        double dx = golemLoc.getX() - targetLoc.getX();
        double dz = golemLoc.getZ() - targetLoc.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 14.0 && world != null) {
            Vector away = golemLoc.toVector().subtract(targetLoc.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = golemLoc.getDirection().setY(0).multiply(-1);
            }
            Location backTarget = targetLoc.clone().add(away.normalize().multiply(13.5));
            Location safeBack = WardenLocationUtil.findSafeTeleportLocation(golemLoc, backTarget, 2.0);

            world.spawnParticle(Particle.PORTAL, golemLoc.clone().add(0, 1.5, 0), 35, 0.5, 1.0, 0.5, 0.4);
            world.spawnParticle(Particle.DUST, golemLoc.clone().add(0, 1.0, 0), 25, 0.4, 0.8, 0.4, 0.0,
                    new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.8f));
            world.playSound(golemLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.8f, 1.1f);
            WardenAudio.playCustomSound(golemLoc, "haohan:boss.mortalblade_whoosh", 1.6f, 1.3f);

            safeBack.setDirection(targetLoc.toVector().subtract(safeBack.toVector()));
            golem.teleport(safeBack);

            world.spawnParticle(Particle.PORTAL, safeBack.clone().add(0, 1.5, 0), 30, 0.5, 1.0, 0.5, 0.4);
        }

        state.slamTargetGroundLoc = WardenLocationUtil.adjustToTerrainSurface(target.getLocation(), 0.0);
        WardenCombatHandler.executeSingleAttackPhase(golem, state, "skill_shield_sword_slam", 0.15);
    }

    public static void handleShieldSwordSlamExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;

        // Dynamic player tracking: Continuously track target location during early airborne phase
        if (target != null && target.isValid() && target.getGameMode() != GameMode.SPECTATOR && !target.isDead()) {
            if (state.attackTicks <= MathUtil.secondsToTicks(3.90)) {
                state.slamTargetGroundLoc = WardenLocationUtil.adjustToTerrainSurface(target.getLocation(), 0.0);
            }
        }
        if (state.slamTargetGroundLoc == null) {
            state.slamTargetGroundLoc = WardenLocationUtil.adjustToTerrainSurface(golemLoc.clone().add(golemLoc.getDirection().multiply(6.0)), 0.0);
        }

        Location targetLand = state.slamTargetGroundLoc;

        // Continuous damage, blood drain visual and position lock while player is impaled by the claymore (like ThrustFling)
        if (state.impaledTargetUUID != null && !state.weaponPickupDone && state.attackTicks < MathUtil.secondsToTicks(4.65)) {
            Player impaledPlayer = Bukkit.getPlayer(state.impaledTargetUUID);
            if (impaledPlayer != null && impaledPlayer.isValid() && impaledPlayer.getGameMode() != GameMode.SPECTATOR && !impaledPlayer.isDead()) {
                Location pinLoc = targetLand.clone().add(0, 0.05, 0);
                impaledPlayer.teleport(pinLoc);
                impaledPlayer.setVelocity(new Vector(0, 0.0, 0));
                impaledPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 6, false, false, true));
                impaledPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 10, 128, false, false, false));

                // Continuous bleed / blood drain damage pulse every 4 ticks (0.20s) exactly matching ThrustFlingSkill
                if (state.attackTicks % 4 == 0) {
                    impaledPlayer.setNoDamageTicks(0);
                    WardenCombatHandler.applyCombatDamage(impaledPlayer, 4.0, golem);
                    impaledPlayer.playSound(impaledPlayer.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                    WardenAudio.playCustomSound(impaledPlayer.getLocation(), "haohan:boss.slash_light", 1.2f, 1.4f);

                    Location bloodLoc = impaledPlayer.getLocation().add(0, 0.9, 0);
                    world.spawnParticle(Particle.DUST, bloodLoc, 20, 0.25, 0.35, 0.25, 0.0,
                            new Particle.DustOptions(Color.MAROON, 1.9f));
                    world.spawnParticle(Particle.DUST, bloodLoc, 15, 0.2, 0.25, 0.2, 0.0,
                            new Particle.DustOptions(Color.RED, 1.7f));
                    world.spawnParticle(Particle.CRIMSON_SPORE, bloodLoc, 12, 0.3, 0.3, 0.3, 0.08);
                    world.spawnParticle(Particle.SWEEP_ATTACK, bloodLoc, 1, 0.2, 0.1, 0.2, 0);
                    world.spawnParticle(Particle.BLOCK, bloodLoc, 8, 0.2, 0.2, 0.2, 0.1,
                            Material.REDSTONE_BLOCK.createBlockData());
                }
            } else {
                state.impaledTargetUUID = null;
            }
        }

        // Always orient boss towards target
        Vector toTargetXZ = targetLand.toVector().subtract(golemLoc.toVector()).setY(0);
        float dynamicYaw = toTargetXZ.lengthSquared() > 0.01 ? MathUtil.getYaw(toTargetXZ) : golemLoc.getYaw();

        // PHASE 1: HIGH AIRBORNE LEAP & MID-AIR AIM (0.0s - 1.55s)
        if (state.attackTicks <= MathUtil.secondsToTicks(1.55)) {
            if (state.attackTicks <= MathUtil.secondsToTicks(0.40)) {
                Vector leapVel = golem.getVelocity();
                leapVel.setY(0.68);
                Vector forwardGlide = targetLand.toVector().subtract(golemLoc.toVector()).setY(0).normalize().multiply(0.24);
                leapVel.setX(forwardGlide.getX());
                leapVel.setZ(forwardGlide.getZ());
                golem.setVelocity(leapVel);
            } else {
                Vector hoverVel = golem.getVelocity();
                hoverVel.setY(Math.max(-0.04, hoverVel.getY() * 0.5));
                golem.setVelocity(hoverVel);
            }

            golem.setRotation(dynamicYaw, 0f);
            ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
            if (me != null) {
                me.setYBodyRot(dynamicYaw);
                me.setYHeadRot(dynamicYaw);
            }

            Location wingAura = golemLoc.clone().add(0, 2.5, 0);
            world.spawnParticle(Particle.PORTAL, wingAura, 8, 0.6, 0.6, 0.6, 0.2);
            world.spawnParticle(Particle.DUST, wingAura, 6, 0.5, 0.5, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(80, 210, 255), 1.8f));
        }

        // PHASE 2: SHIELD DETACH & THROWN FLYING SHIELD (1.65s)
        if (state.attackTicks == MathUtil.secondsToTicks(1.65) && !state.shieldThrownDone) {
            state.shieldThrownDone = true;

            Location shieldSpawn = getBoneWorldLocation(golem, "right_forearm", "right_arm");
            if (shieldSpawn == null) {
                shieldSpawn = golemLoc.clone().add(0, 3.2, 0).add(golemLoc.getDirection().multiply(1.5));
            }

            WardenAnimationController.setBoneVisible(golem, "shield", false);

            world.playSound(shieldSpawn, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.8f, 1.6f);
            world.playSound(shieldSpawn, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 1.5f);
            WardenAudio.playCustomSound(shieldSpawn, "haohan:boss.slash_light", 1.8f, 1.2f);

            spawnThrownShieldProjectile(plugin, golem, state, shieldSpawn, targetLand.clone());
        }

        // PHASE 3: SWORD DETACH & THROWN CLAYMORE PLUNGE (2.55s)
        if (state.attackTicks == MathUtil.secondsToTicks(2.55) && !state.swordThrownDone) {
            state.swordThrownDone = true;

            Location swordSpawn = getBoneWorldLocation(golem, "left_forearm", "left_arm");
            if (swordSpawn == null) {
                swordSpawn = golemLoc.clone().add(0, 3.8, 0).add(golemLoc.getDirection().multiply(1.5));
            }

            WardenAnimationController.setBoneVisible(golem, "sword", false);

            world.playSound(swordSpawn, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.8f);
            world.playSound(swordSpawn, Sound.ITEM_TRIDENT_THROW, 1.8f, 0.7f);
            WardenAudio.playCustomSound(swordSpawn, "haohan:boss.murasama", 1.8f, 1.0f);

            spawnThrownSwordProjectile(plugin, golem, state, swordSpawn, targetLand.clone());
        }

        // PHASE 4: SUPERSONIC RIDER KICK METEOR DIVE & GROUND SHOCKWAVE (3.80s - 4.50s)
        // Lands 4.2 blocks behind the thrown weapons location and triggers 7.8m shockwave ONLY when contacting ground
        if (state.attackTicks >= MathUtil.secondsToTicks(3.80) && state.attackTicks <= MathUtil.secondsToTicks(4.50) && !state.slamImpactDone) {
            Vector toTarget = targetLand.toVector().subtract(golemLoc.toVector()).setY(0);
            Vector approachDir = toTarget.lengthSquared() > 0.01 ? toTarget.clone().normalize() : golemLoc.getDirection().setY(0).normalize();

            // Offset landing position: lands ~4.2 blocks back from the thrown weapons location
            Location nearLandingTarget = targetLand.clone().subtract(approachDir.clone().multiply(4.2));
            nearLandingTarget = WardenLocationUtil.adjustToTerrainSurface(nearLandingTarget, 0.0);

            Vector toLand = nearLandingTarget.toVector().subtract(golemLoc.toVector());
            Vector diveDir = toLand.clone().setY(0);
            double distToLandXZ = diveDir.length();

            float kickYaw = approachDir.lengthSquared() > 0.01 ? MathUtil.getYaw(approachDir) : dynamicYaw;
            golem.setRotation(kickYaw, 0f);
            ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
            if (me != null) {
                me.setYBodyRot(kickYaw);
                me.setYHeadRot(kickYaw);
            }

            if (state.attackTicks == MathUtil.secondsToTicks(3.85)) {
                world.playSound(golemLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 2.0f, 1.4f);
                WardenAudio.playCustomSound(golemLoc, "haohan:boss.arcslash", 2.0f, 1.1f);
            }

            // High-speed dive downwards
            if (state.attackTicks >= MathUtil.secondsToTicks(3.90)) {
                Vector diveVel = new Vector(0, -2.5, 0);
                if (distToLandXZ > 0.3) {
                    diveDir.normalize().multiply(Math.min(distToLandXZ * 0.9, 2.4));
                    diveVel.setX(diveDir.getX());
                    diveVel.setZ(diveDir.getZ());
                }
                golem.setVelocity(diveVel);

                world.spawnParticle(Particle.SONIC_BOOM, golemLoc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.DUST, golemLoc.clone().add(0, 0.8, 0), 16, 0.4, 0.6, 0.4, 0.0,
                        new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.5f));
            }

            // Strict ground check: ONLY impact when feet physically reach ground level
            boolean reachedGround = (state.attackTicks >= MathUtil.secondsToTicks(3.95)) && (golemLoc.getY() - nearLandingTarget.getY() <= 0.35);
            boolean timeoutForcedSnap = state.attackTicks >= MathUtil.secondsToTicks(4.40);

            if (reachedGround || timeoutForcedSnap) {
                state.slamImpactDone = true;
                executeMeteorSlamShockwave(plugin, golem, state, nearLandingTarget, kickYaw);
            }
        }

        // PHASE 5: FAST SNAPPY WEAPON RECALL & RETRIEVAL (4.65s - 5.07s)
        // Boss reaches both hands forward to magnetically recall the sword and shield rapidly
        if (state.attackTicks == MathUtil.secondsToTicks(4.65) && !state.weaponPickupDone) {
            world.playSound(golemLoc, Sound.ITEM_TRIDENT_RETURN, 1.8f, 1.4f);
            WardenAudio.playCustomSound(golemLoc, "haohan:boss.mortalblade_swordout", 1.8f, 1.2f);
            WardenAudio.playCustomSound(golemLoc, "haohan:boss.mortalblade_whoosh", 1.8f, 1.2f);

            // Fling impaled player upward as sword is telekinetically unpinned
            if (state.impaledTargetUUID != null) {
                Player impaledPlayer = Bukkit.getPlayer(state.impaledTargetUUID);
                if (impaledPlayer != null && impaledPlayer.isValid() && impaledPlayer.getGameMode() != GameMode.SPECTATOR) {
                    impaledPlayer.setVelocity(new Vector(0, 0.68, 0));
                    impaledPlayer.setNoDamageTicks(0);
                    WardenCombatHandler.applyCombatDamage(impaledPlayer, 8.0, golem);
                    impaledPlayer.playSound(impaledPlayer.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.8f, 0.8f);
                    world.spawnParticle(Particle.SWEEP_ATTACK, impaledPlayer.getLocation().add(0, 1.2, 0), 2, 0.3, 0.2, 0.3, 0);
                    world.spawnParticle(Particle.DUST, impaledPlayer.getLocation().add(0, 1.0, 0), 20, 0.4, 0.5, 0.4, 0.0,
                            new Particle.DustOptions(Color.fromRGB(180, 20, 20), 1.8f));
                }
                state.impaledTargetUUID = null;
            }
        }

        if (state.attackTicks >= MathUtil.secondsToTicks(5.07) && !state.weaponPickupDone) {
            state.weaponPickupDone = true;

            for (Entity e : state.activeWeaponDisplays) {
                if (e != null) {
                    WardenEntityManager.removeTempEntity(e);
                }
            }
            state.activeWeaponDisplays.clear();

            WardenAnimationController.setBoneVisible(golem, "shield", true);
            WardenAnimationController.setBoneVisible(golem, "sword", true);

            world.playSound(golemLoc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.8f, 1.0f);
            world.playSound(golemLoc, Sound.BLOCK_CHAIN_HIT, 1.8f, 1.1f);
            WardenAudio.playCustomSound(golemLoc, "haohan:boss.sword_sheath_in", 1.8f, 1.1f);

            world.spawnParticle(Particle.SWEEP_ATTACK, golemLoc.clone().add(0, 1.2, 0), 3, 0.5, 0.2, 0.5, 0);
            world.spawnParticle(Particle.DUST, golemLoc.clone().add(0, 1.0, 0), 20, 0.6, 0.3, 0.6, 0.0,
                    new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.0f));
        }

        // PHASE 6: RECOVERY & TRANSITION BACK TO IDLE (5.70s)
        if (state.attackTicks >= MathUtil.secondsToTicks(5.70)) {
            state.impaledTargetUUID = null;
            WardenAnimationController.setBoneVisible(golem, "shield", true);
            WardenAnimationController.setBoneVisible(golem, "sword", true);

            for (Entity e : state.activeWeaponDisplays) {
                if (e != null) {
                    WardenEntityManager.removeTempEntity(e);
                }
            }
            state.activeWeaponDisplays.clear();

            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.6 + random.nextDouble() * 0.4);
            state.attackCooldown = MathUtil.secondsToTicks(1.0 + random.nextDouble() * 0.5);
            state.currentAttack = "";
            state.slamTargetGroundLoc = null;
            state.shieldThrownDone = false;
            state.swordThrownDone = false;
            state.slamImpactDone = false;
            state.weaponPickupDone = false;
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.2, 0.2, 1.0, true);
        }
    }

    private static Location getBoneWorldLocation(IronGolem golem, String... boneNames) {
        ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
        if (me != null) {
            ActiveModel activeModel = me.getModel(WardenConstants.MODEL_ID).orElse(null);
            if (activeModel != null) {
                for (String bName : boneNames) {
                    Optional<ModelBone> boneOpt = activeModel.getBone(bName);
                    if (boneOpt.isPresent()) {
                        Location loc = boneOpt.get().getLocation();
                        if (loc != null) return loc.clone();
                    }
                }
            }
        }
        return null;
    }

    private static void spawnThrownShieldProjectile(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Location origin, Location targetGround) {
        World world = origin.getWorld();
        if (world == null) return;

        try {
            Vector throwDir = targetGround.toVector().subtract(origin.toVector()).normalize();
            float shieldYaw = MathUtil.getYaw(throwDir);
            float shieldPitch = (float) Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, throwDir.getY()))));

            Location spawnLoc = origin.clone();
            spawnLoc.setYaw(shieldYaw);
            spawnLoc.setPitch(shieldPitch);

            ItemDisplay shieldEntity = world.spawn(spawnLoc, ItemDisplay.class, entity -> {
                entity.setItemStack(new ItemStack(Material.SHIELD));
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(),
                        new Vector3f(2.8f, 2.8f, 2.8f),
                        new Quaternionf()
                ));
                entity.setBillboard(Display.Billboard.FIXED);
            });
            WardenEntityManager.registerTempEntity(shieldEntity);
            state.activeWeaponDisplays.add(shieldEntity);

            new BukkitRunnable() {
                private int step = 0;
                private final int maxFlightSteps = 10;
                private final Location current = origin.clone();
                private final Location dest = targetGround.clone().add(0, 0.35, 0);
                private boolean recallStarted = false;
                private int recallStep = 0;
                private Location recallStartLoc = null;

                @Override
                public void run() {
                    if (golem.isDead() || !shieldEntity.isValid() || state.weaponPickupDone || !state.currentAttack.equals("skill_shield_sword_slam")) {
                        WardenEntityManager.removeTempEntity(shieldEntity);
                        cancel();
                        return;
                    }

                    step++;
                    double progress = (double) step / (double) maxFlightSteps;

                    if (progress <= 1.0) {
                        // Direct straight-line flight starting from hand
                        double curX = MathUtil.lerp(origin.getX(), dest.getX(), progress);
                        double curY = MathUtil.lerp(origin.getY(), dest.getY(), progress);
                        double curZ = MathUtil.lerp(origin.getZ(), dest.getZ(), progress);

                        current.set(curX, curY, curZ);
                        current.setYaw(shieldYaw);
                        current.setPitch(shieldPitch);
                        shieldEntity.teleport(current);

                        if (step % 2 == 0) {
                            world.playSound(current, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.8f);
                        }
                    } else if (step == maxFlightSteps + 1) {
                        dest.setYaw(shieldYaw);
                        dest.setPitch(shieldPitch);
                        shieldEntity.teleport(dest);
                        world.playSound(dest, Sound.ITEM_SHIELD_BLOCK, 1.8f, 0.8f);
                        world.playSound(dest, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.8f);
                        WardenAudio.playCustomSound(dest, "haohan:boss.parry", 1.8f, 0.8f);
                        world.spawnParticle(Particle.SWEEP_ATTACK, dest, 3, 0.5, 0.1, 0.5, 0);
                        world.spawnParticle(Particle.DUST, dest, 20, 0.8, 0.2, 0.8, 0.0, new Particle.DustOptions(Color.fromRGB(70, 210, 255), 2.2f));

                        for (Player p : world.getPlayers()) {
                            if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                            if (p.getLocation().distanceSquared(dest) <= 3.8 * 3.8) {
                                p.setNoDamageTicks(0);
                                WardenCombatHandler.applyCombatDamage(p, 16.0, golem);
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, MathUtil.secondsToTicks(3.5), 3, false, false, true));
                                Vector push = p.getLocation().toVector().subtract(dest.toVector()).normalize().multiply(0.45).setY(0.24);
                                p.setVelocity(p.getVelocity().add(push));
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.1f, 1.0f);
                            }
                        }
                    } else if (state.attackTicks >= MathUtil.secondsToTicks(4.65)) {
                        // FAST RECALL FLIGHT BACK TO BOSS RIGHT HAND (4.65s -> 5.07s = 8-9 ticks)
                        if (!recallStarted) {
                            recallStarted = true;
                            recallStartLoc = shieldEntity.getLocation().clone();
                        }
                        recallStep++;
                        int recallTotal = Math.max(1, MathUtil.secondsToTicks(5.07) - MathUtil.secondsToTicks(4.65));
                        double recallProg = Math.min(1.0, (double) recallStep / (double) recallTotal);

                        Location targetHand = getBoneWorldLocation(golem, "right_forearm", "right_arm");
                        if (targetHand == null) targetHand = golem.getLocation().add(0, 1.8, 0);

                        double rx = MathUtil.lerp(recallStartLoc.getX(), targetHand.getX(), recallProg);
                        double ry = MathUtil.lerp(recallStartLoc.getY(), targetHand.getY(), recallProg);
                        double rz = MathUtil.lerp(recallStartLoc.getZ(), targetHand.getZ(), recallProg);
                        Location recallLoc = new Location(world, rx, ry, rz, shieldYaw, shieldPitch);
                        shieldEntity.teleport(recallLoc);

                        world.spawnParticle(Particle.DUST, shieldEntity.getLocation(), 2, 0.1, 0.1, 0.1, 0.0,
                                new Particle.DustOptions(Color.fromRGB(80, 210, 255), 1.2f));
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);

        } catch (Throwable e) {
            plugin.getLogger().warning("Error warning shield projectile: " + e.getMessage());
        }
    }

    private static void spawnThrownSwordProjectile(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Location origin, Location targetGround) {
        World world = origin.getWorld();
        if (world == null) return;

        try {
            ItemStack customSwordItem = null;
            try {
                customSwordItem = HaoHanItemCore.get().getItemFactory().create("haohan:lunar_claymore", 1);
            } catch (Throwable ignored) {}
            if (customSwordItem == null) {
                customSwordItem = new ItemStack(Material.NETHERITE_SWORD);
            }

            // Full boss-tier claymore size (8.0 scale)
            final float swordScale = 8.0f;

            // Direct line from Boss hand (origin) to Target Ground (targetGround)
            final Vector throwDir = targetGround.toVector().subtract(origin.toVector()).normalize();
            final float swordYaw = MathUtil.getYaw(throwDir);
            final float swordPitch = (float) Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, throwDir.getY()))));

            // Blade length along throwDir so the tip penetrates targetGround and the pommel stays at destHilt
            final float swordLengthOffset = 2.4f;
            final Location destHilt = targetGround.clone().subtract(throwDir.clone().multiply(swordLengthOffset)).add(0.0, 0.15, 0.0);
            destHilt.setYaw(swordYaw);
            destHilt.setPitch(swordPitch);

            // Precise rotation: In Minecraft item models (+Y = tip), rotating +90 deg around X maps +Y directly to +Z (Entity Forward along throwDir)
            Quaternionf fixedSwordRot = new Quaternionf().rotateX((float) Math.toRadians(90.0f));

            Location spawnLoc = origin.clone();
            spawnLoc.setYaw(swordYaw);
            spawnLoc.setPitch(swordPitch);

            final ItemStack displayItem = customSwordItem;
            ItemDisplay swordEntity = world.spawn(spawnLoc, ItemDisplay.class, entity -> {
                entity.setItemStack(displayItem);
                entity.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        fixedSwordRot,
                        new Vector3f(swordScale, swordScale, swordScale),
                        new Quaternionf()
                ));
                entity.setBillboard(Display.Billboard.FIXED);
            });

            WardenEntityManager.registerTempEntity(swordEntity);
            state.activeWeaponDisplays.add(swordEntity);

            new BukkitRunnable() {
                private int step = 0;
                private final int maxFlightSteps = 12; // Direct straight-line projectile (0.60s)
                private final Location current = origin.clone();
                private boolean recallStarted = false;
                private int recallStep = 0;
                private Location recallStartLoc = null;

                @Override
                public void run() {
                    if (golem.isDead() || !swordEntity.isValid() || state.weaponPickupDone || !state.currentAttack.equals("skill_shield_sword_slam")) {
                        WardenEntityManager.removeTempEntity(swordEntity);
                        cancel();
                        return;
                    }

                    step++;
                    double progress = (double) step / (double) maxFlightSteps;

                    if (progress <= 1.0) {
                        // Direct straight-line flight: Hilt starts at origin (hand) and moves to destHilt
                        double curX = MathUtil.lerp(origin.getX(), destHilt.getX(), progress);
                        double curY = MathUtil.lerp(origin.getY(), destHilt.getY(), progress);
                        double curZ = MathUtil.lerp(origin.getZ(), destHilt.getZ(), progress);

                        current.set(curX, curY, curZ);
                        current.setYaw(swordYaw);
                        current.setPitch(swordPitch);
                        swordEntity.teleport(current);

                        // Trailing particles
                        world.spawnParticle(Particle.DUST, current, 3, 0.2, 0.2, 0.2, 0.0,
                                new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.5f));

                        if (step % 2 == 0) {
                            world.playSound(current, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.9f);
                        }
                    } else if (step == maxFlightSteps + 1) {
                        swordEntity.teleport(destHilt);

                        world.playSound(targetGround, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.8f, 1.4f);
                        world.playSound(targetGround, Sound.ITEM_TRIDENT_HIT, 1.8f, 0.6f);
                        world.playSound(targetGround, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.6f, 0.7f);
                        world.playSound(targetGround, Sound.BLOCK_CHAIN_PLACE, 1.8f, 0.8f);
                        WardenAudio.playCustomSound(targetGround, "haohan:boss.judgementcut", 1.8f, 1.2f);

                        world.spawnParticle(Particle.FLASH, targetGround.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.0, Color.WHITE);
                        world.spawnParticle(Particle.DUST, targetGround.clone().add(0, 0.5, 0), 45, 0.6, 0.5, 0.6, 0.0,
                                new Particle.DustOptions(Color.fromRGB(180, 15, 15), 2.2f));
                        world.spawnParticle(Particle.DUST, targetGround.clone().add(0, 0.5, 0), 30, 0.4, 0.4, 0.4, 0.0,
                                new Particle.DustOptions(Color.fromRGB(120, 5, 5), 1.8f));
                        world.spawnParticle(Particle.CRIMSON_SPORE, targetGround.clone().add(0, 0.8, 0), 25, 0.5, 0.6, 0.5, 0.1);

                        for (Player p : world.getPlayers()) {
                            if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                            if (p.getLocation().distanceSquared(targetGround) <= 4.0 * 4.0) {
                                p.setNoDamageTicks(0);
                                WardenCombatHandler.applyCombatDamage(p, 20.0, golem);

                                // PIN / LOCK THE TARGET IN PLACE AT THE SWORD TIP!
                                state.impaledTargetUUID = p.getUniqueId();
                                Location pinLoc = targetGround.clone();
                                p.teleport(pinLoc);
                                p.setVelocity(new Vector(0, -0.2, 0));
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, MathUtil.secondsToTicks(3.2), 6, false, false, true));
                                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, MathUtil.secondsToTicks(3.2), 128, false, false, false));

                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.2f, 0.9f);
                                p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_FALL, 1.6f, 0.7f);
                            }
                        }
                    } else if (state.attackTicks >= MathUtil.secondsToTicks(4.65)) {
                        // FAST RECALL FLIGHT BACK TO BOSS LEFT HAND (4.65s -> 5.07s = 8-9 ticks)
                        if (!recallStarted) {
                            recallStarted = true;
                            recallStartLoc = swordEntity.getLocation().clone();
                        }
                        recallStep++;
                        int recallTotal = Math.max(1, MathUtil.secondsToTicks(5.07) - MathUtil.secondsToTicks(4.65));
                        double recallProg = Math.min(1.0, (double) recallStep / (double) recallTotal);
                        Location targetHand = getBoneWorldLocation(golem, "left_forearm", "left_arm");
                        if (targetHand == null) targetHand = golem.getLocation().add(0, 1.8, 0);

                        double rx = MathUtil.lerp(recallStartLoc.getX(), targetHand.getX(), recallProg);
                        double ry = MathUtil.lerp(recallStartLoc.getY(), targetHand.getY(), recallProg);
                        double rz = MathUtil.lerp(recallStartLoc.getZ(), targetHand.getZ(), recallProg);
                        Location recallLoc = new Location(world, rx, ry, rz, swordYaw, swordPitch);
                        swordEntity.teleport(recallLoc);

                        world.spawnParticle(Particle.DUST, swordEntity.getLocation().clone().subtract(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.0,
                                new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.5f));
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);

        } catch (Throwable e) {
            plugin.getLogger().warning("Error warning sword projectile: " + e.getMessage());
        }
    }

    private static void executeMeteorSlamShockwave(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Location slamCenter, float impactYaw) {
        World world = slamCenter.getWorld();
        if (world == null) return;

        Location safeLand = WardenLocationUtil.findSafeTeleportLocation(golem.getLocation(), slamCenter, 1.2);
        safeLand.setYaw(impactYaw);
        safeLand.setPitch(0f);
        golem.teleport(safeLand);
        golem.setRotation(impactYaw, 0f);
        golem.setVelocity(new Vector(0, 0, 0));

        ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
        if (me != null) {
            me.setYBodyRot(impactYaw);
            me.setYHeadRot(impactYaw);
        }

        world.playSound(safeLand, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.8f, 0.75f);
        world.playSound(safeLand, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.95f);
        world.playSound(safeLand, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);
        world.playSound(safeLand, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        WardenAudio.playCustomSound(safeLand, "haohan:boss.mortalblade_whoosh", 1.8f, 0.85f);
        WardenAudio.playCustomSound(safeLand, "haohan:boss.judgementcut", 1.6f, 0.95f);

        world.spawnParticle(Particle.EXPLOSION, safeLand.clone().add(0, 0.5, 0), 2, 0.4, 0.1, 0.4, 0);
        world.spawnParticle(Particle.SWEEP_ATTACK, safeLand.clone().add(0, 0.4, 0), 6, 1.5, 0.2, 1.5, 0);
        world.spawnParticle(Particle.DUST, safeLand.clone().add(0, 0.3, 0), 60, 2.5, 0.4, 2.5, 0.0,
                new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.2f));

        Block groundBlock = safeLand.clone().subtract(0, 0.2, 0).getBlock();
        BlockData bData = (!groundBlock.isPassable() && groundBlock.getType().isSolid()) ? groundBlock.getBlockData() : Material.GRAY_CONCRETE.createBlockData();
        world.spawnParticle(Particle.BLOCK, safeLand.clone().add(0, 0.5, 0), 50, 2.0, 0.6, 2.0, 0.25, bData);

        // Spawn smooth fractured explosive burst ring and concentric shockwave ripple
        Random rand = new Random();
        BlockWaveRenderer.spawnBurstRing(plugin, safeLand, 3.2, 10, 0.85, 13, 0.24, rand);
        BlockWaveRenderer.spawnConcentricWave(plugin, safeLand, 5.8, 1.35, 0.80, 13, 0.24, rand);

        // Expanded Shockwave Radius = 7.8m (Just slightly smaller than Ground Slam's 8.5m)
        double slamRadiusSq = 7.8 * 7.8;
        for (Player victim : world.getPlayers()) {
            if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

            Location vLoc = victim.getLocation();
            if (vLoc.distanceSquared(safeLand) <= slamRadiusSq) {
                victim.setNoDamageTicks(0);
                WardenCombatHandler.applyCombatDamage(victim, 22.0, golem);

                // ONLY knock back players who are NOT impaled by the claymore
                boolean isImpaled = (state.impaledTargetUUID != null && victim.getUniqueId().equals(state.impaledTargetUUID));
                if (!isImpaled) {
                    Vector outward = vLoc.toVector().subtract(safeLand.toVector()).setY(0);
                    if (outward.lengthSquared() > 0.001) {
                        outward.normalize().multiply(0.85).setY(0.42);
                    } else {
                        outward = new Vector(0, 0.42, 0);
                    }
                    victim.setVelocity(victim.getVelocity().add(outward));
                }

                victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.8f);
                victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 15, 0.3, 0.3, 0.15);
            }
        }
    }

}
