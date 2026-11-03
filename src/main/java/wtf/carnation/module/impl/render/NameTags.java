package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.EspRender;

public class NameTags extends Module {
    private final NumberValue range = new NumberValue("Range", 48, 8, 128, 4);
    private final BoolValue health = new BoolValue("Health", true);

    public NameTags() {
        super("NameTags", Category.RENDER, "在玩家头顶显示名字与血量");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        GuiGraphicsExtractor g = event.graphics();
        EspRender.begin(g);
        for (Player player : mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            if (player == mc.player || !player.isAlive()) continue;
            String text = player.getName().getString();
            if (health.get()) text += String.format(" %.0f", player.getHealth());
            int color = player.getHealth() > 10 ? 0xFF6EE7A8 : 0xFFE76E6E;
            EspRender.label(g, player, text, 0.4F, color);
        }
        EspRender.end(g);
    }
}
