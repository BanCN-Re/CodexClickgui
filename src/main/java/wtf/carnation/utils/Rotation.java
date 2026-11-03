package wtf.carnation.utils;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A yaw/pitch pair, ported from the Naven rotation helpers. */
public final class Rotation {
    public float yaw;
    public float pitch;

    public Rotation() {
    }

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation(Vector2f vec) {
        this.yaw = vec.x;
        this.pitch = vec.y;
    }

    public static Rotation between(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) -Math.toDegrees(
                Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z))));
        return new Rotation(yaw, pitch);
    }

    public Vector2f toVector() {
        return new Vector2f(yaw, pitch);
    }

    /** Snaps the rotation to the closest value the server can reproduce from mouse input. */
    public void fixSensitivity(float sensitivity) {
        float f = sensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;
        this.yaw = this.yaw - this.yaw % gcd;
        this.pitch = this.pitch - this.pitch % gcd;
    }

    public static float updateRotation(float current, float target, float maxDelta) {
        float diff = Mth.wrapDegrees(target - current);
        if (diff > maxDelta) diff = maxDelta;
        if (diff < -maxDelta) diff = -maxDelta;
        return current + diff;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
