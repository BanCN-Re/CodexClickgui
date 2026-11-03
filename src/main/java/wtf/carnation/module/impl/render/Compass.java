package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

public class Compass extends Module {
    private static final String[] NAMES = {"S", "W", "N", "E"};
    private final BoolValue background = new BoolValue("Background", true);
    private final NumberValue y = new NumberValue("Y", 10, 0, 400, 1);

    public Compass() {
        super("Compass", Category.RENDER, "顶部罗盘，显示当前朝向");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float width = 220, height = 22;
        float left = (screenW - width) / 2;
        float top = y.getFloat();

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        if (background.get()) {
            Render2D.roundedRect(g, left, top, width, height, 6, 0xB0121218);
            Render2D.roundedOutline(g, left, top, width, height, 6, 1, 0xFF262631);
        }
        float center = left + width / 2;
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        for (int i = 0; i < 4; i++) {
            float target = i * 90.0F;
            float offset = Mth.wrapDegrees(target - yaw);
            if (Math.abs(offset) > 70) continue;
            float x = center + offset * 1.4F;
            boolean major = offset > -3 && offset < 3;
            UiFont.SMALL.drawCentered(g, NAMES[i], x, top + height / 2,
                    major ? 0xFF7C5CFF : 0xFF8C8C9C);
        }
        Render2D.rect(g, center - 1, top + height - 4, 2, 3, 0xFF7C5CFF);
        UiFont.SMALL.drawCentered(g, String.format("%.0f", yaw), center, top + 8, 0xFF8C8C9C);
        g.pose().popMatrix();
    }
}
