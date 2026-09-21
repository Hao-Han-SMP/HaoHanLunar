package vn.haohan.lunar.core.features;

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
import vn.haohan.lunar.core.subsystem.LunarSubSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages Lunar dimension visual effects, including biome-specific ambient particles
 * and integration with SkyboxEngine for dynamic atmospheric shaders.
 */
public class VisualMechanic implements Listener, LunarSubSystem {

    private static final String DEFAULT_LUNAR_WORLD_KEY = "haohan:lunar";

    private final HaoHanLunarPlugin plugin;
    private int tickCounter = 0;
    private int skyboxCheckCounter = 0;

    // SkyboxEngine integration settings
    private boolean skyboxEnabled = true;
    private String lunarWorldKey = DEFAULT_LUNAR_WORLD_KEY;
    private String defaultSkybox = "lunar_space";
    private final Map<String, String> biomeSkyboxMap = new HashMap<>();

    // Active state per player: currently applied skybox and expiration timestamp for temporary overrides
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
        return 45;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        cleanup();
    }

    public void loadConfig() {
        biomeSkyboxMap.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("skybox");
        if (sec != null) {
            this.skyboxEnabled = sec.getBoolean("enabled", true);
            this.lunarWorldKey = sec.getString("world", DEFAULT_LUNAR_WORLD_KEY);
            this.defaultSkybox = sec.getString("default-skybox", "lunar_space");

            ConfigurationSection biomes = sec.getConfigurationSection("biome-skyboxes");
            if (biomes != null) {
                for (String key : biomes.getKeys(false)) {
                    biomeSkyboxMap.put(key, biomes.getString(key));
                }
            }
        } else {
            // Default mappings if not explicitly defined in config
            biomeSkyboxMap.put("haohan:lunar_terrae", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_maria", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_craters", "lunar_space");
            biomeSkyboxMap.put("haohan:lunar_crystal_craters", "lunar_animated_stars");
            biomeSkyboxMap.put("haohan:lunar_giant_crystals", "lunar_void_vortex");
            biomeSkyboxMap.put("haohan:lunar_giant_crystal_outskirts", "lunar_animated_stars");
        }
    }

    public void checkSkyboxEngineStatus() {
        if (Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
            plugin.getLogger().info("[VisualMechanic] SkyboxEngine detected! Dynamic lunar skyboxes activated exclusively for " + lunarWorldKey + ".");
        } else {
            plugin.getLogger().info("[VisualMechanic] SkyboxEngine is not loaded. Custom shader skyboxes will remain idle until enabled.");
        }
    }

    @Override
    public void tick() {
        tickCounter++;
        if (tickCounter >= 8) {
            tickCounter = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getKey().toString().equals(lunarWorldKey)) {
                    spawnBiomeParticles(player);
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
                if (activeSkyboxes.containsKey(uuid)) {
                    clearSkybox(player);
                }
                continue;
            }

            // Handle temporary skybox overrides (skills, cutscenes, combat effects)
            Long expire = temporarySkyboxExpirations.get(uuid);
            if (expire != null) {
                if (now < expire) {
                    continue; // Temporary skybox still active
                } else {
                    temporarySkyboxExpirations.remove(uuid); // Expired, fall back to biome skybox
                }
            }

            // Determine target skybox based on player's current biome
            NamespacedKey biomeKey = player.getLocation().getBlock().getBiome().getKey();
            String targetSkybox = biomeSkyboxMap.getOrDefault(biomeKey.toString(), defaultSkybox);

            String current = activeSkyboxes.get(uuid);
            if (current == null || !current.equals(targetSkybox)) {
                applySkybox(player, targetSkybox);
            }
        }
    }

    /**
     * Applies a skybox shader profile to the player using SkyboxEngine.
     */
    public void applySkybox(Player player, String skyboxId) {
        if (!skyboxEnabled || player == null || !player.isOnline()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) return;
        if (skyboxId == null || skyboxId.isBlank()) return;

        String current = activeSkyboxes.get(player.getUniqueId());
        if (skyboxId.equals(current)) {
            return;
        }

        activeSkyboxes.put(player.getUniqueId(), skyboxId);
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skyboxengine enable " + player.getName() + " " + skyboxId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply skybox '" + skyboxId + "' for " + player.getName(), t);
        }
    }

    /**
     * Disables the active custom skybox for the player and resets to default environment.
     */
    public void clearSkybox(Player player) {
        if (player == null) return;
        String current = activeSkyboxes.remove(player.getUniqueId());
        temporarySkyboxExpirations.remove(player.getUniqueId());

        if (Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine") && player.isOnline()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skyboxengine disable " + player.getName());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to disable skybox for " + player.getName(), t);
            }
        }
    }

    /**
     * Temporarily sets an override skybox for the player (e.g. during a special attack or phase transition).
     * Automatically reverts back to the appropriate biome skybox once expired.
     */
    public void setTemporarySkybox(Player player, String skyboxId, long durationTicks) {
        if (!skyboxEnabled || player == null || !player.isOnline()) return;
        long expireAt = System.currentTimeMillis() + (durationTicks * 50L);
        temporarySkyboxExpirations.put(player.getUniqueId(), expireAt);
        applySkybox(player, skyboxId);
    }

    private void spawnBiomeParticles(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        NamespacedKey biomeKey = loc.getBlock().getBiome().getKey();
        String biome = biomeKey.toString();

        switch (biome) {
            case "haohan:lunar_terrae":
                player.spawnParticle(Particle.DUST, loc, 4, 10, 3, 10, 0.01);
                break;
            case "haohan:lunar_maria":
                var blackDust = new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.5f);
                player.spawnParticle(Particle.DUST, loc, 4, 8, 2.5, 8, 0.01, blackDust);
                break;
            case "haohan:lunar_craters":
                player.spawnParticle(Particle.GLOW, loc, 2, 8, 2.5, 8, 0.03);
                break;
            case "haohan:lunar_crystal_craters":
                var amethystPurple = new Particle.DustOptions(Color.fromRGB(184, 115, 245), 1.5f);
                var whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.5f);
                player.spawnParticle(Particle.DUST, loc, 3, 8, 2.5, 8, 0.01, amethystPurple);
                player.spawnParticle(Particle.DUST, loc, 1, 8, 2.5, 8, 0.01, whiteDust);
                break;
            case "haohan:lunar_giant_crystals":
                var crystalPurple = new Particle.DustOptions(Color.fromRGB(179, 51, 230), 1.5f);
                var crystalMagenta = new Particle.DustOptions(Color.fromRGB(242, 66, 186), 1.5f);
                var whiteDust2 = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.5f);
                player.spawnParticle(Particle.DUST, loc, 2, 8, 2.5, 8, 0.01, crystalPurple);
                player.spawnParticle(Particle.DUST, loc, 1, 8, 2.5, 8, 0.01, crystalMagenta);
                player.spawnParticle(Particle.DUST, loc, 1, 8, 2.5, 8, 0.01, whiteDust2);
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
        if (Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
            for (UUID uuid : activeSkyboxes.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skyboxengine disable " + player.getName());
                    } catch (Throwable ignored) {}
                }
            }
        }
        activeSkyboxes.clear();
        temporarySkyboxExpirations.clear();
    }
}
