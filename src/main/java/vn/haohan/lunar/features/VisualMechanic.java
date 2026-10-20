package vn.haohan.lunar.features;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.engine.core.service.IService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages Lunar dimension visual effects, including biome-specific ambient particles
 * and integration with SkyboxEngine for dynamic atmospheric shaders.
 */
public class VisualMechanic implements Listener, IService {

    private final HaoHanLunarPlugin plugin;
    private int tickCounter = 0;
    private int skyboxCheckCounter = 0;

    // SkyboxEngine integration settings
    private boolean skyboxEnabled = true;
    private String lunarWorldKey = HaoHanLunarPlugin.LUNAR_WORLD_KEY.toString();
    private String defaultSkybox = "lunar_space";
    private final Map<String, String> biomeSkyboxMap = new HashMap<>();

    private final Map<UUID, String> activeSkyboxes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> temporarySkyboxExpirations = new ConcurrentHashMap<>();

    public VisualMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        checkSkyboxEngineStatus();
    }

    @Override
    public String name() {
        return "Visual";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void disable(org.bukkit.plugin.Plugin plugin) {
        cleanup();
    }

    public void loadConfig() {
        biomeSkyboxMap.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("skybox");
        if (sec != null) {
            this.skyboxEnabled = sec.getBoolean("enabled", true);
            this.lunarWorldKey = sec.getString("world", HaoHanLunarPlugin.LUNAR_WORLD_KEY.toString());
            this.defaultSkybox = sec.getString("default-skybox", "lunar_space");

            ConfigurationSection biomes = sec.getConfigurationSection("biome-skyboxes");
            if (biomes != null) {
                for (String key : biomes.getKeys(false)) {
                    biomeSkyboxMap.put(key, biomes.getString(key));
                }
            }
        } else {
            // Default configuration values
            this.skyboxEnabled = true;
            this.lunarWorldKey = HaoHanLunarPlugin.LUNAR_WORLD_KEY.toString();
            this.defaultSkybox = "lunar_space";
            biomeSkyboxMap.put("haohan:lunar_terrae", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_maria", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_craters", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_crystal_craters", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_giant_crystals", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_giant_crystal_outskirts", "lunar_space");
        }
    }

    private void checkSkyboxEngineStatus() {
        if (Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
            plugin.getLogger().info("[VisualMechanic] SkyboxEngine hook detected! Atmospheric dynamic skyboxes active.");
        } else {
            plugin.getLogger().info("[VisualMechanic] SkyboxEngine is not loaded. Custom shader skyboxes will remain idle until enabled.");
        }
    }

    @Override
    public void tick() {
        tickCounter++;
        if (tickCounter >= 8) {
            tickCounter = 0;
            if (lunarWorldKey.equals(HaoHanLunarPlugin.LUNAR_WORLD_KEY.toString())) {
                World lunar = HaoHanLunarPlugin.getLunarWorld();
                if (lunar != null) {
                    for (Player player : lunar.getPlayers()) {
                        spawnBiomeParticles(player);
                    }
                }
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().getKey().toString().equals(lunarWorldKey)) {
                        spawnBiomeParticles(player);
                    }
                }
            }
        }

        // Verify and update player skyboxes on biome transitions (every 20 ticks)
        skyboxCheckCounter++;
        if (skyboxCheckCounter >= 20) {
            skyboxCheckCounter = 0;
            if (skyboxEnabled && Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
                updateAllPlayerSkyboxes();
            }
        }
    }

    private void updateAllPlayerSkyboxes() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean inLunar = player.getWorld().getKey().toString().equals(lunarWorldKey);

            if (!inLunar) {
                clearSkybox(player);
                continue;
            }

            // Handle temporary skybox overrides (skills, cutscenes, combat effects)
            Long expire = temporarySkyboxExpirations.get(uuid);
            if (expire != null) {
                if (now < expire) {
                    continue; // Temporary skybox still active
                } else {
                    temporarySkyboxExpirations.remove(uuid); // Expired
                }
            }

            // Determine target skybox based on player's current lunar biome
            NamespacedKey biomeKey = player.getLocation().getBlock().getBiome().getKey();
            String biomeId = biomeKey.toString();
            String targetSkybox = biomeSkyboxMap.getOrDefault(biomeId, defaultSkybox);

            String currentSkybox = activeSkyboxes.get(uuid);
            if (currentSkybox == null || !currentSkybox.equals(targetSkybox)) {
                applySkybox(player, targetSkybox);
            }
        }
    }

    public void applySkybox(Player player, String skyboxId) {
        if (!skyboxEnabled || player == null || !player.isOnline()) return;
        if (!player.getWorld().getKey().toString().equals(lunarWorldKey)) {
            clearSkybox(player);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) return;

        try {
            Class<?> apiClass = Class.forName("de.vectorflare.skyboxengine.SkyboxAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            if (api == null) return;

            Object data = apiClass.getMethod("getSkyboxData", Player.class).invoke(api, player);
            Object settings = apiClass.getMethod("getSkyboxSettings", String.class).invoke(api, skyboxId);
            if (data != null && settings != null) {
                data.getClass().getMethod("clearActiveSkyboxes").invoke(data);
                apiClass.getMethod("addSkyboxToPlayer", Player.class, settings.getClass()).invoke(api, player, settings);
                activeSkyboxes.put(player.getUniqueId(), skyboxId);
            }
        } catch (ClassNotFoundException e) {
            // SkyboxEngine API class not found
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[VisualMechanic] Failed to apply skybox '" + skyboxId + "' to " + player.getName(), t);
        }
    }

    public void applyTemporarySkybox(Player player, String skyboxId, long durationTicks) {
        if (!skyboxEnabled || player == null || !player.isOnline()) return;

        long expirationTime = System.currentTimeMillis() + (durationTicks * 50L);
        temporarySkyboxExpirations.put(player.getUniqueId(), expirationTime);
        applySkybox(player, skyboxId);
    }

    public void clearSkybox(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        activeSkyboxes.remove(uuid);
        temporarySkyboxExpirations.remove(uuid);

        if (!Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) return;

        try {
            Class<?> apiClass = Class.forName("de.vectorflare.skyboxengine.SkyboxAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            if (api != null) {
                Object data = apiClass.getMethod("getSkyboxData", Player.class).invoke(api, player);
                if (data != null) {
                    // Synchronously destroy renderedPlayerSkybox with 0-tick delay before clearing map
                    try {
                        java.lang.reflect.Field renderedField = data.getClass().getDeclaredField("renderedPlayerSkybox");
                        renderedField.setAccessible(true);
                        Object rendered = renderedField.get(data);
                        if (rendered != null) {
                            rendered.getClass().getMethod("removeSkybox", int.class).invoke(rendered, 0);
                            renderedField.set(data, null);
                        }
                        java.lang.reflect.Field mapField = data.getClass().getDeclaredField("playerSkyboxes");
                        mapField.setAccessible(true);
                        Object mapObj = mapField.get(data);
                        if (mapObj instanceof Map<?, ?> skyboxMap) {
                            skyboxMap.clear();
                        }
                    } catch (Throwable ignored) {}
                    data.getClass().getMethod("clearActiveSkyboxes").invoke(data);
                }
            }
        } catch (ClassNotFoundException e) {
            // SkyboxEngine API not present
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[VisualMechanic] Failed to reset skybox for " + player.getName(), t);
        }

        // Remove any orphaned SkyboxEngine ItemDisplay entities in non-lunar worlds
        if (player.isOnline() && !player.getWorld().getKey().toString().equals(lunarWorldKey)) {
            for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 32.0, 32.0, 32.0)) {
                if (entity instanceof org.bukkit.entity.ItemDisplay display) {
                    org.bukkit.inventory.ItemStack stack = display.getItemStack();
                    if (stack != null && stack.getType() == Material.LEATHER_HORSE_ARMOR && stack.hasItemMeta()) {
                        NamespacedKey modelKey = stack.getItemMeta().getItemModel();
                        if (modelKey != null && "skyboxengine".equals(modelKey.getNamespace())) {
                            display.remove();
                        }
                    }
                }
            }
        }
    }

    private void spawnBiomeParticles(Player player) {
        Location loc = player.getLocation();
        String biomeKey = loc.getBlock().getBiome().getKey().toString();

        switch (biomeKey) {
            case "haohan:lunar_terrae":
                player.spawnParticle(Particle.ASH, loc, 3, 10, 3, 10, 0.01);
                break;
            case "haohan:lunar_maria":
                var grayDust = new Particle.DustOptions(Color.fromRGB(80, 80, 80), 1.0f);
                player.spawnParticle(Particle.DUST, loc, 2, 8, 2, 8, 0.01, grayDust);
                break;
            case "haohan:lunar_craters":
                player.spawnParticle(Particle.WHITE_ASH, loc, 4, 10, 3, 10, 0.02);
                break;
            case "haohan:lunar_crystal_craters":
                var crystalCyanDust = new Particle.DustOptions(Color.fromRGB(0, 240, 255), 1.2f);
                player.spawnParticle(Particle.DUST, loc, 3, 8, 2.5, 8, 0.01, crystalCyanDust);
                player.spawnParticle(Particle.END_ROD, loc, 1, 6, 2, 6, 0.01);
                break;
            case "haohan:lunar_giant_crystals":
                var crystalPurple = new Particle.DustOptions(Color.fromRGB(180, 50, 255), 1.4f);
                var whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.0f);
                player.spawnParticle(Particle.DUST, loc, 3, 8, 3, 8, 0.01, crystalPurple);
                player.spawnParticle(Particle.DUST, loc, 2, 8, 3, 8, 0.01, whiteDust);
                player.spawnParticle(Particle.PORTAL, loc, 2, 6, 2, 6, 0.02);
                break;
            case "haohan:lunar_giant_crystal_outskirts":
                var crystalLightBlue = new Particle.DustOptions(Color.fromRGB(102, 191, 255), 1.5f);
                var crystalCyan = new Particle.DustOptions(Color.fromRGB(33, 191, 191), 1.5f);
                var whiteDust3 = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.5f);
                player.spawnParticle(Particle.DUST, loc, 2, 8, 2.5, 8, 0.01, crystalLightBlue);
                player.spawnParticle(Particle.DUST, loc, 1, 8, 2.5, 8, 0.01, crystalCyan);
                player.spawnParticle(Particle.DUST, loc, 1, 8, 2.5, 8, 0.01, whiteDust3);
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (world.getKey().toString().equals(lunarWorldKey)) {
            if (skyboxEnabled && Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && player.getWorld().getKey().toString().equals(lunarWorldKey)) {
                        NamespacedKey biomeKey = player.getLocation().getBlock().getBiome().getKey();
                        String targetSkybox = biomeSkyboxMap.getOrDefault(biomeKey.toString(), defaultSkybox);
                        applySkybox(player, targetSkybox);
                    }
                }, 10L);
            }
        } else {
            // Player joined in Overworld, Nether, or End - explicitly ensure no skybox is applied
            if (Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !player.getWorld().getKey().toString().equals(lunarWorldKey)) {
                        clearSkybox(player);
                    }
                }, 10L);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChangeWorldPre(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getKey().toString().equals(lunarWorldKey)) {
            // Clear Lunar BIOME/DIMENSION skybox entries BEFORE SkyboxEngine's NORMAL listener runs reconstructCustomSkyboxes
            clearSkybox(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        if (toWorld.getKey().toString().equals(lunarWorldKey)) {
            if (!player.getScoreboardTags().contains("lunar_visited")) {
                player.addScoreboardTag("lunar_visited");

                // Show title
                player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("\ud83c\udf19", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    Component.text("MẶT TRĂNG", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                ));

                // Play thunder sound
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.MASTER, 2.0f, 0.7f);
            }

            // Immediately apply the appropriate skybox on entering Lunar dimension
            if (skyboxEnabled && Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
                NamespacedKey biomeKey = player.getLocation().getBlock().getBiome().getKey();
                String targetSkybox = biomeSkyboxMap.getOrDefault(biomeKey.toString(), defaultSkybox);
                applySkybox(player, targetSkybox);
            }
        } else {
            player.removeScoreboardTag("lunar_visited");
            clearSkybox(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.getWorld().getKey().toString().equals(lunarWorldKey)) {
                    clearSkybox(player);
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        activeSkyboxes.remove(uuid);
        temporarySkyboxExpirations.remove(uuid);
    }

    /**
     * Clean up all skyboxes when plugin is disabled or reloaded.
     */
    public void cleanup() {
        if (!Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearSkybox(player);
        }
        activeSkyboxes.clear();
        temporarySkyboxExpirations.clear();
    }
}
