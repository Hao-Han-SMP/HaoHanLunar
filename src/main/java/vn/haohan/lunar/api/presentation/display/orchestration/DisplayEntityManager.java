package vn.haohan.lunar.core.presentation.display.orchestration;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates Minecraft 1.21 Display Entities (BlockDisplay, ItemDisplay, TextDisplay)
 * for skill choreography, boss visual transformations, and guaranteed lifecycle cleanup.
 */
public final class DisplayEntityManager {

    private final Map<UUID, ActiveDisplaySession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> casterSessions = new ConcurrentHashMap<>();
    private DisplayEntityHandler entityHandler = new DefaultDisplayEntityHandler();

    public interface DisplayEntityHandler {
        Display spawn(Location location, DisplaySpawnOptions options);
        void transform(Display display, DisplayTransformOptions options);
        void remove(Display display);
    }

    public void setEntityHandler(DisplayEntityHandler handler) {
        this.entityHandler = handler != null ? handler : new DefaultDisplayEntityHandler();
    }

    public UUID spawnDisplay(Location location, DisplaySpawnOptions options, long currentTick, UUID casterId) {
        Objects.requireNonNull(options, "DisplaySpawnOptions must not be null");
        UUID sessionId = UUID.randomUUID();

        Display display = null;
        UUID entityUuid = sessionId;
        try {
            if (location != null) {
                display = entityHandler.spawn(location, options);
                if (display != null) {
                    entityUuid = display.getUniqueId();
                }
            }
        } catch (Throwable ignored) {}

        long expireTick = currentTick + options.durationTicks();
        ActiveDisplaySession session = new ActiveDisplaySession(sessionId, entityUuid, casterId, display, expireTick, options);
        activeSessions.put(sessionId, session);

        if (casterId != null) {
            casterSessions.computeIfAbsent(casterId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }

        return sessionId;
    }

    public boolean transformDisplay(UUID sessionId, DisplayTransformOptions options) {
        if (sessionId == null || options == null) return false;
        ActiveDisplaySession session = activeSessions.get(sessionId);
        if (session == null) return false;

        session.setCurrentTransform(options);
        Display display = session.getDisplayEntity();
        if (display != null) {
            try {
                entityHandler.transform(display, options);
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public void tick(long currentTick) {
        if (activeSessions.isEmpty()) return;

        for (ActiveDisplaySession session : activeSessions.values()) {
            if (currentTick >= session.getExpireTick()) {
                despawnSession(session.getSessionId());
            }
        }
    }

    public boolean despawnSession(UUID sessionId) {
        if (sessionId == null) return false;
        ActiveDisplaySession session = activeSessions.remove(sessionId);
        if (session == null) return false;

        if (session.getCasterId() != null) {
            Set<UUID> set = casterSessions.get(session.getCasterId());
            if (set != null) {
                set.remove(sessionId);
                if (set.isEmpty()) casterSessions.remove(session.getCasterId());
            }
        }

        Display display = session.getDisplayEntity();
        if (display != null) {
            try {
                entityHandler.remove(display);
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public void cleanupMob(UUID casterId) {
        if (casterId == null) return;
        Set<UUID> sessions = casterSessions.remove(casterId);
        if (sessions == null) return;
        for (UUID sid : sessions) {
            despawnSession(sid);
        }
    }

    public void cleanupAll() {
        for (UUID sid : activeSessions.keySet()) {
            despawnSession(sid);
        }
        activeSessions.clear();
        casterSessions.clear();
    }

    public Optional<ActiveDisplaySession> getSession(UUID sessionId) {
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    public Map<UUID, ActiveDisplaySession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    public int activeCount() {
        return activeSessions.size();
    }

    private static class DefaultDisplayEntityHandler implements DisplayEntityHandler {
        @Override
        public Display spawn(Location location, DisplaySpawnOptions options) {
            World world = location.getWorld();
            if (world == null) return null;

            Transformation transformation = new Transformation(
                    options.translation(),
                    options.leftRotation(),
                    options.scale(),
                    options.rightRotation()
            );

            Display created = switch (options.type()) {
                case BLOCK -> world.spawn(location, BlockDisplay.class, display -> {
                    Material mat = options.blockMaterial() != null ? options.blockMaterial() : Material.CRYING_OBSIDIAN;
                    display.setBlock(mat.createBlockData());
                    applyCommonDisplay(display, transformation, options);
                });
                case ITEM -> world.spawn(location, ItemDisplay.class, display -> {
                    if (options.itemStack() != null) {
                        display.setItemStack(options.itemStack());
                    } else if (options.blockMaterial() != null) {
                        display.setItemStack(new ItemStack(options.blockMaterial()));
                    }
                    applyCommonDisplay(display, transformation, options);
                });
                case TEXT -> world.spawn(location, TextDisplay.class, display -> {
                    if (options.text() != null) {
                        display.text(Component.text(options.text()));
                    }
                    applyCommonDisplay(display, transformation, options);
                });
            };

            return created;
        }

        private void applyCommonDisplay(Display display, Transformation transformation, DisplaySpawnOptions options) {
            display.setTransformation(transformation);
            display.setInterpolationDuration(options.interpolationDuration());
            display.setInterpolationDelay(options.interpolationDelay());
            display.setBillboard(options.billboard());
        }

        @Override
        public void transform(Display display, DisplayTransformOptions options) {
            Transformation transformation = new Transformation(
                    options.translation(),
                    options.leftRotation(),
                    options.scale(),
                    options.rightRotation()
            );
            display.setTransformation(transformation);
            display.setInterpolationDuration(options.interpolationDuration());
            display.setInterpolationDelay(options.interpolationDelay());
        }

        @Override
        public void remove(Display display) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
    }
}
