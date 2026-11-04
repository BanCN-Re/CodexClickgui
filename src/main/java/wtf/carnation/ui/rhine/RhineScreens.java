package wtf.carnation.ui.rhine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.util.Util;
import wtf.carnation.boot.BootFont;
import wtf.carnation.boot.BootPipelines;
import wtf.carnation.boot.BootQuads;
import wtf.carnation.graphics.Render2D;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The terminal skin for the menu screens reached from the title: world list, world creation,
 * multiplayer, options, pause and disconnects.
 *
 * <p>The look is deliberately quiet: a warm near-black field, one hairline header, a faint mark in
 * the middle and outlined buttons that invert on hover. The vanilla widgets keep their layout and
 * clicks; only plain buttons are redrawn.
 */
public final class RhineScreens {
    private static final int PAPER = 0xFFEFEEE9;
    private static final int INK = 0xFF14160F;
    private static final int GRAY = 0xFF8C8B83;
    private static final int ACCENT = 0xFFD8532B;
    private static final int TOP = 0xFF181712;
    private static final int BOTTOM = 0xFF0C0B07;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Map<Screen, Long> OPENED = new WeakHashMap<>();
    private static final Map<Button, Float> HOVER = new IdentityHashMap<>();

    private RhineScreens() {
    }

    public static boolean handles(Screen screen) {
        return screen instanceof SelectWorldScreen
                || screen instanceof CreateWorldScreen
                || screen instanceof JoinMultiplayerScreen
                || screen instanceof OptionsSubScreen
                || screen instanceof OptionsScreen
                || screen instanceof LanguageSelectScreen
                || screen instanceof AccessibilityOptionsScreen
                || screen instanceof CreditsAndAttributionScreen
                || screen instanceof PauseScreen
                || screen instanceof DisconnectedScreen;
    }

    // ---- background ---------------------------------------------------------------------------

    public static void renderBackground(GuiGraphicsExtractor g, Screen screen) {
        int width = g.guiWidth(), height = g.guiHeight();
        long now = Util.getMillis();
        long opened = OPENED.computeIfAbsent(screen, key -> now);
        float appear = clamp01((now - opened) / 300f);

        g.fillGradient(0, 0, width, height, TOP, BOTTOM);
        vignette(g, width, height);
        watermark(g, width, height);
        chrome(g, width, height, appear);
    }

    /** A short paper (or dark, for pause) veil so a screen fades in instead of popping. */
    public static void renderFade(GuiGraphicsExtractor g, Screen screen) {
        Long opened = OPENED.get(screen);
        if (opened == null) return;
        float appear = clamp01((Util.getMillis() - opened) / 280f);
        if (appear >= 1) return;
        int veil = screen instanceof PauseScreen ? 0xFF0B0A07 : PAPER;
        Render2D.rect(g, 0, 0, g.guiWidth(), g.guiHeight(),
                withAlpha(veil, Math.round((1 - appear) * 80)));
    }

    /** Pause keeps the blurred world: only a translucent wash and the chrome are added. */
    public static void renderPauseOverlay(GuiGraphicsExtractor g, Screen screen) {
        int width = g.guiWidth(), height = g.guiHeight();
        long now = Util.getMillis();
        long opened = OPENED.computeIfAbsent(screen, key -> now);
        float appear = clamp01((now - opened) / 300f);

        g.fillGradient(0, 0, width, height, 0xB815140F, 0xE80B0A07);
        watermark(g, width, height);
        chrome(g, width, height, appear);
    }

    private static void chrome(GuiGraphicsExtractor g, int width, int height, float appear) {
        text(g, "RHINE LAB", 28, 18, 12, BootFont.Weight.BOLD, 0.28f, withAlpha(PAPER, 210));
        text(g, "ANALYSIS OS", 28, 33, 7.5f, BootFont.Weight.REGULAR, 0.32f, withAlpha(GRAY, 200));

        right(g, LocalTime.now().format(CLOCK), width - 28, 20, 9.5f, BootFont.Weight.REGULAR, 0.14f,
                withAlpha(PAPER, 200));
        Render2D.rect(g, 28, 46, 40 * appear, 2, withAlpha(ACCENT, 190));

        float sweep = (float) (((Util.getMillis() * 0.05) % (height + 120)) - 60);
        Render2D.rect(g, 0, sweep, width, 1, withAlpha(PAPER, 4));
    }

    private static void vignette(GuiGraphicsExtractor g, int width, int height) {
        int edge = Math.max(24, height / 6);
        g.fillGradient(0, 0, width, edge, 0x66000000, 0x00000000);
        g.fillGradient(0, height - edge, width, height, 0x00000000, 0x77000000);
        g.fillGradient(0, 0, edge, height, 0x55000000, 0x00000000);
        g.fillGradient(width - edge, 0, width, height, 0x00000000, 0x55000000);
    }

    /** A very large, very faint archive mark behind the content. */
    private static void watermark(GuiGraphicsExtractor g, int width, int height) {
        float size = Math.min(width, height) * 1.05f;
        RhineMarkTexture.draw(g, width * 0.5f, height * 0.52f, size, withAlpha(PAPER, 9));
    }

    // ---- widgets ------------------------------------------------------------------------------

    /** Draws a plain button as an outlined Rhine row; callers cancel the vanilla render. */
    public static void renderButton(GuiGraphicsExtractor g, Button button, int mouseX, int mouseY) {
        int x = button.getX(), y = button.getY(), width = button.getWidth(), height = button.getHeight();
        if (width <= 0 || height <= 0) return;
        boolean enabled = button.active;
        boolean hovered = enabled && inside(mouseX, mouseY, button);

        float progress = HOVER.getOrDefault(button, 0f);
        progress += ((hovered ? 1f : 0f) - progress) * 0.24f;
        if (progress < 0.002f) progress = 0f;
        HOVER.put(button, progress);

        int border = !enabled ? withAlpha(PAPER, 26) : withAlpha(PAPER, 70);
        Render2D.roundedOutline(g, x + 0.5f, y + 0.5f, width - 1, height - 1, 2, 1, border);
        if (progress > 0f) {
            Render2D.roundedRect(g, x, y, width, height, 2, withAlpha(PAPER, Math.round(232 * progress)));
            Render2D.rect(g, x, y + height - 1, width * progress, 1, withAlpha(ACCENT, Math.round(210 * progress)));
        }

        int color = !enabled ? withAlpha(PAPER, 55) : Render2D.mix(PAPER, INK, progress);
        float em = Math.min(13f, Math.max(7.5f, height * 0.36f));
        centered(g, button.getMessage().getString(), x + width * 0.5f, y + height * 0.5f, em, color);
    }

    /** A quiet edit field: hairline box, paper text, blink caret. Selection painting stays vanilla-free. */
    public static void renderEditBox(GuiGraphicsExtractor g, EditBox box) {
        int x = box.getX(), y = box.getY(), width = box.getWidth(), height = box.getHeight();
        if (width <= 0 || height <= 0) return;
        boolean focused = box.isFocused();
        Render2D.roundedRect(g, x, y, width, height, 2, withAlpha(PAPER, 8));
        Render2D.roundedOutline(g, x + 0.5f, y + 0.5f, width - 1, height - 1, 2, 1,
                withAlpha(focused ? ACCENT : PAPER, focused ? 220 : 65));

        float em = Math.min(10.5f, Math.max(7.5f, height * 0.38f));
        float baseline = y + height * 0.5f + em * 0.34f;
        String value = box.getValue();
        String hint = box.getMessage().getString();

        if (value.isEmpty()) {
            if (!hint.isEmpty()) left(g, hint, x + 6, baseline, em, withAlpha(GRAY, 170));
            return;
        }

        BootFont font = BootFont.get();
        String shown = value;
        if (font != null && font.covers(value, BootFont.Weight.REGULAR)) {
            float max = width - 12;
            while (shown.length() > 1 && font.width(shown, BootFont.Weight.REGULAR, em, 0) > max) {
                shown = shown.substring(1);
            }
            left(g, shown, x + 6, baseline, em, PAPER);
            if (focused && (Util.getMillis() / 500) % 2 == 0) {
                float caret = x + 6 + font.width(shown, BootFont.Weight.REGULAR, em, 0) + 1;
                Render2D.rect(g, caret, y + 4, 1, height - 8, withAlpha(PAPER, 210));
            }
        } else {
            left(g, shown, x + 6, baseline, em, PAPER);
        }
    }

    /** A hairline slider with a paper knob. */
    public static void renderSlider(GuiGraphicsExtractor g, AbstractSliderButton slider, double value,
                                    int mouseX, int mouseY) {
        int x = slider.getX(), y = slider.getY(), width = slider.getWidth(), height = slider.getHeight();
        if (width <= 0 || height <= 0) return;
        boolean enabled = slider.active;
        boolean hovered = enabled && inside(mouseX, mouseY, slider);
        float p = (float) Math.max(0, Math.min(1, value));
        float cy = y + height * 0.5f;

        Render2D.rect(g, x + 2, cy - 1, width - 4, 2, withAlpha(PAPER, enabled ? 45 : 22));
        Render2D.rect(g, x + 2, cy - 1, (width - 4) * p, 2, withAlpha(PAPER, enabled ? 210 : 90));
        Render2D.circle(g, x + 2 + (width - 4) * p, cy, hovered ? 4.5f : 3.6f,
                withAlpha(PAPER, enabled ? 235 : 90));

        float em = Math.min(10.5f, Math.max(7.5f, height * 0.38f));
        centered(g, slider.getMessage().getString(), x + width * 0.5f, cy, em,
                enabled ? PAPER : withAlpha(PAPER, 70));
    }

    /** A hairline checkbox with a paper tick. */
    public static void renderCheckbox(GuiGraphicsExtractor g, Checkbox box, int mouseX, int mouseY) {
        int x = box.getX(), y = box.getY(), width = box.getWidth(), height = box.getHeight();
        if (width <= 0 || height <= 0) return;
        boolean enabled = box.active;
        boolean hovered = enabled && inside(mouseX, mouseY, box);
        boolean selected = box.selected();
        float size = Math.min(12, height * 0.62f);
        float bx = x, by = y + (height - size) * 0.5f;

        Render2D.roundedOutline(g, bx + 0.5f, by + 0.5f, size - 1, size - 1, 1, 1,
                withAlpha(PAPER, enabled ? (hovered ? 160 : 85) : 40));
        if (selected) {
            Render2D.roundedRect(g, bx + 2, by + 2, size - 4, size - 4, 0.5f,
                    withAlpha(PAPER, enabled ? 220 : 80));
            Render2D.line(g, bx + size * 0.24f, by + size * 0.52f, bx + size * 0.43f, by + size * 0.71f,
                    1.3f, a(INK, enabled ? 230 : 120));
            Render2D.line(g, bx + size * 0.43f, by + size * 0.71f, bx + size * 0.78f, by + size * 0.28f,
                    1.3f, a(INK, enabled ? 230 : 120));
        }

        float em = Math.min(10.5f, Math.max(7.5f, height * 0.40f));
        left(g, box.getMessage().getString(), x + size + 7, y + height * 0.5f + em * 0.34f, em,
                enabled ? PAPER : withAlpha(PAPER, 70));
    }

    private static boolean inside(int mouseX, int mouseY, AbstractWidget widget) {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
                && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }

    // ---- text ---------------------------------------------------------------------------------

    private static void text(GuiGraphicsExtractor g, String value, float x, float top, float em,
                             BootFont.Weight weight, float trackingEm, int color) {
        BootFont font = BootFont.get();
        if (font == null || !font.covers(value, weight)) {
            g.text(Minecraft.getInstance().font, value, Math.round(x), Math.round(top), color, false);
            return;
        }
        BootQuads mesh = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                TextureSetup.singleTexture(font.texture(weight), font.sampler()));
        mesh.begin(g.pose(), g.scissorStack.peek());
        font.draw(mesh, value, x, top + em * 0.72f, em, weight, trackingEm * em, color);
        g.guiRenderState.addGuiElement(mesh);
    }

    private static void left(GuiGraphicsExtractor g, String value, float x, float baseline, float em, int color) {
        BootFont font = BootFont.get();
        if (font == null || !font.covers(value, BootFont.Weight.REGULAR)) {
            g.text(Minecraft.getInstance().font, value, Math.round(x), Math.round(baseline - 7), color, false);
            return;
        }
        BootQuads mesh = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                TextureSetup.singleTexture(font.texture(BootFont.Weight.REGULAR), font.sampler()));
        mesh.begin(g.pose(), g.scissorStack.peek());
        font.draw(mesh, value, x, baseline, em, BootFont.Weight.REGULAR, 0, color);
        g.guiRenderState.addGuiElement(mesh);
    }

    private static void right(GuiGraphicsExtractor g, String value, float right, float top, float em,
                              BootFont.Weight weight, float trackingEm, int color) {
        BootFont font = BootFont.get();
        float width;
        if (font == null || !font.covers(value, weight)) {
            width = Minecraft.getInstance().font.width(value);
            g.text(Minecraft.getInstance().font, value, Math.round(right - width), Math.round(top), color, false);
            return;
        }
        width = font.width(value, weight, em, trackingEm * em);
        text(g, value, right - width, top, em, weight, trackingEm, color);
    }

    private static void centered(GuiGraphicsExtractor g, String value, float cx, float cy, float em, int color) {
        BootFont font = BootFont.get();
        if (font == null || !font.covers(value, BootFont.Weight.REGULAR)) {
            g.centeredText(Minecraft.getInstance().font, value, Math.round(cx), Math.round(cy - 4), color);
            return;
        }
        float width = font.width(value, BootFont.Weight.REGULAR, em, 0);
        BootQuads mesh = new BootQuads(BootPipelines.QUAD_TRANSLUCENT,
                TextureSetup.singleTexture(font.texture(BootFont.Weight.REGULAR), font.sampler()));
        mesh.begin(g.pose(), g.scissorStack.peek());
        font.draw(mesh, value, cx - width * 0.5f, cy + em * 0.36f, em, BootFont.Weight.REGULAR, 0, color);
        g.guiRenderState.addGuiElement(mesh);
    }

    private static float clamp01(double value) {
        return (float) Math.max(0, Math.min(1, value));
    }

    private static int withAlpha(int color, int alpha) {
        return Render2D.withAlpha(color, alpha);
    }

    private static int a(int color, int alpha) {
        return Render2D.withAlpha(color, alpha);
    }
}
