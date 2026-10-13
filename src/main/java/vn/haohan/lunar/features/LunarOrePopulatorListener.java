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

        CustomBlockService cbService;
        try {
            var core = HaoHanItemCore.get();
            if (core == null || (cbService = core.getCustomBlockService()) == null) {
                return;
            }
        } catch (Throwable t) {
            return;
        }

        int converted = populateChunkOres(chunk, cbService);
        pdc.set(populatedKey, PersistentDataType.BYTE, (byte) 1);

        if (converted > 0 && plugin != null) {
            plugin.getSLF4JLogger().debug("Populated {} lunar custom ores in chunk [{}, {}]", converted, chunk.getX(), chunk.getZ());
        }
    }

    /**
     * Scans and populates note blocks matching lunar ore representations in the given chunk.
     *
     * @param chunk the chunk to scan
     * @param cbService ItemCore custom block service
     * @return number of blocks converted
     */
    public int populateChunkOres(Chunk chunk, CustomBlockService cbService) {
        if (chunk == null || cbService == null) return 0;
        int count = 0;
        var world = chunk.getWorld();
        int minY = world != null ? world.getMinHeight() : -32;
        int maxY = world != null ? world.getMaxHeight() - 1 : 159;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.NOTE_BLOCK) {
                        continue;
                    }

                    if (block.getBlockData() instanceof NoteBlock noteBlock) {
                        String oreId = resolveOreId(noteBlock);
                        if (oreId != null) {
                            cbService.placeCustomBlock(block.getLocation(), oreId);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
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
