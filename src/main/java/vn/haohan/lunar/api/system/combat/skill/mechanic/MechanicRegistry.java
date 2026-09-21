package vn.haohan.lunar.api.system.combat.skill.mechanic;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.mob.disguise.DisguiseData;
import vn.haohan.lunar.api.mob.disguise.DisguiseManager;
import vn.haohan.lunar.api.mob.disguise.DisguiseType;
import vn.haohan.lunar.api.mob.pack.PackCoordinationService;
import vn.haohan.lunar.api.mob.signal.MobSignalBus;
import vn.haohan.lunar.api.presentation.audio.SpatialAudioEngine;
import vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge;
import vn.haohan.lunar.api.presentation.display.orchestration.*;
import vn.haohan.lunar.api.presentation.particle.geometric.ParticleChoreographer;
import vn.haohan.lunar.api.system.combat.DamageType;
import vn.haohan.lunar.api.system.combat.cc.CCState;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.aura.*;
import vn.haohan.lunar.api.system.combat.skill.complex.BeamEngine;
import vn.haohan.lunar.api.system.combat.skill.complex.ChainEngine;
import vn.haohan.lunar.api.system.combat.skill.complex.OrbitalEngine;
import vn.haohan.lunar.api.system.combat.skill.complex.SlashEngine;
import vn.haohan.lunar.api.system.combat.skill.interrupt.InterruptReason;
import vn.haohan.lunar.api.system.combat.skill.projectile.ActiveProjectile;
import vn.haohan.lunar.api.system.combat.skill.projectile.ProjectileDefinition;
import vn.haohan.lunar.api.system.combat.skill.projectile.ProjectileTracker;
import vn.haohan.lunar.api.system.combat.skill.projectile.SurfaceMode;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;
import vn.haohan.lunar.api.system.world.environment.EnvironmentalFieldTracker;
import vn.haohan.lunar.api.system.world.hazard.HazardZoneDefinition;
import vn.haohan.lunar.api.system.world.hazard.HazardZoneTracker;
import vn.haohan.lunar.api.system.world.totem.TotemDefinition;
import vn.haohan.lunar.api.system.world.totem.TotemManager;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.system.util.SafeExpressionEvaluator;
import vn.haohan.lunar.core.system.variable.VariableManager;
import vn.haohan.lunar.core.system.variable.VariableScope;
import vn.haohan.lunar.core.system.variable.VariableValue;

import java.util.*;
import java.util.function.BiPredicate;

/** Safe MVP mechanic registry. Arbitrary command execution is intentionally absent. */
public final class MechanicRegistry {

    private static final double MAX_AMOUNT = 1000.0;
    private static final int MAX_COUNT = 500;
    private final Map<String, Mechanic> mechanics = new LinkedHashMap<>();

    private AuraScheduler auraScheduler;
    private AuraRegistry auraRegistry;
    private ProjectileTracker projectileTracker;
    private BiPredicate<String, SkillCastContext> subskillInvoker;
    private VariableManager variableManager;
    private final SpatialAudioEngine audioEngine = new SpatialAudioEngine();
    private TotemManager totemManager = new TotemManager();
    private HazardZoneTracker hazardZoneTracker = new HazardZoneTracker();
    private EnvironmentalFieldTracker fieldTracker = new EnvironmentalFieldTracker();
    private MobSignalBus signalBus = new MobSignalBus();
    private PackCoordinationService packService = new PackCoordinationService();
    private LunarMobManager mobManager;
    private DisguiseManager disguiseManager = new DisguiseManager();
    public DisguiseManager disguiseManager() { return disguiseManager; }
    public void setDisguiseManager(DisguiseManager manager) { if (manager != null) this.disguiseManager = manager; }

    private DisplayEntityManager displayEntityManager = new DisplayEntityManager();
    public DisplayEntityManager displayEntityManager() { return displayEntityManager; }
    public void setDisplayEntityManager(DisplayEntityManager manager) { if (manager != null) this.displayEntityManager = manager; }

    private ParticleChoreographer particleChoreographer = new ParticleChoreographer();
    public ParticleChoreographer particleChoreographer() { return particleChoreographer; }
    public void setParticleChoreographer(ParticleChoreographer choreographer) { if (choreographer != null) this.particleChoreographer = choreographer; }

    public MobSignalBus signalBus() { return signalBus; }
    public void setSignalBus(MobSignalBus bus) { if (bus != null) this.signalBus = bus; }
    public PackCoordinationService packService() { return packService; }
    public void setPackService(PackCoordinationService service) { if (service != null) this.packService = service; }
    public LunarMobManager mobManager() { return mobManager; }
    public void setMobManager(LunarMobManager mobManager) { this.mobManager = mobManager; }


    public TotemManager totemManager() { return totemManager; }
    public void setTotemManager(TotemManager manager) { if (manager != null) this.totemManager = manager; }
    public HazardZoneTracker hazardZoneTracker() { return hazardZoneTracker; }
    public void setHazardZoneTracker(HazardZoneTracker tracker) { if (tracker != null) this.hazardZoneTracker = tracker; }
    public EnvironmentalFieldTracker fieldTracker() { return fieldTracker; }
    public void setFieldTracker(EnvironmentalFieldTracker fieldTracker) {
        if (fieldTracker != null) this.fieldTracker = fieldTracker;
    }


    public MechanicRegistry() {
        registerBuiltins();
    }

    public void setAuraScheduler(AuraScheduler scheduler) {
        this.auraScheduler = scheduler;
    }

    public void setAuraRegistry(AuraRegistry registry) {
        this.auraRegistry = registry;
    }

    public void setProjectileTracker(ProjectileTracker tracker) {
        this.projectileTracker = tracker;
    }

    public void setSubskillInvoker(BiPredicate<String, SkillCastContext> invoker) {
        this.subskillInvoker = invoker;
    }

    public void setVariableManager(VariableManager variableManager) {
        this.variableManager = variableManager;
    }

    public SpatialAudioEngine audioEngine() {
        return audioEngine;
    }

    public synchronized void register(String id, Mechanic mechanic) {
        String normalized = normalize(id);
        Objects.requireNonNull(mechanic, "Mechanic must not be null");
        mechanics.put(normalized, mechanic);
    }

    public Optional<Mechanic> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mechanics.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Map<String, Mechanic> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mechanics));
    }

    public MechanicResult execute(String id, MechanicContext context, Map<String, ?> parameters) {
        if (context == null || parameters == null) {
            return MechanicResult.invalid("Mechanic context and parameters are required");
        }
        Mechanic mechanic;
        try {
            mechanic = mechanics.get(normalize(id));
        } catch (RuntimeException exception) {
            return MechanicResult.invalid(exception.getMessage());
        }
        if (mechanic == null) {
            return MechanicResult.invalid("Unknown mechanic: " + id);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> castParams = (Map<String, Object>) parameters;
            mechanic.execute(context, castParams);
            return MechanicResult.success();
        } catch (RuntimeException exception) {
            return MechanicResult.invalid("Mechanic '" + id + "' failed: " + exception.getMessage());
        }
    }

    private void registerBuiltins() {
        register("totem", (context, params) -> {
            TotemDefinition def = TotemDefinition.fromMap(params);
            long currentTick = context.cast().startedAtTick();
            UUID casterId = context.cast().casterId();
            for (TargetRef target : context.targets()) {
                Location loc = target.asLocation();
                if (loc == null && target.entity() != null) {
                    try { loc = target.entity().getLocation(); } catch (Throwable ignored) {}
                }
                if (loc != null) {
                    totemManager.spawnTotem(casterId, loc, def, currentTick);
                }
            }
        });
        register("hazardzone", (context, params) -> {
            HazardZoneDefinition def = HazardZoneDefinition.fromMap(params);
            long currentTick = context.cast().startedAtTick();
            UUID casterId = context.cast().casterId();
            for (TargetRef target : context.targets()) {
                Location loc = target.asLocation();
                if (loc == null && target.entity() != null) {
                    try { loc = target.entity().getLocation(); } catch (Throwable ignored) {}
                }
                if (loc != null) {
                    hazardZoneTracker.createZone(casterId, loc, def, currentTick);
                }
            }
        });

        register("disguise", (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            if (caster == null) return;
            String rawType = text(params, "type");
            if (rawType.isBlank()) rawType = "PLAYER";
            rawType = rawType.toUpperCase(Locale.ROOT);
            String name = text(params, "name");
            String skin = text(params, "skin");
            String signature = text(params, "signature");
            if (signature.isBlank()) signature = text(params, "sig");
            DisguiseType disguiseType;
            EntityType entityType;
            if ("PLAYER".equals(rawType)) {
                disguiseType = DisguiseType.PLAYER;
                entityType = EntityType.PLAYER;
            } else {
                disguiseType = DisguiseType.MOB;
                try {
                    entityType = EntityType.valueOf(rawType);
                } catch (IllegalArgumentException e) {
                    entityType = EntityType.ZOMBIE;
                }
            }
            DisguiseData data = new DisguiseData(disguiseType, entityType, skin.isBlank() ? null : skin, signature.isBlank() ? null : signature, name.isBlank() ? null : name);
            disguiseManager.disguise(caster, data);
        });

        register("undisguise", (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            if (caster == null) return;
            disguiseManager.undisguise(caster);
        });

        register("changeskin", (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            if (caster == null || disguiseManager == null) return;
            String skin = text(params, "skin");
            String signature = params.containsKey("signature") ? text(params, "signature") : "";
            disguiseManager.changeSkin(caster, skin, signature);
        });

        register("changemodel", (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            if (caster == null) return;
            String model = params.containsKey("model") ? text(params, "model") : (params.containsKey("id") ? text(params, "id") : "");
            String state = params.containsKey("state") ? text(params, "state") : null;
            if (!model.isBlank()) {
                caster.setActiveModelId(model);
            }
            if (state != null) {
                caster.setActiveModelState(state);
            }
        });

        register("interrupt", (context, params) -> {
            String reasonText = (params.containsKey("reason") ? text(params, "reason") : "STUNNED").toUpperCase(Locale.ROOT);
            InterruptReason reason;
            try {
                reason = InterruptReason.valueOf(reasonText);
            } catch (Exception ignored) {
                reason = InterruptReason.STUNNED;
            }
            if (context.cast() != null && context.cast().caster() != null) {
                context.cast().caster().interruptActiveSkills(reason);
            }
        });

        register("damage", (context, params) -> {
            boundedNumber(context, null, params, "amount", MAX_AMOUNT);
            LivingEntity caster = context.cast().caster() != null ? context.cast().caster().entity() : null;
            for (TargetRef target : context.targets()) {
                if (target.entity() instanceof LivingEntity living && valid(living)) {
                    double amount = boundedNumber(context, target, params, "amount", MAX_AMOUNT);
                    living.damage(amount, caster);
                }
            }
        });
        register("heal", (context, params) -> {
            boundedNumber(context, null, params, "amount", MAX_AMOUNT);
            for (TargetRef target : context.targets()) {
                if (target.entity() instanceof LivingEntity living && valid(living)) {
                    double amount = boundedNumber(context, target, params, "amount", MAX_AMOUNT);
                    double max = living.getMaxHealth();
                    living.setHealth(Math.min(max, living.getHealth() + amount));
                }
            }
        });
        register("velocity", (context, params) -> {
            vector(context, null, params);
            for (TargetRef target : context.targets()) {
                if (target.entity() != null && valid(target.entity())) {
                    Vector vector = vector(context, target, params);
                    target.entity().setVelocity(vector);
                }
            }
        });
        register("knockback", (context, params) -> {
            double strength = boundedNumber(params, "strength", 4.0);
            for (TargetRef ref : context.targets()) {
                if (ref.entity() instanceof LivingEntity target && valid(target)) {
                    LivingEntity casterEntity = context.cast().caster() != null ? context.cast().caster().entity() : null;
                    Location origin = casterEntity != null ? casterEntity.getLocation() : target.getLocation();
                    Vector direction = target.getLocation().toVector().subtract(origin.toVector());
                    if (direction.lengthSquared() > 0) {
                        target.setVelocity(direction.normalize().multiply(strength));
                    }
                }
            }
        });
        register("leap", (context, params) -> {
            double strength = boundedNumber(params, "strength", 4.0);
            double upward = boundedNumber(params, "upward", 2.0);
            for (TargetRef ref : context.targets()) {
                if (ref.entity() instanceof LivingEntity target && valid(target)) {
                    LivingEntity casterEntity = context.cast().caster() != null ? context.cast().caster().entity() : null;
                    Location origin = casterEntity != null ? casterEntity.getLocation() : target.getLocation();
                    Vector direction = target.getLocation().toVector().subtract(origin.toVector());
                    if (direction.lengthSquared() > 0) {
                        target.setVelocity(direction.normalize().multiply(strength).setY(upward));
                    }
                }
            }
        });
        register("teleport", (context, params) -> {
            for (TargetRef ref : context.targets()) {
                if (ref.entity() != null && ref.location() != null && valid(ref.entity())) {
                    ref.entity().teleport(ref.location().clone());
                }
            }
        });
        register("potion", (context, params) -> {
            PotionEffectType type = PotionEffectType.getByName(text(params, "type").toUpperCase(Locale.ROOT));
            if (type == null) {
                throw new IllegalArgumentException("Unknown potion type");
            }
            int duration = boundedInt(params, "duration", 1, 20 * 60 * 60);
            int amplifier = boundedInt(params, "amplifier", 0, 255);
            PotionEffect effect = new PotionEffect(type, duration, amplifier);
            for (TargetRef target : context.targets()) {
                if (target.entity() instanceof LivingEntity living && valid(living)) {
                    living.addPotionEffect(effect);
                }
            }
        });
        register("particle", (context, params) -> {
            Particle particle;
            try {
                particle = Particle.valueOf(text(params, "particle").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown particle");
            }
            int count = params.containsKey("count")
                    ? boundedInt(params, "count", 1, MAX_COUNT)
                    : boundedInt(params, "amount", 1, MAX_COUNT);
            for (Location loc : locations(context.targets())) {
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(particle, loc, count, 0, 0, 0, 0);
                }
            }
        });
        register("sound", (context, params) -> {
            Sound sound;
            try { sound = Sound.valueOf(text(params, "sound").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown sound"); }
            float volume = (float) optionalNumber(params, "volume", 1.0);
            float pitch = (float) optionalNumber(params, "pitch", 1.0);
            for (Location loc : locations(context.targets())) {
                if (loc.getWorld() != null) loc.getWorld().playSound(loc, sound, volume, pitch);
            }
        });
        register("stance", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster != null) {
                caster.setStance(text(params, "stance"));
            }
        });
        register("spawn", (context, params) -> {
            EntityType type;
            try { type = EntityType.valueOf(text(params, "type").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown entity type"); }
            int amount = boundedInt(params, "amount", 1, 20);
            for (Location loc : locations(context.targets())) {
                if (loc.getWorld() != null) {
                    for (int i = 0; i < amount; i++) loc.getWorld().spawnEntity(loc, type);
                }
            }
        });
        register("summon", (context, params) -> {
            EntityType type;
            try { type = EntityType.valueOf(text(params, "entity-type").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown entity type"); }
            if (type == EntityType.PLAYER) throw new IllegalArgumentException("Player summon is not allowed");
            for (Location location : locations(context.targets())) {
                if (location.getWorld() != null) location.getWorld().spawnEntity(location, type);
            }
        });
        register("remove", (context, params) -> {
            for (TargetRef ref : context.targets()) {
                if (ref.entity() != null && valid(ref.entity())) {
                    ref.entity().remove();
                }
            }
        });
        register("animation", (context, params) -> {
            text(params, "name");
        });
        register("mount", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            LivingEntity casterEntity = caster != null ? caster.entity() : null;
            if (casterEntity == null) return;
            for (TargetRef ref : context.targets()) {
                if (ref.entity() != null && !ref.entity().getUniqueId().equals(casterEntity.getUniqueId())) {
                    try {
                        ref.entity().addPassenger(casterEntity);
                    } catch (Throwable ignored) {}
                }
            }
        });
        register("dismount", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster != null && caster.entity() != null) {
                try {
                    caster.entity().leaveVehicle();
                } catch (Throwable ignored) {}
                caster.setMountUUID(null);
            }
        });
        register("ejectpassengers", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster != null && caster.entity() != null) {
                try {
                    caster.entity().eject();
                } catch (Throwable ignored) {}
                caster.riderUUIDs().clear();
            }
        });
        register("setweather", (context, params) -> {
            String type = text(params, "type").toUpperCase(Locale.ROOT);
            int duration = params.containsKey("duration") ? ((Number) params.get("duration")).intValue() : 1200;
            Location loc = null;
            if (context.cast().caster() != null && context.cast().caster().entity() != null) {
                loc = context.cast().caster().entity().getLocation();
            } else if (!context.targets().isEmpty()) {
                loc = context.targets().get(0).location();
                if (loc == null && context.targets().get(0).entity() != null) {
                    loc = context.targets().get(0).entity().getLocation();
                }
            }
            if (loc != null && loc.getWorld() != null) {
                World world = loc.getWorld();
                try {
                    switch (type) {
                        case "THUNDER", "STORM" -> {
                            world.setStorm(true);
                            world.setThundering(true);
                            world.setThunderDuration(duration);
                            world.setWeatherDuration(duration);
                        }
                        case "RAIN" -> {
                            world.setStorm(true);
                            world.setThundering(false);
                            world.setWeatherDuration(duration);
                        }
                        case "CLEAR", "SUN" -> {
                            world.setStorm(false);
                            world.setThundering(false);
                            world.setClearWeatherDuration(duration);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
        register("gravityzone", (context, params) -> {
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 15.0;
            double multiplier = params.containsKey("multiplier") ? ((Number) params.get("multiplier")).doubleValue() : 0.2;
            int duration = params.containsKey("duration") ? ((Number) params.get("duration")).intValue() : 200;

            Location origin = null;
            if (!context.targets().isEmpty()) {
                origin = context.targets().get(0).location();
                if (origin == null && context.targets().get(0).entity() != null) {
                    origin = context.targets().get(0).entity().getLocation();
                }
            }
            if (origin == null && context.cast().caster() != null && context.cast().caster().entity() != null) {
                origin = context.cast().caster().entity().getLocation();
            }
            if (origin != null && fieldTracker != null) {
                fieldTracker.addGravityZone(origin, radius, multiplier, duration, context.cast().startedAtTick());
            }
        });
        register("oxygenfield", (context, params) -> {
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 10.0;
            String modeStr = params.containsKey("mode") ? text(params, "mode").toUpperCase(Locale.ROOT) : "DRAIN";
            EnvironmentalFieldTracker.OxygenMode mode = "RESTORE".equals(modeStr) 
                    ? EnvironmentalFieldTracker.OxygenMode.RESTORE 
                    : EnvironmentalFieldTracker.OxygenMode.DRAIN;
            int amount = params.containsKey("amount") ? ((Number) params.get("amount")).intValue() : 5;
            int duration = params.containsKey("duration") ? ((Number) params.get("duration")).intValue() : 100;

            Location origin = null;
            if (!context.targets().isEmpty()) {
                origin = context.targets().get(0).location();
                if (origin == null && context.targets().get(0).entity() != null) {
                    origin = context.targets().get(0).entity().getLocation();
                }
            }
            if (origin == null && context.cast().caster() != null && context.cast().caster().entity() != null) {
                origin = context.cast().caster().entity().getLocation();
            }
            if (origin != null && fieldTracker != null) {
                fieldTracker.addOxygenField(origin, radius, mode, amount, duration, context.cast().startedAtTick());
            }
        });

        // Composite Skill Nodes
        register("subskill", (context, params) -> {
            String skillId = text(params, "skill");
            if (subskillInvoker != null && !skillId.isBlank()) {
                subskillInvoker.test(skillId, context.cast());
            }
        });
        register("skill", (context, params) -> {
            String skillId = text(params, "skill");
            if (subskillInvoker != null && !skillId.isBlank()) {
                subskillInvoker.test(skillId, context.cast());
            }
        });
        register("sequence", (context, params) -> {
            Object rawList = params.get("skills");
            if (rawList instanceof List<?> list && subskillInvoker != null) {
                for (Object item : list) {
                    if (item != null) subskillInvoker.test(item.toString().trim(), context.cast());
                }
            }
        });
        register("chance", (context, params) -> {
            double chance = optionalNumber(params, "chance", 1.0);
            String skillId = text(params, "skill");
            if (Math.random() <= chance && subskillInvoker != null) {
                subskillInvoker.test(skillId, context.cast());
            }
        });
        register("cancel", (context, params) -> {
            context.cast().cancel();
        });
        register("signal", (context, params) -> {
            String sig = text(params, "sig");
            ActiveMob caster = context.cast().caster();
            int depth = context.cast().depth();
            if (context.targets().isEmpty()) {
                if (caster != null && signalBus != null) {
                    signalBus.sendSignal(caster, caster, sig, depth);
                }
            } else {
                for (TargetRef target : context.targets()) {
                    if (target.entity() != null && mobManager != null) {
                        ActiveMob recipient = mobManager.get(target.entity().getUniqueId());
                        if (recipient != null && signalBus != null) {
                            signalBus.sendSignal(caster, recipient, sig, depth);
                        }
                    } else if (caster != null && signalBus != null) {
                        signalBus.sendSignal(caster, caster, sig, depth);
                    }
                }
            }
        });

        register("broadcastsignal", (context, params) -> {
            String sig = text(params, "sig");
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 32.0;
            String target = params.containsKey("target") ? text(params, "target") : "ALL";
            ActiveMob caster = context.cast().caster();
            int depth = context.cast().depth();
            if (caster != null && signalBus != null && mobManager != null) {
                signalBus.broadcastSignal(caster, sig, radius, target, mobManager, depth);
            }
        });

        register("distresscall", (context, params) -> {
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 32.0;
            double threatShare = params.containsKey("threatShare") ? ((Number) params.get("threatShare")).doubleValue() : 0.8;
            ActiveMob caller = context.cast().caster();
            LivingEntity attacker = null;
            if (!context.targets().isEmpty() && context.targets().get(0).entity() instanceof LivingEntity living) {
                attacker = living;
            } else if (context.cast().triggerEntity() instanceof LivingEntity living) {
                attacker = living;
            }
            if (caller != null && attacker != null && packService != null && mobManager != null) {
                packService.broadcastDistressCall(caller, attacker, radius, threatShare, mobManager);
            }
        });


        // Aura Mechanic
        register("aura", (context, params) -> {
            if (auraScheduler == null || auraRegistry == null) return;
            String auraId = text(params, "aura");
            AuraDefinition def = auraRegistry.get(auraId).orElse(null);
            if (def == null) return;

            String stackModeStr = params.containsKey("stack-mode") ? params.get("stack-mode").toString().toUpperCase(Locale.ROOT) : "REFRESH";
            StackMode stackMode;
            try { stackMode = StackMode.valueOf(stackModeStr); }
            catch (IllegalArgumentException e) { stackMode = StackMode.REFRESH; }

            if (def.stackMode() != stackMode) {
                def = new AuraDefinition(def.id(), def.durationTicks(), def.intervalTicks(), def.radius(), def.maxStacks(), stackMode, def.components());
            }

            ActiveMob caster = context.cast().caster();
            UUID ownerId = caster != null ? caster.entityId() : null;

            for (TargetRef targetRef : context.targets()) {
                if (targetRef.entity() instanceof LivingEntity living) {
                    auraScheduler.applyAura(def, AuraAttachment.ofEntity(living), ownerId, context.cast().startedAtTick());
                }
            }
        });

        // Projectile Mechanic
        register("projectile", (context, params) -> {
            if (projectileTracker == null) return;
            double velocity = params.containsKey("velocity") ? Double.parseDouble(params.get("velocity").toString()) : 1.5;
            int maxTicks = params.containsKey("max-ticks") ? Integer.parseInt(params.get("max-ticks").toString()) : 60;
            String surfaceModeStr = params.containsKey("surface-mode") ? params.get("surface-mode").toString().toUpperCase(Locale.ROOT) : "DETONATE";
            SurfaceMode surfaceMode;
            try { surfaceMode = SurfaceMode.valueOf(surfaceModeStr); }
            catch (IllegalArgumentException e) { surfaceMode = SurfaceMode.DETONATE; }

            ActiveMob caster = context.cast().caster();
            if (caster == null || caster.entity() == null) return;

            Location origin = caster.entity().getLocation().clone().add(0, 1.2, 0);
            for (TargetRef targetRef : context.targets()) {
                Vector dir;
                if (targetRef.entity() != null) {
                    dir = targetRef.entity().getLocation().toVector().subtract(origin.toVector()).normalize();
                } else if (targetRef.location() != null) {
                    dir = targetRef.location().toVector().subtract(origin.toVector()).normalize();
                } else {
                    dir = caster.entity().getLocation().getDirection();
                }

                ProjectileDefinition def = ProjectileDefinition.builder("dynamic_proj")
                        .velocity(velocity)
                        .maxTicks(maxTicks)
                        .surfaceMode(surfaceMode)
                        .build();

                ActiveProjectile proj = new ActiveProjectile(def, origin, dir, caster.entityId(), targetRef);
                projectileTracker.spawn(proj);
            }
        });

        // Combat Mechanics: Immunity & Crowd Control
        register("immunity", (context, params) -> {
            int durationTicks = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            String causeStr = params.containsKey("cause") ? params.get("cause").toString() : null;
            String typeStr = params.containsKey("type") ? params.get("type").toString() : params.containsKey("damage-type") ? params.get("damage-type").toString() : null;
            String skillStr = params.containsKey("skill") ? params.get("skill").toString() : null;

            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            List<ActiveMob> targets = new ArrayList<>();
            if (context.targets() == null || context.targets().isEmpty()) {
                if (caster != null) targets.add(caster);
            } else {
                for (TargetRef ref : context.targets()) {
                    if (ref.entity() != null) {
                        ActiveMob mob = mobManager != null ? mobManager.get(ref.entity().getUniqueId()) : null;
                        if (mob != null) {
                            targets.add(mob);
                        } else if (caster != null && ref.entity().getUniqueId().equals(caster.entityId())) {
                            targets.add(caster);
                        }
                    }
                }
                if (targets.isEmpty() && caster != null) {
                    targets.add(caster);
                }
            }

            for (ActiveMob mob : targets) {
                if (causeStr != null) {
                    try {
                        DamageCause cause = DamageCause.valueOf(causeStr.trim().toUpperCase(Locale.ROOT));
                        mob.immunityTable().addCauseImmunity(cause, durationTicks);
                    } catch (IllegalArgumentException ignored) {}
                }
                if (typeStr != null) {
                    String normType = typeStr.trim().toUpperCase(Locale.ROOT);
                    try {
                        DamageCause cause = DamageCause.valueOf(normType);
                        mob.immunityTable().addCauseImmunity(cause, durationTicks);
                    }
                    catch (IllegalArgumentException ignored) {
                    }
                    try {
                        DamageType dtype = DamageType.valueOf(normType);
                        mob.immunityTable().addTypeImmunity(dtype, durationTicks);
                    } catch (IllegalArgumentException ignored) {}
                }
                if (skillStr != null && !skillStr.isBlank()) {
                    mob.immunityTable().addSkillImmunity(skillStr.trim(), durationTicks);
                }
            }
        });

        register("stun", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number)params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.STUN, duration, priority, 1.0);
        });

        register("root", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number) params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.ROOT, duration, priority, 1.0);
        });

        register("silence", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number)params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.SILENCE, duration, priority, 1.0);
        });

        register("disarm", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number)params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.DISARM, duration, priority, 1.0);
        });

        register("invulnerable", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number)params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.INVULNERABLE, duration, priority, 1.0);
        });

        register("slow", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            double intensity = params.containsKey("amount") ? ((Number)params.get("amount")).doubleValue() : params.containsKey("intensity") ? ((Number)params.get("intensity")).doubleValue() : 0.5;
            int priority = params.containsKey("priority") ? ((Number)params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.SLOW, duration, priority, intensity);
        });

        register("fear", (context, params) -> {
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number)params.get("priority")).intValue() : 1;
            applyCCToTargets(context, CCState.FEAR, duration, priority, 1.0);
        });

        register("cc", (context, params) -> {
            String stateStr = text(params, "state").toUpperCase(Locale.ROOT);
            CCState state = CCState.valueOf(stateStr);
            int duration = params.containsKey("duration") ? boundedInt(params, "duration", 1, 72000) : params.containsKey("d") ? boundedInt(params, "d", 1, 72000) : 20;
            int priority = params.containsKey("priority") ? ((Number) params.get("priority")).intValue() : 1;
            double intensity = params.containsKey("amount") ? ((Number)params.get("amount")).doubleValue() : params.containsKey("intensity") ? ((Number)params.get("intensity")).doubleValue() : 1.0;
            applyCCToTargets(context, state, duration, priority, intensity);
        });

        register("taunt", (context, params) -> {
            double amount = params.containsKey("amount") ? ((Number)params.get("amount")).doubleValue() : params.containsKey("a") ? ((Number)params.get("a")).doubleValue() : 50.0;
            long duration = params.containsKey("duration") ? ((Number)params.get("duration")).longValue() : params.containsKey("d") ? ((Number)params.get("d")).longValue() : 60L;
            ActiveMob caster = context.cast().caster();
            UUID casterId = caster != null ? caster.entityId() : (context.cast() != null ? context.cast().casterId() : null);
            if (casterId == null) return;
            long currentTick = context.cast() != null ? context.cast().startedAtTick() : 0L;

            if (context.targets() != null) {
                for (TargetRef ref : context.targets()) {
                    if (ref.entity() != null) {
                        ActiveMob targetMob = mobManager != null ? mobManager.get(ref.entity().getUniqueId()) : null;
                        if (targetMob != null) {
                            targetMob.threatTable().taunt(casterId, amount, duration, currentTick);
                        }
                    }
                }
            }
        });

        // Complex Combat Skills: Chain, Beam, Orbital, Slash
        register("chain", (context, params) -> {
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 5.0;
            int bounces = params.containsKey("bounces") ? ((Number) params.get("bounces")).intValue() : 3;
            double damage = params.containsKey("damage") ? ((Number) params.get("damage")).doubleValue() : 10.0;
            double decay = params.containsKey("decay") ? ((Number) params.get("decay")).doubleValue() : 0.8;
            for (TargetRef ref : context.targets()) {
                if (ref.entity() instanceof LivingEntity living) {
                    LivingEntity casterEntity = context.cast().caster() != null ? context.cast().caster().entity() : null;
                    ChainEngine.executeChain(living, radius, bounces, damage, decay, casterEntity, (t, d) -> {
                        if (casterEntity != null) t.damage(d, casterEntity);
                    });
                }
            }
        });

        register("beam", (context, params) -> {
            double range = params.containsKey("range") ? ((Number) params.get("range")).doubleValue() : 20.0;
            double width = params.containsKey("width") ? ((Number) params.get("width")).doubleValue() : 1.0;
            double damage = params.containsKey("damage") ? ((Number) params.get("damage")).doubleValue() : 20.0;
            boolean stopOnBlock = !params.containsKey("pierce-blocks") || !Boolean.parseBoolean(params.get("pierce-blocks").toString());

            ActiveMob caster = context.cast().caster();
            LivingEntity casterEntity = caster != null ? caster.entity() : null;
            UUID casterId = caster != null ? caster.entityId() : null;

            for (TargetRef ref : context.targets()) {
                Location origin = casterEntity != null ? casterEntity.getLocation().add(0, 1.2, 0) : null;
                Vector dir;
                if (origin != null) {
                    if (ref.location() != null) dir = ref.location().toVector().subtract(origin.toVector()).normalize();
                    else if (ref.entity() != null) dir = ref.entity().getLocation().toVector().subtract(origin.toVector()).normalize();
                    else dir = origin.getDirection();

                    BeamEngine.fireBeam(origin, dir, range, width, stopOnBlock, casterId, (hit, dist) -> {
                        if (casterEntity != null) hit.damage(damage, casterEntity);
                    });
                }
            }
        });

        register("orbital", (context, params) -> {
            int count = params.containsKey("count") ? ((Number) params.get("count")).intValue() : 3;
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 2.5;
            double speed = params.containsKey("speed") ? ((Number) params.get("speed")).doubleValue() : 0.1;
            double damage = params.containsKey("damage") ? ((Number) params.get("damage")).doubleValue() : 10.0;

            ActiveMob caster = context.cast().caster();
            LivingEntity casterEntity = caster != null ? caster.entity() : null;
            UUID casterId = caster != null ? caster.entityId() : null;

            if (casterEntity != null) {
                var points = OrbitalEngine.computeOrbitalLocations(casterEntity.getLocation(), radius, count, 0L, speed);
                OrbitalEngine.checkOrbitalCollisions(points, 1.5, casterId, (hit, pt) -> {
                    if (casterEntity != null) hit.damage(damage, casterEntity);
                });
            }
        });

        register("slash", (context, params) -> {
            double radius = params.containsKey("radius") ? ((Number) params.get("radius")).doubleValue() : 4.0;
            double arc = params.containsKey("arc") ? ((Number) params.get("arc")).doubleValue() : 90.0;
            double damage = params.containsKey("damage") ? ((Number) params.get("damage")).doubleValue() : 15.0;

            ActiveMob caster = context.cast().caster();
            LivingEntity casterEntity = caster != null ? caster.entity() : null;
            UUID casterId = caster != null ? caster.entityId() : null;

            Location origin = casterEntity != null ? casterEntity.getLocation() : null;
            if (origin != null) {
                SlashEngine.executeSlash(origin, origin.getDirection(), radius, arc, casterId, (target, hit) -> {
                    if (casterEntity != null) target.damage(damage, casterEntity);
                });
            }
        });

        // Variable Mechanics (P4)
        register("setvariable", (context, params) -> {
            String varName = text(params, "var");
            String val = params.containsKey("val") ? params.get("val").toString() : (params.containsKey("value") ? params.get("value").toString() : "");
            String scopeName = params.containsKey("scope") ? text(params, "scope").toUpperCase(Locale.ROOT) : "CASTER";
            boolean savePdc = params.containsKey("save") && Boolean.parseBoolean(params.get("save").toString());
            VariableScope scope;
            try { scope = VariableScope.valueOf(scopeName); }
            catch (IllegalArgumentException e) { scope = VariableScope.CASTER; }

            VariableManager vm = variableManager;
            if (vm == null) return;
            VariableValue varVal = VariableValue.parse(val);
            ActiveMob caster = context.cast().caster();

            switch (scope) {
                case GLOBAL -> vm.getGlobal().set(varName, varVal);
                case CASTER -> {
                    if (caster != null) {
                        vm.getCaster(caster.entityId()).set(varName, varVal);
                        if (savePdc) vm.saveToPdc(caster.entity(), varName, varVal);
                    }
                }
                case TARGET -> {
                    for (TargetRef ref : context.targets()) {
                        if (ref.entity() instanceof LivingEntity living) {
                            vm.findTarget(living).ifPresent(h -> h.set(varName, varVal));
                            if (savePdc) vm.saveToPdc(living, varName, varVal);
                        }
                    }
                }
                case CAST -> context.cast().castVariables().set(varName, varVal);
            }
        });

        register("variableadd", (context, params) -> {
            String varName = text(params, "var");
            double addAmount = params.containsKey("val") ? Double.parseDouble(params.get("val").toString())
                    : (params.containsKey("value") ? Double.parseDouble(params.get("value").toString()) : 1.0);
            String scopeName = params.containsKey("scope") ? text(params, "scope").toUpperCase(Locale.ROOT) : "CASTER";
            VariableScope scope;
            try { scope = VariableScope.valueOf(scopeName); }
            catch (IllegalArgumentException e) { scope = VariableScope.CASTER; }

            VariableManager vm = variableManager;
            if (vm == null) return;
            ActiveMob caster = context.cast().caster();

            switch (scope) {
                case GLOBAL -> {
                    double cur = vm.getGlobal().get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                    vm.getGlobal().set(varName, VariableValue.of(cur + addAmount));
                }
                case CASTER -> {
                    if (caster != null) {
                        double cur = vm.getCaster(caster.entityId()).get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                        vm.getCaster(caster.entityId()).set(varName, VariableValue.of(cur + addAmount));
                    }
                }
                case TARGET -> {
                    for (TargetRef ref : context.targets()) {
                        if (ref.entity() instanceof LivingEntity living) {
                            vm.findTarget(living).ifPresent(h -> {
                                double cur = h.get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                                h.set(varName, VariableValue.of(cur + addAmount));
                            });
                        }
                    }
                }
                case CAST -> {
                    double cur = context.cast().castVariables().get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                    context.cast().castVariables().set(varName, VariableValue.of(cur + addAmount));
                }
            }
        });

        register("variablesubtract", (context, params) -> {
            String varName = text(params, "var");
            double subAmount = params.containsKey("val") ? Double.parseDouble(params.get("val").toString())
                    : (params.containsKey("value") ? Double.parseDouble(params.get("value").toString()) : 1.0);
            String scopeName = params.containsKey("scope") ? text(params, "scope").toUpperCase(Locale.ROOT) : "CASTER";
            VariableScope scope;
            try { scope = VariableScope.valueOf(scopeName); }
            catch (IllegalArgumentException e) { scope = VariableScope.CASTER; }

            VariableManager vm = variableManager;
            if (vm == null) return;
            ActiveMob caster = context.cast().caster();

            switch (scope) {
                case GLOBAL -> {
                    double cur = vm.getGlobal().get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                    vm.getGlobal().set(varName, VariableValue.of(cur - subAmount));
                }
                case CASTER -> {
                    if (caster != null) {
                        double cur = vm.getCaster(caster.entityId()).get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                        vm.getCaster(caster.entityId()).set(varName, VariableValue.of(cur - subAmount));
                    }
                }
                case TARGET -> {
                    for (TargetRef ref : context.targets()) {
                        if (ref.entity() instanceof LivingEntity living) {
                            vm.findTarget(living).ifPresent(h -> {
                                double cur = h.get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                                h.set(varName, VariableValue.of(cur - subAmount));
                            });
                        }
                    }
                }
                case CAST -> {
                    double cur = context.cast().castVariables().get(varName).map(v -> (double) v.asDouble()).orElse(0.0);
                    context.cast().castVariables().set(varName, VariableValue.of(cur - subAmount));
                }
            }
        });

        register("variablemath", (context, params) -> {
            String varName = text(params, "var");
            String equation = text(params, "equation");
            String scopeName = params.containsKey("scope") ? text(params, "scope").toUpperCase(Locale.ROOT) : "CASTER";
            VariableScope scope;
            try { scope = VariableScope.valueOf(scopeName); }
            catch (IllegalArgumentException e) { scope = VariableScope.CASTER; }

            VariableManager vm = variableManager;
            if (vm == null) return;
            ActiveMob caster = context.cast().caster();

            double calculated;
            try {
                calculated = SafeExpressionEvaluator.evaluate(equation);
            } catch (Throwable ignored) {
                calculated = 0.0;
            }

            VariableValue val = VariableValue.of(calculated);
            switch (scope) {
                case GLOBAL -> vm.getGlobal().set(varName, val);
                case CASTER -> {
                    if (caster != null) vm.getCaster(caster.entityId()).set(varName, val);
                }
                case TARGET -> {
                    for (TargetRef ref : context.targets()) {
                        if (ref.entity() instanceof LivingEntity living) {
                            vm.findTarget(living).ifPresent(h -> h.set(varName, val));
                        }
                    }
                }
                case CAST -> context.cast().castVariables().set(varName, val);
            }
        });

        register("variableunset", (context, params) -> {
            String varName = text(params, "var");
            String scopeName = params.containsKey("scope") ? text(params, "scope").toUpperCase(Locale.ROOT) : "CASTER";
            VariableScope scope;
            try { scope = VariableScope.valueOf(scopeName); }
            catch (IllegalArgumentException e) { scope = VariableScope.CASTER; }

            VariableManager vm = variableManager;
            if (vm == null) return;
            ActiveMob caster = context.cast().caster();

            switch (scope) {
                case GLOBAL -> vm.getGlobal().remove(varName);
                case CASTER -> {
                    if (caster != null) vm.getCaster(caster.entityId()).remove(varName);
                }
                case TARGET -> {
                    for (TargetRef ref : context.targets()) {
                        if (ref.entity() instanceof LivingEntity living) {
                            vm.findTarget(living).ifPresent(h -> h.remove(varName));
                        }
                    }
                }
                case CAST -> context.cast().castVariables().remove(varName);
            }
        });

        // Visual & Presentation Mechanics (P8)
        register("barcreate", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster == null) return;
            String barId = text(params, "bar");
            if (barId.isBlank()) barId = "default";
            String title = text(params, "title");
            if (title.isBlank()) title = barId;
            float value = (float) optionalNumber(params, "value", 1.0);
            double range = optionalNumber(params, "range", 32.0);
            String colorStr = text(params, "color").toUpperCase(Locale.ROOT);
            BossBar.Color color;
            try { color = BossBar.Color.valueOf(colorStr); } catch (Exception e) { color = BossBar.Color.PURPLE; }
            String styleStr = text(params, "style").toUpperCase(Locale.ROOT);
            BossBar.Overlay overlay;
            try { overlay = BossBar.Overlay.valueOf(styleStr); } catch (Exception e) { overlay = BossBar.Overlay.PROGRESS; }

            caster.bossBars().create(barId, MiniMessage.miniMessage().deserialize(title), value, color, overlay, range);
        });

        register("barset", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster == null) return;
            String barId = text(params, "bar");
            if (barId.isBlank()) barId = "default";
            var barOpt = caster.bossBars().get(barId);
            if (barOpt.isEmpty()) return;
            var bar = barOpt.get();
            if (params.containsKey("value")) {
                bar.setProgress((float) optionalNumber(params, "value", 1.0));
            }
            if (params.containsKey("title")) {
                bar.setTitle(MiniMessage.miniMessage().deserialize(text(params, "title")));
            }
            if (params.containsKey("color")) {
                try { bar.setColor(BossBar.Color.valueOf(text(params, "color").toUpperCase(Locale.ROOT))); } catch (Exception ignored) {}
            }
        });

        register("barremove", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster == null) return;
            String barId = text(params, "bar");
            if (barId.isBlank()) barId = "default";
            caster.bossBars().remove(barId);
        });

        register("speechbubble", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster == null) return;
            String text = text(params, "text");
            int duration = (int) optionalNumber(params, "duration", 60.0);
            double offsetY = optionalNumber(params, "offsetY", 0.5);
            boolean typewriter = Boolean.parseBoolean(String.valueOf(params.getOrDefault("typewriter", false)));
            String style = text(params, "style");
            double audience = optionalNumber(params, "audience", 24.0);
            HaoHanDisplayUIBridge.displayBubble(caster, new HaoHanDisplayUIBridge.BubbleOptions(text, duration, offsetY, typewriter, style, audience));
        });

        register("message", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            String msg = text(params, "msg");
            if (msg.isBlank()) msg = text(params, "message");
            if (msg.isBlank() || caster == null || caster.entity() == null) return;
            Component comp = MiniMessage.miniMessage().deserialize(msg);
            double radius = optionalNumber(params, "radius", 24.0);
            double rSq = radius * radius;
            Location loc = caster.entity().getLocation();
            if (loc != null && loc.getWorld() != null) {
                for (Player p : loc.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) <= rSq) {
                        p.sendMessage(comp);
                    }
                }
            }
        });

        register("bossbar", (context, params) -> {
            Component title = Component.text(text(params, "title"));
            float progress = (float) optionalNumber(params, "progress", 1.0);
            BossBar bar = BossBar.bossBar(title, Math.max(0.0f, Math.min(1.0f, progress)), BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            for (TargetRef ref : context.targets()) {
                if (ref.entity() instanceof Player player && player.isOnline()) {
                    player.showBossBar(bar);
                }
            }
        });

        register("playsound", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster == null || caster.entity() == null) return;
            String sound = text(params, "sound");
            float vol = (float) optionalNumber(params, "volume", 1.0);
            float pitch = (float) optionalNumber(params, "pitch", 1.0);
            double radius = optionalNumber(params, "radius", 32.0);
            String falloffStr = text(params, "falloff").toUpperCase(Locale.ROOT);
            SpatialAudioEngine.FalloffModel model;
            try { model = SpatialAudioEngine.FalloffModel.valueOf(falloffStr); } catch (Exception e) { model = SpatialAudioEngine.FalloffModel.EXPONENTIAL; }

            Location loc = caster.entity().getLocation();
            if (loc != null && loc.getWorld() != null) {
                SpatialAudioEngine.playSoundToAudience(
                        new SpatialAudioEngine.SoundStep(sound, 0, vol, pitch),
                        loc, loc.getWorld().getPlayers(), radius, model
                );
            }
        });

        register("soundsequence", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster == null || caster.entity() == null) return;
            double radius = optionalNumber(params, "radius", 32.0);
            String falloffStr = text(params, "falloff").toUpperCase(Locale.ROOT);
            SpatialAudioEngine.FalloffModel model;
            try { model = SpatialAudioEngine.FalloffModel.valueOf(falloffStr); } catch (Exception e) { model = SpatialAudioEngine.FalloffModel.EXPONENTIAL; }

            Object rawSounds = params.get("sounds");
            if (rawSounds instanceof List<?> soundList) {
                List<SpatialAudioEngine.SoundStep> steps = new ArrayList<>();
                for (Object item : soundList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Object sObj = itemMap.get("s");
                        if (sObj == null) sObj = itemMap.get("sound");
                        String s = sObj != null ? sObj.toString() : "";
                        int d = itemMap.containsKey("d") ? Integer.parseInt(String.valueOf(itemMap.get("d"))) : 0;
                        float v = itemMap.containsKey("v") ? Float.parseFloat(String.valueOf(itemMap.get("v"))) : 1.0f;
                        float p = itemMap.containsKey("p") ? Float.parseFloat(String.valueOf(itemMap.get("p"))) : 1.0f;
                        steps.add(new SpatialAudioEngine.SoundStep(s, d, v, p));
                    }
                }
                audioEngine.playSequence(caster.entityId(), caster.entity().getLocation(), steps, radius, model);
            }
        });

        register("stopsound", (context, params) -> {
            ActiveMob caster = context.cast().caster();
            if (caster != null) {
                audioEngine.stopSound(caster.entityId());
            }
        });

        // Display Entities Orchestration (P17-1)
        register("spawndisplay", (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            UUID casterId = caster != null ? caster.entityId() : null;
            Location origin = resolveOriginLocation(context, caster);
            if (origin == null) return;

            String typeStr = text(params, "type").toUpperCase(Locale.ROOT);
            DisplayType type = DisplayType.BLOCK;
            try { type = DisplayType.valueOf(typeStr); } catch (Exception ignored) {}

            String blockStr = text(params, "block");
            Material mat = Material.CRYING_OBSIDIAN;
            if (!blockStr.isBlank()) {
                try { mat = Material.valueOf(blockStr.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
            }

            int duration = (int) optionalNumber(params, "duration", 100);
            int interpolation = (int) optionalNumber(params, "interpolation", 0);
            int delay = (int) optionalNumber(params, "delay", 0);
            String text = text(params, "text");

            float sx = 1.0f, sy = 1.0f, sz = 1.0f;
            if (params.containsKey("scale")) {
                String scaleVal = params.get("scale").toString();
                if (scaleVal.contains(",")) {
                    String[] parts = scaleVal.split(",");
                    if (parts.length >= 3) {
                        try {
                            sx = Float.parseFloat(parts[0].trim());
                            sy = Float.parseFloat(parts[1].trim());
                            sz = Float.parseFloat(parts[2].trim());
                        } catch (Exception ignored) {}
                    }
                } else {
                    try {
                        float s = Float.parseFloat(scaleVal.trim());
                        sx = s; sy = s; sz = s;
                    } catch (Exception ignored) {}
                }
            }

            float tx = 0.0f, ty = 0.0f, tz = 0.0f;
            if (params.containsKey("translation")) {
                String transVal = params.get("translation").toString();
                String[] parts = transVal.split(",");
                if (parts.length >= 3) {
                    try {
                        tx = Float.parseFloat(parts[0].trim());
                        ty = Float.parseFloat(parts[1].trim());
                        tz = Float.parseFloat(parts[2].trim());
                    } catch (Exception ignored) {}
                }
            }

            float pitch = 0.0f, yaw = 0.0f, roll = 0.0f;
            if (params.containsKey("rotation")) {
                String rotVal = params.get("rotation").toString();
                String[] parts = rotVal.split(",");
                if (parts.length >= 3) {
                    try {
                        pitch = Float.parseFloat(parts[0].trim());
                        yaw = Float.parseFloat(parts[1].trim());
                        roll = Float.parseFloat(parts[2].trim());
                    } catch (Exception ignored) {}
                }
            }

            DisplaySpawnOptions options = DisplaySpawnOptions.builder(type)
                    .block(mat)
                    .text(text.isBlank() ? null : text)
                    .scale(sx, sy, sz)
                    .translation(tx, ty, tz)
                    .rotation(pitch, yaw, roll)
                    .duration(duration)
                    .interpolation(interpolation, delay)
                    .build();

            long tick = context.cast() != null ? context.cast().startedAtTick() : 0L;
            displayEntityManager.spawnDisplay(origin, options, tick, casterId);
        });

        register("displaytransform", (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            if (caster == null) return;

            float sx = 1.0f, sy = 1.0f, sz = 1.0f;
            if (params.containsKey("scale")) {
                String scaleVal = params.get("scale").toString();
                if (scaleVal.contains(",")) {
                    String[] parts = scaleVal.split(",");
                    if (parts.length >= 3) {
                        try {
                            sx = Float.parseFloat(parts[0].trim());
                            sy = Float.parseFloat(parts[1].trim());
                            sz = Float.parseFloat(parts[2].trim());
                        } catch (Exception ignored) {}
                    }
                } else {
                    try {
                        float s = Float.parseFloat(scaleVal.trim());
                        sx = s; sy = s; sz = s;
                    } catch (Exception ignored) {}
                }
            }

            float tx = 0.0f, ty = 0.0f, tz = 0.0f;
            if (params.containsKey("translation")) {
                String transVal = params.get("translation").toString();
                String[] parts = transVal.split(",");
                if (parts.length >= 3) {
                    try {
                        tx = Float.parseFloat(parts[0].trim());
                        ty = Float.parseFloat(parts[1].trim());
                        tz = Float.parseFloat(parts[2].trim());
                    } catch (Exception ignored) {}
                }
            }

            float pitch = 0.0f, yaw = 0.0f, roll = 0.0f;
            if (params.containsKey("rotation")) {
                String rotVal = params.get("rotation").toString();
                String[] parts = rotVal.split(",");
                if (parts.length >= 3) {
                    try {
                        pitch = Float.parseFloat(parts[0].trim());
                        yaw = Float.parseFloat(parts[1].trim());
                        roll = Float.parseFloat(parts[2].trim());
                    } catch (Exception ignored) {}
                }
            }

            int interpolation = (int) optionalNumber(params, "interpolation", 20);
            int delay = (int) optionalNumber(params, "delay", 0);

            DisplayTransformOptions options = DisplayTransformOptions.builder()
                    .scale(sx, sy, sz)
                    .translation(tx, ty, tz)
                    .rotation(pitch, yaw, roll)
                    .interpolation(interpolation, delay)
                    .build();

            for (ActiveDisplaySession session : displayEntityManager.getActiveSessions().values()) {
                if (caster.entityId().equals(session.getCasterId())) {
                    displayEntityManager.transformDisplay(session.getSessionId(), options);
                }
            }
        });

        // Geometric Particle Choreography (P17-2)
        var helixHandler = (Mechanic) (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            Location origin = resolveOriginLocation(context, caster);
            if (origin == null) return;

            Particle particle = parseParticle(params, Particle.FLAME);
            double radius = optionalNumber(params, "radius", 2.0);
            double height = optionalNumber(params, "height", 4.0);
            int points = (int) optionalNumber(params, "points", 40);
            double rotations = optionalNumber(params, "rotations", 2.0);
            long tick = context.cast() != null ? context.cast().startedAtTick() : 0L;

            particleChoreographer.spawnHelix(origin, particle, radius, height, points, rotations, tick);
        };
        register("effect:helix", helixHandler);
        register("helix", helixHandler);

        var ringHandler = (Mechanic) (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            Location origin = resolveOriginLocation(context, caster);
            if (origin == null) return;

            Particle particle = parseParticle(params, Particle.END_ROD);
            double radius = optionalNumber(params, "radius", 3.0);
            int points = (int) optionalNumber(params, "points", 32);
            long tick = context.cast() != null ? context.cast().startedAtTick() : 0L;

            particleChoreographer.spawnRing(origin, particle, radius, points, tick);
        };
        register("effect:ring", ringHandler);
        register("ring", ringHandler);

        var polygonHandler = (Mechanic) (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            Location origin = resolveOriginLocation(context, caster);
            if (origin == null) return;

            Particle particle = parseParticle(params, Particle.CRIT);
            int sides = (int) optionalNumber(params, "sides", 5);
            double radius = optionalNumber(params, "radius", 3.0);
            int pps = (int) optionalNumber(params, "points", 8);
            long tick = context.cast() != null ? context.cast().startedAtTick() : 0L;

            particleChoreographer.spawnPolygon(origin, particle, sides, radius, pps, tick);
        };
        register("effect:polygon", polygonHandler);
        register("polygon", polygonHandler);

        var lineHandler = (Mechanic) (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            Location origin = resolveOriginLocation(context, caster);
            if (origin == null) return;

            Particle particle = parseParticle(params, Particle.ELECTRIC_SPARK);
            double length = optionalNumber(params, "length", 10.0);
            int points = (int) optionalNumber(params, "points", 20);
            Vector dir = origin.getDirection();
            long tick = context.cast() != null ? context.cast().startedAtTick() : 0L;

            particleChoreographer.spawnLine(origin, dir, particle, length, points, tick);
        };
        register("effect:line", lineHandler);
        register("line", lineHandler);

        var arcHandler = (Mechanic) (context, params) -> {
            ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
            Location origin = resolveOriginLocation(context, caster);
            if (origin == null) return;

            Location dest = null;
            if (!context.targets().isEmpty()) {
                TargetRef ref = context.targets().get(0);
                dest = ref.location() != null ? ref.location() : (ref.entity() != null ? ref.entity().getLocation() : null);
            }
            if (dest == null) {
                dest = origin.clone().add(origin.getDirection().multiply(10.0));
            }

            Particle particle = parseParticle(params, Particle.SOUL_FIRE_FLAME);
            double height = optionalNumber(params, "height", 3.0);
            int points = (int) optionalNumber(params, "points", 25);
            long tick = context.cast() != null ? context.cast().startedAtTick() : 0L;

            particleChoreographer.spawnArc(origin, dest, particle, height, points, tick);
        };
        register("effect:arc", arcHandler);
        register("arc", arcHandler);
    }

    private static Location resolveOriginLocation(MechanicContext context, ActiveMob caster) {
        if (!context.targets().isEmpty()) {
            TargetRef ref = context.targets().get(0);
            if (ref.location() != null) return ref.location();
            if (ref.entity() != null) return ref.entity().getLocation();
        }
        if (caster != null && caster.entity() != null) {
            return caster.entity().getLocation();
        }
        return null;
    }

    private static Particle parseParticle(Map<String, Object> params, Particle fallback) {
        String name = text(params, "particle");
        if (name.isBlank()) return fallback;
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<Location> locations(List<TargetRef> targets) {
        return targets.stream().map(TargetRef::location)
                .filter(Objects::nonNull).map(Location::clone).toList();
    }

    private static boolean valid(Entity entity) { return entity.isValid() && !(entity instanceof LivingEntity living && living.isDead()); }
    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private double boundedNumber(MechanicContext context, TargetRef target, Map<String, Object> params, String key, double max) {
        Object value = params.get(key);
        double num;
        if (value instanceof Number number) {
            num = number.doubleValue();
        } else if (value != null) {
            String str = value.toString().trim();
            LivingEntity targetEntity = (target != null && target.entity() instanceof LivingEntity living) ? living : null;
            ActiveMob caster = context != null && context.cast() != null ? context.cast().caster() : null;
            SkillCastContext castCtx = context != null ? context.cast() : null;
            num = SafeExpressionEvaluator.evaluateWithPlaceholders(str, caster, targetEntity, castCtx, variableManager, Double.NaN);
        } else {
            throw new IllegalArgumentException(key + " must be between 0 and " + max);
        }
        if (!Double.isFinite(num) || num < 0 || num > max) {
            throw new IllegalArgumentException(key + " must be between 0 and " + max);
        }
        return num;
    }

    private double boundedNumber(Map<String, Object> params, String key, double max) {
        return boundedNumber(null, null, params, key, max);
    }

    private double optionalNumber(MechanicContext context, TargetRef target, Map<String, Object> params, String key, double fallback) {
        Object value = params.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) {
            return Double.isFinite(number.doubleValue()) ? number.doubleValue() : fallback;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) return fallback;
        LivingEntity targetEntity = (target != null && target.entity() instanceof LivingEntity living) ? living : null;
        ActiveMob caster = context != null && context.cast() != null ? context.cast().caster() : null;
        SkillCastContext castCtx = context != null ? context.cast() : null;
        return SafeExpressionEvaluator.evaluateWithPlaceholders(str, caster, targetEntity, castCtx, variableManager, fallback);
    }

    private double optionalNumber(Map<String, Object> params, String key, double fallback) {
        return optionalNumber(null, null, params, key, fallback);
    }

    private int boundedInt(MechanicContext context, TargetRef target, Map<String, Object> params, String key, int min, int max) {
        Object value = params.get(key);
        double num;
        if (value instanceof Number number) {
            num = number.doubleValue();
        } else if (value != null) {
            String str = value.toString().trim();
            LivingEntity targetEntity = (target != null && target.entity() instanceof LivingEntity living) ? living : null;
            ActiveMob caster = context != null && context.cast() != null ? context.cast().caster() : null;
            SkillCastContext castCtx = context != null ? context.cast() : null;
            num = SafeExpressionEvaluator.evaluateWithPlaceholders(str, caster, targetEntity, castCtx, variableManager, Double.NaN);
        } else {
            throw new IllegalArgumentException(key + " is out of range");
        }
        int intVal = (int) Math.round(num);
        if (!Double.isFinite(num) || intVal < min || intVal > max) {
            throw new IllegalArgumentException(key + " is out of range");
        }
        return intVal;
    }

    private int boundedInt(Map<String, Object> params, String key, int min, int max) {
        return boundedInt(null, null, params, key, min, max);
    }

    private Vector vector(MechanicContext context, TargetRef target, Map<String, Object> params) {
        return new Vector(
                boundedAxis(context, target, params.get("x")),
                boundedAxis(context, target, params.get("y")),
                boundedAxis(context, target, params.get("z"))
        );
    }

    private Vector vector(Map<String, Object> params) {
        return vector(null, null, params);
    }

    private double boundedAxis(MechanicContext context, TargetRef target, Object raw) {
        if (raw == null) return 0.0;
        if (raw instanceof Number number) {
            return Math.max(-10.0, Math.min(10.0, number.doubleValue()));
        }
        String str = raw.toString().trim();
        if (str.isEmpty()) return 0.0;
        LivingEntity targetEntity = (target != null && target.entity() instanceof LivingEntity living) ? living : null;
        ActiveMob caster = context != null && context.cast() != null ? context.cast().caster() : null;
        SkillCastContext castCtx = context != null ? context.cast() : null;
        double val = SafeExpressionEvaluator.evaluateWithPlaceholders(str, caster, targetEntity, castCtx, variableManager, 0.0);
        return Math.max(-10.0, Math.min(10.0, val));
    }

    private double boundedAxis(Object raw) {
        return boundedAxis(null, null, raw);
    }

    private void applyCCToTargets(MechanicContext context, CCState state, int duration, int priority, double intensity) {
        ActiveMob caster = context.cast() != null ? context.cast().caster() : null;
        UUID casterId = caster != null ? caster.entityId() : (context.cast() != null ? context.cast().casterId() : null);

        List<TargetRef> targets = context.targets();
        if (targets == null || targets.isEmpty()) {
            if (caster != null) {
                caster.crowdControl().apply(state, duration, casterId, priority, intensity);
            }
            return;
        }

        for (TargetRef ref : targets) {
            if (ref.entity() != null) {
                UUID targetId = ref.entity().getUniqueId();
                ActiveMob targetMob = mobManager != null ? mobManager.get(targetId) : null;
                if (targetMob != null) {
                    targetMob.crowdControl().apply(state, duration, casterId, priority, intensity);
                } else if (caster != null && targetId.equals(caster.entityId())) {
                    caster.crowdControl().apply(state, duration, casterId, priority, intensity);
                }
            }
        }
    }

    private static String normalize(String id) {
        Objects.requireNonNull(id, "Mechanic ID must not be null");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Mechanic ID must not be blank");
        }
        return normalized;
    }
}
