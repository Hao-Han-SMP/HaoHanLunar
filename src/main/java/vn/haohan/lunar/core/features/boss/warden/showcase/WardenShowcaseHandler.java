package vn.haohan.lunar.core.features.boss.warden.showcase;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.util.MathUtil;
import vn.haohan.lunar.core.features.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.core.features.boss.warden.WardenConstants;
import vn.haohan.lunar.core.features.boss.warden.WardenState;
import vn.haohan.lunar.core.features.boss.warden.combat.CrescentBladeWaveTask;
import vn.haohan.lunar.core.features.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.core.features.boss.warden.skills.*;
import vn.haohan.lunar.core.features.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.core.features.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.core.features.boss.warden.visual.WardenVFX;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class WardenShowcaseHandler {

    private WardenShowcaseHandler() {}

    public record ShowcaseSkillEntry(String skillKey, String displayName, String englishName) {}

    public static final List<ShowcaseSkillEntry> SHOWCASE_SKILLS = List.of(
            new ShowcaseSkillEntry("attack_slash_left", "Chém Chéo Trái", "Slash Left"),
            new ShowcaseSkillEntry("attack_sweep_right", "Chém Quét Phải", "Sweep Right Crescent Wave"),
            new ShowcaseSkillEntry("attack_slash_straight", "Bổ Trảm Địa Chấn", "Overhead Ground Slam"),
            new ShowcaseSkillEntry("attack_thrust_fling", "Đâm Kiếm Hất Tung", "Thrust & Mortal Fling"),
            new ShowcaseSkillEntry("skill_aerial_slash_combo", "Trảm Không Liên Hoàn", "4-Slash Aerial Combo"),
            new ShowcaseSkillEntry("skill_shield_charge", "Lao Khiên Tông Phá", "Shield Charge Rush"),
            new ShowcaseSkillEntry("skill_shield_block", "Giơ Khiên Thủ & Phản Đòn", "Shield Guard / Parry"),
            new ShowcaseSkillEntry("skill_shield_block_push", "Đỡ Khiên Oanh Kích", "Shield Block & Push"),
            new ShowcaseSkillEntry("skill_shield_sword_slam", "Khiên Kiếm Oanh Tạc", "Shield & Claymore Dive Slam"),
            new ShowcaseSkillEntry("skill_charge_summon", "Triệu Hồi Thiên Lôi", "Celestial Summon Spell")
    );

    /**
     * Spawns all 10 Lunar Warden skills in a neat row in front of the player,
     * constantly looping their attack animations and visual effects into the air.
     */
    public static List<IronGolem> spawnShowcaseLine(
            HaoHanLunarPlugin plugin,
            LunarWardenMechanic mechanic,
            Location originLoc,
            double spacing,
            Player player
    ) {
        World world = originLoc.getWorld();
        if (world == null) return List.of();

        Vector forward = originLoc.getDirection().setY(0).normalize();
        if (forward.lengthSquared() < 0.001) forward = new Vector(0, 0, 1);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        float facingYaw = originLoc.getYaw();
        Location lineCenter = originLoc.clone().add(forward.clone().multiply(9.0));

        int totalSkills = SHOWCASE_SKILLS.size();
        List<IronGolem> spawnedGolems = new ArrayList<>();

        for (int i = 0; i < totalSkills; i++) {
            ShowcaseSkillEntry entry = SHOWCASE_SKILLS.get(i);
            double offset = (i - ((totalSkills - 1) / 2.0)) * spacing;

            Location spot = lineCenter.clone().add(right.clone().multiply(offset));
            spot.setYaw(facingYaw);
            spot.setPitch(0f);
            Location spawnLoc = WardenLocationUtil.adjustToTerrainSurface(spot, 0.0);

            try {
                IronGolem golem = (IronGolem) world.spawnEntity(spawnLoc, EntityType.IRON_GOLEM);
                golem.setRemoveWhenFarAway(false);
                golem.setPersistent(false);
                golem.setAware(false);
                golem.setAI(false);
                golem.setInvulnerable(true);
                golem.setGravity(true);
                golem.setInvisible(true);

                // Apply Scale 3.4
                applyScaleAttribute(golem, 3.4);

                // Custom Name Tag
                String nameTag = "§6[#" + (i + 1) + "] §b§lThe Lunar Warden §8- §e§l" + entry.displayName();
                golem.setCustomName(nameTag);
                golem.setCustomNameVisible(true);

                // Attach ModelEngine ActiveModel
                ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(golem);
                if (modeledEntity != null) {
                    ActiveModel activeModel = ModelEngineAPI.createActiveModel(WardenConstants.MODEL_ID);
                    if (activeModel != null) {
                        activeModel.setScale(3.0f);
                        activeModel.setHitboxScale(3.4f);
                        activeModel.setCanHurt(false);
                        activeModel.setMainHitbox(true);
                        activeModel.setInvisUpdate(true);
                        modeledEntity.addModel(activeModel, true);
                    }
                    modeledEntity.setBaseEntityVisible(false);
                    modeledEntity.setModelRotationLocked(true);
                }

                // Setup WardenState for showcase dummy
                WardenState state = new WardenState();
                state.isShowcaseDummy = true;
                state.dummyLoopSkill = entry.skillKey();
                state.anchorLocation = spawnLoc.clone();

                // Skill-specific state preparations
                if (entry.skillKey().equals("skill_shield_sword_slam")) {
                    state.slamTargetGroundLoc = WardenLocationUtil.adjustToTerrainSurface(
                            spawnLoc.clone().add(forward.clone().multiply(6.0)), 0.0);
                } else if (entry.skillKey().equals("skill_charge_summon")) {
                    Location circleLoc = WardenLocationUtil.adjustToTerrainSurface(
                            spawnLoc.clone().add(forward.clone().multiply(7.0)), 0.0);
                    state.lockedSummonLocations.clear();
                    state.lockedSummonLocations.add(circleLoc);
                }

                // Register state
                mechanic.getBossStates().put(golem.getUniqueId(), state);
                spawnedGolems.add(golem);

                // Launch initial skill animation
                restartShowcaseSkill(golem, state, entry.skillKey(), 0.15);

                // Spawn burst dust on appearance
                world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 1.5, 0), 25, 0.8, 1.0, 0.8, 0.0,
                                    new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.0f));
                world.spawnParticle(Particle.FLASH, spawnLoc.clone().add(0, 1.5, 0), 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        world.playSound(originLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.6f);
        world.playSound(originLoc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.8f, 0.8f);

        return spawnedGolems;
    }

    private static void applyScaleAttribute(IronGolem golem, double scale) {
        try {
            AttributeInstance instance = golem.getAttribute(Attribute.GENERIC_SCALE);
            if (instance != null) {
                instance.setBaseValue(scale);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
            if (attr != null) {
                AttributeInstance instance = golem.getAttribute(attr);
                if (instance != null) {
                    instance.setBaseValue(scale);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
            if (attr != null) {
                AttributeInstance instance = golem.getAttribute(attr);
                if (instance != null) {
                    instance.setBaseValue(scale);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Executes dummy showcase tick: keeps dummy pinned at anchor and loops skills into the air.
     */
    public static void handleShowcaseTick(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Random random) {
        Location anchor = state.anchorLocation;
        if (anchor == null) {
            state.anchorLocation = golem.getLocation().clone();
            anchor = state.anchorLocation;
        }

        // Lock physics & position
        golem.setVelocity(new Vector(0, 0, 0));
        Location currentLoc = golem.getLocation();
        if (currentLoc.distanceSquared(anchor) > 0.05 || Math.abs(currentLoc.getYaw() - anchor.getYaw()) > 1.0f) {
            golem.teleport(anchor);
        }
        golem.setRotation(anchor.getYaw(), 0f);

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(anchor.getYaw());
            modeledEntity.setYHeadRot(anchor.getYaw());
            modeledEntity.setXHeadRot(0f);
        }

        state.attackTicks++;
        String skill = state.dummyLoopSkill;
        if (skill == null || skill.isEmpty()) {
            skill = "attack_slash_left";
            state.dummyLoopSkill = skill;
        }

        World world = anchor.getWorld();
        if (world == null) return;
        Vector forwardDir = anchor.getDirection().setY(0).normalize();
        if (forwardDir.lengthSquared() < 0.001) forwardDir = new Vector(0, 0, 1);

        switch (skill) {
            case "attack_slash_left" -> {
                if (state.attackTicks == state.attackHitTick && !state.attackHitDone) {
                    state.attackHitDone = true;
                    Location strikeCenter = anchor.clone().add(forwardDir.clone().multiply(3.8)).add(0, 1.8, 0);

                    world.playSound(strikeCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.8f);
                    world.playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.6f, 0.65f);
                    world.playSound(strikeCenter, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.3f, 0.85f);
                    world.playSound(strikeCenter, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.8f);
                    world.playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
                    WardenAudio.playCustomSound(strikeCenter, "haohan:boss.slash_heavy", 1.8f, 0.85f);
                    WardenAudio.playCustomSound(strikeCenter, "haohan:boss.arcslash", 1.6f, 1.0f);

                    Location waveStart = anchor.clone().add(forwardDir.clone().multiply(2.2));
                    waveStart.setY(anchor.getY() + 0.6);
                    new CrescentBladeWaveTask(golem, waveStart, forwardDir, false).runTaskTimer(plugin, 1L, 1L);
                }
                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "attack_sweep_right" -> {
                if (state.attackTicks == state.attackHitTick && !state.attackHitDone) {
                    state.attackHitDone = true;
                    Location strikeCenter = anchor.clone().add(forwardDir.clone().multiply(3.8)).add(0, 1.8, 0);

                    world.playSound(strikeCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.8f);
                    world.playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.6f, 0.65f);
                    world.playSound(strikeCenter, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.3f, 0.85f);
                    world.playSound(strikeCenter, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.8f);
                    world.playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
                    WardenAudio.playCustomSound(strikeCenter, "haohan:boss.slash_heavy", 1.8f, 0.85f);
                    WardenAudio.playCustomSound(strikeCenter, "haohan:boss.arcslash", 1.6f, 1.0f);
                    WardenAudio.playCustomSound(strikeCenter, "haohan:boss.murasama", 1.6f, 1.0f);

                    Location waveStart = anchor.clone().add(forwardDir.clone().multiply(2.2));
                    waveStart.setY(anchor.getY() + 0.6);
                    new CrescentBladeWaveTask(golem, waveStart, forwardDir, true).runTaskTimer(plugin, 1L, 1L);
                }
                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "attack_slash_straight" -> {
                if (state.attackTicks >= state.attackHitTick && !state.attackHitDone) {
                    state.attackHitDone = true;
                    GroundSlamSkill.executeGroundSlamAOE(plugin, golem, random);
                }
                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "attack_thrust_fling" -> {
                if (state.attackTicks == state.attackHitTick && !state.attackHitDone) {
                    state.attackHitDone = true;
                    Location strikeEffectPoint = anchor.clone().add(forwardDir.clone().multiply(3.8)).add(0, 2.2, 0);
                    world.playSound(strikeEffectPoint, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.8f, 0.5f);
                    world.playSound(strikeEffectPoint, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.5f);
                    world.playSound(strikeEffectPoint, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 1.6f);
                    WardenAudio.playCustomSound(strikeEffectPoint, "haohan:boss.murasama", 1.8f, 1.2f);
                    WardenAudio.playCustomSound(strikeEffectPoint, "haohan:boss.slash_heavy", 1.8f, 0.8f);

                    world.spawnParticle(Particle.CRIT, strikeEffectPoint, 45, 0.5, 0.5, 0.5, 0.25);
                    world.spawnParticle(Particle.DUST, strikeEffectPoint, 50, 0.5, 0.5, 0.5, 0.0, new Particle.DustOptions(Color.RED, 2.5f));
                    world.spawnParticle(Particle.FLASH, strikeEffectPoint, 2, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
                }
                int flingTick = MathUtil.secondsToTicks(2.40);
                if (state.attackTicks >= flingTick && !state.flingExecuted) {
                    state.flingExecuted = true;
                    Location flingLoc = anchor.clone().add(forwardDir.clone().multiply(4.2)).add(0, 2.8, 0);
                    world.playSound(flingLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);
                    world.playSound(flingLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.5f);
                    WardenAudio.playCustomSound(flingLoc, "haohan:boss.mortalblade_whoosh", 1.8f, 0.8f);
                    WardenAudio.playCustomSound(flingLoc, "haohan:boss.judgementcut", 1.6f, 1.1f);
                    world.spawnParticle(Particle.EXPLOSION, flingLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.SWEEP_ATTACK, flingLoc, 5, 0.5, 0.5, 0.5, 0);
                    world.spawnParticle(Particle.DUST, flingLoc, 40, 0.6, 0.6, 0.6, 0.0, new Particle.DustOptions(Color.RED, 2.0f));
                }
                if (state.attackTicks >= state.attackTotalTicks) {
                    state.flingExecuted = false;
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "skill_aerial_slash_combo" -> {
                AerialSlashComboSkill.handleExecution(plugin, golem, state, null, anchor.getYaw(), 0f, 10.0, random);
                if (state.attackTicks >= AerialSlashComboSkill.TOTAL_TICKS) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "skill_shield_charge" -> {
                Location shieldFront = anchor.clone().add(forwardDir.clone().multiply(1.8)).add(0, 1.6, 0);

                if (state.attackTicks == 1) {
                    WardenAudio.playCustomSound(anchor, "haohan:boss.shield_thud", 1.8f, 0.7f);
                }

                if (state.attackTicks <= MathUtil.secondsToTicks(0.70)) {
                    if (state.attackTicks % 2 == 0) {
                        world.spawnParticle(Particle.DUST, shieldFront, 12, 0.4, 0.6, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(60, 200, 255), 1.8f));
                        world.spawnParticle(Particle.SWEEP_ATTACK, shieldFront, 1, 0.2, 0.2, 0.2, 0);
                    }
                } else if (state.attackTicks <= MathUtil.secondsToTicks(2.0)) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, shieldFront, 3, 0.8, 0.4, 0.8, 0);
                    world.spawnParticle(Particle.DUST, shieldFront, 18, 0.6, 0.8, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(180, 235, 255), 2.2f));
                    world.spawnParticle(Particle.ELECTRIC_SPARK, shieldFront, 8, 0.8, 0.8, 0.8, 0.15);
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, anchor.clone().add(0, 0.2, 0), 4, 0.4, 0.1, 0.4, 0.02);

                    if (state.attackTicks % 3 == 0) {
                        world.playSound(anchor, Sound.ENTITY_IRON_GOLEM_STEP, 1.6f, 0.7f);
                        world.playSound(anchor, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.4f, 1.4f);
                    }
                } else if (state.attackTicks <= MathUtil.secondsToTicks(2.65)) {
                    if (state.attackTicks == MathUtil.secondsToTicks(2.05)) {
                        Location slamCenter = anchor.clone().add(forwardDir.clone().multiply(2.0));
                        slamCenter = WardenLocationUtil.adjustToTerrainSurface(slamCenter, 0.0);

                        world.playSound(slamCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.8f, 0.75f);
                        world.playSound(slamCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
                        world.playSound(slamCenter, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);

                        world.spawnParticle(Particle.EXPLOSION, slamCenter.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.FLASH, slamCenter.clone().add(0, 0.4, 0), 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
                        world.spawnParticle(Particle.DUST, slamCenter.clone().add(0, 0.2, 0), 40, 1.2, 0.2, 1.2, 0.0, new Particle.DustOptions(Color.fromRGB(90, 210, 255), 2.2f));
                    }
                }

                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "skill_shield_block" -> {
                if (state.attackTicks == 1) {
                    world.playSound(anchor, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.8f, 0.9f);
                    world.playSound(anchor, Sound.ITEM_SHIELD_BLOCK, 1.6f, 1.0f);
                    world.playSound(anchor, Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.8f);
                    WardenAudio.playCustomSound(anchor, "haohan:boss.shield_thud", 1.8f, 1.0f);
                }
                if (state.attackTicks % 12 == 0) {
                    Location guardLoc = anchor.clone().add(forwardDir.clone().multiply(1.6)).add(0, 2.0, 0);
                    world.spawnParticle(Particle.FLASH, guardLoc, 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
                    world.spawnParticle(Particle.CRIT, guardLoc, 8, 0.3, 0.3, 0.3, 0.1);
                    world.spawnParticle(Particle.DUST, guardLoc, 12, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.0f));
                    WardenAudio.playCustomSound(guardLoc, "haohan:boss.parry", 1.4f, 1.1f);
                }
                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "skill_shield_block_push" -> {
                ShieldBlockPushSkill.handleShieldBlockPushExecution(plugin, golem, state, null, anchor.getYaw(), 0f, 10.0, random);
                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "skill_shield_sword_slam" -> {
                if (state.slamTargetGroundLoc == null) {
                    state.slamTargetGroundLoc = WardenLocationUtil.adjustToTerrainSurface(anchor.clone().add(forwardDir.clone().multiply(6.0)), 0.0);
                }
                ShieldSwordSlamSkill.handleShieldSwordSlamExecution(plugin, golem, state, null, anchor.getYaw(), 0f, 10.0, random);
                if (state.attackTicks >= MathUtil.secondsToTicks(5.70)) {
                    state.shieldThrownDone = false;
                    state.swordThrownDone = false;
                    state.slamImpactDone = false;
                    state.weaponPickupDone = false;
                    state.slamTargetGroundLoc = WardenLocationUtil.adjustToTerrainSurface(anchor.clone().add(forwardDir.clone().multiply(6.0)), 0.0);
                    restartShowcaseSkill(golem, state, skill, 0.10);
                }
            }
            case "skill_charge_summon" -> {
                Location summonCenter = WardenLocationUtil.adjustToTerrainSurface(anchor.clone().add(forwardDir.clone().multiply(7.0)), 0.0);
                int chantDurationTicks = MathUtil.secondsToTicks(3.4);
                int barrageEndTicks = MathUtil.secondsToTicks(8.5);

                if (state.attackTicks <= chantDurationTicks) {
                    float progress = (float) state.attackTicks / (float) chantDurationTicks;
                    double currentRadius = progress * 2.2;
                    double rotationAngle = state.attackTicks * (0.12 + (progress * 0.28));

                    if (state.attackTicks % 2 == 0) {
                        Location bossChantLoc = anchor.clone().add(forwardDir.clone().multiply(2.2)).add(0, 3.2, 0);
                        world.spawnParticle(Particle.END_ROD, bossChantLoc, 3, 0.4, 0.4, 0.4, 0.02);
                        world.spawnParticle(Particle.DUST, bossChantLoc, 6, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.8f));
                    }

                    if (state.attackTicks % 16 == 0) {
                        world.playSound(anchor, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.9f + (progress * 0.8f));
                        WardenAudio.playCustomSound(anchor, "haohan:boss.magic", 1.8f, 0.8f + (progress * 0.6f));
                    }

                    WardenVFX.renderMagicCircle(summonCenter, currentRadius, rotationAngle, progress, Color.fromRGB(60, 190, 255));

                    if (state.attackTicks == chantDurationTicks) {
                        world.playSound(summonCenter, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.8f, 1.6f);
                        world.playSound(summonCenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.8f);
                        world.spawnParticle(Particle.FLASH, summonCenter.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.0, Color.WHITE);
                        world.playSound(anchor, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
                        world.playSound(anchor, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.5f);
                        WardenAudio.playCustomSound(anchor, "haohan:boss.judgementcut", 2.0f, 1.0f);
                    }
                }

                if (state.attackTicks > chantDurationTicks && state.attackTicks <= barrageEndTicks) {
                    if (state.attackTicks % 3 == 0) {
                        Location skySwordTip = anchor.clone().add(0, 6.8, 0);
                        world.spawnParticle(Particle.END_ROD, skySwordTip, 6, 0.3, 0.6, 0.3, 0.05);
                        world.spawnParticle(Particle.FLASH, skySwordTip, 1, 0.1, 0.1, 0.1, 0.0, Color.fromRGB(160, 230, 255));
                    }

                    int barrageElapsed = state.attackTicks - chantDurationTicks;
                    if (barrageElapsed % 7 == 0) {
                        double offX = (random.nextDouble() - 0.5) * 4.0;
                        double offZ = (random.nextDouble() - 0.5) * 4.0;
                        Location strikePoint = summonCenter.clone().add(offX, 0, offZ);
                        new TargetedLightStrikeTask(golem, strikePoint).runTaskTimer(plugin, 1L, 1L);
                    }
                }

                if (state.attackTicks > barrageEndTicks && state.attackTicks <= state.attackTotalTicks) {
                    if (state.attackTicks == MathUtil.secondsToTicks(9.8)) {
                        world.playSound(anchor, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.6f, 1.0f);
                        world.playSound(anchor, Sound.BLOCK_CHAIN_HIT, 1.4f, 1.2f);
                        WardenAudio.playCustomSound(anchor, "haohan:boss.sword_sheath", 1.8f, 1.0f);
                        Location sheathLoc = anchor.clone().add(forwardDir.clone().multiply(1.2)).add(0, 1.5, 0);
                        world.spawnParticle(Particle.DUST, sheathLoc, 20, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(120, 210, 255), 1.5f));
                    }
                }

                if (state.attackTicks >= state.attackTotalTicks) {
                    restartShowcaseSkill(golem, state, skill, 0.20);
                }
            }
        }
    }

    /**
     * Resets skill parameters and loops the animation and attack phase seamlessly.
     */
    public static void restartShowcaseSkill(IronGolem golem, WardenState state, String skill, double crossFade) {
        state.attackTicks = 0;
        state.attackHitDone = false;
        state.flingExecuted = false;
        state.hitVictimsThisAttack.clear();
        state.multiHitTickMap.clear();
        state.sideGrazeHitTickMap.clear();
        state.prevBladeTip = null;
        state.prevBladeBase = null;
        WardenCombatHandler.executeSingleAttackPhase(golem, state, skill, crossFade);
    }
}
