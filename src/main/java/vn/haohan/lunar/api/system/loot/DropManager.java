package vn.haohan.lunar.api.loot;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.manager.LootManager;
import vn.haohan.lunar.api.integration.bridge.item.HaoHanItemBridge;
import vn.haohan.lunar.api.combat.threat.ThreatTable;
import vn.haohan.lunar.api.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.loot.instanced.InstancedDropTracker;
import vn.haohan.lunar.api.loot.pity.PityManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.subsystem.mob.MobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages loot drop tables and handles loot generation when an ActiveMob dies.
 * Supports Per-Player Instanced Drops, Lootsplosion, Item Glowing, and Pity Guarantee.
 */
public final class DropManager implements LootManager, Listener {

    private final MobDefinitionRegistry mobDefinitions;
    private final MobManager mobManager;
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
                       MobManager mobManager,
                       HaoHanItemBridge itemBridge,
                       ConditionRegistry conditionRegistry) {
        this.mobDefinitions = Objects.requireNonNull(mobDefinitions, "Mob definitions must not be null");
        this.mobManager = Objects.requireNonNull(mobManager, "Mob manager must not be null");
        this.itemBridge = Objects.requireNonNull(itemBridge, "Item bridge must not be null");
        this.conditionRegistry = conditionRegistry != null ? conditionRegistry : new ConditionRegistry();
    }

    public PityManager pityManager() {
        return pityManager;
    }

    public InstancedDropTracker instancedDropTracker() {
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
     * Supports per-player drops if configured in the drop table options.
     */
    public List<ItemStack> handleDeathDrops(ActiveMob mob,
                                            LivingEntity killer,
                                            Location deathLocation,
                                            Random random) {
        if (mob == null || deathLocation == null) {
            return List.of();
        }

        Optional<MobDefinition> definitionOpt = mobDefinitions.get(mob.definitionId());
        String dropTableId = definitionOpt.flatMap(MobDefinition::dropTableReference).orElse(null);
        if (dropTableId == null) {
            return List.of();
        }

        DropTableDefinition table = get(dropTableId).orElse(null);
        if (table == null) {
            return List.of();
        }

        DropOptions opts = table.options();
        List<ItemStack> totalSpawned = new ArrayList<>();

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
                        p = org.bukkit.Bukkit.getPlayer(entry.getKey());
                    } catch (Throwable ignored) {
                    }
                    LivingEntity contributor = p != null ? p : killer;
                    DropMetadata meta = DropMetadata.of(mob, contributor, deathLocation);
                    List<ItemStack> pDrops = table.roll(meta, random, itemBridge, conditionRegistry, pityManager);
                    spawnDrops(pDrops, deathLocation, opts, random, contributor != null ? contributor.getUniqueId() : null);
                    totalSpawned.addAll(pDrops);
                }
            }
        } else {
            DropMetadata metadata = DropMetadata.of(mob, killer, deathLocation);
            List<ItemStack> drops = table.roll(metadata, random, itemBridge, conditionRegistry, pityManager);
            spawnDrops(drops, deathLocation, opts, random, killer != null ? killer.getUniqueId() : null);
            totalSpawned.addAll(drops);
        }

        return List.copyOf(totalSpawned);
    }

    private void spawnDrops(List<ItemStack> drops, Location loc, DropOptions opts, Random random, UUID ownerUuid) {
        if (loc == null || loc.getWorld() == null || drops.isEmpty()) return;

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
                if (ownerUuid != null) {
                    long curTick = 0L;
                    try {
                        curTick = org.bukkit.Bukkit.getCurrentTick();
                    } catch (Throwable ignored) {}
                    instancedDropTracker.protect(itemEntity.getUniqueId(), ownerUuid, curTick);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        ActiveMob mob = mobManager.get(entity.getUniqueId());
        if (mob == null) {
            return;
        }

        // Despawn guard: do not drop loot if entity despawned rather than died from combat
        if (entity.getHealth() > 0.0 && entity.getLastDamageCause() == null) {
            return;
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
            currentTick = org.bukkit.Bukkit.getCurrentTick();
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
        return List.of();
    }
}
