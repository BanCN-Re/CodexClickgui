package wtf.carnation.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * TrueType faces from assets/carnation/font: the system UI fonts (Segoe UI, Microsoft YaHei, see
 * {@link SystemFonts}) with Inter and MiSans as the bundled fallback, and Lucide for icons.
 *
 * <p>Minecraft samples glyph atlases with NEAREST filtering, so text is only sharp when one atlas
 * pixel lands on one screen pixel. Each face is therefore baked at a few fixed scales
 * ({@code <face>_<scale>.json}, oversample = scale); a screen picks one with {@link #useScale}
 * and draws its layout at exactly that many screen pixels per unit.
 */
public enum UiFont {
    SMALL("small", 15),
    BODY("body", 17),
    SEMIBOLD("semibold", 17),
    TITLE("title", 22),
    HERO("hero", 34),
    ICON_S("icon_s", 18),
    ICON_M("icon_m", 20),
    ICON_L("icon_l", 22);

    private static int scaleIndex = 1;

    /**
     * Minecraft puts a TrueType glyph's baseline this far below the draw position whatever the
     * size (the ascent of the vanilla bitmap font).
     */
    private static final float BASELINE = 7;
    /** The visual middle of mixed Latin/Chinese text, above the baseline, as a fraction of the size. */
    private static final float TEXT_MIDDLE = 0.37f;
    /** Lucide's em square sits on the baseline, so its middle is half the size above it. */
    private static final float ICON_MIDDLE = 0.5f;

    private final float size;
    private final FontDescription[] fonts = new FontDescription[Baked.SCALES.length];

    UiFont(String face, float size) {
        this.size = size;
        for (int i = 0; i < Baked.SCALES.length; i++) {
            fonts[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("carnation", face + "_" + Baked.KEYS[i]));
        }
    }

    /** Selects the largest baked scale not above {@code maxScale} (at least the smallest one) and returns it. */
    public static float useScale(float maxScale) {
        int chosen = 0;
        for (int i = 0; i < Baked.SCALES.length; i++) {
            if (Baked.SCALES[i] <= maxScale + 1.0e-4f) chosen = i;
        }
        scaleIndex = chosen;
        return Baked.SCALES[chosen];
    }

    public static float scale() {
        return Baked.SCALES[scaleIndex];
    }

    public float size() {
        return size;
    }

    public Component text(String text) {
        FontDescription font = fonts[scaleIndex];
        return Component.literal(text).withStyle(style -> style.withFont(font));
    }

    public float width(String text) {
        if (text == null) return 0;
        return Minecraft.getInstance().font.width(text(text));
    }

    /** Draws with the top of the line at {@code y}, snapped to whole screen pixels. */
    public void draw(GuiGraphicsExtractor g, String text, float x, float y, int color) {
        if (text == null || Render2D.alpha(color) == 0 || text.isEmpty()) return;
        float s = scale();
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x * s) / s, Math.round(y * s) / s);
        g.text(Minecraft.getInstance().font, text(text), 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** Draws with the line's visual middle at {@code centerY}. */
    public void drawMiddle(GuiGraphicsExtractor g, String text, float x, float centerY, int color) {
        draw(g, text, x, centerY - BASELINE + size * TEXT_MIDDLE, color);
    }

    public void drawCentered(GuiGraphicsExtractor g, String text, float centerX, float centerY, int color) {
        drawMiddle(g, text, centerX - width(text) / 2, centerY, color);
    }

    public void drawRight(GuiGraphicsExtractor g, String text, float right, float centerY, int color) {
        drawMiddle(g, text, right - width(text), centerY, color);
    }

    /** Draws a Lucide icon centred on a point. */
    public void icon(GuiGraphicsExtractor g, char icon, float centerX, float centerY, int color) {
        String glyph = String.valueOf(icon);
        draw(g, glyph, centerX - width(glyph) / 2, centerY - BASELINE + size * ICON_MIDDLE, color);
    }

    /** Cuts text to fit, adding an ellipsis when something was removed. Single pass, O(n). */
    public String ellipsize(String text, float maxWidth) {
        if (width(text) <= maxWidth) return text;
        int end = 0;
        float acc = width("…");
        for (int i = 0; i < text.length(); i++) {
            float advance = width(String.valueOf(text.charAt(i)));
            if (acc + advance > maxWidth) break;
            acc += advance;
            end = i + 1;
        }
        return text.substring(0, end) + "…";
    }

    /** The baked scales; a holder class because enum constructors cannot read the enum's own statics. */
    private static final class Baked {
        static final float[] SCALES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        static final String[] KEYS = {"75", "100", "125", "150", "200"};
    }
}
