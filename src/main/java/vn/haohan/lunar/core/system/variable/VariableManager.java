package vn.haohan.lunar.core.system.variable;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry and lifecycle manager for scoped variables in HaoHanLunar.
 * Supports PDC persistence for CASTER / TARGET variables.
 */
public final class VariableManager implements Listener {

    private static final String PDC_PREFIX = "var_";
    private final VariableHolder globalVariables = new VariableHolder();
    private final Map<UUID, VariableHolder> playerVariables = new ConcurrentHashMap<>();
    private final Map<UUID, VariableHolder> casterVariables = new ConcurrentHashMap<>();

    public VariableHolder getGlobal() {
        return globalVariables;
    }

    public VariableHolder getPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return new VariableHolder();
        }
        return playerVariables.computeIfAbsent(playerUuid, k -> new VariableHolder());
    }

    public VariableHolder getCaster(UUID casterUuid) {
        if (casterUuid == null) {
            return new VariableHolder();
        }
        return casterVariables.computeIfAbsent(casterUuid, k -> new VariableHolder());
    }

    public Optional<VariableHolder> findTarget(LivingEntity target) {
        if (target == null) {
            return Optional.empty();
        }
        if (target instanceof Player player) {
            return Optional.of(getPlayer(player.getUniqueId()));
        }
        return Optional.of(getCaster(target.getUniqueId()));
    }

    public void cleanupPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            playerVariables.remove(playerUuid);
        }
    }

    public void cleanupCaster(UUID casterUuid) {
        if (casterUuid != null) {
            casterVariables.remove(casterUuid);
        }
    }

    public void clearAll() {
        globalVariables.clear();
        playerVariables.clear();
        casterVariables.clear();
    }

    public Optional<VariableValue> resolve(VariableScope scope, String varName,
                                           ActiveMob caster, LivingEntity target,
                                           SkillCastContext context) {
        if (scope == null || varName == null || varName.isBlank()) {
            return Optional.empty();
        }
        return switch (scope) {
            case GLOBAL -> globalVariables.get(varName);
            case CASTER -> caster != null ? getCaster(caster.entityId()).get(varName) : Optional.empty();
            case TARGET -> findTarget(target).flatMap(h -> h.get(varName));
            case CAST -> context != null ? context.castVariables().get(varName) : Optional.empty();
        };
    }

    /**
     * Persists a variable into the entity's PersistentDataContainer.
     */
    public void saveToPdc(LivingEntity entity, String varName, VariableValue value) {
        if (entity == null || varName == null || varName.isBlank() || value == null) return;
        try {
            NamespacedKey key = new NamespacedKey("haohanlunar", PDC_PREFIX + varName.trim().toLowerCase(Locale.ROOT));
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(key, PersistentDataType.STRING, value.type().name() + ":" + value.asString());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Loads a variable from the entity's PersistentDataContainer.
     */
    public Optional<VariableValue> loadFromPdc(LivingEntity entity, String varName) {
        if (entity == null || varName == null || varName.isBlank()) return Optional.empty();
        try {
            NamespacedKey key = new NamespacedKey("haohanlunar", PDC_PREFIX + varName.trim().toLowerCase(Locale.ROOT));
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            String raw = pdc.get(key, PersistentDataType.STRING);
            if (raw == null || !raw.contains(":")) return Optional.empty();
            int idx = raw.indexOf(':');
            String typeStr = raw.substring(0, idx);
            String valStr = raw.substring(idx + 1);
            VariableType type = VariableType.valueOf(typeStr);
            return Optional.of(switch (type) {
                case INT -> VariableValue.of(Integer.parseInt(valStr));
                case FLOAT -> VariableValue.of(Float.parseFloat(valStr));
                case DOUBLE -> VariableValue.of(Double.parseDouble(valStr));
                case BOOLEAN -> VariableValue.of(Boolean.parseBoolean(valStr));
                default -> VariableValue.of(valStr);
            });
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event != null && event.getPlayer() != null) {
            cleanupPlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event != null && event.getEntity() != null) {
            cleanupCaster(event.getEntity().getUniqueId());
        }
    }
}
