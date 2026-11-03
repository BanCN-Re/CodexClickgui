package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

/** Bottom-center toggle toast, modelled after Naven's Island panel. */
public class Island extends Module {
    private final NumberValue duration = new NumberValue("Duration", 2500, 500, 6000, 250);

    public Island() {
        super("Island", Category.RENDER, "底部居中的模块开关提示");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (Module.lastToggled == null) return;
        long elapsed = System.currentTimeMillis() - Module.lastToggledAt;
        if (elapsed > duration.get()) return;

        float fade = (float) Math.min(1.0, (duration.get() - elapsed) / 400.0);
        if (fade <= 0) return;

        String name = Module.lastToggled.getName();
        String state = Module.lastToggledState ? "ON" : "OFF";
        int accent = Module.lastToggledState ? 0xFF7C5CFF : 0xFFE76E6E;

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float screenH = mc.getWindow().getHeight();
        float width = UiFont.SEMIBOLD.width(name) + 60;
        float left = (screenW - width) / 2;
        float top = screenH - 90;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        int alpha = (int) (0xC0 * fade);
        Render2D.roundedRect(g, left, top, width, 26, 8, (alpha << 24) | 0x121218);
        Render2D.roundedRect(g, left, top, 3, 26, 1.5F, (alpha << 24) | (accent & 0xFFFFFF));
        UiFont.SEMIBOLD.drawMiddle(g, name, left + 14, top + 13, 0xFFEDEDF5);
        UiFont.SEMIBOLD.drawRight(g, state, left + width - 12, top + 13,
                (alpha << 24) | (accent & 0xFFFFFF));
        g.pose().popMatrix();
    }
}
