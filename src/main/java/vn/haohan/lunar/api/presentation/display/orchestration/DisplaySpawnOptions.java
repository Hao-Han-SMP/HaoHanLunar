package vn.haohan.lunar.core.presentation.display.orchestration;

import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Configuration options for spawning a Minecraft 1.21 Display entity.
 */
public record DisplaySpawnOptions(
        DisplayType type,
        Material blockMaterial,
        ItemStack itemStack,
        String text,
        Vector3f scale,
        Vector3f translation,
        Quaternionf leftRotation,
        Quaternionf rightRotation,
        int durationTicks,
        int interpolationDuration,
        int interpolationDelay,
        Display.Billboard billboard
) {
    public DisplaySpawnOptions {
        Objects.requireNonNull(type, "DisplayType must not be null");
        if (scale == null) scale = new Vector3f(1.0f, 1.0f, 1.0f);
        if (translation == null) translation = new Vector3f(0.0f, 0.0f, 0.0f);
        if (leftRotation == null) leftRotation = new Quaternionf();
        if (rightRotation == null) rightRotation = new Quaternionf();
        if (durationTicks <= 0) durationTicks = 100;
        if (interpolationDuration < 0) interpolationDuration = 0;
        if (interpolationDelay < 0) interpolationDelay = 0;
        if (billboard == null) billboard = Display.Billboard.FIXED;
    }

    public static Builder builder(DisplayType type) {
        return new Builder(type);
    }

    public static class Builder {
        private final DisplayType type;
        private Material blockMaterial = Material.CRYING_OBSIDIAN;
        private ItemStack itemStack;
        private String text;
        private Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);
        private Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);
        private Quaternionf leftRotation = new Quaternionf();
        private Quaternionf rightRotation = new Quaternionf();
        private int durationTicks = 100;
        private int interpolationDuration = 0;
        private int interpolationDelay = 0;
        private Display.Billboard billboard = Display.Billboard.FIXED;

        public Builder(DisplayType type) {
            this.type = Objects.requireNonNull(type, "DisplayType must not be null");
        }

        public Builder block(Material material) {
            this.blockMaterial = material;
            return this;
        }

        public Builder item(ItemStack item) {
            this.itemStack = item;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder scale(float x, float y, float z) {
            this.scale = new Vector3f(x, y, z);
            return this;
        }

        public Builder translation(float x, float y, float z) {
            this.translation = new Vector3f(x, y, z);
            return this;
        }

        public Builder rotation(float pitch, float yaw, float roll) {
            this.leftRotation = new Quaternionf().rotateXYZ(
                    (float) Math.toRadians(pitch),
                    (float) Math.toRadians(yaw),
                    (float) Math.toRadians(roll)
            );
            return this;
        }

        public Builder duration(int durationTicks) {
            this.durationTicks = durationTicks;
            return this;
        }

        public Builder interpolation(int duration, int delay) {
            this.interpolationDuration = duration;
            this.interpolationDelay = delay;
            return this;
        }

        public Builder billboard(Display.Billboard billboard) {
            this.billboard = billboard;
            return this;
        }

        public DisplaySpawnOptions build() {
            return new DisplaySpawnOptions(
                    type, blockMaterial, itemStack, text, scale, translation,
                    leftRotation, rightRotation, durationTicks, interpolationDuration,
                    interpolationDelay, billboard
            );
        }
    }
}
