package vn.haohan.lunar.api.system.loot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.event.LootGenerateEvent;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.manager.LootManager;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.mob.equipment.EquipmentApplier;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.api.system.loot.instanced.InstancedDropTracker;
import vn.haohan.lunar.api.system.loot.pity.PityManager;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages loot drop tables and handles loot generation when an ActiveMob dies.
 * Supports Per-Player Instanced Drops, Lootsplosion, Item Glowing, Particle Beams,
 * Experience Orbs, Recursive Sub-Tables, and firing LootGenerateEvent for external interception/customization.
 */
public final class DropManager implements LootManager, Listener {

    private final MobDefinitionRegistry mobDefinitions;
    private final LunarMobManager mobManager;
    private final HaoHanItemBridge itemBridge;
    private final ConditionRegistry conditionRegistry;
    private final PityManager pityManager = new PityManager();
    private final InstancedDropTracker instancedDropTracker = new InstancedDropTracker();
    private final Map<String, DropTableDefinition> dropTables = new ConcurrentHashMap<>();

    public DropManager() {
        this.mobDefinitions = new MobDefinitionRegistry();
        this.mobManager = null;
        this.itemBridge = HaoHanItemBridge.get();
        this.conditionRegistry = new ConditionRegistry();
    }

    public DropManager(MobDefinitionRegistry mobDefinitions,
                       LunarMobManager mobManager,
                       HaoHanItemBridge itemBridge,
                       ConditionRegistry conditionRegistry) {
        this.mobDefinitions = mobDefinitions;
        this.mobManager = mobManager;
        this.itemBridge = itemBridge != null ? itemBridge : HaoHanItemBridge.get();
        this.conditionRegistry = conditionRegistry != null ? conditionRegistry : new ConditionRegistry();
    }

    public PityManager pityManager() {
        return pityManager;
    }

    public InstancedDropTracker instancedTracker() {
        return instancedDropTracker;
    }

    public void register(DropTableDefinition table) {
        Objects.requireNonNull(table, "Drop table must not be null");
        dropTables.put(normalize(table.id()), table);
    }

    public Optional<DropTableDefinition> get(String tableId) {
        if (tableId == null || tableId.isBlank()) return Optional.empty();
        return Optional.ofNullable(dropTables.get(normalize(tableId)));
    }

    public boolean hasTable(String tableId) {
        if (tableId == null || tableId.isBlank()) return false;
        return dropTables.containsKey(normalize(tableId));
    }

    public boolean unregister(String tableId) {
        if (tableId == null || tableId.isBlank()) return false;
        return dropTables.remove(normalize(tableId)) != null;
    }

    public void replaceAll(Collection<DropTableDefinition> tables) {
        Objects.requireNonNull(tables, "Drop tables must not be null");
        Map<String, DropTableDefinition> newTables = new ConcurrentHashMap<>();
        for (DropTableDefinition table : tables) {
            newTables.put(normalize(table.id()), table);
        }
        dropTables.clear();
        dropTables.putAll(newTables);
    }

    public Map<String, DropTableDefinition> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dropTables));
    }

    /**
     * Resolves loot for an active mob death using the specified random generator.
     * Fires LootGenerateEvent for external listeners to inspect, modify, or cancel drops.
     * Supports per-player instanced drops if configured in the drop table options.
     */
    public List<ItemStack> handleDeathDrops(ActiveMob mob,
                                            LivingEntity killer,
                                            Location deathLocation,
                                            Random random) {
        if (mob == null || deathLocation == null) {
            return List.of();
        }

        Optional<MobDefinition> definitionOpt = mobDefinitions != null ? mobDefinitions.get(mob.definitionId()) : Optional.empty();
        String dropTableId = definitionOpt.flatMap(MobDefinition::dropTableReference).or(() -> mob.definition() != null ? mob.definition().dropTableReference() : Optional.empty()).orElse(null);

        DropTableDefinition table = dropTableId != null ? get(dropTableId).orElse(null) : null;
        DropOptions opts = table != null ? table.options() : DropOptions.DEFAULT;
        List<ItemStack> totalSpawned = new ArrayList<>();

        if (table != null) {
            if (opts.dropsPerPlayer()) {
                ThreatTable threat = mob.threatTable();
                double totalThreat = threat.totalThreat();
                double minPercent = opts.dropsPerPlayerRequiredDamagePercent();

                for (Map.Entry<UUID, Double> entry : threat.snapshot().entrySet()) {
                    double threatScore = entry.getValue() != null ? entry.getValue() : 0.0;
                    double percent = totalThreat > 0 ? (threatScore / totalThreat) * 100.0 : 100.0;
                    if (percent >= minPercent) {
                        Player p = null;
                        try {
                            p = Bukkit.getPlayer(entry.getKey());
                        }
                        catch (Throwable ignored) {
                        }
                        LivingEntity contributor = p != null ? p : killer;
                        DropMetadata meta = DropMetadata.of(mob, contributor, deathLocation);
                        DropRollResult rollResult = table.rollComposite(meta, random, itemBridge, conditionRegistry, pityManager, this::get, 0);
                        List<ItemStack> pDrops = rollResult.items();

                        if (rollResult.experience() > 0 && deathLocation.getWorld() != null) {
                            try {
                                deathLocation.getWorld().spawn(deathLocation, ExperienceOrb.class, orb -> orb.setExperience(rollResult.experience()));
                            } catch (Throwable ignored) {}
                        }

                        if (!pDrops.isEmpty()) {
                            Player recipient = p != null ? p : (contributor instanceof Player cp ? cp : (killer instanceof Player kp ? kp : null));
                            List<ItemStack> finalDrops = pDrops;
                            try {
                                LootGenerateEvent lootEvent = new LootGenerateEvent(mob, recipient, pDrops);
                                if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                                    Bukkit.getPluginManager().callEvent(lootEvent);
                                    if (lootEvent.isCancelled()) {
                                        continue;
                                    }
                                    finalDrops = lootEvent.drops();
                                }
                            } catch (Throwable ignored) {}

                            if (!finalDrops.isEmpty()) {
                                spawnDrops(finalDrops, deathLocation, opts, random, contributor != null ? contributor.getUniqueId() : null);
                                totalSpawned.addAll(finalDrops);
                            }
                        }
                    }
                }
            } else {
                DropMetadata metadata = DropMetadata.of(mob, killer, deathLocation);
                DropRollResult rollResult = table.rollComposite(metadata, random, itemBridge, conditionRegistry, pityManager, this::get, 0);
                List<ItemStack> drops = rollResult.items();

                if (rollResult.experience() > 0 && deathLocation.getWorld() != null) {
                    try {
                        deathLocation.getWorld().spawn(deathLocation, ExperienceOrb.class, orb -> orb.setExperience(rollResult.experience()));
                    } catch (Throwable ignored) {}
                }

                if (!drops.isEmpty()) {
                    Player recipient = killer instanceof Player kp ? kp : null;
                    List<ItemStack> finalDrops = drops;
                    try {
                        LootGenerateEvent lootEvent = new LootGenerateEvent(mob, recipient, drops);
                        if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                            Bukkit.getPluginManager().callEvent(lootEvent);
                            if (lootEvent.isCancelled()) {
                                finalDrops = List.of();
                            } else {
                                finalDrops = lootEvent.drops();
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (!finalDrops.isEmpty()) {
                        spawnDrops(finalDrops, deathLocation, opts, random, killer != null ? killer.getUniqueId() : null);
                        totalSpawned.addAll(finalDrops);
                    }
                }
            }
        }

        if (mob.definition() != null && mob.definition().equipment() != null && !mob.definition().equipment().isEmpty()) {
            List<ItemStack> eqDrops = EquipmentApplier.resolveEquipmentDrops(mob.definition().equipment(), null);
            if (!eqDrops.isEmpty()) {
                spawnDrops(eqDrops, deathLocation, opts, random, killer != null ? killer.getUniqueId() : null);
                totalSpawned.addAll(eqDrops);
            }
        }

        return List.copyOf(totalSpawned);
    }

    private void spawnDrops(List<ItemStack> drops, Location loc, DropOptions opts, Random random, UUID ownerUuid) {
        if (loc == null || loc.getWorld() == null || drops == null || drops.isEmpty()) return;

        for (ItemStack drop : drops) {
            try {
                Item itemEntity = loc.getWorld().dropItemNaturally(loc, drop);
                if (opts.dropsGlowByDefault()) {
                    itemEntity.setGlowing(true);
                }
                if (opts.dropsDoLootsplosion()) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double speed = 0.2 + random.nextDouble() * 0.3;
                    double vx = Math.cos(angle) * speed;
                    double vz = Math.sin(angle) * speed;
                    double vy = 0.35 + random.nextDouble() * 0.2;
                    itemEntity.setVelocity(new Vector(vx, vy, vz));
                }
                if (opts.dropsHaveBeamByDefault()) {
                    spawnItemBeam(itemEntity);
                }
                if (ownerUuid != null) {
                    long curTick = 0L;
                    try {
                        curTick = Bukkit.getCurrentTick();
                    } catch (Throwable ignored) {}
                    instancedDropTracker.protect(itemEntity.getUniqueId(), ownerUuid, curTick);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void spawnItemBeam(Item itemEntity) {
        if (itemEntity == null) return;
        try {
            HaoHanLunarPlugin plugin = HaoHanLunarPlugin.getInstance();
            if (plugin == null || !plugin.isEnabled()) {
                renderBeamColumn(itemEntity.getLocation());
                return;
            }
            new BukkitRunnable() {
                private int ticksRemaining = 60; // Max 60 seconds (60 repeats @ 20 ticks)

                @Override
                public void run() {
                    if (!itemEntity.isValid() || itemEntity.isDead() || --ticksRemaining <= 0) {
                        cancel();
                        return;
                    }
                    renderBeamColumn(itemEntity.getLocation());
                }
            }.runTaskTimer(plugin, 0L, 20L);
        } catch (Throwable ignored) {
            renderBeamColumn(itemEntity.getLocation());
        }
    }

    private void renderBeamColumn(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        for (double dy = 0.5; dy <= 10.0; dy += 0.8) {
            world.spawnParticle(Particle.END_ROD, x, y + dy, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        ActiveMob mob = mobManager != null ? mobManager.get(entity.getUniqueId()) : null;
        if (mob == null) {
            return;
        }

        // Despawn guard: do not drop loot if entity despawned rather than died from combat
        if (entity.getHealth() > 0.0 && entity.getLastDamageCause() == null) {
            return;
        }

        // Prevent vanilla drops if custom mob options request it
        if (mob.options() != null && mob.options().preventOtherDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        Player killer = entity.getKiller();
        handleDeathDrops(mob, killer, entity.getLocation(), ThreadLocalRandom.current());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Item item = event.getItem();
        if (item == null) return;

        long currentTick = 0L;
        try {
            currentTick = Bukkit.getCurrentTick();
        } catch (Throwable ignored) {}

        if (!instancedDropTracker.canPickup(player.getUniqueId(), item.getUniqueId(), currentTick)) {
            event.setCancelled(true);
        }
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    // --- LootManager API Implementation ---
    @Override
    public boolean hasDropTable(String dropTableId) {
        return hasTable(dropTableId);
    }

    @Override
    public List<ItemStack> generateDrops(String dropTableId, LivingEntity victim, Player killer, double luckBonus) {
        DropTableDefinition table = get(dropTableId).orElse(null);
        if (table == null) return List.of();
        ActiveMob mob = (victim != null && mobManager != null) ? mobManager.get(victim.getUniqueId()) : null;
        Location loc = victim != null ? victim.getLocation() : (killer != null ? killer.getLocation() : null);
        DropMetadata metadata = new DropMetadata(mob, killer, loc, luckBonus <= 0 ? 1.0 : luckBonus, 0L);
        return table.rollComposite(metadata, ThreadLocalRandom.current(), itemBridge, conditionRegistry, pityManager, this::get, 0).items();
    }
}
