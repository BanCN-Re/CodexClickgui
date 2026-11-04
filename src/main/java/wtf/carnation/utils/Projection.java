package wtf.carnation.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * World-to-screen projection for the 2D ESP overlays.
 *
 * <p>Derived from the player's eye position and applied view rotation rather than
 * {@code GameRenderer.mainCamera()}: the HUD extraction pass runs before the frame's camera is
 * set up, so a camera-based projection returns stale angles and entities in plain sight end up
 * "behind" it. The player state is already final by the time the HUD is drawn.</p>
 */
public final class Projection {

    private Projection() {
    }

    /**
     * Returns GUI-scaled pixel coordinates (the space every HUD module draws in), or null when the
     * point is behind the viewer.
     */
    public static Vec3 project(Vec3 world) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        Vec3 eye = mc.player.getEyePosition();
        double dx = world.x - eye.x;
        double dy = world.y - eye.y;
        double dz = world.z - eye.z;

        double yaw = Math.toRadians(mc.player.getYRot());
        double pitch = Math.toRadians(mc.player.getXRot());
        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        // Camera basis: right, up, forward (MC: yaw 0 faces +Z, positive pitch looks down).
        double rightX = -cosY, rightZ = -sinY;
        double fwdX = -sinY * cosP, fwdY = -sinP, fwdZ = cosY * cosP;
        double upX = -sinY * sinP, upY = cosP, upZ = cosY * sinP;

        double x = dx * rightX + dz * rightZ;
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
        // The framebuffer is guiScale times larger than the GUI space HUD drawing lives in, so the
        // projection has to be divided back down or every ESP box lands off-screen by that factor.
        double guiScale = Math.max(1.0, mc.getWindow().getGuiScale());
        double sx = (ndcX * 0.5 + 0.5) * screenW / guiScale;
        double sy = (1.0 - (ndcY * 0.5 + 0.5)) * screenH / guiScale;
        return new Vec3(sx, sy, z);
    }
}
