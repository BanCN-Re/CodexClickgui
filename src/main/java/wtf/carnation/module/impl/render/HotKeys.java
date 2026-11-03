package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

public class HotKeys extends Module {
    private final BoolValue background = new BoolValue("Background", true);

    public HotKeys() {
        super("HotKeys", Category.RENDER, "左下角显示 WASD / 跳跃 / 潜行 / 疾跑状态");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenH = mc.getWindow().getHeight();

        String[][] keys = {
                {"W", String.valueOf(mc.options.keyUp.isDown())},
                {"A", String.valueOf(mc.options.keyLeft.isDown())},
                {"S", String.valueOf(mc.options.keyDown.isDown())},
                {"D", String.valueOf(mc.options.keyRight.isDown())},
                {"JMP", String.valueOf(mc.options.keyJump.isDown())},
                {"SNK", String.valueOf(mc.options.keyShift.isDown())},
                {"SPR", String.valueOf(mc.options.keySprint.isDown())}
        };

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        float x = 8;
        float y = screenH - 26;
        for (String[] key : keys) {
            boolean down = Boolean.parseBoolean(key[1]);
            float width = UiFont.SMALL.width(key[0]) + 10;
            if (background.get()) {
                Render2D.roundedRect(g, x, y, width, 18, 4,
                        down ? 0xFF2A2540 : 0xB0121218);
            }
            UiFont.SMALL.drawCentered(g, key[0], x + width / 2, y + 9,
                    down ? 0xFFEDEDF5 : 0xFF6E6E7E);
            x += width + 3;
        }
        g.pose().popMatrix();
    }
}
