package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;

public class PearlInfo extends Module {
    private final BoolValue background = new BoolValue("Background", true);

    public PearlInfo() {
        super("PearlInfo", Category.RENDER, "显示背包内末影珍珠数量");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        int pearls = 0;
        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.ENDER_PEARL)) {
                pearls += inventory.getItem(slot).getCount();
            }
        }

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float screenH = mc.getWindow().getHeight();
        String text = "Pearls " + pearls;
        float width = UiFont.SMALL.width(text) + 16;
        float left = screenW - width - 8;
        float top = screenH - 34;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        if (background.get()) {
            Render2D.roundedRect(g, left, top, width, 20, 5, 0xB0121218);
            Render2D.roundedOutline(g, left, top, width, 20, 5, 1, 0xFF262631);
        }
        UiFont.SMALL.drawCentered(g, text, left + width / 2, top + 10,
                pearls > 0 ? 0xFF7C5CFF : 0xFF8C8C9C);
        g.pose().popMatrix();
    }
}
