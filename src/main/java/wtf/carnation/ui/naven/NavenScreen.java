package wtf.carnation.ui.naven;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import wtf.carnation.Client;
import wtf.carnation.graphics.Icons;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.Value;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.ModeValue;
import wtf.carnation.module.value.impl.NumberValue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-to-one port of NeoNaven's {@code tech.naven.ui.ClickGUI}.
 *
 * <p>Field names, geometry, draw order and input flow are the original's; only the API
 * surface is translated: {@code RenderUtils.drawRoundedRect} → {@link Render2D#roundedRect},
 * {@code SmoothAnimationTimer} → {@link Smooth} (same linear step from {@code AnimationUtils}),
 * {@code opensans.render(..., scale)} → {@link UiFont} baked sizes, and Naven's value classes
 * → the client's {@link Value} subclasses.</p>
 *
 * <p>Two constants differ, both forced: the collapsed window height is
 * {@code 40 + categories * 25} (Naven's 4 categories give the same 140; this client has 6
 * categories and collapses to 190), and the window background is one translucent black layer
 * compositing Naven's two stacked ones, because 26.3 has no GUI blur hook.</p>
 */
public class NavenScreen extends Screen {

    // ---- original fields ----------------------------------------------------------------------

    public static float windowX = 100.0F;
    public static float windowY = 100.0F;
    public static float windowWidth = 400.0F;
    public static float windowHeight = 250.0F;
    Category selectedCategory = null;
    Module selectedModule = null;
    /**
     * Drag/resize origin, in GUI pixels.
     *
     * <p>Naven keeps this as an {@code int[]} because its {@code mouseX/mouseY} are {@code int}.
     * Here the mouse position is a {@code float} (26.3 hands the screen fractional GUI coordinates),
     * so truncating the origin made every drag step drop the fractional part of the delta and the
     * window crept downwards even while the cursor was still. It is a {@code double[]} for the same
     * reason the position is floating point.</p>
     */
    double[] dragMousePosition = new double[]{-1, -1};
    boolean hoveringBack = false;
    Smooth widthAnimation = new Smooth(100.0F);
    Smooth heightAnimation = new Smooth(collapsedH());
    Smooth titleAnimation = new Smooth(100.0F);
    Smooth titleHoverAnimation = new Smooth(0.0F);
    Smooth categoryMotionY = new Smooth(0.0F);
    Smooth moduleValuesMotionY = new Smooth(0.0F);
    Map<Category, Smooth> categoryXAnimation = new EnumMap<>(Category.class);
    Map<Category, Smooth> categoryYAnimation = new EnumMap<>(Category.class);
    Map<Module, Smooth> modulesAnimation = new IdentityHashMap<>();
    Map<Module, Smooth> modulesToggleAnimation = new IdentityHashMap<>();
    Map<Value<?>, Smooth> valuesAnimation = new IdentityHashMap<>();
    String titleDisplayName = "";
    float finalModuleHeight;
    float finalValueHeight;
    boolean clickReturnModules = false;
    boolean clickReturnCategories = false;
    boolean clickOpenCategoryModules = false;
    boolean clickResizeWindow = false;
    boolean clickDragWindow = false;
    Category hoveringCategory = null;
    Module hoveringModule = null;
    Module bindingModule = null;
    Smooth moduleSwapAnimation = new Smooth(0.0F);
    Smooth bindingAnimation = new Smooth(0.0F);
    List<Module> categoryModules;
    List<Value<?>> renderValues;
    BoolValue hoveringBooleanValue;
    NumberValue hoveringFloatValue;
    NumberValue draggingFloatValue;
    ModeValue hoveringModeValue;
    int targetModeValueIndex;
    String bindingModuleName = "";
    private boolean mouseDown = false;
    private float minCatY = 0.0F;
    private Smooth moduleAlphaAnimation = new Smooth(0.0F);
    private long moduleAlphaTimer = 0L;
    private float minValueY = 0.0F;
    private Smooth valuesAlphaAnimation = new Smooth(0.0F);
    private long valuesAlphaTimer = 0L;

    // ---- frame state --------------------------------------------------------------------------

    private float mouseX, mouseY;
    private float deltaMs = 16.7F;
    private long lastFrameNanos;
    private final List<Hit> hits = new ArrayList<>();
    private boolean hasInputPosition;

    /** A clickable area registered while drawing; resolved back-to-front against the event position. */
    private record Hit(float x, float y, float w, float h, Runnable left, Runnable right, Runnable middle) {
        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    /** Naven with 4 categories collapses to 140; keep the same header + 25px row pitch. */
    private static float collapsedH() {
        return 40.0F + Category.values().length * 25.0F;
    }

    /** Naven's SmoothAnimationTimer: value steps by {@code delta * max(10, |diff|*40) * speed / 1000}. */
    static final class Smooth {
        float target;
        float speed = 0.4F;
        float value;
        private float deltaMs = 16.7F;

        Smooth(float target) {
            this.target = target;
            this.value = target;
        }

        Smooth(float target, float value) {
            this.target = target;
            this.value = value;
        }

        void update() {
            float step = Math.max(10.0F, Math.abs(value - target) * 40.0F) * speed;
            float add = deltaMs * (step / 1000.0F);
            if (value < target) {
                value = Math.min(value + add, target);
            } else {
                value = Math.max(value - add, target);
            }
        }
    }

    public NavenScreen() {
        super(Component.literal("Naven"));
        for (Category value : Category.values()) {
            categoryXAnimation.put(value, new Smooth(0.0F));
            categoryYAnimation.put(value, new Smooth(0.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // Naven's init(): mode values replay their enable animation when the screen opens.
        if (Client.instance == null) return;
        for (Category category : Category.values()) {
            for (Module module : Client.instance.getModuleManager().getModulesByCategory(category)) {
                modulesAnimation.computeIfAbsent(module, m -> new Smooth(0.0F, 255.0F));
                modulesToggleAnimation.computeIfAbsent(module, m -> new Smooth(0.0F));
                for (Value<?> value : module.getValues()) {
                    if (value instanceof ModeValue) {
                        Smooth animation = valuesAnimation.computeIfAbsent(value, v -> new Smooth(0.0F));
                        animation.value = 0.0F;
                        animation.target = 255.0F;
                    }
                }
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        if (Client.instance != null) Client.instance.getConfigManager().saveAllConfig();
    }

    // ---- frame --------------------------------------------------------------------------------

    /** Translation of Naven's {@code render(GuiGraphics, mouseX, mouseY, partialTick)}. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float partialTick) {
        super.extractRenderState(g, guiMouseX, guiMouseY, partialTick);

        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            deltaMs = Math.min(50.0F, (now - lastFrameNanos) / 1.0e6F);
        }
        lastFrameNanos = now;
        for (Smooth animation : allAnimations()) animation.deltaMs = deltaMs;

        if (!this.hasInputPosition) {
            this.mouseX = guiMouseX;
            this.mouseY = guiMouseY;
        }
        this.hits.clear();
        this.hoveringModule = null;
        this.clickReturnModules = this.clickReturnCategories = this.clickOpenCategoryModules = false;
        UiFont.useGuiScale();

        if (this.selectedCategory == null) {
            this.widthAnimation.target = 100.0F;
            this.heightAnimation.target = collapsedH();
        } else {
            this.widthAnimation.target = windowWidth;
            this.heightAnimation.target = windowHeight;
        }
        this.widthAnimation.update();
        this.heightAnimation.update();

        Render2D.shadow(g, windowX, windowY, this.widthAnimation.value, this.heightAnimation.value, 5.0F,
                24.0F, 6.0F, 0x60000000);
        Render2D.roundedRect(g, windowX, windowY, this.widthAnimation.value, this.heightAnimation.value, 5.0F,
                Colors.getColor(0, 0, 0, 94));

        if (this.selectedCategory == null) {
            this.hit(windowX, windowY, 100.0F, 40.0F, this::beginWindowDrag, null, null);
        } else {
            this.hit(windowX, windowY, windowWidth, 25.0F, this::beginWindowDrag, null, null);
        }

        for (Category value : Category.values()) {
            Smooth xAnimation = this.categoryXAnimation.get(value);
            Smooth yAnimation = this.categoryYAnimation.get(value);
            if (this.selectedCategory == null) {
                yAnimation.target = 255.0F;
            } else {
                yAnimation.target = 0.0F;
            }
            xAnimation.update();
            yAnimation.update();

            float height = (float) (value.ordinal() * 25) * (yAnimation.value / 255.0F);
            if (yAnimation.target >= 4.0F) {
                int color = ((int) yAnimation.value << 24) | 0xFFFFFF;
                UiFont.Z_ICON_XS.icon(g, iconOf(value), windowX + 14.0F + xAnimation.value,
                        windowY + 47.0F + height, color);
                UiFont.Z_SMALL.drawMiddle(g, value.getName(), windowX + 25.0F + xAnimation.value,
                        windowY + 47.0F + height, color);
            }

            boolean hovering = mouseX >= windowX && mouseY >= windowY + 40.0F + height
                    && mouseX < windowX + 100.0F && mouseY < windowY + 40.0F + height + 20.0F;
            xAnimation.target = hovering ? 5.0F : 0.0F;

            if (yAnimation.value >= 250.0F && hovering) {
                this.hoveringCategory = value;
                this.clickOpenCategoryModules = true;
            }

            if (yAnimation.value >= 250.0F) {
                Category target = value;
                this.hit(windowX, windowY + 40.0F + height, 100.0F, 20.0F, () -> {
                    this.selectedCategory = target;
                    this.categoryMotionY.value = this.categoryMotionY.target = 0.0F;
                    this.moduleSwapAnimation.value = 5.0F;
                    this.moduleSwapAnimation.target = 255.0F;
                }, null, null);
            }
        }

        this.titleAnimation.update();
        this.titleHoverAnimation.update();
        if (this.titleAnimation.value > 5.0F) {
            int alpha = (int) this.titleAnimation.value;
            UiFont.Z_SMALL.drawMiddle(g, this.titleDisplayName, windowX + 6.0F + this.titleHoverAnimation.value,
                    windowY + 10.0F, (alpha << 24) | 0xFFFFFF);
        }

        int brandAlpha = (int) (255.0F - this.titleAnimation.value);
        if (brandAlpha > 0) {
            UiFont.TITLE.drawCentered(g, "Naven", windowX + 50.0F, windowY + 17.0F,
                    (brandAlpha << 24) | 0xFFFFFF);
        }

        if (this.selectedCategory != null) {
            this.titleAnimation.target = 255.0F;
            this.titleDisplayName = "< "
                    + this.selectedCategory.getName()
                    + (this.selectedModule != null ? " / " + this.selectedModule.getName()
                    + " - " + this.selectedModule.getDescription() : "");
            float titleWidth = UiFont.Z_SMALL.width(this.titleDisplayName);
            this.hoveringBack = mouseX >= windowX + 8.0F && mouseY >= windowY + 5.0F
                    && mouseX < windowX + 5.0F + titleWidth && mouseY < windowY + 5.0F + UiFont.Z_SMALL.size();
            this.titleHoverAnimation.target = this.hoveringBack && !this.clickResizeWindow && !this.clickDragWindow
                    ? -2.0F : 0.0F;

            this.hit(windowX + 5.0F, windowY + 3.0F, titleWidth + 5.0F, 14.0F, () -> {
                this.selectedCategory = null;
                this.selectedModule = null;
                this.renderValues = null;
            }, null, null);

            if (this.categoryMotionY.target < -this.finalModuleHeight) {
                this.categoryMotionY.target = -this.finalModuleHeight;
            }
            if (this.categoryMotionY.target > 0.0F) {
                this.categoryMotionY.target = 0.0F;
            }
            this.categoryMotionY.update();
        } else {
            this.titleAnimation.target = 4.0F;
        }

        float clipX = windowX;
        float clipY = windowY + 20.0F;
        float clipW = this.widthAnimation.value;
        float clipH = Math.max(0.0F, this.heightAnimation.value - 20.0F);
        g.enableScissor((int) Math.floor(clipX), (int) Math.floor(clipY),
                (int) Math.ceil(clipX + clipW), (int) Math.ceil(clipY + clipH));

        List<Module> inList = this.selectedCategory == null ? null
                : Client.instance.getModuleManager().getModulesByCategory(this.selectedCategory);
        if (inList != null) {
            this.categoryModules = inList;
            this.moduleSwapAnimation.target = 255.0F;
        } else {
            this.moduleSwapAnimation.target = 5.0F;
        }
        this.moduleSwapAnimation.update();
        if (inList == null && this.moduleSwapAnimation.value < 8.0F) {
            this.categoryModules = null;
        }

        if (this.categoryModules != null) {
            float renderModuleHeight = 0.0F;
            this.minCatY = windowHeight - 25.0F;

            for (Module categoryModule : this.categoryModules) {
                float rowY = windowY + 20.0F + renderModuleHeight + this.categoryMotionY.value;
                boolean isHovering = mouseX >= windowX + 5.0F && mouseY >= windowY + 20.0F
                        && mouseX < windowX + 125.0F && mouseY < windowY + 20.0F + (windowHeight - 25.0F)
                        && mouseY >= rowY && mouseY < rowY + 25.0F
                        && this.moduleSwapAnimation.value > 250.0F
                        && this.bindingModule == null;

                Smooth moduleToggleAnimation = this.modulesToggleAnimation
                        .computeIfAbsent(categoryModule, m -> new Smooth(0.0F));
                moduleToggleAnimation.target = categoryModule.isEnabled()
                        ? this.moduleSwapAnimation.value : 6.0F;
                moduleToggleAnimation.update();

                int alpha = (int) this.moduleSwapAnimation.value;
                int enabledColor = Colors.getColor(54, 98, 236, (int) moduleToggleAnimation.value);
                int disabledColor = Colors.getColor(25, 25, 25, alpha);
                Render2D.roundedRect(g, windowX + 5.0F, rowY, 120.0F, 25.0F, 5.0F, disabledColor);
                Render2D.roundedRect(g, windowX + 5.0F, rowY, 120.0F, 25.0F, 5.0F, enabledColor);

                Smooth moduleAnimation = this.modulesAnimation
                        .computeIfAbsent(categoryModule, m -> new Smooth(0.0F, 255.0F));
                if (isHovering) {
                    moduleAnimation.target = 150.0F;
                    this.hoveringModule = categoryModule;
                } else {
                    moduleAnimation.target = 5.0F;
                }
                moduleAnimation.update();

                int hoveringColor = Colors.getColor(255, 255, 255, (int) moduleAnimation.value / 3);
                Render2D.roundedRect(g, windowX + 5.0F, rowY, 120.0F, 25.0F, 5.0F, hoveringColor);

                UiFont.Z_SMALL.drawMiddle(g, categoryModule.getName(), windowX + 13.0F, rowY + 12.5F,
                        (alpha << 24) | 0xFFFFFF);

                if (this.moduleSwapAnimation.value > 250.0F) {
                    Module targetModule = categoryModule;
                    this.hit(windowX + 5.0F, rowY, 120.0F, 25.0F, targetModule::toggle,
                            () -> this.selectModule(targetModule), () -> this.bindingModule = targetModule);
                }
                renderModuleHeight += 30.0F;
            }

            this.finalModuleHeight = renderModuleHeight + 20.0F - windowHeight;
            this.minCatY -= renderModuleHeight - 5.0F;
            float totalHeight = this.finalModuleHeight + windowHeight;
            if (totalHeight > windowHeight - 25.0F) {
                this.moduleAlphaAnimation.update();
                if (System.currentTimeMillis() - this.moduleAlphaTimer >= 1000L) {
                    this.moduleAlphaAnimation.target = 0.0F;
                } else {
                    this.moduleAlphaAnimation.target = 255.0F;
                }

                float viewable = windowHeight - 25.0F;
                float progress = clamp(-this.categoryMotionY.value / -this.minCatY, 0.0F, 1.0F);
                float ratio = viewable / totalHeight * viewable;
                float barHeight = Math.max(ratio, 20.0F);
                float position = progress * (viewable - barHeight);
                Render2D.roundedRect(g, windowX + 127.0F, windowY + 20.0F + position, 3.0F, barHeight, 1.5F,
                        RenderUtils.reAlpha(3630060, this.moduleAlphaAnimation.value / 255.0F));
            }
        }

        if (this.renderValues != null) {
            boolean isValueInBound = mouseX >= windowX + 140.0F && mouseY >= windowY + 20.0F
                    && mouseX < windowX + 140.0F + (windowWidth - 155.0F)
                    && mouseY < windowY + 20.0F + (windowHeight - 25.0F);
            float motion = this.moduleValuesMotionY.value;
            if (this.moduleValuesMotionY.target < -this.finalValueHeight) {
                this.moduleValuesMotionY.target = -this.finalValueHeight;
            }
            if (this.moduleValuesMotionY.target > 0.0F) {
                this.moduleValuesMotionY.target = 0.0F;
            }
            this.moduleValuesMotionY.update();
            float x = 0.0F;
            float valueHeight = 0.0F;
            this.minValueY = windowHeight - 25.0F;
            this.hoveringBooleanValue = null;

            for (Value<?> value : this.renderValues) {
                if (value.isAvailable() && value instanceof BoolValue booleanValue) {
                    Smooth animation = valuesAnimation.computeIfAbsent(booleanValue, v -> new Smooth(0.0F));
                    animation.target = booleanValue.get() ? 255.0F : 0.0F;
                    animation.update();

                    float currentLength = UiFont.Z_SMALL.width(value.getName()) + 23.0F;
                    if (x + currentLength + 20.0F > windowWidth - 155.0F) {
                        x = 0.0F;
                        valueHeight += 20.0F;
                    }

                    if (isValueInBound
                            && mouseX >= windowX + 130.0F + x
                            && mouseY >= windowY + valueHeight + motion + 20.0F
                            && mouseX < windowX + 130.0F + x + currentLength
                            && mouseY < windowY + valueHeight + motion + 33.0F) {
                        this.hoveringBooleanValue = booleanValue;
                    }

                    int enabledColor = Colors.getColor(54, 98, 236, (int) animation.value);
                    Render2D.roundedRect(g, windowX + 140.0F + x, windowY + valueHeight + motion + 20.0F,
                            12.0F, 12.0F, 2.0F, Colors.getColor(0, 0, 0, 150));
                    Render2D.roundedRect(g, windowX + 142.0F + x, windowY + valueHeight + motion + 22.0F,
                            8.0F, 8.0F, 2.0F, enabledColor);
                    UiFont.Z_SMALL.drawMiddle(g, value.getName(), windowX + 155.0F + x,
                            windowY + valueHeight + motion + 26.0F, 0xFFFFFFFF);
                    BoolValue targetBoolean = booleanValue;
                    this.hit(windowX + 130.0F + x, windowY + valueHeight + motion + 20.0F, currentLength, 13.0F,
                            targetBoolean::toggle, null, null);
                    x += currentLength;
                }
            }

            valueHeight += 10.0F;
            this.hoveringFloatValue = null;

            for (Value<?> valuex : this.renderValues) {
                if (valuex.isAvailable() && valuex instanceof NumberValue floatValue) {
                    Smooth animationx = valuesAnimation.computeIfAbsent(floatValue, v -> new Smooth(0.0F));
                    if (isValueInBound
                            && mouseX >= windowX + 140.0F
                            && mouseY >= windowY + valueHeight + motion + 39.5F
                            && mouseX < windowX + 140.0F + (windowWidth - 155.0F)
                            && mouseY < windowY + valueHeight + motion + 49.5F) {
                        this.hoveringFloatValue = floatValue;
                    }

                    UiFont.Z_SMALL.drawMiddle(g, valuex.getName(), windowX + 140.0F,
                            windowY + valueHeight + motion + 31.0F, 0xFFFFFFFF);
                    String currentValue = (float) Math.round(floatValue.get() * 100.0F) / 100.0F
                            + " / " + trim(floatValue.getMax());
                    UiFont.Z_SMALL.drawRight(g, currentValue, windowX + windowWidth - 15.0F,
                            windowY + valueHeight + motion + 31.0F, 0xFFFFFFFF);
                    float stage = (float) ((floatValue.get() - floatValue.getMin())
                            / (floatValue.getMax() - floatValue.getMin()));
                    int enabledColor = Colors.getColor(54, 98, 236, 255);
                    Render2D.roundedRect(g, windowX + 140.0F, windowY + valueHeight + motion + 42.0F,
                            windowWidth - 155.0F, 5.0F, 3.0F, Colors.getColor(0, 0, 0, 150));
                    animationx.target = (windowWidth - 155.0F) * stage;
                    animationx.update();
                    Render2D.roundedRect(g, windowX + 140.0F, windowY + valueHeight + motion + 42.0F,
                            animationx.value, 5.0F, 3.0F, enabledColor);
                    Render2D.roundedRect(g, windowX + 135.0F + animationx.value,
                            windowY + valueHeight + motion + 39.5F, 10.0F, 10.0F, 5.0F, 0xFFFFFFFF);
                    NumberValue targetNumber = floatValue;
                    this.hit(windowX + 140.0F, windowY + valueHeight + motion + 39.5F,
                            windowWidth - 155.0F, 10.0F,
                            () -> this.draggingFloatValue = targetNumber, null, null);
                    valueHeight += 25.0F;
                }
            }

            this.hoveringModeValue = null;

            for (Value<?> valuexx : this.renderValues) {
                if (valuexx.isAvailable() && valuexx instanceof ModeValue modeValue) {
                    Smooth animationx = valuesAnimation.computeIfAbsent(modeValue, v -> new Smooth(0.0F));
                    animationx.update();
                    UiFont.Z_SMALL.drawMiddle(g, valuexx.getName(), windowX + 140.0F,
                            windowY + valueHeight + motion + 31.0F, 0xFFFFFFFF);
                    x = 0.0F;
                    valueHeight += 15.0F;

                    String[] modes = modeValue.getModes();
                    for (int modeIndex = 0; modeIndex < modes.length; modeIndex++) {
                        String mode = modes[modeIndex];
                        float currentLengthx = UiFont.Z_SMALL.width(mode) + 20.0F;
                        if (x + currentLengthx + 20.0F > windowWidth - 155.0F) {
                            x = 0.0F;
                            valueHeight += 20.0F;
                        }

                        if (isValueInBound
                                && mouseX >= windowX + 140.0F + x
                                && mouseY >= windowY + valueHeight + motion + 25.0F
                                && mouseX < windowX + 140.0F + x + currentLengthx
                                && mouseY < windowY + valueHeight + motion + 38.0F) {
                            this.hoveringModeValue = modeValue;
                            this.targetModeValueIndex = modeIndex;
                        }

                        int enabledColor = Colors.getColor(54, 98, 236,
                                modeValue.is(mode) ? (int) animationx.value : 10);
                        Render2D.roundedRect(g, windowX + 140.0F + x, windowY + valueHeight + motion + 27.0F,
                                10.0F, 10.0F, 5.0F, Colors.getColor(0, 0, 0, 150));
                        Render2D.roundedRect(g, windowX + 141.0F + x, windowY + valueHeight + motion + 28.0F,
                                8.0F, 8.0F, 5.0F, enabledColor);
                        UiFont.Z_SMALL.drawMiddle(g, mode, windowX + 152.0F + x,
                                windowY + valueHeight + motion + 32.0F, 0xFFFFFFFF);
                        ModeValue targetMode = modeValue;
                        int targetIndex = modeIndex;
                        this.hit(windowX + 140.0F + x, windowY + valueHeight + motion + 25.0F, currentLengthx, 13.0F,
                                () -> {
                                    targetMode.set(targetMode.getModes()[targetIndex]);
                                    Smooth animation = valuesAnimation
                                            .computeIfAbsent(targetMode, v -> new Smooth(0.0F));
                                    animation.value = 0.0F;
                                    animation.target = 255.0F;
                                }, null, null);
                        x += currentLengthx;
                    }

                    valueHeight += 20.0F;
                }
            }

            this.finalValueHeight = valueHeight - windowHeight + 25.0F;
            this.minValueY -= valueHeight;
            float valTotalHeight = this.finalValueHeight + windowHeight;
            if (valTotalHeight > windowHeight - 25.0F) {
                this.valuesAlphaAnimation.update();
                if (System.currentTimeMillis() - this.valuesAlphaTimer >= 1000L) {
                    this.valuesAlphaAnimation.target = 0.0F;
                } else {
                    this.valuesAlphaAnimation.target = 255.0F;
                }

                float viewable = windowHeight - 25.0F;
                float progress = clamp(-this.moduleValuesMotionY.value / -this.minValueY, 0.0F, 1.0F);
                float ratio = viewable / valTotalHeight * viewable;
                float barHeight = Math.max(ratio, 20.0F);
                float position = progress * (viewable - barHeight);
                Render2D.roundedRect(g, windowX + windowWidth - 8.0F, windowY + 20.0F + position,
                        3.0F, barHeight, 1.5F,
                        RenderUtils.reAlpha(3630060, this.valuesAlphaAnimation.value / 255.0F));
            }
        }

        if (this.draggingFloatValue != null) {
            float stage = (this.mouseX - windowX - 140.0F) / (windowWidth - 160.0F);
            float valuexxx = (float) (this.draggingFloatValue.getMin()
                    + (this.draggingFloatValue.getMax() - this.draggingFloatValue.getMin()) * stage);
            if (valuexxx < this.draggingFloatValue.getMin()) {
                valuexxx = (float) this.draggingFloatValue.getMin();
            }
            if (valuexxx > this.draggingFloatValue.getMax()) {
                valuexxx = (float) this.draggingFloatValue.getMax();
            }
            this.draggingFloatValue.set((double) valuexxx);
        }

        if (this.clickDragWindow && this.mouseDown) {
            windowX = (float) (windowX + (this.mouseX - this.dragMousePosition[0]));
            windowY = (float) (windowY + (this.mouseY - this.dragMousePosition[1]));
            this.SetDragPosition(this.mouseX, this.mouseY);
        }

        if (this.categoryModules != null && !this.hoveringBack && this.clickResizeWindow && this.mouseDown) {
            windowWidth = (float) (windowWidth + (this.mouseX - this.dragMousePosition[0]));
            windowHeight = (float) (windowHeight + (this.mouseY - this.dragMousePosition[1]));
            if (windowWidth < 500.0F) {
                windowWidth = 500.0F;
            }
            if (windowHeight < 300.0F) {
                windowHeight = 300.0F;
            }
            this.SetDragPosition(this.mouseX, this.mouseY);
        }

        if (this.bindingModule != null) {
            this.bindingAnimation.target = 250.0F;
            this.bindingModuleName = this.bindingModule.getName();
        } else {
            this.bindingAnimation.target = 5.0F;
        }

        this.bindingAnimation.update();
        if (this.bindingAnimation.value > 6.0F) {
            int dim = (int) this.bindingAnimation.value;
            Render2D.rect(g, windowX, windowY, this.widthAnimation.value, this.heightAnimation.value,
                    Colors.getColor(0, 0, 0, dim / 2));
            int alpha = (int) this.bindingAnimation.value;
            String line1 = "Press a key to bind " + this.bindingModuleName;
            String line2 = "(Press ESC to remove/cancel key bind)";
            UiFont.BODY.drawCentered(g, line1, windowX + this.widthAnimation.value / 2.0F,
                    windowY + this.heightAnimation.value / 2.0F - 10.0F, (alpha << 24) | 0xFFFFFF);
            UiFont.Z_SMALL.drawCentered(g, line2, windowX + this.widthAnimation.value / 2.0F,
                    windowY + this.heightAnimation.value / 2.0F + 15.0F, (alpha << 24) | 0xFFFFFF);
        }

        if (this.selectedCategory != null) {
            this.hit(windowX + windowWidth - 10.0F, windowY + windowHeight - 10.0F, 10.0F, 10.0F, () -> {
                this.SetDragPosition(this.mouseX, this.mouseY);
                this.clickResizeWindow = true;
            }, null, null);
            UiFont.Z_ICON_XS.icon(g, Icons.SQUARE, windowX + this.widthAnimation.value - 5.0F,
                    windowY + this.heightAnimation.value - 5.0F, 0x80FFFFFF);
        }

        g.disableScissor();
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public void mouseMoved(double x, double y) {
        this.mouseX = (float) x;
        this.mouseY = (float) y;
        this.hasInputPosition = true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.mouseX = (float) event.x();
        this.mouseY = (float) event.y();
        this.hasInputPosition = true;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.mouseX = (float) event.x();
        this.mouseY = (float) event.y();
        this.hasInputPosition = true;
        int mouseButton = event.button();
        if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
            this.mouseDown = true;
        }

        if (this.bindingModule != null) {
            if (mouseButton == InputConstants.MOUSE_BUTTON_4 || mouseButton == InputConstants.MOUSE_BUTTON_5) {
                this.bindingModule.setKey(-mouseButton);
                this.bindingModule = null;
            }
            return true;
        }

        // Resolve the click against the areas registered while drawing (back-to-front).
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (!hit.contains(this.mouseX, this.mouseY)) continue;
            Runnable action = mouseButton == InputConstants.MOUSE_BUTTON_LEFT ? hit.left
                    : mouseButton == InputConstants.MOUSE_BUTTON_RIGHT ? hit.right
                    : mouseButton == InputConstants.MOUSE_BUTTON_MIDDLE ? hit.middle : null;
            if (action != null) {
                action.run();
                return true;
            }
        }
        return true;
    }

    private void beginWindowDrag() {
        this.SetDragPosition(this.mouseX, this.mouseY);
        this.clickDragWindow = true;
    }

    private void selectModule(Module module) {
        this.selectedModule = module;
        this.renderValues = new ArrayList<>(module.getValues());
        this.moduleValuesMotionY.target = this.moduleValuesMotionY.value = 0.0F;
    }

    private void hit(float x, float y, float w, float h, Runnable left, Runnable right, Runnable middle) {
        this.hits.add(new Hit(x, y, w, h, left, right, middle));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int pButton = event.button();
        if (pButton == InputConstants.MOUSE_BUTTON_LEFT) {
            this.mouseDown = false;
        }
        if (this.clickResizeWindow) {
            this.clickResizeWindow = false;
            this.SetDragPosition(-1, -1);
        }
        if (this.clickDragWindow) {
            this.clickDragWindow = false;
            this.SetDragPosition(-1, -1);
        }
        if (this.draggingFloatValue != null) {
            this.draggingFloatValue = null;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.bindingModule != null) {
            int pKeyCode = event.key();
            if (pKeyCode == InputConstants.KEY_ESCAPE) {
                this.bindingModule.setKey(0);
                this.bindingModule = null;
                return true;
            }
            this.bindingModule.setKey(pKeyCode);
            this.bindingModule = null;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pHorizontalDelta, double pDelta) {
        if (this.bindingModule == null
                && pMouseX >= windowX + 5.0F && pMouseY >= windowY + 20.0F
                && pMouseX < windowX + 105.0F && pMouseY < windowY + 20.0F + (windowHeight - 5.0F)) {
            this.categoryMotionY.target = (float) ((double) this.categoryMotionY.target + pDelta * 15.0);
            this.moduleAlphaTimer = System.currentTimeMillis();
        }

        if (this.renderValues != null
                && this.bindingModule == null
                && pMouseX >= windowX + 140.0F && pMouseY >= windowY + 20.0F
                && pMouseX < windowX + 140.0F + (windowWidth - 155.0F)
                && pMouseY < windowY + 20.0F + (windowHeight - 25.0F)) {
            this.moduleValuesMotionY.target = (float) ((double) this.moduleValuesMotionY.target + pDelta * 15.0);
            this.valuesAlphaTimer = System.currentTimeMillis();
        }

        return true;
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Iterable<Smooth> allAnimations() {
        List<Smooth> all = new ArrayList<>();
        all.add(this.widthAnimation);
        all.add(this.heightAnimation);
        all.add(this.titleAnimation);
        all.add(this.titleHoverAnimation);
        all.add(this.categoryMotionY);
        all.add(this.moduleValuesMotionY);
        all.add(this.moduleSwapAnimation);
        all.add(this.bindingAnimation);
        all.add(this.moduleAlphaAnimation);
        all.add(this.valuesAlphaAnimation);
        all.addAll(this.categoryXAnimation.values());
        all.addAll(this.categoryYAnimation.values());
        all.addAll(this.modulesAnimation.values());
        all.addAll(this.modulesToggleAnimation.values());
        all.addAll(this.valuesAnimation.values());
        return all;
    }

    public void SetDragPosition(double x, double y) {
        this.dragMousePosition[0] = x;
        this.dragMousePosition[1] = y;
    }

    private static char iconOf(Category category) {
        return switch (category) {
            case COMBAT -> Icons.SWORDS;
            case MOVEMENT -> Icons.FOOTPRINTS;
            case WORLD -> Icons.BLOCKS;
            case RENDER -> Icons.EYE;
            case PLAYER -> Icons.USER;
            case MISC -> Icons.ELLIPSIS;
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    /** Naven's Colors.getColor, kept as a local helper. */
    static final class Colors {
        static int getColor(int red, int green, int blue, int alpha) {
            int color = 0;
            color |= alpha << 24;
            color |= red << 16;
            color |= green << 8;
            return color | blue;
        }
    }

    /** Naven's RenderUtils.reAlpha: the argument is RGB, the alpha comes from the factor. */
    static final class RenderUtils {
        static int reAlpha(int color, float alpha) {
            int alphaChannel = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
            return (alphaChannel << 24) | (color & 0xFFFFFF);
        }
    }
}
