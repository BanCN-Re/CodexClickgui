package wtf.carnation.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Port of Naven's {@code Render3DUtils}, rebuilt on 26.3's submit-node path.
 *
 * <p>Naven draws through {@code MultiBufferSource.BufferSource} while temporarily restoring the
 * level projection matrix and calling {@code endLastBatch()} afterwards. 26.3 dropped both: geometry
 * is handed to {@link SubmitNodeCollector#submitCustomGeometry}, which the renderer batches itself.
 * The 26.3 level pose stack is <em>identity</em> at this point (the view rotation is applied as a
 * uniform, not baked into the pose), so callers pass vertices already made camera-relative by
 * subtracting {@link wtf.carnation.event.impl.render.Render3DEvent#camera()}.</p>
 *
 * <p>Vanilla's {@code debug_filled_box} (position+colour quads, translucent, depth-tested without
 * writing) stands in for Naven's {@code NO_DEPTH_TEST} ESP pipeline; lines use the standard line
 * render type, which is also depth-tested.</p>
 */
public final class Render3DUtils {
    /** Line width in pixels; the vanilla line pipeline reads it from the vertex attribute. */
    private static final float LINE_WIDTH = 2.0F;

    private Render3DUtils() {
    }

    /** A filled, camera-relative box. 26.3 has no chained-fill helper, so the six quads are explicit. */
    public static void filledBox(PoseStack stack, SubmitNodeCollector collector, AABB box,
                                 float red, float green, float blue, float alpha) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        collector.submitCustomGeometry(stack, RenderTypes.debugFilledBox(), (pose, out) -> {
            quad(out, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, red, green, blue, alpha);
            quad(out, pose, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, red, green, blue, alpha);
            quad(out, pose, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, red, green, blue, alpha);
            quad(out, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, red, green, blue, alpha);
            quad(out, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, red, green, blue, alpha);
            quad(out, pose, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, red, green, blue, alpha);
        });
    }

    /** The box's twelve edges. */
    public static void outlinedBox(PoseStack stack, SubmitNodeCollector collector, AABB box,
                                   float red, float green, float blue, float alpha) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        Vec3[] corners = {
                new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), new Vec3(x1, y1, z2),
                new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), new Vec3(x1, y2, z2)};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        collector.submitCustomGeometry(stack, RenderTypes.lines(), (pose, out) -> {
            for (int[] edge : edges) {
                edge(out, pose, corners[edge[0]], corners[edge[1]], red, green, blue, alpha);
            }
        });
    }

    /** A camera-relative polyline; {@code origin} is the camera position the points are shifted by. */
    public static void lineStrip(PoseStack stack, SubmitNodeCollector collector, List<Vec3> points,
                                 Vec3 origin, float red, float green, float blue, float alpha) {
        if (points.size() < 2) return;
        collector.submitCustomGeometry(stack, RenderTypes.lines(), (pose, out) -> {
            for (int i = 1; i < points.size(); i++) {
                Vec3 from = points.get(i - 1).subtract(origin);
                Vec3 to = points.get(i).subtract(origin);
                edge(out, pose, from, to, red, green, blue, alpha);
            }
        });
    }

    /** One segment, emitted twice with opposite normals so it reads from either side. */
    private static void edge(VertexConsumer out, PoseStack.Pose pose, Vec3 from, Vec3 to,
                             float red, float green, float blue, float alpha) {
        Vec3 normal = to.subtract(from).normalize();
        vertex(out, pose, from, normal, red, green, blue, alpha);
        vertex(out, pose, to, normal, red, green, blue, alpha);
        Vec3 back = normal.scale(-1.0);
        vertex(out, pose, to, back, red, green, blue, alpha);
        vertex(out, pose, from, back, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float red, float green, float blue, float alpha) {
        vertex(out, pose, x1, y1, z1, red, green, blue, alpha);
        vertex(out, pose, x2, y2, z2, red, green, blue, alpha);
        vertex(out, pose, x3, y3, z3, red, green, blue, alpha);
        vertex(out, pose, x4, y4, z4, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point, Vec3 normal,
                               float red, float green, float blue, float alpha) {
        out.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(LINE_WIDTH);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                               float red, float green, float blue, float alpha) {
        out.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
    }

    /** World-space AABB translated into camera-relative coordinates, ready for the level pose. */
    public static AABB cameraRelative(AABB box, Vec3 camera) {
        return box.move(-camera.x, -camera.y, -camera.z);
    }
}
