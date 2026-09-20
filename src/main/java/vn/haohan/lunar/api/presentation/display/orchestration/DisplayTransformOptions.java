package vn.haohan.lunar.api.presentation.display.orchestration;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Configuration options for interpolating/transforming an active Display entity.
 */
public record DisplayTransformOptions(
        Vector3f scale,
        Vector3f translation,
        Quaternionf leftRotation,
        Quaternionf rightRotation,
        int interpolationDuration,
        int interpolationDelay
) {
    public DisplayTransformOptions {
        if (scale == null) scale = new Vector3f(1.0f, 1.0f, 1.0f);
        if (translation == null) translation = new Vector3f(0.0f, 0.0f, 0.0f);
        if (leftRotation == null) leftRotation = new Quaternionf();
        if (rightRotation == null) rightRotation = new Quaternionf();
        if (interpolationDuration < 0) interpolationDuration = 0;
        if (interpolationDelay < 0) interpolationDelay = 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);
        private Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);
        private Quaternionf leftRotation = new Quaternionf();
        private Quaternionf rightRotation = new Quaternionf();
        private int interpolationDuration = 20;
        private int interpolationDelay = 0;

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

        public Builder interpolation(int duration, int delay) {
            this.interpolationDuration = duration;
            this.interpolationDelay = delay;
            return this;
        }

        public DisplayTransformOptions build() {
            return new DisplayTransformOptions(
                    scale, translation, leftRotation, rightRotation,
                    interpolationDuration, interpolationDelay
            );
        }
    }
}
