package wtf.carnation.boot;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Arrays;

/**
 * A batch of textured quads — the boot screen's glyphs — submitted as one GUI render state.
 *
 * <p>One state per texture (bold / regular) per frame keeps the GUI pass from re-binding the
 * glyph atlas for every letter, which would otherwise be hundreds of tiny draw calls a frame.
 */
public final class BootQuads implements GuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textures;
    private final Matrix3x2f pose = new Matrix3x2f();
    private ScreenRectangle scissor;

    private float[] xy = new float[1024];      // x, y per vertex
    private float[] uv = new float[1024];      // u, v per vertex
    private int[] colors = new int[512];       // ARGB per vertex
    private int vertices;

    public BootQuads(RenderPipeline pipeline, TextureSetup textures) {
        this.pipeline = pipeline;
        this.textures = textures;
    }

    public void begin(Matrix3x2fc pose, ScreenRectangle scissor) {
        this.pose.set(pose);
        this.scissor = scissor;
        this.vertices = 0;
    }

    public void quad(float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int color) {
        if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) return;
        vertex(x0, y0, u0, v0, color);
        vertex(x1, y0, u1, v0, color);
        vertex(x1, y1, u1, v1, color);
        vertex(x0, y0, u0, v0, color);
        vertex(x1, y1, u1, v1, color);
        vertex(x0, y1, u0, v1, color);
    }

    private void vertex(float x, float y, float u, float v, int color) {
        if (vertices * 2 >= xy.length) {
            int size = Math.max(64, xy.length);
            xy = Arrays.copyOf(xy, size * 2);
            uv = Arrays.copyOf(uv, size * 2);
        }
        if (vertices >= colors.length) colors = Arrays.copyOf(colors, Math.max(64, colors.length) * 2);
        xy[vertices * 2] = x;
        xy[vertices * 2 + 1] = y;
        uv[vertices * 2] = u;
        uv[vertices * 2 + 1] = v;
        colors[vertices] = color;
        vertices++;
    }

    public boolean isEmpty() {
        return vertices == 0;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < vertices; i++) {
            consumer.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1])
                    .setColor(colors[i])
                    .setUv(uv[i * 2], uv[i * 2 + 1]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textures;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRectangle bounds() {
        if (vertices == 0) return new ScreenRectangle(0, 0, 1, 1);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < vertices; i++) {
            float x = xy[i * 2], y = xy[i * 2 + 1];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        int left = (int) Math.floor(minX) - 1, top = (int) Math.floor(minY) - 1;
        ScreenRectangle local = new ScreenRectangle(left, top,
                (int) Math.ceil(maxX) + 1 - left, (int) Math.ceil(maxY) + 1 - top);
        ScreenRectangle transformed = local.transformMaxBounds(pose);
        if (scissor != null) {
            ScreenRectangle clipped = scissor.intersection(transformed);
            return clipped == null ? transformed : clipped;
        }
        return transformed;
    }
}
