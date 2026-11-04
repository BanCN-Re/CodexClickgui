package wtf.carnation.utils;

import java.util.Random;

/** Subset of Naven's {@code tech.naven.utils.MathUtils} used by the migrated modules. */
public final class MathUtils {
    public static final Random random = new Random();

    private MathUtils() {
    }

    public static float clampPitch_To90(float pitch) {
        if (pitch > 90.0F) return 90.0F;
        return pitch < -90.0F ? -90.0F : pitch;
    }

    public static double interpolate(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static float interpolate(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double getRandomDoubleInRange(double minDouble, double maxDouble) {
        return minDouble >= maxDouble ? minDouble : random.nextDouble() * (maxDouble - minDouble) + minDouble;
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clampValue(float value, float floor, float cap) {
        return value < floor ? floor : Math.min(value, cap);
    }

    public static int clampValue(int value, int floor, int cap) {
        return value < floor ? floor : Math.min(value, cap);
    }
}
