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
import wtf.carnation.module.value.impl.NumberValue;

public class Watermark extends Module {
    private final BoolValue fps = new BoolValue("FPS", true);
    private final BoolValue coords = new BoolValue("Coords", false);
    private final BoolValue background = new BoolValue("Background", true);
    private final NumberValue x = new NumberValue("X", 8, 0, 800, 1);
    private final NumberValue y = new NumberValue("Y", 8, 0, 600, 1);

    public Watermark() {
        super("Watermark", Category.RENDER, "左上角水印：客户端名、FPS 与坐标");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float px = x.getFloat(), py = y.getFloat();

        StringBuilder text = new StringBuilder("Naven Modern");
        if (fps.get()) text.append("  ").append(mc.getFps()).append(" fps");
        if (coords.get()) {
            text.append(String.format("  %.1f %.1f %.1f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }

        float width = UiFont.SEMIBOLD.width(text.toString()) + 16;
        float height = 24;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        if (background.get()) {
            Render2D.shadow(g, px, py, width, height, 6, 8, 4, 0x60000000);
            Render2D.roundedRect(g, px, py, width, height, 6, 0xD8121218);
            Render2D.roundedOutline(g, px, py, width, height, 6, 1, 0xFF262631);
        }
        UiFont.SEMIBOLD.drawMiddle(g, text.toString(), px + 8, py + height / 2, 0xFFEDEDF5);
        g.pose().popMatrix();
    }
}
