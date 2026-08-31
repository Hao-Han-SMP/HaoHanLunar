package vn.haohan.lunar.mechanics.boss.warden.visual;

import org.bukkit.Location;
import org.bukkit.SoundCategory;

public final class WardenAudio {
    private WardenAudio() {}

    public static void playCustomSound(Location loc, String soundKey, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null) return;
        try {
            loc.getWorld().playSound(loc, soundKey, SoundCategory.HOSTILE, volume, pitch);
        } catch (Throwable ignored) {}
    }
}
