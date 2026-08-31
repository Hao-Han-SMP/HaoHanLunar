package vn.haohan.lunar.mechanics.boss.warden.util;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Robust lifecycle manager for all temporary skill entities (BlockDisplays, ItemDisplays, ArmorStands, etc.).
 * Ensures entities are NEVER saved to chunk files and are cleanly purged on reload, restart, chunk load, or crash recovery.
 */
public final class WardenEntityManager implements Listener {

    public static final String LUNAR_TEMP_TAG = "haohan_lunar_temp_entity";
    private static final Set<Entity> ACTIVE_TEMP_ENTITIES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public WardenEntityManager() {}

    /**
     * Registers a temporary entity with non-persistent flags, scoreboard tracking tags, and active lifecycle tracking.
     */
    public static <T extends Entity> T registerTempEntity(T entity) {
        if (entity == null) return null;
        try {
            entity.setPersistent(false);
            entity.addScoreboardTag(LUNAR_TEMP_TAG);
            ACTIVE_TEMP_ENTITIES.add(entity);
        } catch (Throwable ignored) {}
        return entity;
    }

    /**
     * Removes and unregisters a temporary entity.
     */
    public static void removeTempEntity(Entity entity) {
        if (entity == null) return;
        ACTIVE_TEMP_ENTITIES.remove(entity);
        try {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity);
            if (me != null) {
                me.destroy();
            }
        } catch (Throwable ignored) {}
        try {
            entity.remove();
        } catch (Throwable ignored) {}
    }

    /**
     * Purges all tracked in-memory entities and scans all worlds for orphaned temporary visual entities.
     */
    public static void purgeAllTempEntities() {
        // 1. Purge active tracked entities
        for (Entity e : ACTIVE_TEMP_ENTITIES) {
            removeTempEntity(e);
        }
        ACTIVE_TEMP_ENTITIES.clear();

        // 2. Scan all loaded entities across all worlds for orphaned tags or orphaned skill entities
        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            for (Entity entity : world.getEntities()) {
                if (entity == null || !entity.isValid()) continue;

                if (entity.getScoreboardTags().contains(LUNAR_TEMP_TAG)) {
                    removeTempEntity(entity);
                    continue;
                }

                // Also clean up any lingering unregistered BlockDisplays that were orphaned from GroundSlam
                if (entity instanceof BlockDisplay bd) {
                    if (!bd.isPersistent() || bd.getScoreboardTags().contains(LUNAR_TEMP_TAG)) {
                        bd.remove();
                    }
                } else if (entity instanceof ItemDisplay id) {
                    if (id.getScoreboardTags().contains(LUNAR_TEMP_TAG)) {
                        id.remove();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity == null || !entity.isValid()) continue;
            if (entity.getScoreboardTags().contains(LUNAR_TEMP_TAG)) {
                removeTempEntity(entity);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity != null && entity.getScoreboardTags().contains(LUNAR_TEMP_TAG)) {
                removeTempEntity(entity);
            }
        }
    }
}
