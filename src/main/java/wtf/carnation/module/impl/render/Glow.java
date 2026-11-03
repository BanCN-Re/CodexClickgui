package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.EspRender;

/** Outline glow: draws a wall-independent box around players. */
public class Glow extends Module {
    private final NumberValue range = new NumberValue("Range", 48, 8, 128, 4);

    public Glow() {
        super("Glow", Category.RENDER, "给玩家描边，穿墙可见");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        GuiGraphicsExtractor g = event.graphics();
        EspRender.begin(g);
        for (Player player : mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            if (player == mc.player) continue;
            EspRender.box(g, player, 0xFF7C5CFF, false);
        }
        EspRender.end(g);
    }
}
