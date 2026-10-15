package vn.haohan.lunar.features;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.block.CustomBlockService;
import vn.haohan.lunar.HaoHanLunarPlugin;

/**
 * Bridges worldgen ore features to HaoHanItemCore's CustomBlock system.
 *
 * <p>When new chunks in the lunar world are generated and loaded, this listener
 * scans for temporary ore NoteBlock states placed by the datapack feature, registers them
 * into CustomBlockStorage, transforms the physical block to BARRIER, and stamps a persistent
 * chunk flag to avoid reprocessing on subsequent loads.</p>
 */
public final class LunarOrePopulatorListener implements Listener {

    private final HaoHanLunarPlugin plugin;
    private final NamespacedKey populatedKey;

    public LunarOrePopulatorListener(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        this.populatedKey = new NamespacedKey(plugin, "ores_populated");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }

        Chunk chunk = event.getChunk();
        if (!HaoHanLunarPlugin.isLunarWorld(chunk.getWorld())) {
            return;
        }

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (pdc.has(populatedKey, PersistentDataType.BYTE)) {
            return;
        }

        // NoteBlocks are natively recognized by ItemCore via variations 771..774.
        // Chunk scanning to convert to barrier is disabled.
        pdc.set(populatedKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Scans and populates note blocks matching lunar ore representations in the given chunk.
     *
     * @deprecated NoteBlocks are now recognized natively by ItemCore as NativeCustomBlock.
     * Chunk scanning is no longer needed and is disabled.
     *
     * @param chunk the chunk to scan
     * @param cbService ItemCore custom block service
     * @return number of blocks converted
     */
    @Deprecated
    public int populateChunkOres(Chunk chunk, CustomBlockService cbService) {
        return 0;
    }

    /**
     * Resolves custom ore ID from NoteBlock state.
     */
    public static String resolveOreId(NoteBlock noteBlock) {
        if (noteBlock == null) return null;
        if (noteBlock.getInstrument() != org.bukkit.Instrument.PLING) {
            return null;
        }

        return resolveOreId(noteBlock.getInstrument(), noteBlock.getNote() != null ? noteBlock.getNote().getId() : -1);
    }

    public static String resolveOreId(org.bukkit.Instrument instrument, int noteId) {
        if (instrument == null) return null;
        return resolveOreId(instrument.name(), noteId);
    }

    public static String resolveOreId(String instrumentName, int noteId) {
        if (!"PLING".equalsIgnoreCase(instrumentName)) {
            return null;
        }

        return switch (noteId) {
            case 24 -> "haohan:anorthosite_ore";
            case 23 -> "haohan:ilmenite_ore";
            case 22 -> "haohan:pyroxene_ore";
            case 21 -> "haohan:kreep_basalt";
            default -> null;
        };
    }
}
