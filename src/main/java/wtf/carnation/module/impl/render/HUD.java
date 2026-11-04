package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.Client;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.SmoothAnimationTimer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of Naven's {@code HUD}.
 *
 * <p>Watermark: Naven draws a rounded plate, punches a stencil and repaints it with a header band;
 * 26.3 has no stencil helper surfaced for GUI drawing, so the plate is a rounded rect with a
 * rounded header strip clipped by drawing it first and the body on top. The layout (5,5 origin,
 * 14px horizontal padding, "Naven | version | Huty | FPS | time") is the original's.</p>
 *
 * <p>ArrayList: the row background, rainbow cycling, left/right direction, animation and offsets
 * all come from Naven. Naven's {@code font.getWidth(..., size)} takes a scale argument; our baked
 * atlas has fixed sizes, so {@code ArrayList Size} scales the pose instead — that is the only
 * behavioural difference.</p>
 */
public class HUD extends Module {
    public static final int HEADER_COLOR = 0xFF962D2D;
    public static final int BODY_COLOR = 0x78000000;
    public static final int BACKGROUND_COLOR = 0x28000000;
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss");

    /** Watermark plate origin, as in Naven. */
    private static final float WATERMARK_X = 5.0F;
    private static final float WATERMARK_Y = 5.0F;
    private static final float WATERMARK_RADIUS = 5.0F;
    private static final float WATERMARK_HEADER = 3.0F;

    public final BoolValue waterMark = new BoolValue("Water Mark", true);
    public final NumberValue watermarkSize = new NumberValue("Watermark Size", 0.4, 0.1, 1.0, 0.01,
            this::watermarkVisible);
    public final BoolValue moduleToggleSound = new BoolValue("Module Toggle Sound", true);
    public final BoolValue notification = new BoolValue("Notification", true);
    public final BoolValue arrayList = new BoolValue("Array List", true);
    public final BoolValue prettyModuleName = new BoolValue("Pretty Module Name", false,
            this::arrayListVisible);
    public final BoolValue hideRenderModules = new BoolValue("Hide Render Modules", false,
            this::arrayListVisible);
    public final BoolValue rainbow = new BoolValue("Rainbow", true, this::arrayListVisible);
    public final NumberValue rainbowSpeed = new NumberValue("Rainbow Speed", 10.0, 1.0, 20.0, 0.1,
            this::arrayListVisible);
    public final NumberValue rainbowOffset = new NumberValue("Rainbow Offset", 10.0, 1.0, 20.0, 0.1,
            this::arrayListVisible);
    public final ModeValue arrayListDirection = new ModeValue("ArrayList Direction",
            new String[]{"Right", "Left"}, "Right", this::arrayListVisible);
    public final NumberValue xOffset = new NumberValue("X Offset", 1.0, -100.0, 100.0, 1.0,
            this::arrayListVisible);
    public final NumberValue yOffset = new NumberValue("Y Offset", 1.0, 1.0, 100.0, 1.0,
            this::arrayListVisible);
    public final NumberValue arrayListSize = new NumberValue("ArrayList Size", 0.4, 0.1, 1.0, 0.01,
            this::arrayListVisible);

    private final Map<Module, SmoothAnimationTimer> animations = new IdentityHashMap<>();
    private List<Module> renderModules;
    private boolean cachedHideRender;
    private boolean cachedPrettyName;
    private float width;
    private float watermarkHeight;

    public HUD() {
        super("HUD", Category.RENDER, "Displays information on your screen");
    }

    /**
     * The ArrayList caches a width-sorted copy, so it has to rebuild when a module toggles (a
     * suffix changes the width) or when a setting that changes the rows or their order changes.
     */
    private boolean renderListDirty() {
        return Module.arrayListDirty
                || this.cachedHideRender != this.hideRenderModules.get()
                || this.cachedPrettyName != this.prettyModuleName.get()
                || this.renderModules.size() != Client.instance.getModuleManager().getModules().size();
    }

    @Override
    protected void onEnable() {
        Module.arrayListDirty = true;
    }

    private boolean arrayListVisible() {
        return arrayList.get();
    }

    private boolean watermarkVisible() {
        return waterMark.get();
    }

    public String getModuleDisplayName(Module module) {
        String name = this.prettyModuleName.get() ? module.getPrettyName() : module.getName();
        return name + (module.getSuffix() == null ? "" : " " + module.getSuffix());
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        GuiGraphicsExtractor graphics = event.graphics();
        UiFont.useGuiScale();

        if (this.waterMark.get()) this.drawWatermark(graphics);
        if (this.arrayList.get()) this.drawArrayList(graphics);

        // Last, so toasts sit above both.
        if (this.notification.get() && Client.instance != null) {
            Client.instance.getNotificationManager().render(graphics);
        }
    }

    /**
     * Naven's watermark: a rounded black card, a header band in {@link #HEADER_COLOR} and a body
     * band under it.
     *
     * <p>Naven punches a stencil so the two bands take the card's rounding. 26.3's GUI drawing has
     * no stencil, so the card is painted first and the body band is a <em>rounded</em> rect whose
     * top edge hides under the header — a plain rect there would square off the bottom corners.</p>
     */
    private void drawWatermark(GuiGraphicsExtractor graphics) {
        String text = "Naven | " + Client.ver + " | Huty | " + mc.getFps() + " FPS | "
                + FORMAT.format(new Date());

        // Naven measures the text at the chosen size and pads it by a fixed 14 px; the card is
        // painted four pixels wider than that measurement, which is the little tab on the right.
        float size = this.watermarkSize.getFloat();
        float textWidth = UiFont.SMALL.width(text) * size;
        float textHeight = UiFont.SMALL.lineHeight() * size;
        this.width = textWidth + 14.0F;
        this.watermarkHeight = textHeight;
        float plateWidth = this.width + 4.0F;
        float plateHeight = textHeight + 8.0F;

        // Card, then the 3 px header band Naven fills through its stencil (no stencil in 26.3, so
        // the band is its own small rounded rect and the card's corner rounding stays intact).
        Render2D.roundedRect(graphics, WATERMARK_X, WATERMARK_Y, plateWidth, plateHeight,
                WATERMARK_RADIUS, BODY_COLOR);
        Render2D.roundedRect(graphics, WATERMARK_X, WATERMARK_Y, plateWidth, WATERMARK_HEADER,
                WATERMARK_HEADER * 0.5F, HEADER_COLOR);

        // Naven draws the text at (12,10) whatever the size, so the offsets are not scaled.
        graphics.pose().pushMatrix();
        graphics.pose().translate(WATERMARK_X + 7.0F, WATERMARK_Y + 5.0F);
        graphics.pose().scale(size, size);
        UiFont.SMALL.drawInBox(graphics, text, 0.0F, 0.0F, textHeight / Math.max(size, 1.0e-4F),
                0xFFFFFFFF);
        graphics.pose().popMatrix();
    }

    private void drawArrayList(GuiGraphicsExtractor graphics) {
        if (Client.instance == null) return;

        if (this.renderModules == null || this.renderListDirty()) {
            this.cachedHideRender = this.hideRenderModules.get();
            this.cachedPrettyName = this.prettyModuleName.get();
            this.renderModules = new ArrayList<>(Client.instance.getModuleManager().getModules());
            if (this.cachedHideRender) {
                this.renderModules.removeIf(module -> module.getCategory() == Category.RENDER);
            }
            this.renderModules.sort((a, b) -> Float.compare(
                    UiFont.SMALL.width(this.getModuleDisplayName(b)),
                    UiFont.SMALL.width(this.getModuleDisplayName(a))));
        }

        // Measured fresh every frame: suffixes ("Single"/"Multi", bps, ticks) change the row width
        // without touching the cached order, and a stale maxWidth clips the longest row.
        float maxWidth = 0.0F;
        for (Module module : this.renderModules) {
            maxWidth = Math.max(maxWidth, UiFont.SMALL.width(this.getModuleDisplayName(module)));
        }
        boolean left = this.arrayListDirection.is("Left");
        float arrayListX = left
                ? 3.0F + this.xOffset.getFloat()
                : mc.getWindow().getGuiScaledWidth() - maxWidth - 6.0F + this.xOffset.getFloat();
        float arrayListY = this.yOffset.getFloat();
        float rowHeight = UiFont.SMALL.lineHeight();
        float height = 0.0F;
        float scale = this.arrayListSize.getFloat() / 0.4F;
        boolean unscaled = Math.abs(scale - 1.0F) < 1.0e-3;

        for (Module module : this.renderModules) {
            SmoothAnimationTimer animation = this.animations
                    .computeIfAbsent(module, ignored -> new SmoothAnimationTimer(0.0F));
            animation.target = module.isEnabled() ? 100.0F : 0.0F;
            animation.update(true);
            if (animation.value <= 0.0F) continue;

            float progress = animation.value / 100.0F;
            String displayName = this.getModuleDisplayName(module);
            float stringWidth = UiFont.SMALL.width(displayName);
            // Slide in from the anchored side: a row grows out of the screen edge as it animates.
            float innerX = left
                    ? -stringWidth * (1.0F - progress)
                    : maxWidth - stringWidth * progress;
            float rowX = arrayListX + innerX;
            float rowTop = arrayListY + height;

            // Plate and glyphs share one box, so the text can never drift off its background.
            Render2D.rect(graphics, rowX - 2.0F, rowTop, stringWidth + 4.0F, rowHeight,
                    BACKGROUND_COLOR);

            int color = 0xFFFFFF;
            if (this.rainbow.get()) {
                color = rainbowColor((int) (-height * this.rainbowOffset.getFloat()));
            }
            int alpha = Math.round(progress * 255.0F);

            if (unscaled) {
                UiFont.SMALL.drawInBox(graphics, displayName, rowX, rowTop, rowHeight,
                        (alpha << 24) | color);
            } else {
                graphics.pose().pushMatrix();
                graphics.pose().translate(rowX, rowTop);
                graphics.pose().scale(scale, scale);
                UiFont.SMALL.drawInBox(graphics, displayName, 0.0F, 0.0F, rowHeight / scale,
                        (alpha << 24) | color);
                graphics.pose().popMatrix();
            }

            height += rowHeight;
        }
    }

    /** Naven's {@code RenderUtils.getRainbowOpaque(index, 1, 1, speed)}. */
    private int rainbowColor(int index) {
        float speed = (21.0F - this.rainbowSpeed.getFloat()) * 1000.0F;
        float hue = ((System.currentTimeMillis() + index) % (long) speed) / speed;
        return java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F);
    }
}
