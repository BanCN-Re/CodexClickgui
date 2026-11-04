package wtf.carnation.boot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The boot screen's own typeface.
 *
 * <p>The loading overlay is drawn while the resource reload is still running, so Minecraft's font
 * providers do not exist yet — every {@code getText} call would render the missing-glyph box. This
 * class instead samples a glyph atlas baked into the mod jar (see {@code tools/build_boot_font.py}):
 * white glyphs on transparent, tinted per vertex at draw time.
 *
 * <p>Metrics are kept in em fractions, so text can be drawn at any size from the same atlas.
 */
public final class BootFont {
    public enum Weight {
        BOLD, REGULAR
    }

    /** One glyph: advance and ink box in em units, then its rect in the atlas. */
    public record Glyph(float advance, float left, float top, float width, float height,
                        float u0, float v0, float u1, float v1) {
    }

    private static BootFont instance;
    private static boolean unavailable;

    private final Map<Character, Glyph> bold = new HashMap<>();
    private final Map<Character, Glyph> regular = new HashMap<>();
    private final DynamicTexture boldTexture;
    private final DynamicTexture regularTexture;
    private final GpuSampler sampler;

    private BootFont(JsonObject root) throws IOException {
        JsonObject weights = root.getAsJsonObject("weights");
        readWeight(weights.getAsJsonObject("bold"), bold);
        readWeight(weights.getAsJsonObject("regular"), regular);
        boldTexture = texture(weights.getAsJsonObject("bold"));
        regularTexture = texture(weights.getAsJsonObject("regular"));
        sampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    private void readWeight(JsonObject weight, Map<Character, Glyph> into) {
        JsonObject glyphs = weight.getAsJsonObject("glyphs");
        for (String key : glyphs.keySet()) {
            if (key.isEmpty()) continue;
            var values = glyphs.getAsJsonArray(key);
            float[] v = new float[values.size()];
            for (int i = 0; i < v.length; i++) v[i] = values.get(i).getAsFloat();
            into.put(key.charAt(0), new Glyph(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]));
        }
    }

    private static DynamicTexture texture(JsonObject weight) throws IOException {
        String file = weight.get("file").getAsString();
        byte[] png;
        try (InputStream in = BootFont.class.getResourceAsStream("/assets/carnation/boot/" + file)) {
            if (in == null) throw new IOException("Missing boot atlas " + file);
            png = in.readAllBytes();
        }
        NativeImage image = NativeImage.read(png);
        return new DynamicTexture(() -> "carnation_boot_" + file, image);
    }

    /** Loads the atlas once, on the render thread; null when the assets cannot be read. */
    public static BootFont get() {
        if (instance != null || unavailable) return instance;
        try (InputStream in = BootFont.class.getResourceAsStream("/assets/carnation/boot/font.json")) {
            if (in == null) throw new IOException("Missing boot font metrics");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            instance = new BootFont(JsonParser.parseString(json).getAsJsonObject());
        } catch (Throwable throwable) {
            unavailable = true;
            org.slf4j.LoggerFactory.getLogger("Carnation/Boot").warn("Boot atlas unavailable", throwable);
        }
        return instance;
    }

    public GpuTextureView texture(Weight weight) {
        return (weight == Weight.BOLD ? boldTexture : regularTexture).getTextureView();
    }

    public GpuSampler sampler() {
        return sampler;
    }

    public Glyph glyph(Weight weight, char c) {
        Map<Character, Glyph> map = weight == Weight.BOLD ? bold : regular;
        Glyph glyph = map.get(c);
        if (glyph == null) glyph = map.get('?');
        return glyph;
    }

    /** Whether every character of {@code text} exists in the atlas at this weight. */
    public boolean covers(String text, Weight weight) {
        if (text == null) return false;
        Map<Character, Glyph> map = weight == Weight.BOLD ? bold : regular;
        for (int i = 0; i < text.length(); i++) {
            if (!map.containsKey(text.charAt(i))) return false;
        }
        return true;
    }

    /** Width of a line in the same units {@code em} is given in. */
    public float width(String text, Weight weight, float em, float tracking) {
        if (text == null || text.isEmpty()) return 0;
        float pen = 0;
        for (int i = 0; i < text.length(); i++) {
            Glyph g = glyph(weight, text.charAt(i));
            if (g != null) pen += g.advance() * em + tracking;
        }
        return Math.max(0, pen - tracking);
    }

    /** Adds the line's quads to {@code mesh}; {@code baseline} is the text baseline. */
    public void draw(BootQuads mesh, String text, float x, float baseline, float em, Weight weight,
                     float tracking, int color) {
        if (text == null || text.isEmpty()) return;
        float pen = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph g = glyph(weight, c);
            if (g == null) continue;
            if (g.width() > 0 && g.height() > 0) {
                float left = pen + g.left() * em;
                float top = baseline + g.top() * em;
                mesh.quad(left, top, left + g.width() * em, top + g.height() * em,
                        g.u0(), g.v0(), g.u1(), g.v1(), color);
            }
            pen += g.advance() * em + tracking;
        }
    }
}
