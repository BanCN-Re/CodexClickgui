package wtf.carnation.ui.rhine;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.slf4j.LoggerFactory;
import wtf.carnation.boot.BootPipelines;
import wtf.carnation.boot.BootQuads;
import wtf.carnation.graphics.Render2D;

import java.io.InputStream;

/**
 * The exact Rhine Lab mark, rasterised from {@code LBEILC/RhineLabUI} {@code src/brand.ts} (MIT).
 *
 * <p>Two open ribbon halves and the two signs, stroked at the reference widths, baked to
 * {@code assets/carnation/textures/rhine_mark.png} by {@code tools/MarkRender.java}. Drawing it as
 * one tinted quad keeps the crossing fold crisp at every size; the earlier polyline approximation
 * both lost the fold and showed scalloped edges.
 */
public final class RhineMarkTexture {
    /** The reference SVG view box; the PNG keeps the same aspect. */
    public static final float VIEW_W = 310f, VIEW_H = 145f;

    private static DynamicTexture texture;
    private static GpuSampler sampler;
    private static boolean failed;
    private static float[] contour;

    private RhineMarkTexture() {
    }

    /** Draws the mark centred on {@code cx, cy}; the signs are part of the texture. */
    public static void draw(GuiGraphicsExtractor g, float cx, float cy, float width, int color) {
        if (!ensure() || width <= 0 || (color >>> 24) == 0) return;
        float height = width * VIEW_H / VIEW_W;
        BootQuads mesh = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                TextureSetup.singleTexture(texture.getTextureView(), sampler));
        mesh.begin(g.pose(), g.scissorStack.peek());
        mesh.quad(cx - width * 0.5f, cy - height * 0.5f, cx + width * 0.5f, cy + height * 0.5f,
                0.0f, 0.0f, 1.0f, 1.0f, color);
        g.guiRenderState.addGuiElement(mesh);
    }

    /**
     * The boot's moving stroke: the same Bezier contour as {@code bootMarkContour} in RhineLabUI's
     * {@code src/brand.ts}, revealed from 0 to 1.
     */
    public static void drawContour(GuiGraphicsExtractor g, float cx, float cy, float width, int color,
                                   float progress) {
        if ((color >>> 24) == 0 || progress <= 0) return;
        float height = width * VIEW_H / VIEW_W;
        float[] source = contour();
        float[] mapped = new float[source.length];
        for (int i = 0; i < source.length / 2; i++) {
            mapped[i * 2] = cx - width * 0.5f + source[i * 2] / VIEW_W * width;
            mapped[i * 2 + 1] = cy - height * 0.5f + source[i * 2 + 1] / VIEW_H * height;
        }
        Render2D.ribbon(g, mapped, width * 26f / VIEW_W, color, Math.min(1f, progress));
    }

    private static float[] contour() {
        if (contour != null) return contour;
        // The nine cubic segments of bootMarkContour, after M295 73. Each row is c1, c2, end.
        float[][] segments = {
                {295, 41, 273, 15, 240, 15},
                {221, 15, 207, 23, 192, 38},
                {186, 43, 181, 47, 176, 52},
                {127, 96, 103, 128, 70, 128},
                {38, 128, 15, 101, 15, 70},
                {15, 39, 37, 15, 70, 15},
                {103, 15, 127, 48, 156, 75},
                {182, 99, 208, 128, 240, 128},
                {273, 128, 295, 105, 295, 73},
        };
        int per = 16;
        float[] points = new float[(segments.length * per + 1) * 2];
        int at = 0;
        float px = 295, py = 73;
        points[at++] = px;
        points[at++] = py;
        for (float[] segment : segments) {
            for (int i = 1; i <= per; i++) {
                float t = i / (float) per, u = 1 - t;
                float x = u * u * u * px + 3 * u * u * t * segment[0] + 3 * u * t * t * segment[2]
                        + t * t * t * segment[4];
                float y = u * u * u * py + 3 * u * u * t * segment[1] + 3 * u * t * t * segment[3]
                        + t * t * t * segment[5];
                points[at++] = x;
                points[at++] = y;
            }
            px = segment[4];
            py = segment[5];
        }
        // Drop the duplicate closing point so the polyline wraps cleanly.
        contour = java.util.Arrays.copyOf(points, at - 2);
        return contour;
    }

    /** X of the addition sign's centre for a mark of {@code width}, relative to {@code cx}. */
    public static float plusX(float width) {
        return (69f / VIEW_W - 0.5f) * width;
    }

    /** X of the subtraction sign's centre for a mark of {@code width}, relative to {@code cx}. */
    public static float minusX(float width) {
        return (241f / VIEW_W - 0.5f) * width;
    }

    private static boolean ensure() {
        if (texture != null) return true;
        if (failed) return false;
        try (InputStream in = RhineMarkTexture.class.getResourceAsStream(
                "/assets/carnation/textures/rhine_mark.png")) {
            if (in == null) throw new IllegalStateException("Missing rhine_mark.png");
            NativeImage image = NativeImage.read(in.readAllBytes());
            texture = new DynamicTexture(() -> "carnation_rhine_mark", image);
            sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
            return true;
        } catch (Throwable throwable) {
            failed = true;
            LoggerFactory.getLogger("Carnation/Mark").warn("Rhine mark texture unavailable", throwable);
            return false;
        }
    }
}
