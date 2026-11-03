package wtf.carnation.utils;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/** Minimal world-to-screen projection for the HUD/ESP modules. */
public final class Projection {

    private Projection() {
    }

    /** Returns GUI-scaled pixel coordinates, or null when the point is behind the camera. */
    public static Vec3 project(Vec3 world) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return null;
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        double dx = world.x - camPos.x;
        double dy = world.y - camPos.y;
        double dz = world.z - camPos.z;

        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        // Camera basis: right, up, forward (MC: yaw 0 faces +Z, positive pitch looks down).
        double rightX = -cosY, rightY = 0, rightZ = -sinY;
        double fwdX = -sinY * cosP, fwdY = -sinP, fwdZ = cosY * cosP;
        double upX = -sinY * sinP, upY = cosP, upZ = cosY * sinP;

        double x = dx * rightX + dy * rightY + dz * rightZ;
        double y = dx * upX + dy * upY + dz * upZ;
        double z = dx * fwdX + dy * fwdY + dz * fwdZ;
        if (z <= 0.05) return null;

        double fov = mc.options.fov().get();
        double f = 1.0 / Math.tan(Math.toRadians(fov) / 2.0);
        double screenW = mc.getWindow().getWidth();
        double screenH = mc.getWindow().getHeight();
        double aspect = screenW / screenH;
        double ndcX = (x / z) * f / aspect;
        double ndcY = (y / z) * f;
        double sx = (ndcX * 0.5 + 0.5) * screenW;
        double sy = (1.0 - (ndcY * 0.5 + 0.5)) * screenH;
        return new Vec3(sx, sy, z);
    }
}
