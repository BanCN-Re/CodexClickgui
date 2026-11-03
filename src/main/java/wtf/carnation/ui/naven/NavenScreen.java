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
import java.util.List;
import java.util.Map;

/**
 * Port of Naven's ClickGUI.
 *
 * <p>Everything below mirrors {@code awa.qwq.ovo.Naven.ui.ClickGUI}: the 400x250 glassy
 * window, the 100-wide category column with its icons, the 120x25 module rows in the theme
 * blue, the settings column that starts at x+140 and the smooth timers that drive the
 * expand/collapse and hover transitions.</p>
 */
public class NavenScreen extends Screen {

    private static final float WINDOW_W = 400, WINDOW_H = 250;
    private static final float COLLAPSED_W = 100, COLLAPSED_H = 185;
    private static final float MODULE_W = 120, MODULE_H = 25, MODULE_GAP = 5;
    private static final float PANEL_X = 140, PANEL_W = WINDOW_W - 155;

    private static final int ACCENT = 0xFF3662EC;
    private static final int ROW_OFF = 0xFF191919;
    private static final int TEXT = 0xFFFFFFFF;
    /** Dark, nearly opaque panel — this is how the original reads on screen. */
    private static final int WINDOW_BG = 0xF01A1A22;

    private record Hit(float x, float y, float w, float h, Runnable left, Runnable right, Module module) {
        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    /** Same easing as Naven's SmoothAnimationTimer. */
    private static final class Smooth {
        float value;
        float target;

        Smooth(float value) {
            this.value = value;
            this.target = value;
        }

        void update() {
            float diff = target - value;
            float step = Math.min(1.0F, 0.22F + Math.abs(diff) * 0.03F);
            value += diff * step;
            if (Math.abs(diff) < 0.5F) value = target;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Category, Smooth> categoryAlpha = new EnumMap<>(Category.class);
    private final Map<Category, Smooth> categoryShift = new EnumMap<>(Category.class);
    private final Map<Module, Smooth> moduleHover = new java.util.IdentityHashMap<>();
    private final Map<Module, Smooth> moduleToggle = new java.util.IdentityHashMap<>();

    private final Smooth widthAnim = new Smooth(COLLAPSED_W);
    private final Smooth heightAnim = new Smooth(COLLAPSED_H);
    private final Smooth titleAnim = new Smooth(4);
    private final Smooth titleShift = new Smooth(0);
    private final Smooth moduleSwap = new Smooth(5);
    private final Smooth valueAnim = new Smooth(0);

    private float winX = 100, winY = 100;
    private float windowW = WINDOW_W, windowH = WINDOW_H;
    private Category selected;
    private Module selectedModule;
    private Module bindingModule;
    private boolean resizing;
    private float resizeStartW, resizeStartH;
    private float moduleScroll, valueScroll;
    private float dragOffsetX, dragOffsetY;
    private boolean dragging;
    private NumberValue draggingValue;
    private float sliderX, sliderW;
    private float mouseX, mouseY;

    public NavenScreen() {
        super(Component.literal("Naven"));
        for (Category category : Category.values()) {
            categoryAlpha.put(category, new Smooth(255));
            categoryShift.put(category, new Smooth(0));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float partialTick) {
        super.extractRenderState(g, guiMouseX, guiMouseY, partialTick);
        UiFont.useGuiScale();
        mouseX = guiMouseX;
        mouseY = guiMouseY;
        hits.clear();

        boolean open = selected != null;
        widthAnim.target = open ? windowW : COLLAPSED_W;
        heightAnim.target = open ? windowH : COLLAPSED_H;
        widthAnim.update();
        heightAnim.update();
        titleAnim.target = open ? 255 : 4;
        titleAnim.update();
        titleShift.update();
        moduleSwap.target = open ? 255 : 5;
        moduleSwap.update();
        valueAnim.target = selectedModule != null ? 255 : 0;
        valueAnim.update();

        drawWindow(g);
    }

    private void drawWindow(GuiGraphicsExtractor g) {
        float width = widthAnim.value;
        float height = heightAnim.value;

        // glassy window: a dark panel like the original
        Render2D.roundedRect(g, winX, winY, width, height, 5, WINDOW_BG);

        // drag area (header)
        addHit(winX, winY, width, open() ? 25 : 40, () -> {
            dragging = true;
            dragOffsetX = mouseX - winX;
            dragOffsetY = mouseY - winY;
        }, null);

        // title / breadcrumb
        if (!open()) {
            int alpha = (int) (255 - titleAnim.value);
            UiFont.Z_BOLD.drawCentered(g, "Naven", winX + COLLAPSED_W / 2, winY + 12,
                    (alpha << 24) | (TEXT & 0xFFFFFF));
        } else {
            String title = "< " + selected.getName()
                    + (selectedModule != null ? " / " + selectedModule.getName()
                    + " - " + selectedModule.getDescription() : "");
            int alpha = (int) titleAnim.value;
            UiFont.Z_SMALL.drawMiddle(g, title, winX + 6 + titleShift.value, winY + 11,
                    (alpha << 24) | (TEXT & 0xFFFFFF));
            addHit(winX + 6, winY + 3, UiFont.Z_SMALL.width(title) + 6, 18,
                    () -> {
                        if (selectedModule != null) selectedModule = null;
                        else selected = null;
                    }, null);
        }

        // categories
        for (Category category : Category.values()) {
            Smooth alphaAnim = categoryAlpha.get(category);
            Smooth shiftAnim = categoryShift.get(category);
            alphaAnim.target = open() ? 0 : 255;
            alphaAnim.update();
            shiftAnim.update();

            float offset = category.ordinal() * 25 * (alphaAnim.value / 255.0F);
            float y = winY + 41 + offset;
            if (alphaAnim.value > 1) {
                int color = ((int) alphaAnim.value << 24) | (TEXT & 0xFFFFFF);
                UiFont.ICON_S.icon(g, iconOf(category), winX + 13 + shiftAnim.value, y, color);
                UiFont.Z_SMALL.drawMiddle(g, category.getName(), winX + 25 + shiftAnim.value, y, color);
            }
            boolean hover = !open() && inside(mouseX, mouseY, winX, y - 10, COLLAPSED_W, 20);
            shiftAnim.target = hover ? 5 : 0;
            if (hover && alphaAnim.value > 200) {
                addHit(winX, y - 10, COLLAPSED_W, 20, () -> selected = category, null);
            }
        }

        if (open()) {
            // the module list and the values column are clipped to the window body
            g.enableScissor((int) winX, (int) (winY + 20), (int) (winX + width), (int) (winY + height));
            drawModules(g);
            if (selectedModule != null) drawValues(g);
            g.disableScissor();

            // resize grip in the bottom-right corner, as in Naven (invisible, like the original)
            float gripX = winX + width - 10, gripY = winY + height - 10;
            addHit(gripX, gripY, 10, 10, () -> {
                resizing = true;
                resizeStartW = windowW;
                resizeStartH = windowH;
            }, null);
        }
    }

    private void drawModules(GuiGraphicsExtractor g) {
        List<Module> modules = Client.instance.getModuleManager().getModulesByCategory(selected);
        float listTop = winY + 20;
        float listHeight = windowH - 25;
        float content = modules.size() * (MODULE_H + MODULE_GAP);
        moduleScroll = clamp(moduleScroll, 0, Math.max(0, content - listHeight));
        int listAlpha = (int) moduleSwap.value;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            Smooth toggle = moduleToggle.computeIfAbsent(module, m -> new Smooth(6));
            Smooth hover = moduleHover.computeIfAbsent(module, m -> new Smooth(5));
            toggle.target = module.isEnabled() ? moduleSwap.value : 6;
            toggle.update();

            float y = listTop + i * (MODULE_H + MODULE_GAP) - moduleScroll;
            if (y + MODULE_H < listTop || y > listTop + listHeight) continue;

            boolean isHover = inside(mouseX, mouseY, winX + 5, y, MODULE_W, MODULE_H);
            hover.target = isHover ? 150 : 5;
            hover.update();

            Render2D.roundedRect(g, winX + 5, y, MODULE_W, MODULE_H, 5,
                    (listAlpha << 24) | (ROW_OFF & 0xFFFFFF));
            Render2D.roundedRect(g, winX + 5, y, MODULE_W, MODULE_H, 5,
                    ((int) toggle.value << 24) | (ACCENT & 0xFFFFFF));
            if (hover.value > 1) {
                Render2D.roundedRect(g, winX + 5, y, MODULE_W, MODULE_H, 5,
                        ((int) (hover.value / 3) << 24) | 0xFFFFFF);
            }

            UiFont.Z_SMALL.drawMiddle(g, module.getName(), winX + 13, y + MODULE_H / 2,
                    (listAlpha << 24) | (TEXT & 0xFFFFFF));

            Module target = module;
            addHit(winX + 5, y, MODULE_W, MODULE_H, target::toggle,
                    () -> selectedModule = target == selectedModule ? null : target, target);
        }

        if (content > listHeight) {
            float ratio = listHeight / content;
            float barHeight = Math.max(20, listHeight * ratio);
            float position = (moduleScroll / (content - listHeight)) * (listHeight - barHeight);
            Render2D.roundedRect(g, winX + 127, listTop + position, 3, barHeight, 1.5f, 0xFF37303C);
        }
    }

    private void drawValues(GuiGraphicsExtractor g) {
        List<Value<?>> values = new ArrayList<>();
        for (Value<?> value : selectedModule.getValues()) {
            if (value.isAvailable()) values.add(value);
        }
        if (values.isEmpty()) return;

        float panelWidth = windowW - 155;
        float x0 = winX + PANEL_X;
        float y = winY + 20 + valueScroll;
        float bottom = winY + windowH - 5;

        // booleans as chips
        float chipX = 0;
        for (Value<?> value : values) {
            if (!(value instanceof BoolValue bool)) continue;
            float labelWidth = UiFont.Z_SMALL.width(value.getName()) + 23;
            if (chipX + labelWidth > panelWidth) {
                chipX = 0;
                y += 20;
            }
            if (y + 13 > bottom) break;
            Render2D.roundedRect(g, x0 + chipX, y, 12, 12, 2, 0x96000000);
            if (bool.get()) Render2D.roundedRect(g, x0 + chipX + 2, y + 2, 8, 8, 2, ACCENT);
            UiFont.Z_SMALL.drawMiddle(g, value.getName(), x0 + chipX + 15, y + 6, TEXT);
            addHit(x0 + chipX, y, labelWidth, 13, bool::toggle, null);
            chipX += labelWidth;
        }
        y += 22;

        // floats
        for (Value<?> value : values) {
            if (!(value instanceof NumberValue number)) continue;
            if (y + 25 > bottom) break;
            UiFont.Z_SMALL.drawMiddle(g, value.getName(), x0, y + 8, TEXT);
            String text = number.getDisplayValue() + " / " + trim(number.getMax());
            UiFont.Z_SMALL.drawRight(g, text, winX + windowW - 15, y + 8, TEXT);

            float stage = (float) ((number.get() - number.getMin())
                    / (number.getMax() - number.getMin()));
            Render2D.roundedRect(g, x0, y + 18, panelWidth, 5, 3, 0x96000000);
            Render2D.roundedRect(g, x0, y + 18, panelWidth * stage, 5, 3, ACCENT);
            Render2D.circle(g, x0 + panelWidth * stage, y + 20.5f, 5, TEXT);

            if (inside(mouseX, mouseY, x0, y + 16, panelWidth, 10)) {
                draggingValue = number;
                sliderX = x0;
                sliderW = panelWidth;
            }
            y += 25;
        }

        // modes
        for (Value<?> value : values) {
            if (!(value instanceof ModeValue mode)) continue;
            if (y + 20 > bottom) break;
            UiFont.Z_SMALL.drawMiddle(g, value.getName(), x0, y + 8, TEXT);
            y += 15;
            float chipX2 = 0;
            for (String option : mode.getModes()) {
                float width = UiFont.Z_SMALL.width(option) + 20;
                if (chipX2 + width > panelWidth) {
                    chipX2 = 0;
                    y += 20;
                }
                if (y + 13 > bottom) break;
                Render2D.roundedRect(g, x0 + chipX2, y + 2, 10, 10, 5, 0x96000000);
                if (mode.is(option)) Render2D.roundedRect(g, x0 + chipX2 + 1, y + 3, 8, 8, 5, ACCENT);
                UiFont.Z_SMALL.drawMiddle(g, option, x0 + chipX2 + 12, y + 7, TEXT);
                addHit(x0 + chipX2, y, width, 13, () -> mode.set(option), null);
                chipX2 += width;
            }
            y += 20;
        }
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mouseX = (float) event.x();
        mouseY = (float) event.y();
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mouseX, mouseY)) continue;
            Runnable action = right ? hit.right : hit.left;
            if (action == null) continue;
            action.run();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        mouseX = (float) event.x();
        mouseY = (float) event.y();
        if (dragging) {
            winX = mouseX - dragOffsetX;
            winY = mouseY - dragOffsetY;
            return true;
        }
        if (draggingValue != null) {
            float ratio = (float) Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
            double min = draggingValue.getMin();
            double max = draggingValue.getMax();
            draggingValue.set(min + (max - min) * ratio);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        draggingValue = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (open() && inside((float) x, (float) y, winX, winY + 20, WINDOW_W, WINDOW_H - 25)) {
            moduleScroll = clamp(moduleScroll - (float) scrollY * 25, 0, Float.MAX_VALUE);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private boolean open() {
        return selected != null;
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

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    private void addHit(float x, float y, float w, float h, Runnable left, Runnable right) {
        hits.add(new Hit(x, y, w, h, left, right, null));
    }

    private void addHit(float x, float y, float w, float h, Runnable left, Runnable right, Module module) {
        hits.add(new Hit(x, y, w, h, left, right, module));
    }
}
