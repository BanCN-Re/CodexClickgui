package wtf.carnation.utils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Port of Naven's {@code ProjectionUtils.project}, returning GUI-scaled pixels. */
public final class ProjectionUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    private ProjectionUtils() {
    }

    public static Vector2f project(double x, double y, double z, float renderPartialTicks) {
        if (mc.gameRenderer == null) return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        double dx = x - camPos.x;
        double dy = y - camPos.y;
        double dz = z - camPos.z;

        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        double rightX = -cosY, rightZ = -sinY;
        double fwdX = -sinY * cosP, fwdY = -sinP, fwdZ = cosY * cosP;
        double upX = -sinY * sinP, upY = cosP, upZ = cosY * sinP;

        double rx = dx * rightX + dz * rightZ;
        double ry = dx * upX + dy * upY + dz * upZ;
        double rz = dx * fwdX + dy * fwdY + dz * fwdZ;
        if (rz >= 0.0) return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);

        double fov = mc.options.fov().get();
        float halfWidth = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float halfHeight = mc.getWindow().getGuiScaledHeight() / 2.0F;
        float scaleFactor = (float) (halfHeight / (-rz * Math.tan(Math.toRadians(fov / 2.0))));
        return new Vector2f(halfWidth + (float) rx * scaleFactor, halfHeight - (float) ry * scaleFactor);
    }
}
