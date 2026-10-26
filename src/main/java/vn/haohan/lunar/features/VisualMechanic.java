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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages Lunar dimension visual effects, including biome-specific ambient particles,
 * multi-colored low-frequency falling meteors / shooting stars,
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

    // Multi-colored shooting stars (sao băng)
    private static final Particle.DustOptions[] METEOR_PALETTES = new Particle.DustOptions[] {
            new Particle.DustOptions(Color.fromRGB(0, 240, 255), 1.8f),   // Cyan / Electric Blue
            new Particle.DustOptions(Color.fromRGB(195, 60, 255), 1.8f),  // Amethyst / Violet
            new Particle.DustOptions(Color.fromRGB(255, 175, 30), 1.8f),  // Solar Amber / Gold
            new Particle.DustOptions(Color.fromRGB(30, 255, 140), 1.8f),  // Emerald / Aurora Green
            new Particle.DustOptions(Color.fromRGB(245, 250, 255), 2.0f), // Stellar White / Diamond Silver
            new Particle.DustOptions(Color.fromRGB(255, 65, 140), 1.8f)   // Supernova Crimson / Rose
    };

    private final List<ShootingStar> activeMeteors = new ArrayList<>();
    private final Random meteorRandom = new Random();
    private int meteorCooldown = 100;

    public static class ShootingStar {
        private final World world;
        private double x, y, z;
        private final double vx, vy, vz;
        private final Particle.DustOptions dustOptions;
        private int remainingTicks;

        public ShootingStar(World world, double x, double y, double z, double vx, double vy, double vz, Particle.DustOptions dustOptions, int duration) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.dustOptions = dustOptions;
            this.remainingTicks = duration;
        }

        public boolean tick() {
            if (remainingTicks <= 0 || !world.isChunkLoaded((int) x >> 4, (int) z >> 4)) {
                return false;
            }

            double nextX = x + vx;
            double nextY = y + vy;
            double nextZ = z + vz;

            // Render continuous smooth incandescent streak
            int steps = 5;
            for (int i = 0; i < steps; i++) {
                double t = (double) i / steps;
                double px = x + vx * t;
                double py = y + vy * t;
                double pz = z + vz * t;
                world.spawnParticle(Particle.DUST, px, py, pz, 1, 0, 0, 0, 0, dustOptions);
            }

            // Bright sparkling meteor head
            world.spawnParticle(Particle.END_ROD, nextX, nextY, nextZ, 1, 0.05, 0.05, 0.05, 0.01);
            if (remainingTicks % 3 == 0) {
                world.spawnParticle(Particle.FIREWORK, nextX, nextY, nextZ, 1, 0.02, 0.02, 0.02, 0.02);
            }

            x = nextX;
            y = nextY;
            z = nextZ;
            remainingTicks--;

            if (remainingTicks == 0) {
                // Subtle dissipation sparkle at terminal point
                world.spawnParticle(Particle.FIREWORK, x, y, z, 3, 0.15, 0.15, 0.15, 0.05);
            }

            return remainingTicks > 0;
        }
    }

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
            this.skyboxEnabled = true;
            this.lunarWorldKey = HaoHanLunarPlugin.LUNAR_WORLD_KEY.toString();
            this.defaultSkybox = "lunar_space";
        }

        registerDefaultBiomeSkyboxes();
    }

    private void registerDefaultBiomeSkyboxes() {
        biomeSkyboxMap.putIfAbsent("haohan:lunar_terrae", "lunar_space");
        biomeSkyboxMap.putIfAbsent("haohan:lunar_maria", "lunar_space");
        biomeSkyboxMap.putIfAbsent("haohan:lunar_craters", "lunar_space");
        biomeSkyboxMap.putIfAbsent("haohan:lunar_crystal_craters", "lunar_space");
        biomeSkyboxMap.putIfAbsent("haohan:lunar_giant_crystals", "lunar_space");
        biomeSkyboxMap.putIfAbsent("haohan:lunar_giant_crystal_outskirts", "lunar_space");

        biomeSkyboxMap.putIfAbsent("minecraft:the_end", "lunar_space");
        biomeSkyboxMap.putIfAbsent("minecraft:end_highlands", "lunar_space");
        biomeSkyboxMap.putIfAbsent("minecraft:end_barrens", "lunar_space");
        biomeSkyboxMap.putIfAbsent("minecraft:end_midlands", "lunar_space");
        biomeSkyboxMap.putIfAbsent("minecraft:small_end_islands", "lunar_space");
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
        // 1. Tick active falling meteors
        if (!activeMeteors.isEmpty()) {
            activeMeteors.removeIf(star -> !star.tick());
        }

        // 2. Random low-frequency shooting star spawn in Lunar dimension (~every 20-40 seconds)
        if (meteorCooldown > 0) {
            meteorCooldown--;
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
                    if (meteorRandom.nextDouble() < 0.04) {
                        spawnShootingStar(player);
                        meteorCooldown = 300 + meteorRandom.nextInt(400); // 15 - 35s cooldown
                        break;
                    }
                }
            }
        }

        // 3. Biome ambient particles (every 8 ticks)
        tickCounter++;
        if (tickCounter >= 8) {
            tickCounter = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
                    spawnBiomeParticles(player);
                }
            }
        }

        // 4. Verify and update player skyboxes on biome transitions (every 20 ticks)
        skyboxCheckCounter++;
        if (skyboxCheckCounter >= 20) {
            skyboxCheckCounter = 0;
            if (skyboxEnabled && Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) {
                updateAllPlayerSkyboxes();
            }
        }
    }

    public void spawnShootingStar(Player player) {
        World world = player.getWorld();
        Location pLoc = player.getLocation();

        // High altitude origin across upper skybox
        double ox = pLoc.getX() + (meteorRandom.nextDouble() - 0.5) * 120.0;
        double oy = Math.max(150.0, pLoc.getY() + 65.0 + meteorRandom.nextDouble() * 35.0);
        double oz = pLoc.getZ() + (meteorRandom.nextDouble() - 0.5) * 120.0;

        // Diagonal downward velocity vector
        double angle = meteorRandom.nextDouble() * 2.0 * Math.PI;
        double speed = 2.8 + meteorRandom.nextDouble() * 1.5;
        double vx = Math.cos(angle) * speed;
        double vy = -(0.65 + meteorRandom.nextDouble() * 0.55);
        double vz = Math.sin(angle) * speed;

        Particle.DustOptions palette = METEOR_PALETTES[meteorRandom.nextInt(METEOR_PALETTES.length)];
        int duration = 16 + meteorRandom.nextInt(10); // 16 to 25 ticks (0.8 - 1.25 seconds)

        activeMeteors.add(new ShootingStar(world, ox, oy, oz, vx, vy, vz, palette, duration));

        // Subtle ethereal twinkle sound effect
        player.playSound(pLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.35f, 1.8f);
    }

    private void updateAllPlayerSkyboxes() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean inLunar = HaoHanLunarPlugin.isLunarWorld(player.getWorld());

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

            // Continuously purge any lingering dragon battle boss bar and fog
            try {
                org.bukkit.boss.DragonBattle battle = player.getWorld().getEnderDragonBattle();
                if (battle != null) {
                    if (!battle.hasBeenPreviouslyKilled()) {
                        battle.setPreviouslyKilled(true);
                    }
                    if (battle.getBossBar() != null) {
                        battle.getBossBar().removePlayer(player);
                        battle.getBossBar().setVisible(false);
                    }
                }
            } catch (Throwable ignored) {}

            // Unconditionally use lunar skybox for all players in the Lunar world regardless of biome
            String targetSkybox = (defaultSkybox != null && !defaultSkybox.isEmpty()) ? defaultSkybox : "lunar_space";

            String currentSkybox = activeSkyboxes.get(uuid);
            if (currentSkybox == null || !currentSkybox.equals(targetSkybox)) {
                applySkybox(player, targetSkybox);
            }
        }
    }

    public void applySkybox(Player player, String skyboxId) {
        if (!skyboxEnabled || player == null || !player.isOnline()) return;
        if (!HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            clearSkybox(player);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) return;

        try {
            Class<?> apiClass = Class.forName("de.vectorflare.skyboxengine.SkyboxAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            if (api == null) return;

            Object data = apiClass.getMethod("getSkyboxData", Player.class).invoke(api, player);
            Object settings = null;
            try {
                settings = apiClass.getMethod("getSkyboxSettings", String.class).invoke(api, skyboxId);
            } catch (Throwable ignored) {}
            if (settings == null) {
                try {
                    settings = apiClass.getMethod("getSkyboxSettings", String.class).invoke(api, "skyboxengine:model_shader_1");
                } catch (Throwable ignored) {}
                if (settings == null) {
                    try {
                        settings = apiClass.getMethod("getSkyboxSettings", String.class).invoke(api, "model_shader_1");
                    } catch (Throwable ignored) {}
                }
            }
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
                    try {
                        java.lang.reflect.Field renderedField = data.getClass().getDeclaredField("renderedPlayerSkybox");
                        renderedField.setAccessible(true);
                        Object rendered = renderedField.get(data);
                        if (rendered != null) {
                            rendered.getClass().getMethod("removeSkybox", int.class).invoke(rendered, 0);
                            renderedField.set(data, null);
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
                if (entity instanceof org.bukkit.entity.ItemDisplay) {
                    entity.remove();
                }
            }
        }
    }

    private void spawnBiomeParticles(Player player) {
        Location loc = player.getLocation();
        String biomeKey = loc.getBlock().getBiome().getKey().toString();

        switch (biomeKey) {
            case "haohan:lunar_terrae":
            case "minecraft:desert":
            case "minecraft:end_highlands":
                player.spawnParticle(Particle.ASH, loc, 3, 10, 3, 10, 0.01);
                break;
            case "haohan:lunar_maria":
            case "minecraft:badlands":
            case "minecraft:the_end":
                var grayDust = new Particle.DustOptions(Color.fromRGB(80, 80, 80), 1.0f);
                player.spawnParticle(Particle.DUST, loc, 2, 8, 2, 8, 0.01, grayDust);
                break;
            case "haohan:lunar_craters":
            case "minecraft:eroded_badlands":
            case "minecraft:end_barrens":
                player.spawnParticle(Particle.WHITE_ASH, loc, 4, 10, 3, 10, 0.02);
                break;
            case "haohan:lunar_crystal_craters":
            case "minecraft:small_end_islands":
                var crystalCyanDust = new Particle.DustOptions(Color.fromRGB(0, 240, 255), 1.2f);
                player.spawnParticle(Particle.DUST, loc, 3, 8, 2.5, 8, 0.01, crystalCyanDust);
                player.spawnParticle(Particle.END_ROD, loc, 1, 6, 2, 6, 0.01);
                break;
            case "haohan:lunar_giant_crystals":
            case "minecraft:deep_dark":
                var crystalPurple = new Particle.DustOptions(Color.fromRGB(180, 50, 255), 1.4f);
                var whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.0f);
                player.spawnParticle(Particle.DUST, loc, 3, 8, 3, 8, 0.01, crystalPurple);
                player.spawnParticle(Particle.DUST, loc, 2, 8, 3, 8, 0.01, whiteDust);
                player.spawnParticle(Particle.PORTAL, loc, 2, 6, 2, 6, 0.02);
                break;
            case "haohan:lunar_giant_crystal_outskirts":
            case "minecraft:end_midlands":
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

        if (HaoHanLunarPlugin.isLunarWorld(world)) {
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
        if (!HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            clearSkybox(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        if (HaoHanLunarPlugin.isLunarWorld(toWorld)) {
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
     * Clean up all skyboxes and active meteors when plugin is disabled or reloaded.
     */
    public void cleanup() {
        activeMeteors.clear();
        if (!Bukkit.getPluginManager().isPluginEnabled("SkyboxEngine")) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearSkybox(player);
        }
        activeSkyboxes.clear();
        temporarySkyboxExpirations.clear();
    }
}
