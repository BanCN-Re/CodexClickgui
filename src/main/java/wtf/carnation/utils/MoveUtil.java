package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Shared movement math for the movement modules. */
public final class MoveUtil {

    private static final Minecraft mc = Minecraft.getInstance();

    private MoveUtil() {
    }

    public static boolean isMoving() {
        if (mc.player == null) return false;
        Vec2 input = mc.player.input.getMoveVector();
        return Math.abs(input.x) > 0.01F || Math.abs(input.y) > 0.01F;
    }

    public static double getSpeed() {
        if (mc.player == null) return 0;
        Vec3 velocity = mc.player.getDeltaMovement();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    /** Rewrites the horizontal velocity, keeping the current input direction. */
    public static void setSpeed(double speed) {
        if (mc.player == null) return;
        Vec2 input = mc.player.input.getMoveVector();
        float forward = input.y;
        float strafe = input.x;
        if (Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) return;
        double yaw = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-4) return;
        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(x / length * speed, velocity.y, z / length * speed);
    }

    public static boolean isOnGround() {
        return mc.player != null && mc.player.onGround();
    }

    public static float getDirection() {
        if (mc.player == null) return 0;
        Vec2 input = mc.player.input.getMoveVector();
        float forward = input.y;
        float strafe = input.x;
        float yaw = mc.player.getYRot();
        if (forward < 0) yaw += 180.0F;
        float factor = forward < 0 ? -0.5F : 0.5F;
        if (strafe != 0) yaw += strafe > 0 ? -factor : factor;
        return yaw;
    }
}
