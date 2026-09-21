package vn.haohan.lunar.core.subsystem.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import vn.haohan.lunar.api.event.LunarMobTargetChangeEvent;
import vn.haohan.lunar.api.event.LunarSkillPostCastEvent;
import vn.haohan.lunar.api.event.LunarSkillPreCastEvent;
import vn.haohan.lunar.api.mob.phase.MobPhase;
import vn.haohan.lunar.api.mob.phase.MobPhaseMachine;
import vn.haohan.lunar.api.mob.phase.PhaseContext;
import vn.haohan.lunar.api.system.combat.skill.*;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionResult;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;
import vn.haohan.lunar.api.system.combat.skill.target.TargeterContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargeterRegistry;
import vn.haohan.lunar.api.system.combat.threat.TargetChangeReason;
import vn.haohan.lunar.core.system.throttle.DynamicThrottlingEngine;
import vn.haohan.lunar.core.system.variable.VariableHolder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * High-performance, multithread-friendly event and timer dispatcher for custom mob skills.
 * Optimized with DynamicThrottlingEngine LOD to eliminate unnecessary calculations on inactive mobs.
 */
public class MobSkillRuntime implements Listener {

    private final LunarMobManager mobManager;
    private final SkillRegistry skillRegistry;
    private final MechanicRegistry mechanicRegistry;
    private final ConditionRegistry conditionRegistry;
    private final TargeterRegistry targeterRegistry;
    private final vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry extendedTargeterRegistry = new vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry();
    private final CooldownRegistry cooldownRegistry;
    private final SkillScheduler skillScheduler;
    private final DynamicThrottlingEngine throttlingEngine;
    private final Logger logger;

    // Cache parsed inline skill entries: "skillName ~trigger chance" -> ParsedSkillEntry
    private final Map<String, ParsedSkillRef> skillRefCache = new ConcurrentHashMap<>();

    public MobSkillRuntime(LunarMobManager mobManager, SkillRegistry skillRegistry, MechanicRegistry mechanicRegistry, ConditionRegistry conditionRegistry, TargeterRegistry targeterRegistry, CooldownRegistry cooldownRegistry, SkillScheduler skillScheduler, DynamicThrottlingEngine throttlingEngine, Logger logger) {
        this.mobManager = Objects.requireNonNull(mobManager, "MobManager must not be null");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "SkillRegistry must not be null");
        this.mechanicRegistry = Objects.requireNonNull(mechanicRegistry, "MechanicRegistry must not be null");
        this.conditionRegistry = Objects.requireNonNull(conditionRegistry, "ConditionRegistry must not be null");
        this.targeterRegistry = Objects.requireNonNull(targeterRegistry, "TargeterRegistry must not be null");
        this.cooldownRegistry = Objects.requireNonNull(cooldownRegistry, "CooldownRegistry must not be null");
        this.skillScheduler = Objects.requireNonNull(skillScheduler, "SkillScheduler must not be null");
        this.throttlingEngine = throttlingEngine != null ? throttlingEngine : new DynamicThrottlingEngine();
        this.logger = logger != null ? logger : Logger.getLogger(MobSkillRuntime.class.getName());
    }

    public CooldownRegistry cooldowns() {
        return cooldownRegistry;
    }

    /**
     * Periodic ticking for all active mobs to trigger ON_TIMER and ON_COMBAT skills.
     */
    public void tick(long currentTick) {
        for (ActiveMob mob : mobManager.snapshot()) {
            LivingEntity entity = mob.entity();
            if (entity == null || !entity.isValid() || entity.isDead()) {
                continue;
            }

            evaluatePhaseTransition(mob, currentTick, null);

            boolean inCombat = mob.threatTable() != null && !mob.threatTable().isEmpty();
            if (!throttlingEngine.shouldTickMobAI(inCombat, 16.0, currentTick)) {
                continue;
            }

            LivingEntity primaryTarget = resolvePrimaryTarget(mob);

            // 1. Dispatch ON_TIMER
            dispatchTrigger(mob, SkillTrigger.ON_TIMER, primaryTarget, null, Map.of(), currentTick);

            // 2. Dispatch ON_COMBAT if in combat
            if (inCombat) {
                dispatchTrigger(mob, SkillTrigger.ON_COMBAT, primaryTarget, null, Map.of(), currentTick);
            }
        }
    }

    /**
     * Dispatches a specific SkillTrigger for an active mob.
     */
    public void dispatchTrigger(ActiveMob mob, SkillTrigger trigger, Entity triggerEntity, Location triggerLocation, Map<String, Object> extraVariables, long currentTick) {
        if (mob == null) return;
        LivingEntity caster = mob.entity();
        if (caster == null || !caster.isValid() || caster.isDead()) return;

        // CC check: Stunned or silenced mobs cannot cast skills (except ON_DEATH)
        if ((mob.crowdControl().isSilenced() || mob.crowdControl().isStunned()) && trigger != SkillTrigger.ON_DEATH) {
            return;
        }

        List<String> skillRefs = mob.definition().skillReferences();
        if (skillRefs.isEmpty()) return;

        Map<String, Object> baseVars = new HashMap<>(extraVariables);
        baseVars.put("current_tick", currentTick);
        baseVars.put("caster_name", mob.definition().displayName());
        baseVars.put("stance", mob.stance());
        if (triggerEntity != null) {
            baseVars.put("trigger_entity_uuid", triggerEntity.getUniqueId().toString());
        }

        for (String rawRef : skillRefs) {
            ParsedSkillRef ref = skillRefCache.computeIfAbsent(rawRef, this::parseSkillRef);
            if (ref == null) continue;

            // Chance roll
            if (ref.chance() < 1.0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() > ref.chance()) {
                continue;
            }

            Optional<SkillChainDefinition> chainOpt = skillRegistry.get(ref.skillId());
            if (chainOpt.isEmpty()) continue;
            SkillChainDefinition chain = chainOpt.get();

            // Trigger match check
            boolean matchesTrigger = false;
            if (ref.explicitTrigger() != null) {
                matchesTrigger = (ref.explicitTrigger() == trigger);
            } else {
                matchesTrigger = chain.definition().triggers().contains(trigger);
            }
            if (!matchesTrigger) continue;

            // Cooldown check
            if (!cooldownRegistry.isReady(mob.entityId(), chain.definition().id(), currentTick)) {
                continue;
            }

            // Condition evaluation
            ConditionContext conditionContext = new ConditionContext(caster, triggerEntity, mob.stance(), cooldownRegistry, baseVars, currentTick);

            if (!chain.conditions().isEmpty()) {
                ConditionResult condResult = conditionRegistry.and(conditionContext, chain.conditions());
                if (!condResult.valid() || !condResult.matched()) {
                    continue;
                }
            }

            // Acquire cooldown
            if (!cooldownRegistry.tryAcquire(mob.entityId(), chain.definition(), currentTick)) {
                continue;
            }

            // Execute mechanics definition prepared
            SkillDefinition skillDef = chain.definition();
            if (!skillDef.triggers().contains(trigger)) {
                Set<SkillTrigger> triggers = new HashSet<>(skillDef.triggers());
                triggers.add(trigger);
                skillDef = new SkillDefinition(skillDef.id(), triggers, skillDef.cooldownTicks());
            }

            // Target resolution
            LivingEntity targetLiving = triggerEntity instanceof LivingEntity le ? le : resolvePrimaryTarget(mob);
            TargeterContext targeterContext = new TargeterContext(caster, targetLiving, triggerLocation != null ? triggerLocation : caster.getLocation(), 32.0, 64);
            List<TargetRef> targets = resolveTargets(mob, chain.targeter(), targeterContext, skillDef, trigger, currentTick);

            LivingEntity primaryTarget = triggerEntity instanceof LivingEntity le ? le : (!targets.isEmpty() && targets.get(0).entity() instanceof LivingEntity le2 ? le2 : null);

            // Fire LunarSkillPreCastEvent
            try {
                LunarSkillPreCastEvent preCast = new LunarSkillPreCastEvent(mob, chain.definition(), primaryTarget, 1.0);
                Bukkit.getPluginManager().callEvent(preCast);
                if (preCast.isCancelled()) {
                    continue;
                }
            }
            catch (Throwable ignored) {
                // If Bukkit events cannot be called in standalone tests, proceed
            }

            VariableHolder castVars = new VariableHolder();
            baseVars.forEach((k, v) -> {
                if (v != null) {
                    castVars.set(k, String.valueOf(v));
                }
            });

            SkillCastContext castContext = new SkillCastContext(mob, skillDef, trigger, currentTick, triggerEntity, triggerLocation != null ? triggerLocation : caster.getLocation(), castVars, new AtomicBoolean(false), 0, baseVars);

            executeMechanics(mob, chain, castContext, targets);

            // Fire LunarSkillPostCastEvent
            try {
                LunarSkillPostCastEvent postCast = new LunarSkillPostCastEvent(mob, chain.definition(), primaryTarget, true);
                Bukkit.getPluginManager().callEvent(postCast);
            }
            catch (Throwable ignored) {
            }
        }
    }

    public boolean executeSkillDirectly(ActiveMob mob, String skillId, Entity triggerEntity, Location triggerLocation, long currentTick) {
        if (mob == null || skillId == null || skillId.isBlank()) return false;
        LivingEntity caster = mob.entity();
        if (caster == null || !caster.isValid() || caster.isDead()) return false;

        Optional<SkillChainDefinition> chainOpt = skillRegistry.get(skillId.trim().toLowerCase(Locale.ROOT));
        if (chainOpt.isEmpty()) return false;

        SkillChainDefinition chain = chainOpt.get();
        SkillDefinition skillDef = chain.definition();

        LivingEntity targetLiving = triggerEntity instanceof LivingEntity le ? le : resolvePrimaryTarget(mob);
        TargeterContext targeterContext = new TargeterContext(caster, targetLiving, triggerLocation != null ? triggerLocation : caster.getLocation(), 32.0, 64);
        List<TargetRef> targets = resolveTargets(mob, chain.targeter(), targeterContext, skillDef, SkillTrigger.ON_TIMER, currentTick);

        Map<String, Object> baseVars = new HashMap<>();
        baseVars.put("current_tick", currentTick);
        baseVars.put("caster_name", mob.definition().displayName());
        baseVars.put("stance", mob.stance());
        if (triggerEntity != null) {
            baseVars.put("trigger_entity_uuid", triggerEntity.getUniqueId().toString());
        }

        VariableHolder castVars = new VariableHolder();
        baseVars.forEach((k, v) -> {
            if (v != null) castVars.set(k, String.valueOf(v));
        });

        SkillCastContext castContext = new SkillCastContext(mob, skillDef, SkillTrigger.ON_TIMER, currentTick, triggerEntity, triggerLocation != null ? triggerLocation : caster.getLocation(), castVars, new AtomicBoolean(false), 0, baseVars);
        executeMechanics(mob, chain, castContext, targets);
        return true;
    }

    private void executeMechanics(ActiveMob mob, SkillChainDefinition chain, SkillCastContext castContext, List<TargetRef> targets) {
        for (SkillChainDefinition.MechanicStep step : chain.mechanics()) {
            if (castContext.isCancelled()) break;

            if (step.delayTicks() > 0) {
                skillScheduler.schedule(mob.entityId(), step.delayTicks(), 1L, step.repeat(), () -> {
                    if (!castContext.isCancelled()) {
                        mechanicRegistry.execute(step.id(), new MechanicContext(castContext, targets), step.parameters());
                    }
                });
            } else {
                for (int rep = 0 ; rep < step.repeat() ; rep++) {
                    mechanicRegistry.execute(step.id(), new MechanicContext(castContext, targets), step.parameters());
                }
            }
        }
    }

    private List<TargetRef> resolveTargets(ActiveMob mob, String targeterName, TargeterContext context, SkillDefinition skillDef, SkillTrigger trigger, long currentTick) {
        if (targeterName == null || targeterName.isBlank()) {
            return targeterRegistry.resolve("self", context);
        }
        if (targeterName.startsWith("@") || extendedTargeterRegistry.hasTargeter(targeterName)) {
            try {
                SkillCastContext castContext = new SkillCastContext(mob, skillDef, trigger, currentTick, context.targetOptional().orElse(null), context.origin());

                if (extendedTargeterRegistry.isLocationTargeter(targeterName)) {
                    var locs = extendedTargeterRegistry.resolveLocations(targeterName, castContext);
                    if (locs != null && !locs.isEmpty()) {
                        return locs.stream().map(TargetRef::location).toList();
                    }
                } else {
                    var entities = extendedTargeterRegistry.resolveEntities(targeterName, castContext);
                    if (entities != null && !entities.isEmpty()) {
                        return entities.stream().map(TargetRef::entity).toList();
                    }
                }
            }
            catch (Throwable ignored) {
            }
        }
        return targeterRegistry.resolve(targeterName, context);
    }

    private LivingEntity resolvePrimaryTarget(ActiveMob mob) {
        if (mob.entity() instanceof org.bukkit.entity.Mob m && m.getTarget() != null) {
            return m.getTarget();
        }
        if (mob.threatTable() != null) {
            var top = mob.threatTable().topTarget();
            if (top.isPresent()) {
                Entity ent = Bukkit.getEntity(top.get());
                if (ent instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                    return living;
                }
            }
        }
        return null;
    }

    private ParsedSkillRef parseSkillRef(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] tokens = raw.trim().split("\\s+");
        String skillId = tokens[0];
        SkillTrigger explicitTrigger = null;
        double chance = 1.0;

        for (int i = 1 ; i < tokens.length ; i++) {
            String token = tokens[i];
            if (token.startsWith("~")) {
                String trigName = token.substring(1).toLowerCase(Locale.ROOT);
                for (SkillTrigger st : SkillTrigger.values()) {
                    if (st.configName().equalsIgnoreCase(trigName) || st.name().equalsIgnoreCase(trigName)) {
                        explicitTrigger = st;
                        break;
                    }
                }
            } else {
                try {
                    chance = Double.parseDouble(token);
                }
                catch (NumberFormatException ignored) {
                }
            }
        }

        return new ParsedSkillRef(skillId, explicitTrigger, Math.max(0.0, Math.min(1.0, chance)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        long currentTick = skillScheduler.currentTick();
        Entity rawAttacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            rawAttacker = byEntity.getDamager();
            if (rawAttacker instanceof Projectile proj && proj.getShooter() instanceof Entity shooterEnt) {
                rawAttacker = shooterEnt;
            }
        }

        // 1. Attacker CC Checks (Stun / Disarm)
        if (rawAttacker instanceof LivingEntity livingAttacker) {
            ActiveMob activeAttacker = mobManager.get(livingAttacker.getUniqueId());
            if (activeAttacker != null) {
                if (activeAttacker.crowdControl().isStunned()) {
                    event.setCancelled(true);
                    return;
                }
                if (activeAttacker.crowdControl().isDisarmed()) {
                    DamageCause cause = event.getCause();
                    if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // 2. Victim CC, Immunity & DamageModifier Checks
        if (event.getEntity() instanceof LivingEntity livingVictim) {
            ActiveMob activeVictim = mobManager.get(livingVictim.getUniqueId());
            if (activeVictim != null) {
                if (activeVictim.crowdControl().isInvulnerable()) {
                    event.setCancelled(true);
                    return;
                }
                if (activeVictim.immunityTable().hasCauseImmunity(event.getCause(), currentTick)) {
                    event.setCancelled(true);
                    return;
                }
                EntityType attackerType = rawAttacker != null ? rawAttacker.getType() : null;
                double multiplier = activeVictim.damageModifiers().calculateMultiplier(event.getCause(), attackerType);
                if (multiplier <= 0.0) {
                    event.setCancelled(true);
                    return;
                }
                if (multiplier != 1.0) {
                    event.setDamage(event.getDamage() * multiplier);
                }

                // Threat recording on active victim
                if (rawAttacker instanceof LivingEntity livingAttacker) {
                    double finalDamage = event.getFinalDamage();
                    UUID previousTarget = activeVictim.threatTable().currentTargetId();
                    activeVictim.threatTable().addDamageThreat(livingAttacker.getUniqueId(), finalDamage, currentTick);

                    var newTargetOpt = activeVictim.threatTable().evaluateTarget(currentTick);
                    if (newTargetOpt.isPresent() && !newTargetOpt.get().equals(previousTarget)) {
                        UUID newTargetId = newTargetOpt.get();
                        Entity newTargetEntity = Bukkit.getEntity(newTargetId);
                        if (newTargetEntity instanceof LivingEntity newTargetLiving) {
                            LivingEntity prevLiving = previousTarget != null && Bukkit.getEntity(previousTarget) instanceof LivingEntity pl ? pl : null;
                            LunarMobTargetChangeEvent targetEvent = new LunarMobTargetChangeEvent(activeVictim, prevLiving, newTargetLiving, activeVictim.threatTable().getThreat(newTargetId), TargetChangeReason.DAMAGE_THREAT);
                            try {
                                Bukkit.getPluginManager().callEvent(targetEvent);
                                if (!targetEvent.isCancelled()) {
                                    if (activeVictim.entity() instanceof org.bukkit.entity.Mob bukkitMob) {
                                        bukkitMob.setTarget(targetEvent.newTarget().orElse(newTargetLiving));
                                    }
                                } else {
                                    activeVictim.threatTable().setCurrentTargetId(previousTarget);
                                }
                            }
                            catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        }

        if (event.isCancelled()) return;

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity attacker = rawAttacker;

            // Check if attacker is active mob (ON_ATTACK)
            if (attacker instanceof LivingEntity livingAttacker) {
                ActiveMob activeAttacker = mobManager.get(livingAttacker.getUniqueId());
                if (activeAttacker != null) {
                    Map<String, Object> vars = Map.of("damage", event.getDamage(), "cause", event.getCause().name());
                    dispatchTrigger(activeAttacker, SkillTrigger.ON_ATTACK, byEntity.getEntity(), byEntity.getEntity().getLocation(), vars, skillScheduler.currentTick());
                }
            }

            // Check if victim is active mob (ON_DAMAGED)
            if (byEntity.getEntity() instanceof LivingEntity livingVictim) {
                ActiveMob activeVictim = mobManager.get(livingVictim.getUniqueId());
                if (activeVictim != null) {
                    Map<String, Object> vars = Map.of("damage", event.getDamage(), "damage_cause", event.getCause().name());
                    dispatchTrigger(activeVictim, SkillTrigger.ON_DAMAGED, attacker, livingVictim.getLocation(), vars, skillScheduler.currentTick());
                    evaluatePhaseTransition(activeVictim, skillScheduler.currentTick(), null);
                }
            }
        } else if (event.getEntity() instanceof LivingEntity livingVictim) {
            ActiveMob activeVictim = mobManager.get(livingVictim.getUniqueId());
            if (activeVictim != null) {
                Map<String, Object> vars = Map.of("damage", event.getDamage(), "damage_cause", event.getCause().name());
                dispatchTrigger(activeVictim, SkillTrigger.ON_DAMAGED, null, livingVictim.getLocation(), vars, skillScheduler.currentTick());
                evaluatePhaseTransition(activeVictim, skillScheduler.currentTick(), null);
            }
        }
    }

    // --- Bukkit Event Listeners ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        ActiveMob activeDead = mobManager.get(dead.getUniqueId());
        LivingEntity killer = dead.getKiller();

        // Remove dead entity from all active mobs' threat tables
        UUID deadId = dead.getUniqueId();
        for (ActiveMob mob : mobManager.snapshot()) {
            if (mob.threatTable() != null) {
                mob.threatTable().removeTarget(deadId);
            }
        }

        // Victim dead mob
        if (activeDead != null) {
            dispatchTrigger(activeDead, SkillTrigger.ON_DEATH, killer, dead.getLocation(), Map.of(), skillScheduler.currentTick());
        }

        // Killer mob
        if (killer != null) {
            ActiveMob activeKiller = mobManager.get(killer.getUniqueId());
            if (activeKiller != null) {
                dispatchTrigger(activeKiller, SkillTrigger.ON_KILL, dead, killer.getLocation(), Map.of(), skillScheduler.currentTick());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        ProjectileSource shooter = proj.getShooter();
        if (shooter instanceof LivingEntity living) {
            ActiveMob mob = mobManager.get(living.getUniqueId());
            if (mob != null) {
                dispatchTrigger(mob, SkillTrigger.ON_SHOOT, null, proj.getLocation(), Map.of(), skillScheduler.currentTick());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            ActiveMob mob = mobManager.get(living.getUniqueId());
            if (mob != null) {
                dispatchTrigger(mob, SkillTrigger.ON_HEAL, null, living.getLocation(), Map.of("amount", event.getAmount(), "heal_reason", event.getRegainReason().name()), skillScheduler.currentTick());
            }

            // Heal threat: add threat to active mobs targeting or engaged with this entity
            UUID healedId = living.getUniqueId();
            long curTick = skillScheduler.currentTick();
            for (ActiveMob activeMob : mobManager.snapshot()) {
                if (activeMob.threatTable() != null && activeMob.threatTable().hasTarget(healedId)) {
                    activeMob.threatTable().addHealThreat(healedId, event.getAmount(), curTick);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            ActiveMob mob = mobManager.get(living.getUniqueId());
            if (mob != null) {
                dispatchTrigger(mob, SkillTrigger.ON_TELEPORT, null, event.getTo(), Map.of(), skillScheduler.currentTick());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetChange(LunarMobTargetChangeEvent event) {
        ActiveMob mob = event.mob();
        if (mob != null) {
            dispatchTrigger(mob, SkillTrigger.ON_TARGET_CHANGE, event.newTarget().orElse(null), mob.entity().getLocation(), Map.of("reason", event.reason().name()), skillScheduler.currentTick());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof LivingEntity living) {
            ActiveMob mob = mobManager.get(living.getUniqueId());
            if (mob != null) {
                dispatchTrigger(mob, SkillTrigger.ON_INTERACT, event.getPlayer(), living.getLocation(), Map.of(), skillScheduler.currentTick());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        for (ActiveMob mob : mobManager.snapshot()) {
            if (mob.threatTable() != null) {
                mob.threatTable().removeTarget(playerId);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        World currentWorld = event.getPlayer().getWorld();
        for (ActiveMob mob : mobManager.snapshot()) {
            if (mob.threatTable() != null && mob.entity() != null && !mob.entity().getWorld().equals(currentWorld)) {
                mob.threatTable().removeTarget(playerId);
            }
        }
    }

    public void evaluatePhaseTransition(ActiveMob mob, long currentTick, String signal) {
        if (mob == null) return;
        LivingEntity entity = mob.entity();
        if (entity == null || !entity.isValid() || entity.isDead()) return;

        MobPhaseMachine machine = mob.phaseMachine();
        if (machine == null) {
            Object rawPhases = ActiveMob.findOption(mob.definition().options(), "phases", "phase_machine", "phasemachine");
            if (rawPhases != null) {
                List<MobPhase> phases = MobPhase.parsePhases(rawPhases);
                if (!phases.isEmpty()) {
                    machine = new MobPhaseMachine(phases, cooldownRegistry, phaseSkill -> {
                        executeSkillDirectly(phaseSkill.mob(), phaseSkill.skillId(), null, null, currentTick);
                    });
                    mob.setPhaseMachine(machine);
                }
            }
        }

        if (machine != null) {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();
            long aliveTicks = entity.getTicksLived();
            int targetCount = mob.threatTable() != null ? mob.threatTable().size() : 0;
            Map<String, Object> vars = Map.of("stance", mob.stance());
            PhaseContext context = new PhaseContext(mob, health, maxHealth, currentTick, aliveTicks, targetCount, vars, signal);
            machine.update(context);
        }
    }

    public void onSignal(ActiveMob mob, String signal, long currentTick) {
        if (mob == null || signal == null) return;
        evaluatePhaseTransition(mob, currentTick, signal);
        dispatchTrigger(mob, SkillTrigger.ON_SIGNAL, null, null, Map.of("signal", signal), currentTick);
    }

    private record ParsedSkillRef(String skillId, SkillTrigger explicitTrigger, double chance) {
    }
}
