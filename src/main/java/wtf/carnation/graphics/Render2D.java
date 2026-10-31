package wtf.carnation.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Arrays;

/**
 * 2D drawing on top of {@link GuiGraphicsExtractor}: shapes go through
 * {@link CarnationPipelines#GUI_TRIANGLES} as {@link ShapeRenderState}s, text through the vanilla font.
 *
 * <p>Edges are anti-aliased with a one-pixel alpha ramp, measured in real screen pixels from the
 * current pose and GUI scale, so shapes stay smooth at any scale.
 */
public final class Render2D {
    private static final Mesh MESH = new Mesh();

    private Render2D() {
    }

    // ---- shapes -------------------------------------------------------------------------------

    public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        roundedRect(g, x, y, w, h, 0, color);
    }

    public static void roundedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || alpha(color) == 0) return;
        float f = feather(g);
        float[] box = snapBox(g, x, y, w, h);
        x = box[0];
        y = box[1];
        w = box[2];
        h = box[3];
        Mesh mesh = MESH.reset();
        float r = clampRadius(radius, w, h);
        int n = segments(r, g);
        float[] inner = ring(x, y, w, h, r, -f * 0.5f, n);
        float[] outer = ring(x, y, w, h, r, f * 0.5f, n);
        mesh.fan(x + w * 0.5f, y + h * 0.5f, inner, color);
        mesh.strip(inner, color, outer, withAlpha(color, 0));
        submit(g, mesh);
    }

    public static void roundedOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                                      float thickness, int color) {
        if (w <= 0 || h <= 0 || alpha(color) == 0) return;
        float f = feather(g);
        float[] box = snapBox(g, x, y, w, h);
        x = box[0];
        y = box[1];
        w = box[2];
        h = box[3];
        Mesh mesh = MESH.reset();
        float r = clampRadius(radius, w, h);
        int n = segments(r, g);
        float t = Math.max(thickness, f);
        float[] a = ring(x, y, w, h, r, -t - f * 0.5f, n);
        float[] b = ring(x, y, w, h, r, -t + f * 0.5f, n);
        float[] c = ring(x, y, w, h, r, -f * 0.5f, n);
        float[] d = ring(x, y, w, h, r, f * 0.5f, n);
        int clear = withAlpha(color, 0);
        mesh.strip(a, clear, b, color);
        mesh.strip(b, color, c, color);
        mesh.strip(c, color, d, clear);
        submit(g, mesh);
    }

    /** A blurred drop shadow, like CSS {@code box-shadow: 0 0 blur color}. */
    public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                              float blur, int color) {
        shadow(g, x, y, w, h, radius, blur, 0, color);
    }

    /**
     * A blurred drop shadow, like CSS {@code box-shadow: 0 offsetY blur color}: the shape's edge
     * convolved with a Gaussian (sigma = blur / 2), built as rings whose alpha follows the
     * Gaussian's tail so the falloff is smooth instead of a linear ramp.
     */
    public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius,
                              float blur, float offsetY, int color) {
        if (w <= 0 || h <= 0 || alpha(color) == 0) return;
        Mesh mesh = MESH.reset();
        y += offsetY;
        float r = clampRadius(radius, w, h);
        float sigma = Math.max(0.5f, blur / 2);
        int n = segments(r + sigma, g);
        int steps = 12;
        float start = -Math.min(2 * sigma, Math.min(w, h) * 0.5f), end = 3 * sigma;

        float[] previous = ring(x, y, w, h, r, start, n);
        int previousColor = gaussianTail(color, start, sigma);
        mesh.fan(x + w * 0.5f, y + h * 0.5f, previous, previousColor);
        for (int i = 1; i <= steps; i++) {
            // Denser rings near the edge, where the falloff changes fastest.
            float t = (float) i / steps;
            float d = start + (end - start) * t * t * (3 - 2 * t);
            float[] next = ring(x, y, w, h, r, d, n);
            int nextColor = i == steps ? withAlpha(color, 0) : gaussianTail(color, d, sigma);
            mesh.strip(previous, previousColor, next, nextColor);
            previous = next;
            previousColor = nextColor;
        }
        submit(g, mesh);
    }

    /** The colour at distance {@code d} outside a blurred edge: alpha * 0.5 * erfc(d / (sigma * sqrt 2)). */
    private static int gaussianTail(int color, float d, float sigma) {
        double z = d / (sigma * Math.sqrt(2));
        return withAlpha(color, (int) Math.round(alpha(color) * 0.5 * erfc(z)));
    }

    /** Complementary error function (Abramowitz and Stegun 7.1.26, error below 1.5e-7). */
    private static double erfc(double z) {
        double t = 1 / (1 + 0.3275911 * Math.abs(z));
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
        double erfcAbs = poly * Math.exp(-z * z);
        return z >= 0 ? erfcAbs : 2 - erfcAbs;
    }

    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
        roundedRect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    public static void circleOutline(GuiGraphicsExtractor g, float cx, float cy, float radius, float thickness, int color) {
        roundedOutline(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, thickness, color);
    }

    /** A colour along the x axis, for {@link #gradientBar}. */
    @FunctionalInterface
    public interface Gradient {
        int at(float x);
    }

    /**
     * A horizontal bar filled by {@code gradient}, with separate left and right corner radii (a
     * fill that ends under a knob keeps a square right end). Built from thin columns so any number
     * of colour stops survives; the top and bottom edges get the usual one-pixel feather.
     */
    public static void gradientBar(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                   float leftRadius, float rightRadius, Gradient gradient) {
        if (w <= 0 || h <= 0) return;
        float f = feather(g);
        float rl = clampRadius(leftRadius, w, h), rr = clampRadius(rightRadius, w, h);
        Mesh mesh = MESH.reset();
        float step = Math.max(f, 3);
        float previousX = Float.NaN, previousTop = 0, previousBottom = 0;
        int previousColor = 0;
        float px = x;
        while (true) {
            float inset = 0;
            if (rl > 0 && px < x + rl) {
                float d = x + rl - px;
                inset = rl - (float) Math.sqrt(Math.max(0, rl * rl - d * d));
            } else if (rr > 0 && px > x + w - rr) {
                float d = px - (x + w - rr);
                inset = rr - (float) Math.sqrt(Math.max(0, rr * rr - d * d));
            }
            float top = y + inset, bottom = y + h - inset;
            int color = gradient.at(px);
            if (!Float.isNaN(previousX)) {
                int clear = withAlpha(color, 0), previousClear = withAlpha(previousColor, 0);
                quadInto(mesh, previousX, previousTop + f * 0.5f, px, top + f * 0.5f,
                        px, bottom - f * 0.5f, previousX, previousBottom - f * 0.5f,
                        previousColor, color, color, previousColor);
                quadInto(mesh, previousX, previousTop - f * 0.5f, px, top - f * 0.5f,
                        px, top + f * 0.5f, previousX, previousTop + f * 0.5f,
                        previousClear, clear, color, previousColor);
                quadInto(mesh, previousX, previousBottom - f * 0.5f, px, bottom - f * 0.5f,
                        px, bottom + f * 0.5f, previousX, previousBottom + f * 0.5f,
                        previousColor, color, clear, previousClear);
            }
            previousX = px;
            previousTop = top;
            previousBottom = bottom;
            previousColor = color;
            if (px >= x + w) break;
            // Fine steps through the rounded ends, coarser through the straight middle.
            boolean inCurve = px < x + rl || px >= x + w - rr - step;
            px = Math.min(x + w, px + (inCurve ? Math.max(f, 1) : step));
        }
        submit(g, mesh);
    }

    /** A filled quad with one colour per corner (clockwise), e.g. for gradient bands. */
    public static void quad(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float x2, float y2,
                            float x3, float y3, int c0, int c1, int c2, int c3) {
        Mesh mesh = MESH.reset();
        quadInto(mesh, x0, y0, x1, y1, x2, y2, x3, y3, c0, c1, c2, c3);
        submit(g, mesh);
    }

    private static void quadInto(Mesh mesh, float x0, float y0, float x1, float y1, float x2, float y2,
                                 float x3, float y3, int c0, int c1, int c2, int c3) {
        mesh.vertex(x0, y0, c0);
        mesh.vertex(x1, y1, c1);
        mesh.vertex(x2, y2, c2);
        mesh.vertex(x0, y0, c0);
        mesh.vertex(x2, y2, c2);
        mesh.vertex(x3, y3, c3);
    }

    /** A line of the given width with soft ends, e.g. for icons and underlines. */
    public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-4f || alpha(color) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate((float) Math.atan2(dy, dx));
        roundedRect(g, -width * 0.5f, -width * 0.5f, length + width, width, width * 0.5f, color);
        g.pose().popMatrix();
    }

    // ---- text ---------------------------------------------------------------------------------

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int width(String text) {
        return font().width(text);
    }

    public static void text(GuiGraphicsExtractor g, String text, float x, float y, int color) {
        text(g, Component.literal(text), x, y, color, 1.0f);
    }

    public static void text(GuiGraphicsExtractor g, Component text, float x, float y, int color, float scale) {
        if (alpha(color) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (scale != 1.0f) g.pose().scale(scale, scale);
        g.text(font(), text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static void centeredText(GuiGraphicsExtractor g, String text, float cx, float y, int color) {
        text(g, text, cx - width(text) * 0.5f, y, color);
    }

    /** Cuts text to fit {@code maxWidth}, adding an ellipsis when something was removed. */
    public static String ellipsize(String text, int maxWidth) {
        if (width(text) <= maxWidth) return text;
        String dots = "...";
        int end = text.length();
        while (end > 0 && width(text.substring(0, end) + dots) > maxWidth) end--;
        return text.substring(0, end) + dots;
    }

    // ---- colour -------------------------------------------------------------------------------

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    /** Multiplies the colour's own alpha by {@code factor}. */
    public static int fade(int color, float factor) {
        return withAlpha(color, Math.round(alpha(color) * Math.max(0, Math.min(1, factor))));
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * t);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int gr = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | gr << 8 | b;
    }

    // ---- geometry -----------------------------------------------------------------------------

    /** One real screen pixel in the current pose's units. */
    private static float feather(GuiGraphicsExtractor g) {
        Matrix3x2fc pose = g.pose();
        float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
        float pixels = Minecraft.getInstance().getWindow().getGuiScale() * Math.max(1.0e-3f, poseScale);
        return 1.0f / pixels;
    }

    /**
     * Puts a rectangle on the physical pixel grid. A box that starts half a pixel in makes its own
     * one-pixel feather ramp straddle two pixels, which is what reads as a ragged edge.
     */
    private static float[] snapBox(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        float grid = 1.0f / feather(g);
        float sx = Math.round(x * grid) / grid;
        float sy = Math.round(y * grid) / grid;
        float right = Math.round((x + w) * grid) / grid;
        float bottom = Math.round((y + h) * grid) / grid;
        return new float[]{sx, sy, Math.max(grid, right - sx), Math.max(grid, bottom - sy)};
    }

    private static float clampRadius(float radius, float w, float h) {
        return Math.max(0, Math.min(radius, Math.min(w, h) * 0.5f));
    }

    private static int segments(float radius, GuiGraphicsExtractor g) {
        // One quad per ~2 physical pixels of arc: below that the straight chords between corner
        // points are long enough to see as facets on a rounded card.
        float pixels = radius / feather(g);
        return Math.max(4, Math.min(64, Math.round(pixels * 0.9f)));
    }

    /**
     * The outline of a rounded rectangle pushed outward by {@code offset} (inward when negative),
     * clockwise from the top-left corner, as x/y pairs with {@code n} points per corner.
     */
    private static float[] ring(float x, float y, float w, float h, float r, float offset, int n) {
        float[] points = new float[n * 8];
        float[] cx = {x + r, x + w - r, x + w - r, x + r};
        float[] cy = {y + r, y + r, y + h - r, y + h - r};
        float d = Math.max(0, r + offset);
        // An inward offset deeper than the radius collapses each corner to its centre; the rest of
        // the offset is applied by shrinking the shape below.
        float shift = r + offset < 0 ? r + offset : 0;
        int index = 0;
        for (int corner = 0; corner < 4; corner++) {
            double start = Math.PI * (1.0 + corner * 0.5);
            for (int i = 0; i < n; i++) {
                double angle = start + Math.PI * 0.5 * i / (n - 1);
                points[index++] = cx[corner] + (float) Math.cos(angle) * d;
                points[index++] = cy[corner] + (float) Math.sin(angle) * d;
            }
        }
        if (shift < 0) {
            // Shrink the collapsed shape towards the centre instead of leaving it at full size.
            float centerX = x + w * 0.5f, centerY = y + h * 0.5f;
            float scaleX = Math.max(0, (w * 0.5f + shift) / (w * 0.5f));
            float scaleY = Math.max(0, (h * 0.5f + shift) / (h * 0.5f));
            for (int i = 0; i < points.length; i += 2) {
                points[i] = centerX + (points[i] - centerX) * scaleX;
                points[i + 1] = centerY + (points[i + 1] - centerY) * scaleY;
            }
        }
        return points;
    }

    private static void submit(GuiGraphicsExtractor g, Mesh mesh) {
        if (mesh.count == 0) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < mesh.count; i++) {
            float px = mesh.positions[i * 2], py = mesh.positions[i * 2 + 1];
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        int left = (int) Math.floor(minX) - 1, top = (int) Math.floor(minY) - 1;
        ScreenRectangle local = new ScreenRectangle(left, top,
                (int) Math.ceil(maxX) + 1 - left, (int) Math.ceil(maxY) + 1 - top);
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle bounds = local.transformMaxBounds(pose);
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) return;
        }
        g.guiRenderState.addGuiElement(new ShapeRenderState(CarnationPipelines.GUI_TRIANGLES, pose,
                Arrays.copyOf(mesh.positions, mesh.count * 2), Arrays.copyOf(mesh.colors, mesh.count),
                mesh.count, scissor, bounds));
    }

    /** A reusable growable triangle list. */
    private static final class Mesh {
        float[] positions = new float[1024];
        int[] colors = new int[512];
        int count;

        Mesh reset() {
            count = 0;
            return this;
        }

        void vertex(float x, float y, int color) {
            if (count == colors.length) {
                colors = Arrays.copyOf(colors, count * 2);
                positions = Arrays.copyOf(positions, count * 4);
            }
            positions[count * 2] = x;
            positions[count * 2 + 1] = y;
            colors[count++] = color;
        }

        /** Triangles from a centre point to every edge of a convex outline. */
        void fan(float cx, float cy, float[] outline, int color) {
            int points = outline.length / 2;
            for (int i = 0; i < points; i++) {
                int next = (i + 1) % points;
                vertex(cx, cy, color);
                vertex(outline[i * 2], outline[i * 2 + 1], color);
                vertex(outline[next * 2], outline[next * 2 + 1], color);
            }
        }

        /** A closed band between two outlines with the same point count. */
        void strip(float[] a, int colorA, float[] b, int colorB) {
            int points = a.length / 2;
            for (int i = 0; i < points; i++) {
                int next = (i + 1) % points;
                vertex(a[i * 2], a[i * 2 + 1], colorA);
                vertex(b[i * 2], b[i * 2 + 1], colorB);
                vertex(b[next * 2], b[next * 2 + 1], colorB);

                vertex(a[i * 2], a[i * 2 + 1], colorA);
                vertex(b[next * 2], b[next * 2 + 1], colorB);
                vertex(a[next * 2], a[next * 2 + 1], colorA);
            }
        }
    }
}
