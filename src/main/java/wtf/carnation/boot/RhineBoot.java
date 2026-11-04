package wtf.carnation.boot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.Util;
import wtf.carnation.Client;
import wtf.carnation.audio.RhineSounds;
import wtf.carnation.graphics.Render2D;

import java.util.Locale;

/**
 * The Rhine Lab boot sequence that replaces vanilla's Mojang logo + progress bar.
 *
 * <p>It is drawn from {@code LoadingOverlay.extractRenderState}, i.e. while the resource reload is
 * still running. That constraint shapes everything here: no {@code Font} (glyph providers are
 * loaded by the reload itself), no screen widgets, only GUI triangles and the pre-baked glyph
 * atlas. Colour, motion and the timeline are all local.
 *
 * <p>Nine scenes cut from the reference terminal: power-on self test, permission request, mark
 * draw, identity check, archive mount, structural analysis, permission scan, clearance stamp and
 * welcome. Real reload progress drives the percentage, the bar and the manifest. Any key skips to
 * the closing flash; {@code .boot} replays the sequence in game.
 */
public final class RhineBoot {
    // The design canvas; everything is laid out in these units and scaled to the window.
    public static final float W = 1600f, H = 900f;

    // Scene boundaries in milliseconds; HOLD is the fully composed welcome frame.
    private static final double POWER_AT = 0, ACCESS_AT = 2200, MARK_AT = 4400, AUTH_AT = 7400,
            MOUNT_AT = 10000, ANALYSIS_AT = 12800, SCAN_AT = 15800, CLEARANCE_AT = 19400,
            WELCOME_AT = 21200;
    private static final double HOLD = 25800, TOTAL = 26600;
    private static final double[] CUE_TIMES = {
            ACCESS_AT + 50, MARK_AT + 1200, AUTH_AT + 50, MOUNT_AT + 100,
            MOUNT_AT + 800, ANALYSIS_AT + 100, SCAN_AT, WELCOME_AT + 320
    };

    // Palette sampled from the reference boot screen: warm paper, near-black ink, one accent.
    private static final int PAPER = 0xFFEFEEE9;
    private static final int PAPER_DEEP = 0xFFE9E8E1;
    private static final int PAPER_LIGHT = 0xFFF4F3EE;
    private static final int INK = 0xFF14160F;
    private static final int INK_SOFT = 0xFF3C3F36;
    private static final int GRAY = 0xFF8C8B83;
    private static final int ACCENT = 0xFFD8532B;

    /** Set by the keyboard hook; a press after the sequence started skips to the closing flash. */
    private static volatile long skipAt;

    private final long startedAt;
    private final boolean replay;
    private double clock;
    private long lastFrame;
    private long holdSince = -1;
    private long whiteSince = -1;
    private boolean exiting;
    private boolean finished;
    private float progressShown;

    private final int[] typedChars = new int[5];
    private int cueIndex;

    private BootFont font;
    private BootQuads boldMesh;
    private BootQuads regularMesh;

    public RhineBoot(long now, long offsetMs, boolean replay) {
        this.replay = replay;
        this.startedAt = now;
        this.clock = Math.max(0, offsetMs);
        this.lastFrame = now;
    }

    /** Called from the keyboard handler; ignored when no sequence is on screen. */
    public static void requestSkip() {
        skipAt = Util.getMillis();
    }

    /** True once the white flash has fully covered the screen; the overlay can be removed. */
    public boolean finished() {
        return finished;
    }

    /** Current position on the boot timeline, in milliseconds. */
    // ---- clock --------------------------------------------------------------------------------

    public void update(long now, boolean fading) {
        double delta = Math.min(90, Math.max(0, now - lastFrame));
        lastFrame = now;

        if (!exiting) {
            clock = Math.min(HOLD, clock + delta);
            if (skipAt > startedAt) exiting = true;
            if (fading && clock >= HOLD - 1) exiting = true;
            if (replay && clock >= HOLD - 1) {
                if (holdSince < 0) holdSince = now;
                else if (now - holdSince > 550) exiting = true;
            }
        }

        if (exiting) {
            // A skip runs the remaining scenes at 14x; otherwise the last scene plays normally.
            clock = Math.min(TOTAL, clock + delta * (skipAt > startedAt ? 14.0 : 1.0));
            if (clock >= TOTAL - 1) {
                if (whiteSince < 0) whiteSince = now;
                else if (now - whiteSince >= 180) finished = true;
            }
        }
    }

    // ---- frame --------------------------------------------------------------------------------

    public void render(GuiGraphicsExtractor g, float actualProgress, float vanillaProgress,
                       long fadeStartedAt, long now) {
        update(now, fadeStartedAt > 0);
        playCues(clock);

        float target = actualProgress < 0
                ? clamp01(clock / HOLD)
                : clamp01(Math.max(actualProgress, vanillaProgress));
        progressShown += (target - progressShown) * 0.18f;

        font = BootFont.get();

        float guiWidth = g.guiWidth(), guiHeight = g.guiHeight();
        Render2D.rect(g, 0, 0, guiWidth, guiHeight, PAPER);

        float scale = Math.min(guiWidth / W, guiHeight / H);
        float originX = (guiWidth - W * scale) * 0.5f;
        float originY = (guiHeight - H * scale) * 0.5f;
        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale, scale);

        if (font != null) {
            if (boldMesh == null) {
                boldMesh = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                        TextureSetup.singleTexture(font.texture(BootFont.Weight.BOLD), font.sampler()));
                regularMesh = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                        TextureSetup.singleTexture(font.texture(BootFont.Weight.REGULAR), font.sampler()));
            }
            boldMesh.begin(g.pose(), g.scissorStack.peek());
            regularMesh.begin(g.pose(), g.scissorStack.peek());
        }

        double t = clock;

        backdrop(g);
        frameMarks(g);
        sweep(g, t);
        topLeft(g, t);
        topRight(g, t);
        powerOn(g, t);
        accessLine(g, t);
        requestPanel(g, t);
        schematic(g, t);
        markAndAuth(g, t);
        manifest(g, t);
        analysis(g, t);
        scan(g, t);
        clearance(g, t);
        welcome(g, t);
        captions(g, t);
        logs(g, t);
        progress(g, t);
        skipHint(g, t);

        g.pose().popMatrix();

        if (font != null) {
            submit(g, boldMesh);
            submit(g, regularMesh);
        }

        float flash = smooth((float) ((clock - (HOLD + 120)) / (TOTAL - HOLD - 120)));
        if (flash > 0) {
            Render2D.rect(g, 0, 0, guiWidth, guiHeight, Render2D.withAlpha(0xFFFFFFFF, Math.round(flash * 255)));
        }
    }

    private static void submit(GuiGraphicsExtractor g, BootQuads mesh) {
        if (mesh != null && !mesh.isEmpty()) g.guiRenderState.addGuiElement(mesh);
    }

    // ---- backdrop -----------------------------------------------------------------------------

    private void backdrop(GuiGraphicsExtractor g) {
        pool(g, 1180, 80, 620, PAPER_LIGHT, 22);
        pool(g, 220, 840, 560, PAPER_DEEP, 18);
        int grid = Render2D.withAlpha(INK, 7);
        for (float x = 100; x < W; x += 100) Render2D.rect(g, x, 0, 0.65f, H, grid);
        for (float y = 100; y < H; y += 100) Render2D.rect(g, 0, y, W, 0.65f, grid);
    }

    /** A soft radial wash built from stacked discs; a single disc leaves a visible rim. */
    private static void pool(GuiGraphicsExtractor g, float cx, float cy, float radius, int color, int layers) {
        for (int i = layers; i >= 1; i--) {
            Render2D.circle(g, cx, cy, radius * i / layers, Render2D.withAlpha(color, 2));
        }
    }

    private void frameMarks(GuiGraphicsExtractor g) {
        int color = a(INK, 0.35f * seg(clock, 150, 700));
        if (color >>> 24 == 0) return;
        float inset = 26, length = 18;
        corner(g, inset, inset, length, 1, 1, color);
        corner(g, W - inset, inset, length, -1, 1, color);
        corner(g, inset, H - inset, length, 1, -1, color);
        corner(g, W - inset, H - inset, length, -1, -1, color);
    }

    private static void corner(GuiGraphicsExtractor g, float x, float y, float length, int dx, int dy, int color) {
        float x0 = dx > 0 ? x : x - length;
        float y0 = dy > 0 ? y : y - length;
        Render2D.rect(g, x0, y, length, 1, color);
        Render2D.rect(g, x, y0, 1, length, color);
    }

    /** A faint horizontal read line drifting down the page. */
    private void sweep(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, 1200, 1800) * (1 - seg(t, 21700, 22100));
        if (alpha <= 0) return;
        double span = H + 240;
        float y = (float) ((((t - 1200) * 0.15) % span + span) % span) - 120;
        Render2D.rect(g, 0, y, W, 1, Render2D.withAlpha(INK, Math.round(7 * alpha)));
        Render2D.rect(g, 0, y - 4, W, 1, Render2D.withAlpha(INK, Math.round(3 * alpha)));
    }

    private void topLeft(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, 220, 680);
        if (alpha <= 0) return;
        label("RHINE LAB", 56, 110, 46, BootFont.Weight.BOLD, 0.012f, a(INK, alpha));
        label("SYNTHESIZE INFORMATION", 56, 134, 12.5f, BootFont.Weight.REGULAR, 0.115f, a(GRAY, alpha));
        label("ANALYSIS OS", 56, 179, 35, BootFont.Weight.REGULAR, 0.285f, a(INK, alpha));

        float rule = seg(t, 400, 900);
        Render2D.rect(g, 56, 205, 430 * rule, 1, a(INK, 0.22f * alpha));
    }

    private void topRight(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, 500, 950);
        if (alpha <= 0) return;
        String text = "ENTER SYSTEM";
        float em = 12.5f, tracking = 0.14f;
        float width = font.width(text, BootFont.Weight.REGULAR, em, tracking * em);
        float right = 1528;
        label(text, right - width, 118, em, BootFont.Weight.REGULAR, tracking, a(GRAY, alpha));

        int ink = a(INK, 0.8f * alpha);
        float ax = right + 8, ay = 121;
        Render2D.line(g, ax, ay, ax + 20, ay - 20, 1.3f, ink);
        Render2D.line(g, ax + 20, ay - 20, ax + 8, ay - 19, 1.3f, ink);
        Render2D.line(g, ax + 20, ay - 20, ax + 19, ay - 8, 1.3f, ink);
    }

    // ---- scene 1: power-on self test ----------------------------------------------------------

    private void powerOn(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, 40, 260) * (1 - seg(t, 1950, 2190));
        if (alpha <= 0) return;
        float cx = W * 0.5f;

        if (font != null) {
            String title = "RHINE LAB SECURE SHELL";
            float em = 13.5f, tracking = 0.38f;
            float full = font.width(title, BootFont.Weight.REGULAR, em, tracking * em);
            label(reveal(title, t, 120, 620), cx - full * 0.5f, 356, em,
                    BootFont.Weight.REGULAR, tracking, a(INK, alpha));
            centered("CARNATION // " + Client.ver + " // JAVA " + javaVersion(), cx, 384, 10.5f, 0.30f,
                    a(GRAY, alpha * seg(t, 420, 760)));
        }

        float rule = seg(t, 260, 1080);
        if (rule > 0) {
            Render2D.rect(g, cx - 190, 404, 380 * rule, 1, a(INK, 0.28f * alpha));
            Render2D.rect(g, cx - 190 + 380 * rule - 2, 401, 2, 7, a(ACCENT, alpha));
        }

        if (font == null) return;
        String[][] post = {
                {"CPU CORE", "OK"},
                {"GRAPHICS", "VULKAN"},
                {"MEMORY", memory() + " MB"},
                {"PACKAGES", moduleCount() + " MODULES"},
        };
        float left = 610, right = 990, em = 11, tracking = 0.08f;
        for (int i = 0; i < post.length; i++) {
            float start = 700 + i * 220;
            float rowAlpha = alpha * seg(t, start, start + 160);
            if (rowAlpha <= 0) continue;
            float baseline = 452 + i * 24;
            label(post[i][0], left, baseline, em, BootFont.Weight.REGULAR, tracking, a(GRAY, rowAlpha));
            float valueWidth = font.width(post[i][1], BootFont.Weight.REGULAR, em, tracking * em);
            label(post[i][1], right - valueWidth, baseline, em, BootFont.Weight.REGULAR, tracking,
                    a(INK, rowAlpha));
            Render2D.rect(g, left + 120, baseline - 4, right - left - 120 - valueWidth - 16, 1,
                    a(INK, 0.12f * rowAlpha));
        }

        float bar = seg(t, 700, 1900);
        if (bar > 0) {
            float x = 610, y = 566, width = 380;
            Render2D.rect(g, x, y, width, 2, a(INK, 0.14f * alpha));
            Render2D.rect(g, x, y, width * bar, 2, a(INK, 0.8f * alpha));
            for (int i = 0; i <= 24; i++) {
                Render2D.rect(g, x + i * (width / 24f), y - 2, 1, 6, a(INK, 0.20f * alpha));
            }
        }
    }

    // ---- scene 2: permission request ----------------------------------------------------------

    private void accessLine(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, ACCESS_AT, ACCESS_AT + 280) * (1 - seg(t, ACCESS_AT + 1700, ACCESS_AT + 2150));
        if (alpha <= 0) return;
        float cx = W * 0.5f, baseline = 420, em = 26, tracking = 0.40f;
        if (font != null) {
            String text = "ACCESS PERMISSION REQUIRED";
            float full = font.width(text, BootFont.Weight.REGULAR, em, tracking * em);
            String typed = reveal(text, t, ACCESS_AT + 50, ACCESS_AT + 700);
            typeTick(typed.length(), 0);
            label(typed, cx - full * 0.5f, baseline, em, BootFont.Weight.REGULAR, tracking, a(INK, alpha));
            if (t < ACCESS_AT + 780 && ((long) (clock / 420) & 1L) == 0L) {
                float pen = cx - full * 0.5f + font.width(typed, BootFont.Weight.REGULAR, em, tracking * em) + 3;
                Render2D.rect(g, pen, baseline - 18, 11, 22, a(ACCENT, alpha * 0.9f));
            }
            centered("RHINE LAB  //  SECURE SHELL", cx, 360, 11.5f, tracking * 0.8f, a(GRAY, alpha * 0.9f));
        }
        float rule = seg(t, ACCESS_AT + 200, ACCESS_AT + 1100);
        Render2D.rect(g, W * 0.5f - 330, baseline + 26, 660 * rule, 1, a(INK, 0.20f * alpha));
    }

    /** The three credential rows under the request line. */
    private void requestPanel(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, ACCESS_AT + 450, ACCESS_AT + 650)
                * (1 - seg(t, ACCESS_AT + 1700, ACCESS_AT + 2150));
        if (alpha <= 0) return;

        float left = 560, right = 1040;
        label("PERMISSION REQUEST", left, 484, 11.5f, BootFont.Weight.REGULAR, 0.30f, a(GRAY, alpha));
        Render2D.rect(g, left, 492, right - left, 1, a(INK, 0.20f * alpha));

        String[][] rows = {
                {"OPERATOR", operator()},
                {"CLEARANCE", "LEVEL 04"},
                {"REQUEST", "RX-0xA7F3-19C2"},
        };
        float em = 14, tracking = 0.10f;
        for (int i = 0; i < rows.length; i++) {
            double start = ACCESS_AT + 530 + i * 200;
            float rowAlpha = alpha * seg(t, start, start + 180);
            if (rowAlpha <= 0) continue;
            float baseline = 522 + i * 34;
            label(rows[i][0], left, baseline, em, BootFont.Weight.REGULAR, tracking, a(GRAY, rowAlpha));
            float valueWidth = font.width(rows[i][1], BootFont.Weight.REGULAR, em, tracking * em);
            label(rows[i][1], right - valueWidth, baseline, em, BootFont.Weight.REGULAR, tracking,
                    a(INK, rowAlpha));
            Render2D.rect(g, left + 165, baseline - 4, right - left - 165 - valueWidth - 18, 1,
                    a(INK, 0.16f * rowAlpha));
        }

        float waiting = seg(t, ACCESS_AT + 1250, ACCESS_AT + 1450) * alpha;
        if (waiting > 0) {
            float blink = 0.55f + 0.45f * (float) Math.abs(Math.sin(clock / 380.0));
            centered("AWAITING AUTHORIZATION", W * 0.5f, 642, 11, 0.34f, a(ACCENT, waiting * blink));
        }
    }

    // ---- scene 3: mark draw + identity --------------------------------------------------------

    private void markAndAuth(GuiGraphicsExtractor g, double t) {
        float logoIn = seg(t, MARK_AT, MARK_AT + 400);
        if (logoIn <= 0) return;

        float welcome = seg(t, WELCOME_AT, WELCOME_AT + 350);
        float cx = 636 + 64 * welcome, cy = 442 - 112 * welcome;
        float size = 1 - 0.36f * welcome;

        // The mark hands the stage to the cassette and the stamp, then returns for the welcome.
        float logoAlpha;
        if (t < 12400) logoAlpha = logoIn;
        else if (t < 15900) logoAlpha = logoIn * (1 - seg(t, 12400, 12800));
        else if (t < WELCOME_AT) logoAlpha = 0;
        else logoAlpha = seg(t, WELCOME_AT, WELCOME_AT + 350);

        // The reference opens by drawing the contour, then the filled plate settles over it.
        float strokeDraw = seg(t, MARK_AT, MARK_AT + 1400);
        float filled = seg(t, MARK_AT + 1350, MARK_AT + 1700);
        if (logoAlpha > 0) {
            if (strokeDraw > 0 && filled < 1) {
                wtf.carnation.ui.rhine.RhineMarkTexture.drawContour(g, cx, cy, 292 * size,
                        a(INK, logoAlpha * (1 - filled)), strokeDraw);
            }
            if (filled > 0) {
                wtf.carnation.ui.rhine.RhineMarkTexture.draw(g, cx, cy, 292 * size,
                        a(INK, logoAlpha * filled));
            }
        }

        if (font != null) markCallouts(g, t, cx, cy, size);

        if (font == null) return;
        float markAlpha = logoIn * (1 - seg(t, 12400, 12800));
        if (markAlpha > 0) {
            String mark = "RHINE·LAB";
            float em = 15, tracking = 0.52f;
            float full = font.width(mark, BootFont.Weight.BOLD, em, tracking * em);
            label(reveal(mark, t, MARK_AT + 300, MARK_AT + 1300), cx - full * 0.5f, cy + 121 * size, em,
                    BootFont.Weight.BOLD, tracking, a(INK, markAlpha));
        }

        float authAlpha = seg(t, AUTH_AT, AUTH_AT + 220) * (1 - seg(t, AUTH_AT + 2150, AUTH_AT + 2500));
        if (authAlpha > 0) {
            float x = 830, em = 22.5f, tracking = 0.03f;
            String name = operator();
            String line1 = "· ID CONFIRMED : " + name;
            String line2 = "· REQUEST RECEIVED";
            String line3 = "· START PROCESSING";
            String line4 = "· PERMISSION GRANTED";

            String first = reveal(line1, t, AUTH_AT + 50, AUTH_AT + 380);
            typeTick(first.length(), 1);
            label(first, x, 448, em, BootFont.Weight.REGULAR, tracking, a(INK, authAlpha));
            float second = seg(t, AUTH_AT + 700, AUTH_AT + 830);
            if (second > 0) {
                String secondText = reveal(line2, t, AUTH_AT + 710, AUTH_AT + 930);
                typeTick(secondText.length(), 2);
                label(secondText, x, 486, em, BootFont.Weight.REGULAR,
                        tracking, a(INK, authAlpha * second));
            }
            float third = seg(t, AUTH_AT + 1250, AUTH_AT + 1380);
            if (third > 0) {
                String typed = reveal(line3, t, AUTH_AT + 1260, AUTH_AT + 1510);
                typeTick(typed.length(), 3);
                int dots = typed.length() >= line3.length() ? (int) ((t - AUTH_AT - 1550) / 160) % 4 : 0;
                label(typed + ".".repeat(Math.max(0, dots)), x, 524, em, BootFont.Weight.REGULAR, tracking,
                        a(INK, authAlpha * third));
            }
            float fourth = seg(t, AUTH_AT + 1950, AUTH_AT + 2100);
            if (fourth > 0) {
                label(line4, x, 562, em, BootFont.Weight.REGULAR, tracking, a(ACCENT, authAlpha * fourth));
            }
        }
    }

    /** "+ ADDITION" / "- SUBTRACTION" leaders, as in the reference key visual. */
    private void markCallouts(GuiGraphicsExtractor g, double t, float cx, float cy, float size) {
        float alpha = seg(t, MARK_AT + 1500, MARK_AT + 1900) * (1 - seg(t, AUTH_AT, AUTH_AT + 200));
        if (alpha <= 0) return;
        int ink = a(INK, 0.55f * alpha);
        int dot = a(INK, 0.75f * alpha);

        float lobe = -wtf.carnation.ui.rhine.RhineMarkTexture.plusX(292 * size);
        float ex = cx - 156 * size, ey = cy - 88 * size;
        Render2D.line(g, cx - lobe, cy, ex, ey, 1, ink);
        Render2D.circle(g, cx - lobe, cy, 2.4f, dot);
        rightText("· ADDITION", ex - 8, ey + 4, 10.5f, 0.30f, ink);

        float ex2 = cx + 156 * size, ey2 = cy - 88 * size;
        Render2D.line(g, cx + lobe, cy, ex2, ey2, 1, ink);
        Render2D.circle(g, cx + lobe, cy, 2.4f, dot);
        leftText("· SUBTRACTION", ex2 + 8, ey2 + 4, 10.5f, 0.30f, ink);
    }

    private void playCues(double t) {
        if (exiting) return;
        while (cueIndex < CUE_TIMES.length && t >= CUE_TIMES[cueIndex]) {
            switch (cueIndex) {
                case 0 -> RhineSounds.voice(1);
                case 1 -> RhineSounds.brand();
                case 2 -> RhineSounds.voice(2);
                case 3 -> RhineSounds.voice(3);
                case 4 -> RhineSounds.ticksRun();
                case 5 -> RhineSounds.voice(4);
                case 6 -> RhineSounds.voice(5);
                case 7 -> RhineSounds.voice(6);
                default -> {
                }
            }
            cueIndex++;
        }
    }

    private void typeTick(int length, int slot) {
        if (length < typedChars[slot]) typedChars[slot] = length;
        if (length >= typedChars[slot] + 2) {
            typedChars[slot] = length;
            RhineSounds.key();
        }
    }

    private void logo(GuiGraphicsExtractor g, float cx, float cy, float size, float alpha) {
        wtf.carnation.ui.rhine.RhineMarkTexture.draw(g, cx, cy, 292 * size, a(INK, alpha));
    }

    // ---- scene 4: archive mount ---------------------------------------------------------------

    /** Technical-drawing overlay around the mark: brackets, dashed orbit, dimension line. */
    private void schematic(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, MOUNT_AT + 60, MOUNT_AT + 360) * (1 - seg(t, 12450, 12750));
        if (alpha <= 0) return;

        float x0 = 456, y0 = 302, x1 = 790, y1 = 582, cx = 636, cy = 442;
        int hair = a(INK, 0.30f * alpha), strong = a(INK, 0.60f * alpha);

        float arm = 20;
        bracket(g, x0, y0, arm, 1, 1, strong);
        bracket(g, x1, y0, arm, -1, 1, strong);
        bracket(g, x0, y1, arm, 1, -1, strong);
        bracket(g, x1, y1, arm, -1, -1, strong);

        float spin = (float) t * 0.02f;
        int dash = a(INK, 0.28f * alpha);
        for (int i = 0; i < 24; i++) {
            double angle = Math.toRadians(i * 15 + spin);
            float inner = 150, outer = 156;
            Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1.4f, dash);
        }

        Render2D.rect(g, 806, y0 + 20, 1, y1 - y0 - 40, hair);
        Render2D.rect(g, 800, y0 + 20, 12, 1, hair);
        Render2D.rect(g, 800, y1 - 20, 12, 1, hair);

        if (font != null) {
            centered("FIG.01  STRUCTURAL ARCHIVE", cx, 282, 11.5f, 0.30f, a(GRAY, alpha));
            centered("SCALE 1:1   //   R 64.0   //   H 280.0", cx, 610, 11.5f, 0.26f, a(GRAY, alpha));
        }
    }

    private static void bracket(GuiGraphicsExtractor g, float x, float y, float arm, int dx, int dy, int color) {
        float x0 = dx > 0 ? x : x - arm;
        float y0 = dy > 0 ? y : y - arm;
        Render2D.rect(g, x0, y, arm, 1, color);
        Render2D.rect(g, x, y0, 1, arm, color);
    }

    private void manifest(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, MOUNT_AT + 220, MOUNT_AT + 520) * (1 - seg(t, 12450, 12750));
        if (alpha <= 0) return;

        float x = 880, width = 440;
        label("ARCHIVE MANIFEST", x, 610, 12, BootFont.Weight.REGULAR, 0.28f, a(INK, alpha));
        Render2D.rect(g, x, 620, width, 1, a(INK, 0.22f * alpha));

        String[] names = {"TEXTURES", "MODELS", "SHADERS", "SOUND"};
        float[] ratios = {1.00f, 0.93f, 1.04f, 0.82f};
        float p = clamp01(progressShown);
        for (int i = 0; i < names.length; i++) {
            float baseline = 652 + i * 33;
            float fill = clamp01(p * ratios[i])
                    * seg(t, MOUNT_AT + 300 + i * 160, MOUNT_AT + 1300 + i * 160);
            label(names[i], x, baseline, 11.5f, BootFont.Weight.REGULAR, 0.18f, a(INK_SOFT, alpha));
            Render2D.rect(g, x + 110, baseline - 4, 220, 3, a(INK, 0.16f * alpha));
            Render2D.rect(g, x + 110, baseline - 4, 220 * fill, 3, a(INK, 0.9f * alpha));
            label(Math.round(fill * 100) + "%", x + 344, baseline, 11, BootFont.Weight.BOLD, 0.05f,
                    a(GRAY, alpha));
        }

        float mounted = seg(t, MOUNT_AT + 300, MOUNT_AT + 2000);
        int files = 1024 + Math.round(p * 3072 * mounted);
        label("FILES  " + files + " / 4096", x, 788, 11, BootFont.Weight.REGULAR, 0.20f, a(GRAY, alpha));
    }

    // ---- scene 5: structural analysis ---------------------------------------------------------

    /** The archive cassette as a technical drawing: exploded plates, callouts, parts list, title block. */
    private void analysis(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, ANALYSIS_AT, ANALYSIS_AT + 320) * (1 - seg(t, 15500, 15800));
        if (alpha <= 0) return;

        // A 3 px drift keeps the sheet from reading as a frozen frame.
        float cx = 700 + (float) Math.sin(t * 0.00062) * 3.5f;
        float cy = 450 + (float) Math.cos(t * 0.00048) * 2.5f;
        int ink = a(INK, 0.85f * alpha), hair = a(INK, 0.30f * alpha), faint = a(INK, 0.16f * alpha);
        float w = 420, h = 260;

        // Inspection sweep: a lit line crossing the part, wrapping every 1.7 s.
        float sweep = (float) (((t - ANALYSIS_AT) % 1700) / 1700);
        float sx = cx - w * 0.5f + w * sweep;
        for (int i = 1; i <= 8; i++) {
            Render2D.rect(g, sx - i * 2.2f, cy - h * 0.5f - 20, 2.2f, h + 40,
                    a(ACCENT, 0.018f * (1 - i / 9f) * alpha));
        }
        Render2D.rect(g, sx, cy - h * 0.5f - 20, 1, h + 40, a(ACCENT, 0.35f * alpha));

        // Plates: the cover lifts a few pixels off the base as the scene settles.
        float lift = 8 * seg(t, ANALYSIS_AT + 600, ANALYSIS_AT + 1400);
        Render2D.roundedOutline(g, cx - w * 0.5f, cy - h * 0.5f - lift, w, h, 18, 1.6f, ink);
        Render2D.roundedOutline(g, cx - w * 0.5f + 18, cy - h * 0.5f + 18 - lift, w - 36, h - 36, 12, 1, hair);

        // Section hatching along the left edge, so the drawing reads as a cut.
        for (int i = 0; i < 26; i++) {
            float y = cy - h * 0.5f + 26 + i * 8.2f - lift;
            Render2D.line(g, cx - w * 0.5f + 4, y, cx - w * 0.5f + 18, y + 10, 1, a(INK, 0.16f * alpha));
        }

        // Optical assembly: two rings joined by a bridge.
        Render2D.rect(g, cx - 70, cy - 9, 140, 18, a(INK, 0.22f * alpha));
        float ring = 62 * (0.88f + 0.12f * seg(t, ANALYSIS_AT + 200, ANALYSIS_AT + 900));
        Render2D.circleOutline(g, cx - 70, cy, ring, 12, ink);
        Render2D.circleOutline(g, cx + 70, cy, ring, 12, ink);
        Render2D.circle(g, cx - 70, cy, 8, hair);
        Render2D.circle(g, cx + 70, cy, 8, hair);
        arc(g, cx - 70, cy, ring + 13, -90 + (float) t * 0.05f, 90, 1, a(ACCENT, 0.45f * alpha));
        arc(g, cx + 70, cy, ring + 13, 90 - (float) t * 0.05f, 90, 1, a(ACCENT, 0.45f * alpha));

        // Inner bus and connector pins.
        Render2D.rect(g, cx - 150, cy + 96, 300, 1, a(INK, 0.22f * alpha));
        for (int i = 0; i < 5; i++) {
            float x = cx - 150 + i * 74;
            Render2D.rect(g, x, cy + 90, 1, 13, a(INK, 0.22f * alpha));
            Render2D.circle(g, x, cy + 96, 2.2f, a(INK, 0.35f * alpha));
        }
        for (int i = 0; i < 9; i++) {
            Render2D.rect(g, cx + w * 0.5f - 40, cy - 40 + i * 10, 12, 4, a(INK, 0.26f * alpha));
        }

        // Fasteners at the four corners.
        for (int sxSign = -1; sxSign <= 1; sxSign += 2) {
            for (int sySign = -1; sySign <= 1; sySign += 2) {
                float px = cx + sxSign * (w * 0.5f - 24), py = cy + sySign * (h * 0.5f - 24);
                Render2D.circle(g, px, py, 4, hair);
                Render2D.line(g, px - 7, py, px + 7, py, 1, faint);
                Render2D.line(g, px, py - 7, px, py + 7, 1, faint);
            }
        }

        // Dimension lines and their extension ticks.
        Render2D.rect(g, cx - w * 0.5f, cy - h * 0.5f - 34, w, 1, faint);
        Render2D.rect(g, cx - w * 0.5f, cy - h * 0.5f - 38, 1, 9, faint);
        Render2D.rect(g, cx + w * 0.5f, cy - h * 0.5f - 38, 1, 9, faint);
        Render2D.rect(g, cx - w * 0.5f - 34, cy - h * 0.5f, 1, h, faint);
        Render2D.rect(g, cx - w * 0.5f - 38, cy - h * 0.5f, 9, 1, faint);
        Render2D.rect(g, cx - w * 0.5f - 38, cy + h * 0.5f, 9, 1, faint);
        // Arrowheads turn the rules into real dimensions.
        for (int s = -1; s <= 1; s += 2) {
            float ex = cx + s * w * 0.5f, ey = cy - h * 0.5f - 34;
            Render2D.line(g, ex, ey, ex - s * 9, ey - 3.5f, 1, faint);
            Render2D.line(g, ex, ey, ex - s * 9, ey + 3.5f, 1, faint);
            float fx = cx - w * 0.5f - 34, fy = cy + s * h * 0.5f;
            Render2D.line(g, fx, fy, fx - 3.5f, fy - s * 9, 1, faint);
            Render2D.line(g, fx, fy, fx + 3.5f, fy - s * 9, 1, faint);
        }

        // Register crosses on the sheet, not on the part.
        for (int sxSign = -1; sxSign <= 1; sxSign += 2) {
            for (int sySign = -1; sySign <= 1; sySign += 2) {
                float px = cx + sxSign * (w * 0.5f + 52), py = cy + sySign * (h * 0.5f + 40);
                Render2D.line(g, px - 5, py, px + 5, py, 1, faint);
                Render2D.line(g, px, py - 5, px, py + 5, 1, faint);
            }
        }

        if (font != null) {
            centered("ARCHIVE CASSETTE  //  TYPE-04", cx, 290, 11, 0.30f, a(GRAY, alpha));
            centered("CROSS-SECTION 0.42", cx, 620, 11, 0.30f, a(GRAY, alpha));
            label("420.0", cx - 8, cy - h * 0.5f - 44, 10, BootFont.Weight.REGULAR, 0.1f, a(GRAY, alpha));
            label("260.0", cx - w * 0.5f - 78, cy + 4, 10, BootFont.Weight.REGULAR, 0.1f, a(GRAY, alpha));
            String read = "X " + String.format("%05.1f", (sx - cx + w * 0.5f) / w * 420);
            leftText(read, cx + w * 0.5f + 44, 636, 10, 0.14f, a(ACCENT, 0.7f * alpha));
        }

        callout(g, t, ANALYSIS_AT + 900, 910, 360, 990, 336, 1010, "GLASS PLATE", alpha);
        callout(g, t, ANALYSIS_AT + 1500, 770, 450, 990, 450, 1010, "OPTICAL CORE", alpha);
        callout(g, t, ANALYSIS_AT + 2100, 910, 548, 990, 566, 1010, "INFORMATION BASE", alpha);

        partsList(g, t, alpha);
        titleBlock(g, t, alpha);
    }

    /** Left column: the three parts the section is made of, revealed one at a time. */
    private void partsList(GuiGraphicsExtractor g, double t, float alpha) {
        if (font == null) return;
        String[] names = {"GLASS PLATE", "OPTICAL CORE", "INFORMATION BASE"};
        float x = 56;
        float in = seg(t, ANALYSIS_AT + 500, ANALYSIS_AT + 900) * alpha;
        if (in <= 0) return;
        label("PARTS LIST", x, 372, 11, BootFont.Weight.REGULAR, 0.24f, a(GRAY, in));
        Render2D.rect(g, x, 384, 360, 1, a(INK, 0.16f * in));

        for (int i = 0; i < names.length; i++) {
            float p = seg(t, ANALYSIS_AT + 620 + i * 220, ANALYSIS_AT + 880 + i * 220) * alpha;
            if (p <= 0) continue;
            float y = 420 + i * 36;
            label(String.format("%02d", i + 1), x, y, 13, BootFont.Weight.BOLD, 0.06f, a(ACCENT, p));
            label(names[i], x + 42, y, 12, BootFont.Weight.REGULAR, 0.18f, a(INK_SOFT, p));
            float width = font.width(names[i], BootFont.Weight.REGULAR, 12, 0.18f * 12);
            Render2D.rect(g, x + 42 + width + 14, y - 4, 300 * p, 1, a(INK, 0.10f * p));
        }

        float note = seg(t, ANALYSIS_AT + 1500, ANALYSIS_AT + 1800) * alpha;
        if (note > 0) {
            label("3 PARTS  //  1 ASSEMBLY", x, 548, 10.5f, BootFont.Weight.REGULAR, 0.22f, a(GRAY, note));
            Render2D.rect(g, x, 560, 200, 1, a(INK, 0.14f * note));
        }
    }

    /** Drawing title block in the lower right: the giveaway that this is a real sheet. */
    private void titleBlock(GuiGraphicsExtractor g, double t, float alpha) {
        if (font == null) return;
        float in = seg(t, ANALYSIS_AT + 500, ANALYSIS_AT + 1000) * alpha;
        if (in <= 0) return;

        float x = 1080, y = 688, w = 440, h = 112;
        Render2D.rect(g, x, y, w, 1.5f, a(INK, 0.55f * in));
        Render2D.rect(g, x, y + h, w, 1.5f, a(INK, 0.55f * in));
        Render2D.rect(g, x, y, 1.5f, h, a(INK, 0.55f * in));
        Render2D.rect(g, x + w, y, 1.5f, h, a(INK, 0.55f * in));
        Render2D.rect(g, x + w * 0.42f, y, 1, h, a(INK, 0.30f * in));
        Render2D.rect(g, x + w * 0.70f, y, 1, h, a(INK, 0.30f * in));
        Render2D.rect(g, x, y + 54, w, 1, a(INK, 0.30f * in));

        label("RHINE LAB  //  R&D DIVISION", x + 12, y + 22, 11, BootFont.Weight.REGULAR, 0.14f,
                a(INK, 0.85f * in));
        label("ARCHIVE CASSETTE TYPE-04", x + 12, y + 44, 10.5f, BootFont.Weight.REGULAR, 0.10f,
                a(GRAY, in));
        label("RL-0042-C", x + 12, y + 88, 19, BootFont.Weight.BOLD, 0.10f, a(INK, in));

        label("SCALE", x + w * 0.42f + 12, y + 22, 10, BootFont.Weight.REGULAR, 0.20f, a(GRAY, in));
        label("1 : 2", x + w * 0.42f + 12, y + 48, 15, BootFont.Weight.REGULAR, 0.06f, a(INK, in));
        label("SHEET", x + w * 0.42f + 12, y + 74, 10, BootFont.Weight.REGULAR, 0.20f, a(GRAY, in));
        label("1 / 1", x + w * 0.42f + 12, y + 98, 15, BootFont.Weight.REGULAR, 0.06f, a(INK, in));

        label("REV", x + w * 0.70f + 12, y + 22, 10, BootFont.Weight.REGULAR, 0.20f, a(GRAY, in));
        label("C", x + w * 0.70f + 12, y + 48, 15, BootFont.Weight.BOLD, 0.06f, a(ACCENT, in));
        label("STATE", x + w * 0.70f + 12, y + 74, 10, BootFont.Weight.REGULAR, 0.20f, a(GRAY, in));
        label("FINAL", x + w * 0.70f + 12, y + 98, 13, BootFont.Weight.REGULAR, 0.06f, a(INK, in));
    }

    private void callout(GuiGraphicsExtractor g, double t, double start, float x1, float y1,
                         float elbowX, float elbowY, float textX, String text, float alpha) {
        float p = seg(t, start, start + 320);
        if (p <= 0) return;
        int ink = a(INK, 0.55f * alpha);
        float ex = x1 + (elbowX - x1) * Math.min(1, p * 1.6f);
        float ey = y1 + (elbowY - y1) * Math.min(1, p * 1.6f);
        Render2D.line(g, x1, y1, ex, ey, 1, ink);
        if (p > 0.6f) {
            float q = (p - 0.6f) / 0.4f;
            Render2D.line(g, elbowX, elbowY, elbowX + (textX - 8 - elbowX) * q, elbowY, 1, ink);
            if (font != null) label(text, textX, elbowY + 4, 11, BootFont.Weight.REGULAR, 0.20f,
                    a(INK, alpha * q));
        }
        Render2D.circle(g, x1, y1, 2.5f, a(INK, 0.7f * alpha));
    }

    // ---- scene 6: permission scan -------------------------------------------------------------

    /** When the six checks have all resolved; the scene then holds on the granted state. */
    private static final double SCAN_DONE = SCAN_AT + 1850;

    private void scan(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, SCAN_AT, SCAN_AT + 260) * (1 - seg(t, 18800, 19400));
        if (alpha <= 0) return;
        float cx = 636, cy = 442;
        float done = seg(t, SCAN_DONE, SCAN_DONE + 340);

        scanField(g, t, alpha, cx, cy);
        scanReticle(g, t, alpha, cx, cy, done);
        scanScope(g, t, alpha);
        scanMatrix(g, t, alpha, done);
        scanCells(g, t, alpha);
    }

    /** The load grid under the instrument: one dot per four percent of the read. */
    private void scanCells(GuiGraphicsExtractor g, double t, float alpha) {
        float cx = 636, cy = 748;
        float p = clamp01((t - SCAN_AT) / (SCAN_DONE - SCAN_AT));
        int lit = Math.round(p * 25);
        for (int i = 0; i < 25; i++) {
            float x = cx - 28 + (i % 5) * 14, y = cy + (i / 5) * 14;
            boolean on = i < lit;
            Render2D.rect(g, x, y, on ? 5 : 3, on ? 5 : 3,
                    on ? a(ACCENT, 0.85f * alpha) : a(INK, 0.18f * alpha));
        }
    }

    /** Paper grid plus the read beam sweeping the instrument area. */
    private void scanField(GuiGraphicsExtractor g, double t, float alpha, float cx, float cy) {
        int grid = a(INK, 0.05f * alpha);
        for (int i = -6; i <= 6; i++) {
            Render2D.rect(g, cx + i * 60, cy - 210, 1, 420, grid);
            Render2D.rect(g, cx - 210, cy + i * 60, 420, 1, grid);
        }

        // A hard line with a soft band trailing behind it, wrapping once per 1.5 s.
        float span = 420, period = 1500;
        float by = cy - 210 + (float) (((t - SCAN_AT) % period) / period) * span;
        for (int i = 1; i <= 10; i++) {
            float h = i * 1.8f;
            Render2D.rect(g, cx - 210, by - h, 420, 1.8f, a(ACCENT, 0.008f * (1 - i / 11f) * alpha));
        }
        Render2D.rect(g, cx - 210, by, 420, 1, a(ACCENT, 0.5f * alpha));
    }

    private void scanReticle(GuiGraphicsExtractor g, double t, float alpha, float cx, float cy,
                             float done) {
        float dt = (float) (t - SCAN_AT);
        float spin = dt * 0.06f;
        float head = -90 + dt * 0.42f;

        // Sonar pings: rings leave the core twice while the aperture turns.
        for (int i = 0; i < 2; i++) {
            float p = (dt / 1150f + i * 0.5f) % 1f;
            Render2D.circleOutline(g, cx, cy, 40 + p * 148, 1.3f,
                    a(ACCENT, 0.32f * (1 - p) * alpha));
        }

        Render2D.circleOutline(g, cx, cy, 170, 1.1f, a(INK, 0.18f * alpha));
        Render2D.circleOutline(g, cx, cy, 132, 1f, a(INK, 0.10f * alpha));

        // Radar sweep: fourteen ring segments fading behind a bright edge.
        for (int i = 0; i < 14; i++) {
            float a0 = head - (i + 1) * 4.4f, a1 = head - i * 4.4f;
            float f = 1 - i / 14f;
            wedge(g, cx, cy, 170, 24, a0, a1, a(ACCENT, 0.09f * f * f * alpha));
        }
        Render2D.line(g, cx, cy,
                cx + (float) Math.cos(Math.toRadians(head)) * 170,
                cy + (float) Math.sin(Math.toRadians(head)) * 170, 1.2f, a(ACCENT, 0.5f * alpha));

        // Spokes and a crosshair fill the aperture, so the core is never an empty dish.
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45 + 22.5 + spin * 0.2f);
            float inner = 52, outer = 112;
            Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1,
                    a(INK, 0.14f * alpha));
        }
        Render2D.rect(g, cx - 22, cy, 44, 1, a(INK, 0.35f * alpha));
        Render2D.rect(g, cx, cy - 22, 1, 44, a(INK, 0.35f * alpha));

        // Graduated bezel.
        int tick = a(INK, 0.45f * alpha);
        for (int i = 0; i < 48; i++) {
            double angle = Math.toRadians(i * 7.5 + spin * 0.35f);
            float inner = 176, outer = i % 6 == 0 ? 192 : 184;
            Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1.1f, tick);
        }

        // Counter-rotating aperture arcs.
        arc(g, cx, cy, 152, -spin * 1.1f, 128, 1.4f, a(ACCENT, 0.72f * alpha));
        arc(g, cx, cy, 152, -spin * 1.1f + 180, 62, 1.4f, a(ACCENT, 0.34f * alpha));
        arc(g, cx, cy, 164, spin * 0.8f, 40, 1f, a(INK, 0.32f * alpha));

        // Lock-on crosses appear one by one around the field.
        for (int i = 0; i < 8; i++) {
            float p = seg(t, SCAN_AT + 380 + i * 150, SCAN_AT + 560 + i * 150);
            if (p <= 0) continue;
            double angle = Math.toRadians(-78 + i * 45);
            float x = cx + (float) Math.cos(angle) * 112, y = cy + (float) Math.sin(angle) * 112;
            float size = 3 + 4 * p;
            int line = a(INK, 0.42f * p * alpha);
            Render2D.line(g, x - size, y, x + size, y, 1.1f, line);
            Render2D.line(g, x, y - size, x, y + size, 1.1f, line);
            Render2D.circle(g, x, y, 1.5f, a(ACCENT, 0.75f * p * alpha));
        }

        // Framing brackets snap in, then hold.
        float slide = 22 * (1 - seg(t, SCAN_AT + 80, SCAN_AT + 620));
        int bracket = a(INK, 0.55f * alpha);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                corner(g, cx + sx * (150 + slide), cy + sy * (150 + slide), 16, -sx, -sy, bracket);
            }
        }

        // Outer progress arc with a travelling head.
        float p = clamp01((t - SCAN_AT) / (SCAN_DONE - SCAN_AT));
        arc(g, cx, cy, 200, -90, 360 * p, 2.4f, a(INK, 0.72f * alpha));
        double headAngle = Math.toRadians(-90 + 360 * p);
        Render2D.circle(g, cx + (float) Math.cos(headAngle) * 200,
                cy + (float) Math.sin(headAngle) * 200, 3.2f, a(ACCENT, alpha));

        if (done > 0) {
            Render2D.circleOutline(g, cx, cy, 170 + 46 * done, 1.6f,
                    a(ACCENT, 0.55f * (1 - done) * alpha));
            Render2D.circle(g, cx, cy, 4 + 3 * (1 - done), a(ACCENT, 0.85f * alpha));
        } else {
            Render2D.circle(g, cx, cy, 3.5f, a(INK, 0.6f * alpha));
        }

        if (font != null) {
            boolean granted = done > 0.45f;
            centered(granted ? "PERMISSION AUTHORIZED" : "VERIFYING PERMISSION", cx, 244, 14.5f, 0.34f,
                    granted ? a(ACCENT, alpha) : a(INK, alpha));
            float rule = seg(t, SCAN_AT, SCAN_AT + 380);
            Render2D.rect(g, cx - 120 * rule, 258, 240 * rule, 1, a(INK, 0.18f * alpha));
            centered("SCAN " + Math.round(p * 100) + "%", cx, cy + 240, 11.5f, 0.30f,
                    a(GRAY, 0.9f * alpha));
        }
    }

    /** Left column: the scope the scan is walking through, with the raw read scrolling up. */
    private void scanScope(GuiGraphicsExtractor g, double t, float alpha) {
        if (font == null) return;
        float x = 56;
        label("PERMISSION SCOPE", x, 300, 11, BootFont.Weight.REGULAR, 0.22f, a(GRAY, alpha));
        label("04", x, 348, 34, BootFont.Weight.BOLD, 0.04f, a(INK, alpha));
        float width = font.width("04", BootFont.Weight.BOLD, 34, 0.04f * 34);
        label("/ 07", x + width + 9, 348, 13, BootFont.Weight.REGULAR, 0.16f, a(GRAY, alpha));

        // Load rail fills bottom-up as the scan advances.
        float p = clamp01((t - SCAN_AT) / (SCAN_DONE - SCAN_AT));
        float top = 386, bottom = 640;
        Render2D.rect(g, x, top, 1, bottom - top, a(INK, 0.16f * alpha));
        Render2D.rect(g, x, bottom - (bottom - top) * p, 1, (bottom - top) * p,
                a(ACCENT, 0.8f * alpha));
        for (int i = 0; i <= 12; i++) {
            float y = bottom - i * (bottom - top) / 12f;
            Render2D.rect(g, x, y, i % 3 == 0 ? 8 : 5, 1, a(INK, 0.22f * alpha));
        }

        // Hex readout: one new row per 90 ms, so the column crawls upward.
        int step = (int) ((t - SCAN_AT) / 90);
        for (int i = 0; i < 11; i++) {
            int seed = step - (10 - i);
            int v0 = noise(seed), v1 = noise(seed * 31 + 7);
            String text = String.format("%04X  %04X  %02X", v0 & 0xFFFF, v1 & 0xFFFF, (v0 >>> 16) & 0xFF);
            label(text, x + 20, 392 + i * 22, 11, BootFont.Weight.REGULAR, 0.06f,
                    a(INK_SOFT, (0.16f + 0.55f * i / 10f) * alpha));
        }

        long read = (long) ((t - SCAN_AT) * 1430);
        label("READ 0x" + String.format("%08X", read & 0xFFFFFFFFL), x, 692, 12.5f,
                BootFont.Weight.REGULAR, 0.10f, a(INK, 0.75f * alpha));
    }

    /** Right column: the six checks that must resolve before the stamp lands. */
    private void scanMatrix(GuiGraphicsExtractor g, double t, float alpha, float done) {
        if (font == null) return;
        String[][] rows = {
                {"HANDSHAKE", "OK"},
                {"SIGNATURE", "OK"},
                {"CLEARANCE", "04"},
                {"GATE", "OPEN"},
                {"INTEGRITY", "PASS"},
                {"SESSION", "READY"},
        };
        float left = 950, right = 1300, em = 14.5f, tracking = 0.06f;
        label("PERMISSION MATRIX", left, 320, 11, BootFont.Weight.REGULAR, 0.22f, a(GRAY, alpha));
        Render2D.rect(g, left, 332, right - left, 1, a(INK, 0.14f * alpha));

        for (int i = 0; i < rows.length; i++) {
            double start = SCAN_AT + 240 + i * 205;
            float p = seg(t, start, start + 220);
            if (p <= 0) continue;
            float baseline = 380 + i * 42;
            float rowAlpha = alpha * p;

            label(rows[i][0], left, baseline, em, BootFont.Weight.REGULAR, tracking,
                    a(INK_SOFT, rowAlpha));
            float nameWidth = font.width(rows[i][0], BootFont.Weight.REGULAR, em, tracking * em);

            String value = reveal(rows[i][1], t, start + 40, start + 220);
            float valueWidth = font.width(value, BootFont.Weight.REGULAR, em, tracking * em);
            label(value, right - valueWidth, baseline, em, BootFont.Weight.REGULAR, tracking,
                    a(INK, rowAlpha));

            float from = left + nameWidth + 14, to = right - 58;
            if (to > from) {
                Render2D.rect(g, from, baseline - 4, to - from, 1, a(INK, 0.10f * rowAlpha));
                float wipe = seg(t, start + 120, start + 300);
                if (wipe > 0) {
                    Render2D.rect(g, from, baseline - 4, (to - from) * wipe, 1,
                            a(ACCENT, 0.55f * rowAlpha));
                }
            }
            float mark = seg(t, start + 160, start + 300);
            if (mark > 0) Render2D.rect(g, right + 14, baseline - 8, 5 * mark, 5, a(ACCENT, 0.85f * rowAlpha));
        }

        float verdict = seg(t, SCAN_DONE - 100, SCAN_DONE + 200);
        if (verdict > 0) {
            float baseline = 380 + rows.length * 42;
            float tint = verdict * alpha;
            label("RESULT", left, baseline, 12, BootFont.Weight.REGULAR, 0.24f, a(GRAY, tint));
            label("PERMISSION GRANTED", left + 96, baseline, 17, BootFont.Weight.BOLD, 0.10f,
                    a(ACCENT, tint));
            Render2D.rect(g, left, baseline + 10, (right - left) * verdict, 1.5f, a(ACCENT, tint));
        }
    }

    /** A filled ring sector, used for the radar sweep. */
    private static void wedge(GuiGraphicsExtractor g, float cx, float cy, float radius, float inner,
                              float fromDeg, float toDeg, int color) {
        if ((color >>> 24) == 0) return;
        double a0 = Math.toRadians(fromDeg), a1 = Math.toRadians(toDeg);
        float ox0 = cx + (float) Math.cos(a0) * radius, oy0 = cy + (float) Math.sin(a0) * radius;
        float ox1 = cx + (float) Math.cos(a1) * radius, oy1 = cy + (float) Math.sin(a1) * radius;
        float ix0 = cx + (float) Math.cos(a0) * inner, iy0 = cy + (float) Math.sin(a0) * inner;
        float ix1 = cx + (float) Math.cos(a1) * inner, iy1 = cy + (float) Math.sin(a1) * inner;
        int clear = Render2D.withAlpha(color, 0);
        Render2D.quad(g, ix0, iy0, ox0, oy0, ox1, oy1, ix1, iy1, color, color, clear, clear);
    }

    // ---- scene 7: clearance stamp -------------------------------------------------------------

    private void clearance(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, CLEARANCE_AT + 40, CLEARANCE_AT + 300) * (1 - seg(t, 20800, 21200));
        if (alpha <= 0) return;

        float left = 280, top = 250, right = 1320, bottom = 640;
        float cx = (left + right) * 0.5f, cy = (top + bottom) * 0.5f;

        float slam = seg(t, CLEARANCE_AT + 40, CLEARANCE_AT + 320);
        float scale = 1.08f - 0.08f * slam;

        // The sheet snaps in: two hairlines settle outward from the card's edge.
        for (int i = 0; i < 2; i++) {
            float p = seg(t, CLEARANCE_AT + 60 + i * 90, CLEARANCE_AT + 420 + i * 90);
            if (p <= 0) continue;
            float pad = 10 + i * 14;
            Render2D.rect(g, left - pad * p, top - pad * p, right - left + pad * p * 2, 1,
                    a(INK, 0.16f * (1 - i * 0.4f) * alpha));
            Render2D.rect(g, left - pad * p, bottom + pad * p, right - left + pad * p * 2, 1,
                    a(INK, 0.16f * (1 - i * 0.4f) * alpha));
        }

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);

        Render2D.rect(g, left, top, right - left, bottom - top, a(PAPER_LIGHT, 0.55f * alpha));
        Render2D.rect(g, left, top, right - left, 2, a(INK, alpha));
        Render2D.rect(g, left, bottom, right - left, 2, a(INK, alpha));
        Render2D.rect(g, left, top, 2, bottom - top, a(INK, alpha));
        Render2D.rect(g, right, top, 2, bottom - top, a(INK, alpha));
        Render2D.rect(g, left + 14, top + 14, right - left - 28, 1, a(INK, 0.22f * alpha));
        Render2D.rect(g, left + 14, bottom - 14, right - left - 28, 1, a(INK, 0.22f * alpha));
        Render2D.rect(g, 660, top + 30, 1, bottom - top - 60, a(INK, 0.18f * alpha));

        // Corner ticks slide into the card's four corners.
        float slide = 26 * (1 - seg(t, CLEARANCE_AT + 40, CLEARANCE_AT + 460));
        int tick = a(INK, 0.65f * alpha);
        corner(g, left - 22 - slide, top - 22 - slide, 18, 1, 1, tick);
        corner(g, right + 22 + slide, top - 22 - slide, 18, -1, 1, tick);
        corner(g, left - 22 - slide, bottom + 22 + slide, 18, 1, -1, tick);
        corner(g, right + 22 + slide, bottom + 22 + slide, 18, -1, -1, tick);

        g.pose().popMatrix();

        seal(g, t, alpha, 790, 420);
        cardRows(g, t, alpha);
    }

    /** The rubber stamp: rings, bezel ticks and the mark, pressed down in the middle of the card. */
    private void seal(GuiGraphicsExtractor g, double t, float alpha, float cx, float cy) {
        float press = seg(t, CLEARANCE_AT + 60, CLEARANCE_AT + 340);
        float scale = 1.55f - 0.55f * press;
        float spin = (float) (t - CLEARANCE_AT) * 0.12f;

        // Impact: a soft ring and 20 ticks thrown off the stamp as it lands.
        float burst = 1 - seg(t, CLEARANCE_AT + 60, CLEARANCE_AT + 620);
        if (burst > 0) {
            Render2D.circleOutline(g, cx, cy, 96 + 62 * (1 - burst), 1.2f,
                    a(ACCENT, 0.28f * burst * alpha));
            for (int i = 0; i < 20; i++) {
                double angle = Math.PI * 2 * i / 20 + 0.08;
                float inner = 104 + 14 * (1 - burst), outer = inner + 22 * burst;
                Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                        cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1,
                        a(ACCENT, 0.35f * burst * alpha));
            }
        }

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);

        Render2D.circleOutline(g, cx, cy, 96, 2.4f, a(ACCENT, 0.85f * alpha));
        Render2D.circleOutline(g, cx, cy, 86, 1, a(ACCENT, 0.45f * alpha));
        for (int i = 0; i < 40; i++) {
            double angle = Math.toRadians(i * 9 + spin);
            float inner = 96, outer = i % 5 == 0 ? 104 : 100;
            Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1,
                    a(ACCENT, 0.5f * alpha));
        }
        wtf.carnation.ui.rhine.RhineMarkTexture.draw(g, cx, cy - 14, 104, a(ACCENT, 0.9f * alpha));
        g.pose().popMatrix();

        if (font != null) {
            // Text is drawn outside the scaled pose, so its offset and size are scaled by hand.
            centered("04", cx, cy + 62 * scale, 26 * scale, BootFont.Weight.BOLD, 0.06f,
                    a(ACCENT, alpha * press));
        }
    }

    /** Left block (the verdict) and right block (the record) of the clearance card. */
    private void cardRows(GuiGraphicsExtractor g, double t, float alpha) {
        if (font == null) return;
        // The card is still scaling for the first 300 ms; the type arrives once it has settled.
        float body = alpha * seg(t, CLEARANCE_AT + 140, CLEARANCE_AT + 480);

        label("RHINE LAB  //  SECURITY DIVISION", 312, 296, 10.5f, BootFont.Weight.REGULAR, 0.18f,
                a(GRAY, body));
        label("CLEARANCE", 312, 350, 13, BootFont.Weight.REGULAR, 0.34f, a(INK_SOFT, body));
        label("04", 310, 486, 118, BootFont.Weight.BOLD, 0.02f, a(INK, body));

        float wipe = seg(t, CLEARANCE_AT + 120, CLEARANCE_AT + 420);
        centered("ACCESS GRANTED", 456, 546, 18, BootFont.Weight.REGULAR, 0.42f,
                a(ACCENT, body * wipe));
        Render2D.rect(g, 312, 566, 288 * wipe, 1.5f, a(ACCENT, 0.75f * body));
        label("SCOPE 04 / 07   //   SECURE CHANNEL", 312, 592, 10.5f, BootFont.Weight.REGULAR, 0.18f,
                a(GRAY, body));

        // Record column.
        String[][] rows = {
                {"OPERATOR", operator()},
                {"SESSION", "04-7C2F"},
                {"ISSUED", java.time.LocalDate.now() + "  " + issueTime()},
                {"STATUS", "GRANTED"},
        };
        float label_x = 880, valueRight = 1248;
        for (int i = 0; i < rows.length; i++) {
            float p = seg(t, CLEARANCE_AT + 140 + i * 90, CLEARANCE_AT + 380 + i * 90) * body;
            if (p <= 0) continue;
            float baseline = 330 + i * 46;
            boolean last = i == rows.length - 1;
            int titleColor = last ? a(ACCENT, 0.9f * p) : a(GRAY, p);
            int valueColor = last ? a(ACCENT, p) : a(INK, p);
            label(rows[i][0], label_x, baseline, 10.5f, BootFont.Weight.REGULAR, 0.24f, titleColor);

            float valueWidth = font.width(rows[i][1], BootFont.Weight.REGULAR, 13, 0.05f * 13);
            label(rows[i][1], valueRight - valueWidth, baseline, 13, BootFont.Weight.REGULAR, 0.05f,
                    valueColor);
            float from = label_x + 96;
            float to = valueRight - valueWidth - 16;
            if (to > from) {
                Render2D.rect(g, from, baseline - 4, to - from, 1, a(INK, 0.12f * p));
            }
        }

        Render2D.rect(g, 880, 500, 368, 1, a(INK, 0.18f * alpha));

        // Barcode: the record's paper trail.
        for (int i = 0; i < 46; i++) {
            int v = noise(i * 7 + 3);
            float bar = 1 + (v & 3) * 0.8f;
            float x = 880 + i * 5.2f;
            Render2D.rect(g, x, 520, bar, 26 + ((v >>> 5) & 7), a(INK, 0.55f * alpha));
        }
        label("RL-2026-04-C", 880, 578, 11, BootFont.Weight.REGULAR, 0.24f, a(INK_SOFT, alpha));
        rightText("ARCHIVE CLEARANCE", 1248, 578, 10.5f, 0.20f, a(GRAY, alpha));
    }

    private static String issueTime() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    // ---- scene 8: welcome ---------------------------------------------------------------------

    private void welcome(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float in = seg(t, WELCOME_AT + 320, WELCOME_AT + 700);
        if (in <= 0) return;
        float cx = 636 + 64 * seg(t, WELCOME_AT, WELCOME_AT + 350);
        centered("WELCOME TO", cx, 420, 16, 0.46f, a(INK_SOFT, in * 0.9f));
        centered("RHINE LAB.LLC.", cx, 494, 54, BootFont.Weight.BOLD, 0.012f, a(INK, in));
        centered("INTERNAL DATABASE", cx, 548, 17, 0.56f, a(INK_SOFT, in * 0.95f));

        float rule = seg(t, WELCOME_AT + 750, WELCOME_AT + 1000);
        Render2D.rect(g, cx - 190 * rule, 516, 380 * rule, 1, a(INK, 0.22f * in));
    }

    // ---- persistent furniture ----------------------------------------------------------------

    private void captions(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, AUTH_AT + 50, AUTH_AT + 350);
        if (alpha > 0) {
            label("身份信息确认：" + operator(), 56, 850, 21, BootFont.Weight.REGULAR, 0.04f, a(GRAY, alpha));
        }

        float powered = seg(t, 5000, 5350);
        if (powered > 0) {
            String pre = "POWERED BY ";
            float em = 13.5f;
            float widthPre = font.width(pre, BootFont.Weight.REGULAR, em, 0.10f * em);
            float widthMark = font.width("RHINE LAB", BootFont.Weight.BOLD, em, 0.02f * em);
            float bar = 32, gap = 10;
            float x = 1544 - (widthPre + widthMark + gap + bar);
            label(pre, x, 850, em, BootFont.Weight.REGULAR, 0.10f, a(GRAY, powered));
            label("RHINE LAB", x + widthPre, 850, em, BootFont.Weight.BOLD, 0.02f, a(INK, powered));
            Render2D.rect(g, x + widthPre + widthMark + gap, 842, bar, 7, a(INK, powered));
        }
    }

    /** Left column telemetry: real client facts, revealed one line at a time, oldest scrolling out. */
    private void logs(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, AUTH_AT + 100, AUTH_AT + 350) * (1 - seg(t, 12450, 12750));
        if (alpha <= 0) return;

        String[] lines = {
                "MOUNT CARNATION CORE",
                "FABRIC LOADER 0.19.5",
                "MC " + Client.ver + "  //  JAVA " + javaVersion(),
                "MODULE REGISTRY " + moduleCount() + " ENTRIES",
                "MOUNT tex/gui",
                "MOUNT tex/entity",
                "MOUNT models/block",
                "MOUNT shaders/post",
                "MOUNT font/figtree",
                "VERIFY manifest.sha256",
                "ALLOC " + memory() + " MB",
                "RELOAD " + Math.round(progressShown * 100) + "%",
        };

        int newest = (int) Math.floor((t - AUTH_AT - 100) / 200);
        newest = Math.max(0, Math.min(lines.length - 1, newest));
        int visible = 6;
        float em = 12, tracking = 0.11f;
        for (int i = Math.max(0, newest - visible + 1); i <= newest; i++) {
            float slot = i - (newest - visible + 1);
            float baseline = 560 + slot * 22;
            float lineAlpha = alpha * (i == newest ? 1f : 0.55f);
            String prefix = "> ";
            label(prefix, 56, baseline, em, BootFont.Weight.REGULAR, tracking, a(ACCENT, lineAlpha));
            float px = 56 + font.width(prefix, BootFont.Weight.REGULAR, em, tracking * em);
            label(lines[i], px, baseline, em, BootFont.Weight.REGULAR, tracking, a(GRAY, lineAlpha));
        }
    }

    private void progress(GuiGraphicsExtractor g, double t) {
        float alpha = seg(t, 1200, 1600);
        if (alpha <= 0) return;

        float p = clamp01(progressShown);
        float x = 776, y = 840, width = 300;
        Render2D.rect(g, x, y, width, 2, a(INK, 0.18f * alpha));
        Render2D.rect(g, x, y, width * p, 2, a(INK, 0.9f * alpha));

        int tick = a(INK, 0.30f * alpha);
        for (int i = 0; i <= 14; i++) {
            Render2D.rect(g, x + i * (width / 14f), y - 3, 1, 6, tick);
        }

        if (font != null) {
            String phase = t < MOUNT_AT ? "RESOURCE RELOAD"
                    : t < ANALYSIS_AT ? "ARCHIVE MOUNT"
                    : t < SCAN_AT ? "STRUCTURAL ANALYSIS"
                    : t < CLEARANCE_AT ? "VERIFYING PERMISSION"
                    : t < WELCOME_AT ? "CLEARANCE GRANTED" : "SYSTEM READY";
            float em = 12, tracking = 0.14f;
            float widthPhase = font.width(phase, BootFont.Weight.REGULAR, em, tracking * em);
            label(phase, x - 18 - widthPhase, 847, em, BootFont.Weight.REGULAR, tracking, a(GRAY, alpha));
            label(Math.round(p * 100) + "%", x + width + 16, 848, 13.5f, BootFont.Weight.BOLD, 0.02f,
                    a(INK, alpha));
        }
    }

    private void skipHint(GuiGraphicsExtractor g, double t) {
        if (font == null) return;
        float alpha = seg(t, 2800, 3200) * (1 - seg(t, 25400, 25700));
        if (alpha <= 0) return;
        float blink = 0.45f + 0.35f * (float) Math.abs(Math.sin(clock / 700.0));
        centered("PRESS ANY KEY TO SKIP", W * 0.5f, 874, 10.5f, 0.32f, a(GRAY, alpha * blink));
    }

    // ---- text ---------------------------------------------------------------------------------

    private void label(String text, float x, float baseline, float em, BootFont.Weight weight,
                       float trackingEm, int color) {
        if (font == null || text == null || text.isEmpty() || (color >>> 24) == 0) return;
        BootQuads mesh = weight == BootFont.Weight.BOLD ? boldMesh : regularMesh;
        if (mesh != null) font.draw(mesh, text, x, baseline, em, weight, trackingEm * em, color);
    }

    private void leftText(String text, float x, float baseline, float em, float trackingEm, int color) {
        if (font == null) return;
        font.draw(regularMesh, text, x, baseline, em, BootFont.Weight.REGULAR, trackingEm * em, color);
    }

    private void rightText(String text, float right, float baseline, float em, float trackingEm, int color) {
        if (font == null) return;
        float width = font.width(text, BootFont.Weight.REGULAR, em, trackingEm * em);
        font.draw(regularMesh, text, right - width, baseline, em, BootFont.Weight.REGULAR, trackingEm * em, color);
    }

    private void centered(String text, float cx, float baseline, float em, float trackingEm, int color) {
        centered(text, cx, baseline, em, BootFont.Weight.REGULAR, trackingEm, color);
    }

    private void centered(String text, float cx, float baseline, float em, BootFont.Weight weight,
                          float trackingEm, int color) {
        if (font == null || text == null || text.isEmpty() || (color >>> 24) == 0) return;
        BootQuads mesh = weight == BootFont.Weight.BOLD ? boldMesh : regularMesh;
        if (mesh == null) return;
        float width = font.width(text, weight, em, trackingEm * em);
        font.draw(mesh, text, cx - width * 0.5f, baseline, em, weight, trackingEm * em, color);
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

    // ---- helpers ------------------------------------------------------------------------------

    private static String javaVersion() {
        String version = System.getProperty("java.version", "-");
        int dot = version.indexOf('.');
        return dot > 0 ? version.substring(0, dot) : version;
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

    private static int moduleCount() {
        try {
            return Client.instance == null ? 0 : Client.instance.getModuleManager().getModules().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String memory() {
        long used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
        long max = Runtime.getRuntime().maxMemory() >> 20;
        return used + " / " + max;
    }

    private static String reveal(String text, double t, double start, double end) {
        if (t <= start) return "";
        double p = Math.min(1, (t - start) / Math.max(1, end - start));
        int count = (int) Math.ceil(text.length() * p);
        return text.substring(0, Math.max(1, Math.min(text.length(), count)));
    }

    private static float clamp01(double value) {
        return (float) Math.max(0, Math.min(1, value));
    }

    private static float smooth(float p) {
        p = clamp01(p);
        return p * p * (3 - 2 * p);
    }

    private static float seg(double t, double start, double end) {
        return smooth((float) ((t - start) / Math.max(1, end - start)));
    }

    /** Cheap deterministic hash: the scan needs churn that never repeats on screen. */
    private static int noise(int seed) {
        int x = seed * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        x *= 0xC2B2AE35;
        return x ^ (x >>> 16);
    }

    private static int a(int color, float factor) {
        return Render2D.withAlpha(color, Math.round(((color >>> 24) & 0xFF) * clamp01(factor)));
    }
}
