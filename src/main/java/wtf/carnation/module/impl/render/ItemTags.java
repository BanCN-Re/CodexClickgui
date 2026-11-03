package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.item.ItemEntity;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.EspRender;

public class ItemTags extends Module {
    private final NumberValue range = new NumberValue("Range", 24, 4, 64, 2);

    public ItemTags() {
        super("ItemTags", Category.RENDER, "显示掉落物的名称与数量");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        GuiGraphicsExtractor g = event.graphics();
        EspRender.begin(g);
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            var stack = item.getItem();
            if (stack.isEmpty()) continue;
            String text = stack.getHoverName().getString();
            if (stack.getCount() > 1) text += " x" + stack.getCount();
            EspRender.label(g, item, text, 0.3F, 0xFFEDEDF5);
        }
        EspRender.end(g);
    }
}
