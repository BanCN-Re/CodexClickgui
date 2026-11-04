package wtf.carnation.ui.rhine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import wtf.carnation.boot.BootFont;
import wtf.carnation.boot.BootPipelines;
import wtf.carnation.boot.BootQuads;
import wtf.carnation.graphics.Render2D;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Rhine loading screen shown while terrain / chunks stream in after a world join.
 *
 * <p>Replaces {@code LevelLoadingScreen}'s chunk map and "Loading terrain..." bar with the same
 * paper language as the boot sequence: archive mark, rotating ring, segmented progress and the
 * world name. Real client progress drives the bar.
 */
public final class RhineLoading {
    private static final float W = 1600f, H = 900f;

    private static final int PAPER = 0xFFEFEEE9;
    private static final int PAPER_DEEP = 0xFFE9E8E1;
    private static final int PAPER_LIGHT = 0xFFF4F3EE;
    private static final int INK = 0xFF14160F;
    private static final int INK_SOFT = 0xFF3C3F36;
    private static final int GRAY = 0xFF8C8B83;
    private static final int ACCENT = 0xFFD8532B;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Map<Screen, Long> OPENED = new WeakHashMap<>();

    private RhineLoading() {
    }

    public static void render(GuiGraphicsExtractor g, float progress, Screen screen) {
        long now = Util.getMillis();
        long opened = OPENED.computeIfAbsent(screen, key -> now);
        float p = Math.max(0, Math.min(1, progress));

        BootFont font = BootFont.get();
        BootQuads bold = null, regular = null;

        float guiWidth = g.guiWidth(), guiHeight = g.guiHeight();
        Render2D.rect(g, 0, 0, guiWidth, guiHeight, PAPER);

        float scale = Math.min(guiWidth / W, guiHeight / H);
        float originX = (guiWidth - W * scale) * 0.5f;
        float originY = (guiHeight - H * scale) * 0.5f;
        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale, scale);

        if (font != null) {
            bold = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                    TextureSetup.singleTexture(font.texture(BootFont.Weight.BOLD), font.sampler()));
            regular = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                    TextureSetup.singleTexture(font.texture(BootFont.Weight.REGULAR), font.sampler()));
            bold.begin(g.pose(), g.scissorStack.peek());
            regular.begin(g.pose(), g.scissorStack.peek());
        }

        pool(g, 1180, 80, 620, PAPER_LIGHT, 20);
        pool(g, 220, 840, 560, PAPER_DEEP, 16);
        int grid = Render2D.withAlpha(INK, 6);
        for (float x = 100; x < W; x += 100) Render2D.rect(g, x, 0, 0.65f, H, grid);
        for (float y = 100; y < H; y += 100) Render2D.rect(g, 0, y, W, 0.65f, grid);

        float sweep = (float) (((now * 0.05) % (H + 200)) - 100);
        Render2D.rect(g, 0, sweep, W, 1, Render2D.withAlpha(INK, 4));

        if (regular != null) {
            label(font, bold, "RHINE LAB", 56, 110, 46, BootFont.Weight.BOLD, 0.012f, INK);
            label(font, regular, "SYNTHESIZE INFORMATION", 56, 134, 12.5f, BootFont.Weight.REGULAR, 0.115f, GRAY);
            label(font, regular, "ANALYSIS OS", 56, 179, 35, BootFont.Weight.REGULAR, 0.285f, INK);
            Render2D.rect(g, 56, 205, 430, 1, Render2D.withAlpha(INK, 50));

            String time = LocalTime.now().format(CLOCK);
            right(font, regular, time, 1544, 118, 13, BootFont.Weight.REGULAR, 0.12f, INK);
            right(font, regular, "TERRAIN ACCESS", 1544, 142, 10, BootFont.Weight.REGULAR, 0.30f, GRAY);
        }

        // Archive mark inside a rotating technical ring.
        float cx = 800, cy = 392;
        Render2D.circleOutline(g, cx, cy, 176, 1, Render2D.withAlpha(INK, 40));
        float spin = (float) (now % 600000L) * 0.05f;
        arc(g, cx, cy, 190, spin, 64, 1.4f, Render2D.withAlpha(INK, 150));
        arc(g, cx, cy, 190, spin + 180, 42, 1.4f, Render2D.withAlpha(ACCENT, 150));
        int tick = Render2D.withAlpha(INK, 55);
        for (int i = 0; i < 48; i++) {
            double angle = Math.toRadians(i * 7.5 + spin * 0.3);
            float inner = 204, outer = i % 6 == 0 ? 214 : 210;
            Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1, tick);
        }
        float pulse = 1f + 0.008f * (float) Math.sin(now / 520.0);
        logo(g, cx, cy, 300 * pulse, INK);

        if (regular != null) {
            String name = worldName();
            centered(font, bold, name, W * 0.5f, 604, 38, BootFont.Weight.BOLD, 0.03f, INK);

            float x = 590, y = 654, width = 420;
            Render2D.rect(g, x, y, width, 2, Render2D.withAlpha(INK, 40));
            Render2D.rect(g, x, y, width * p, 2, INK);
            for (int i = 0; i <= 20; i++) {
                Render2D.rect(g, x + i * (width / 20f), y - 4, 1, 8, Render2D.withAlpha(INK, 45));
            }
            right(font, bold, Math.round(p * 100) + "%", 1024, 660, 12, BootFont.Weight.BOLD, 0.02f, INK);
            centered(font, regular, p >= 0.999f ? "READY TO PLAY" : "GENERATING TERRAIN",
                    W * 0.5f, 700, 10.5f, 0.42f, GRAY);

            label(font, regular, "身份信息确认：" + operator(), 56, 850, 21, BootFont.Weight.REGULAR, 0.04f,
                    GRAY);
            String pre = "POWERED BY ";
            float em = 13.5f;
            float widthPre = font.width(pre, BootFont.Weight.REGULAR, em, 0.10f * em);
            float widthMark = font.width("RHINE LAB", BootFont.Weight.BOLD, em, 0.02f * em);
            float bar = 32, gap = 10;
            float start = 1544 - (widthPre + widthMark + gap + bar);
            label(font, regular, pre, start, 850, em, BootFont.Weight.REGULAR, 0.10f, GRAY);
            label(font, bold, "RHINE LAB", start + widthPre, 850, em, BootFont.Weight.BOLD, 0.02f, INK);
            Render2D.rect(g, start + widthPre + widthMark + gap, 842, bar, 7, INK);
        }

        g.pose().popMatrix();

        if (bold != null && !bold.isEmpty()) g.guiRenderState.addGuiElement(bold);
        if (regular != null && !regular.isEmpty()) g.guiRenderState.addGuiElement(regular);

        float appear = Math.min(1f, (now - opened) / 300f);
        if (appear < 1) {
            Render2D.rect(g, 0, 0, guiWidth, guiHeight,
                    Render2D.withAlpha(PAPER, Math.round((1 - appear) * 130)));
        }
    }

    private static void logo(GuiGraphicsExtractor g, float cx, float cy, float width, int ink) {
        RhineMarkTexture.draw(g, cx, cy, width, ink);
    }

    private static void pool(GuiGraphicsExtractor g, float cx, float cy, float radius, int color, int layers) {
        for (int i = layers; i >= 1; i--) {
            Render2D.circle(g, cx, cy, radius * i / layers, Render2D.withAlpha(color, 2));
        }
    }

    private static void arc(GuiGraphicsExtractor g, float cx, float cy, float radius, float startDeg,
                            float sweepDeg, float width, int color) {
        if ((color >>> 24) == 0) return;
        int steps = Math.max(8, Math.round(Math.abs(sweepDeg) / 3.5f));
        float previousX = 0, previousY = 0;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(startDeg + sweepDeg * i / (float) steps);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (i > 0) Render2D.line(g, previousX, previousY, x, y, width, color);
            previousX = x;
            previousY = y;
        }
    }

    private static String worldName() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getSingleplayerServer() != null) {
                String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
                if (name != null && !name.isBlank()) return name.toUpperCase(Locale.ROOT);
            }
            if (minecraft.getCurrentServer() != null) {
                String name = minecraft.getCurrentServer().name;
                if (name != null && !name.isBlank()) return name.toUpperCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return "WORLD ACCESS";
    }

    private static String operator() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            String name = minecraft == null || minecraft.getUser() == null ? null : minecraft.getUser().getName();
            if (name == null || name.isBlank()) return "OPERATOR";
            return name.toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "OPERATOR";
        }
    }

    private static void label(BootFont font, BootQuads mesh, String text, float x, float baseline, float em,
                              BootFont.Weight weight, float trackingEm, int color) {
        if (mesh == null) return;
        font.draw(mesh, text, x, baseline, em, weight, trackingEm * em, color);
    }

    private static void centered(BootFont font, BootQuads mesh, String text, float cx, float baseline,
                                 float em, float trackingEm, int color) {
        if (mesh == null) return;
        float width = font.width(text, BootFont.Weight.REGULAR, em, trackingEm * em);
        font.draw(mesh, text, cx - width * 0.5f, baseline, em, BootFont.Weight.REGULAR, trackingEm * em, color);
    }

    private static void centered(BootFont font, BootQuads mesh, String text, float cx, float baseline,
                                 float em, BootFont.Weight weight, float trackingEm, int color) {
        if (mesh == null) return;
        float width = font.width(text, weight, em, trackingEm * em);
        font.draw(mesh, text, cx - width * 0.5f, baseline, em, weight, trackingEm * em, color);
    }

    private static void right(BootFont font, BootQuads mesh, String text, float right, float baseline,
                              float em, BootFont.Weight weight, float trackingEm, int color) {
        if (mesh == null) return;
        float width = font.width(text, weight, em, trackingEm * em);
        font.draw(mesh, text, right - width, baseline, em, weight, trackingEm * em, color);
    }
}
