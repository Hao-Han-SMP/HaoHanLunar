package vn.haohan.lunar.core.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;

/** Stable identity persisted on the entity itself, independent of names and Java classes. */
public record LunarMobIdentity(String mobId, String engineVersion, Optional<String> spawnInstanceId) {

    public static final String NAMESPACE = "haohanlunar";
    private static final NamespacedKey MOB_ID_KEY = new NamespacedKey(NAMESPACE, "mob_id");
    private static final NamespacedKey ENGINE_VERSION_KEY = new NamespacedKey(NAMESPACE, "engine_version");
    private static final NamespacedKey SPAWN_INSTANCE_ID_KEY = new NamespacedKey(NAMESPACE, "spawn_instance_id");

    public LunarMobIdentity(String mobId, String engineVersion, Optional<String> spawnInstanceId) {
        this.mobId = new MobDefinitionId(mobId).value();
        this.engineVersion = requireText(engineVersion, "Engine version");
        this.spawnInstanceId = Objects.requireNonNull(spawnInstanceId, "Spawn instance ID must not be null")
                .filter(value -> !value.isBlank())
                .map(String::trim)
                .map(value -> value);
    }

    public LunarMobIdentity(String mobId, String engineVersion) {
        this(mobId, engineVersion, Optional.empty());
    }

    public static void write(LivingEntity entity, LunarMobIdentity identity) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(identity, "Identity must not be null");
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(MOB_ID_KEY, PersistentDataType.STRING, identity.mobId());
        data.set(ENGINE_VERSION_KEY, PersistentDataType.STRING, identity.engineVersion());
        identity.spawnInstanceId().ifPresentOrElse(
                value -> data.set(SPAWN_INSTANCE_ID_KEY, PersistentDataType.STRING, value),
                () -> data.remove(SPAWN_INSTANCE_ID_KEY));
    }

    public static Optional<LunarMobIdentity> read(LivingEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String mobId = data.get(MOB_ID_KEY, PersistentDataType.STRING);
        String engineVersion = data.get(ENGINE_VERSION_KEY, PersistentDataType.STRING);
        if (mobId == null || engineVersion == null) {
            return Optional.empty();
        }
        String spawnInstanceId = data.get(SPAWN_INSTANCE_ID_KEY, PersistentDataType.STRING);
        try {
            return Optional.of(new LunarMobIdentity(mobId, engineVersion, Optional.ofNullable(spawnInstanceId)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static boolean isLunarMob(LivingEntity entity) {
        return read(entity).isPresent();
    }

    public static NamespacedKey mobIdKey() {
        return MOB_ID_KEY;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
