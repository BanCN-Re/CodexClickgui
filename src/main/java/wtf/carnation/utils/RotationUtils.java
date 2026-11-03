package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Aiming math shared by the aura, assist and crystal modules. */
public final class RotationUtils {

    private RotationUtils() {
    }

    /** Yaw/pitch that points at the entity's eyes. */
    public static Vector2f getRotations(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return new Vector2f(0, 0);
        Vec3 from = mc.player.getEyePosition(1.0F);
        Vec3 to = target.getEyePosition(1.0F);
        Rotation rotation = Rotation.between(from, to);
        return new Vector2f(rotation.yaw, rotation.pitch);
    }

    /**
     * The smallest angular distance between the given rotation and any point of the target's
     * hitbox; used to pick the closest target.
     */
    public static double getMinDistance(Entity target, Vector2f rotation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return Double.MAX_VALUE;
        Vec3 from = mc.player.getEyePosition(1.0F);
        AABB box = target.getBoundingBox();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX;
            double y = (i & 2) == 0 ? box.minY : box.maxY;
            double z = (i & 4) == 0 ? box.minZ : box.maxZ;
            Rotation to = Rotation.between(from, new Vec3(x, y, z));
            double dy = Mth.wrapDegrees(to.yaw - rotation.x);
            double dp = Mth.wrapDegrees(to.pitch - rotation.y);
            double distance = Math.sqrt(dy * dy + dp * dp);
            if (distance < best) best = distance;
        }
        return best;
    }

    /** The point of the box that is closest to the given position. */
    public static Vec3 getClosestPoint(Vec3 from, AABB box) {
        double x = Math.max(box.minX, Math.min(box.maxX, from.x));
        double y = Math.max(box.minY, Math.min(box.maxY, from.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, from.z));
        return new Vec3(x, y, z);
    }

    /** True when the entity is inside the given field of view, in degrees. */
    public static boolean inFoV(Entity entity, float fovDegrees) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == null) return false;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 to = entity.position().subtract(eye);
        double length = to.length();
        if (length < 1.0E-4) return true;
        Vec3 look = mc.player.getViewVector(1.0F);
        double dot = look.dot(to) / length;
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angle <= fovDegrees;
    }

    /** Rotates the strafe input so movement keeps following the server-side yaw. */
    public static Vector2f fixMovement(float forward, float strafe, float serverYaw, float playerYaw) {
        double diff = Mth.wrapDegrees(serverYaw - playerYaw);
        float sin = (float) Math.sin(Math.toRadians(diff));
        float cos = (float) Math.cos(Math.toRadians(diff));
        return new Vector2f(strafe * cos - forward * sin, strafe * sin + forward * cos);
    }
}
