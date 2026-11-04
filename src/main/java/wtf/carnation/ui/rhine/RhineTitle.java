package wtf.carnation.ui.rhine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;
import wtf.carnation.boot.BootFont;
import wtf.carnation.boot.BootPipelines;
import wtf.carnation.boot.BootQuads;
import wtf.carnation.graphics.Render2D;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

/**
 * The Rhine Lab title screen: paper, ink, a terminal menu and a structural archive ornament.
 *
 * <p>It does not replace {@code TitleScreen}; it restyles the real one. The vanilla widgets stay
 * alive as invisible click targets — singleplayer, multiplayer, Realms, options and quit keep
 * their original handlers — while this class paints the menu rows over their bounds. Language and
 * accessibility stay as icon buttons, repositioned into the top-right cluster.
 *
 * <p>Text uses the boot glyph atlas so the title and the boot sequence share one typeface; labels
 * outside the atlas (other languages) fall back to the game font.
 */
public final class RhineTitle {
    public static final float W = 1600f, H = 900f;

    private static final int PAPER = 0xFFEFEEE9;
    private static final int PAPER_DEEP = 0xFFE9E8E1;
    private static final int PAPER_LIGHT = 0xFFF4F3EE;
    private static final int INK = 0xFF14160F;
    private static final int INK_SOFT = 0xFF3C3F36;
    private static final int GRAY = 0xFF8C8B83;
    private static final int ACCENT = 0xFFD8532B;

    private static final String[] CODES = {"SURVIVAL", "NETWORK", "REALMS", "SETTINGS", "TERMINATE"};
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record Row(Button button, String index, String code) {
    }

    private record Link(Button button, String label) {
    }

    private final long shownAt;
    private final List<Row> rows = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final Map<Button, Float> hover = new IdentityHashMap<>();
    private final Set<Button> hoverTicked = Collections.newSetFromMap(new IdentityHashMap<>());
    private Button credits;

    private float scale = 1, originX, originY;
    private int width, height;
    private BootFont font;
    private BootQuads boldMesh, regularMesh;
    private GuiGraphicsExtractor graphics;

    public RhineTitle(long shownAt) {
        this.shownAt = shownAt;
    }

    // ---- layout -------------------------------------------------------------------------------

    /** Rebuilds the menu geometry from the vanilla widgets; {@code init} runs again on resize. */
    public void layout(Screen screen, int width, int height) {
        this.width = width;
        this.height = height;
        this.scale = Math.min(width / W, height / H);
        this.originX = (width - W * scale) * 0.5f;
        this.originY = (height - H * scale) * 0.5f;

        rows.clear();
        links.clear();
        credits = null;

        Map<Integer, Button> found = new HashMap<>();
        List<Button> icons = new ArrayList<>();

        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (child instanceof PlainTextButton plain) {
                credits = plain;
                int x = sx(1180), y = sy(856), w = sw(364), h = Math.max(10, sw(18));
                plain.setX(x);
                plain.setY(y);
                plain.setWidth(w);
                plain.setHeight(h);
                continue;
            }
            if (child instanceof FriendsButton) {
                widget.visible = false;
                continue;
            }
            if (!(child instanceof Button button)) {
                widget.visible = false;
                continue;
            }
            if (child instanceof SpriteIconButton) {
                icons.add(button);
                continue;
            }
            String message = button.getMessage().getString();
            if (message.equals(I18n.get("menu.singleplayer"))) found.put(0, button);
            else if (message.equals(I18n.get("menu.multiplayer"))) found.put(1, button);
            else if (message.equals(I18n.get("menu.online"))) found.put(2, button);
            else if (message.equals(I18n.get("menu.options"))) found.put(3, button);
            else if (message.equals(I18n.get("menu.quit"))) found.put(4, button);
            else widget.visible = false;
        }

        int row = 0;
        for (int rank = 0; rank < CODES.length; rank++) {
            Button button = found.get(rank);
            if (button == null) continue;
            button.setX(sx(120));
            button.setY(sy(430 + row * 62));
            button.setWidth(sw(430));
            button.setHeight(sw(50));
            rows.add(new Row(button, String.format("%02d", row + 1), CODES[rank]));
            row++;
        }

        for (int i = 0; i < icons.size(); i++) {
            Button button = icons.get(i);
            if (i >= 2) {
                button.visible = false;
                continue;
            }
            button.setX(sx(1300));
            button.setY(sy(150 + i * 40));
            button.setWidth(sw(244));
            button.setHeight(sw(30));
            links.add(new Link(button, i == 0 ? "LANGUAGE" : "ACCESSIBILITY"));
        }
    }

    private int sx(float x) {
        return Math.round(originX + x * scale);
    }

    private int sy(float y) {
        return Math.round(originY + y * scale);
    }

    private int sw(float value) {
        return Math.max(1, Math.round(value * scale));
    }

    // ---- render -------------------------------------------------------------------------------

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float appear = clamp01((now - shownAt) / 550f);

        graphics = g;
        font = BootFont.get();
        float guiWidth = g.guiWidth(), guiHeight = g.guiHeight();
        Render2D.rect(g, 0, 0, guiWidth, guiHeight, PAPER);

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

        backdrop(g, now);
        brand(g);
        clock(g);
        ornament(g, now);
        menu(g, mouseX, mouseY);
        links(g, mouseX, mouseY);
        bottom(g, mouseX, mouseY);

        if (font != null) {
            submit(g, boldMesh);
            submit(g, regularMesh);
        }

        g.pose().popMatrix();

        if (appear < 1) {
            Render2D.rect(g, 0, 0, guiWidth, guiHeight, Render2D.withAlpha(PAPER,
                    Math.round((1 - appear) * 255)));
        }
    }

    private static void submit(GuiGraphicsExtractor g, BootQuads mesh) {
        if (mesh != null && !mesh.isEmpty()) g.guiRenderState.addGuiElement(mesh);
    }

    private void backdrop(GuiGraphicsExtractor g, long now) {
        Render2D.circle(g, 1180, 90, 620, Render2D.withAlpha(PAPER_LIGHT, 28));
        Render2D.circle(g, 220, 840, 560, Render2D.withAlpha(PAPER_DEEP, 24));
        int grid = Render2D.withAlpha(INK, 7);
        for (float x = 100; x < W; x += 100) Render2D.rect(g, x, 0, 0.65f, H, grid);
        for (float y = 100; y < H; y += 100) Render2D.rect(g, 0, y, W, 0.65f, grid);

        float inset = 26, length = 18;
        int mark = a(INK, 0.35f);
        corner(g, inset, inset, length, 1, 1, mark);
        corner(g, W - inset, inset, length, -1, 1, mark);
        corner(g, inset, H - inset, length, 1, -1, mark);
        corner(g, W - inset, H - inset, length, -1, -1, mark);

        // A read line drifting down, slower than the boot one.
        float y = (float) (((now * 0.04) % (H + 240)) - 120);
        Render2D.rect(g, 0, y, W, 1, Render2D.withAlpha(INK, 5));
    }

    private static void corner(GuiGraphicsExtractor g, float x, float y, float length, int dx, int dy, int color) {
        float x0 = dx > 0 ? x : x - length;
        float y0 = dy > 0 ? y : y - length;
        Render2D.rect(g, x0, y, length, 1, color);
        Render2D.rect(g, x, y0, 1, length, color);
    }

    private void brand(GuiGraphicsExtractor g) {
        if (font == null) return;
        label("RHINE LAB", 56, 110, 46, BootFont.Weight.BOLD, 0.012f, INK);
        label("SYNTHESIZE INFORMATION", 56, 134, 12.5f, BootFont.Weight.REGULAR, 0.115f, GRAY);
        label("ANALYSIS OS", 56, 179, 35, BootFont.Weight.REGULAR, 0.285f, INK);
        Render2D.rect(g, 56, 205, 430, 1, a(INK, 0.22f));

        float cx = 1140;
    }

    private void clock(GuiGraphicsExtractor g) {
        if (font == null) return;
        String time = LocalTime.now().format(CLOCK);
        right(time, 1544, 118, 13, BootFont.Weight.REGULAR, 0.12f, INK);
    }

    private void ornament(GuiGraphicsExtractor g, long now) {
        float cx = 1140, cy = 470;
        float spin = (float) (now % 240000L) * 0.015f;

        int dash = a(INK, 0.16f);
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10 + spin);
            float inner = 228, outer = 234;
            Render2D.line(g, cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, 1.2f, dash);
        }

        float pulse = 1f + 0.006f * (float) Math.sin(now / 650.0);
        logo(g, cx, cy, pulse, 1f);

        if (font == null) return;
        centered("RHINE LAB.LLC.", cx, 612, 30, 0.02f, INK);
        centered("INTERNAL DATABASE", cx, 644, 14, 0.55f, INK_SOFT);
    }

    private void logo(GuiGraphicsExtractor g, float cx, float cy, float size, float alpha) {
        RhineMarkTexture.draw(g, cx, cy, 384 * size, a(INK, alpha));
    }

    // ---- menu ---------------------------------------------------------------------------------

    private void menu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (font == null) return;
        label("MAIN MENU", 120, 404, 11, BootFont.Weight.REGULAR, 0.34f, GRAY);
        Render2D.rect(g, 120, 414, 430, 1, a(INK, 0.18f));

        long now = Util.getMillis();
        int rowIndex = 0;
        for (Row row : rows) {
            Button button = row.button();
            float appearRow = clamp01((now - shownAt - rowIndex * 45) / 260f);
            rowIndex++;
            float x = canvasX(button.getX()) + (1 - appearRow) * 18;
            float y = canvasY(button.getY());
            float w = button.getWidth() / scale;
            float h = button.getHeight() / scale;
            boolean enabled = button.active;
            boolean hovered = enabled && inside(mouseX, mouseY, button);

            float progress = hover.getOrDefault(button, 0f);
            progress += ((hovered ? 1f : 0f) - progress) * 0.22f;
            if (progress < 0.002f) progress = 0f;
            hover.put(button, progress);

            if (progress > 0.55f && hoverTicked.add(button)) {
                wtf.carnation.audio.RhineSounds.tick();
            } else if (progress < 0.2f) {
                hoverTicked.remove(button);
            }

            if (progress > 0f) {
                Render2D.roundedRect(g, x, y, w, h, 3, a(INK, progress));
                Render2D.rect(g, x - 12, y + h * (1 - progress) * 0.5f, 3, h * progress, a(ACCENT, progress));
            }

            int ink = !enabled ? a(INK, 0.30f) : Render2D.mix(INK, PAPER, progress);
            int micro = !enabled ? a(GRAY, 0.45f) : Render2D.mix(GRAY, a(PAPER, 0.72f), progress);

            float baseline = y + h * 0.63f;
            label(row.index(), x + 4, baseline, 11, BootFont.Weight.REGULAR, 0.08f, micro);
            label(button.getMessage().getString(), x + 62, baseline, 21, BootFont.Weight.REGULAR, 0.015f, ink);
            right(enabled ? row.code() : "UNAVAILABLE", x + w - 4, baseline, 10.5f,
                    BootFont.Weight.BOLD, 0.22f, micro);
            Render2D.rect(g, x, y + h, w, 1, Render2D.mix(a(INK, 0.16f), a(PAPER, 0.35f), progress));
        }
    }

    private void links(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (font == null) return;
        for (Link link : links) {
            Button button = link.button();
            float x = canvasX(button.getX());
            float y = canvasY(button.getY());
            float w = button.getWidth() / scale;
            float h = button.getHeight() / scale;
            boolean hover = inside(mouseX, mouseY, button);
            float baseline = y + h * 0.62f;
            float textWidth = textWidth(link.label(), 10.5f, 0.30f);

            label(link.label(), x + w - textWidth, baseline, 10.5f, BootFont.Weight.REGULAR, 0.30f,
                    hover ? INK : GRAY);
            Render2D.circle(g, x + w - textWidth - 10, baseline - 4, 2.4f,
                    a(hover ? INK : GRAY, hover ? 0.9f : 0.55f));
            if (hover) {
                Render2D.rect(g, x + w - textWidth, baseline + 3, textWidth, 1, a(INK, 0.6f));
            }
        }
    }

    private void bottom(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (font == null) return;
        label("MC 26.3   //   FABRIC LOADER 0.19.5", 56, 848, 11, BootFont.Weight.REGULAR, 0.12f, GRAY);
        label("OPERATOR " + operator(), 56, 870, 10.5f, BootFont.Weight.REGULAR, 0.12f, a(GRAY, 0.8f));

        String pre = "POWERED BY ";
        float em = 12;
        float widthPre = textWidth(pre, em, 0.10f);
        float widthMark = textWidth("RHINE LAB", em, 0.02f);
        float bar = 28, gap = 9;
        float x = 1544 - (widthPre + widthMark + gap + bar);
        label(pre, x, 848, em, BootFont.Weight.REGULAR, 0.10f, GRAY);
        label("RHINE LAB", x + widthPre, 848, em, BootFont.Weight.BOLD, 0.02f, INK);
        Render2D.rect(g, x + widthPre + widthMark + gap, 841, bar, 6, INK);

        if (credits != null) {
            float cx = canvasX(credits.getX());
            float cy = canvasY(credits.getY());
            float cw = credits.getWidth() / scale;
            float ch = credits.getHeight() / scale;
            boolean hover = inside(mouseX, mouseY, credits);
            float textWidth = textWidth("MOJANG CREDITS", 10, 0.16f);
            label("MOJANG CREDITS", cx + cw - textWidth, cy + ch * 0.9f, 10, BootFont.Weight.REGULAR,
                    0.16f, hover ? INK : a(GRAY, 0.85f));
            if (hover) {
                Render2D.rect(g, cx + cw - textWidth, cy + ch * 0.9f + 3, textWidth, 1, a(INK, 0.6f));
            }
        }
    }

    // ---- text ---------------------------------------------------------------------------------

    private static boolean inside(int mouseX, int mouseY, AbstractWidget widget) {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
                && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }

    private float canvasX(float screenX) {
        return (screenX - originX) / scale;
    }

    private float canvasY(float screenY) {
        return (screenY - originY) / scale;
    }

    private void label(String text, float x, float baseline, float em, BootFont.Weight weight,
                       float trackingEm, int color) {
        if (graphics == null || text == null || text.isEmpty() || (color >>> 24) == 0) return;
        if (font != null && font.covers(text, weight)) {
            BootQuads mesh = weight == BootFont.Weight.BOLD ? boldMesh : regularMesh;
            if (mesh != null) font.draw(mesh, text, x, baseline, em, weight, trackingEm * em, color);
        } else {
            fallback(text, x, baseline, em, color);
        }
    }

    /** Right-aligns a line at {@code right}. */
    private void right(String text, float right, float baseline, float em, BootFont.Weight weight,
                       float trackingEm, int color) {
        if (text == null || text.isEmpty()) return;
        float width = textWidth(text, em, trackingEm, weight);
        label(text, right - width, baseline, em, weight, trackingEm, color);
    }

    private void centered(String text, float cx, float baseline, float em, float trackingEm, int color) {
        if (text == null || text.isEmpty()) return;
        float width = textWidth(text, em, trackingEm);
        label(text, cx - width * 0.5f, baseline, em, BootFont.Weight.REGULAR, trackingEm, color);
    }

    private float textWidth(String text, float em, float trackingEm) {
        return textWidth(text, em, trackingEm, BootFont.Weight.REGULAR);
    }

    private float textWidth(String text, float em, float trackingEm, BootFont.Weight weight) {
        if (text == null || text.isEmpty()) return 0;
        if (font != null && font.covers(text, weight)) {
            return font.width(text, weight, em, trackingEm * em);
        }
        Font gameFont = Minecraft.getInstance().font;
        return gameFont.width(text) * (em / 8f) + trackingEm * em * text.length();
    }

    /** Languages outside the atlas fall back to the game font, scaled to the requested size. */
    private void fallback(String text, float x, float baseline, float em, int color) {
        if (graphics == null) return;
        Font gameFont = Minecraft.getInstance().font;
        float unit = em / 8f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, baseline - 7 * unit);
        graphics.pose().scale(unit, unit);
        graphics.text(gameFont, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    // ---- helpers ------------------------------------------------------------------------------

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

    private static float clamp01(double value) {
        return (float) Math.max(0, Math.min(1, value));
    }

    private static int a(int color, float factor) {
        return Render2D.withAlpha(color, Math.round(((color >>> 24) & 0xFF) * clamp01(factor)));
    }
}
