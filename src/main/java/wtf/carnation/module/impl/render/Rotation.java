package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

public class Rotation extends Module {
    private final BoolValue body = new BoolValue("Body", true);

    public Rotation() {
        super("Rotation", Category.RENDER, "显示当前视角与身体朝向");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        String text = String.format("Yaw %.1f  Pitch %.1f", mc.player.getYRot(), mc.player.getXRot());
        if (body.get()) text += String.format("  Body %.1f", mc.player.yBodyRot);

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenH = mc.getWindow().getHeight();
        float width = UiFont.SMALL.width(text) + 16;
        float top = screenH - 52;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        Render2D.roundedRect(g, 8, top, width, 18, 4, 0xB0121218);
        UiFont.SMALL.drawMiddle(g, text, 16, top + 9, 0xFF8C8C9C);
        g.pose().popMatrix();
    }
}
