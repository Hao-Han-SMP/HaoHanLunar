package vn.haohan.lunar.mechanics;

import vn.haohan.lunar.HaoHanLunarPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class VisualMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private int tickCounter = 0;

    public VisualMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        tickCounter++;
        if (tickCounter >= 8) {
            tickCounter = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getKey().toString().equals("haohan:lunar")) {
                    spawnBiomeParticles(player);
                }
            }
        }
    }

    private void spawnBiomeParticles(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        NamespacedKey biomeKey = loc.getBlock().getBiome().getKey();
        String biome = biomeKey.toString();

        switch (biome) {
            case "haohan:lunar_terrae":
                player.spawnParticle(Particle.FIREFLY, loc, 4, 10, 3, 10, 0.01);
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

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        if (toWorld.getKey().toString().equals("haohan:lunar")) {
            if (!player.getScoreboardTags().contains("lunar_visited")) {
                player.addScoreboardTag("lunar_visited");
                
                // Show title
                player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("🌙", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    Component.text("ᴍặᴛ ᴛʀăɴɢ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                ));

                // Play thunder sound
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.MASTER, 2.0f, 0.7f);
            }
        } else {
            player.removeScoreboardTag("lunar_visited");
        }
    }
}
