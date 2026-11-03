package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.graphics.UiFont;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

public class TargetInfo extends Module {
    private final NumberValue range = new NumberValue("Range", 8.0, 3.0, 24.0, 1.0);
    private final BoolValue health = new BoolValue("Health", true);

    public TargetInfo() {
        super("TargetInfo", Category.RENDER, "显示范围内最近玩家的距离与血量");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        Player target = null;
        double best = range.get();
        for (Player candidate : mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            if (candidate == mc.player || !candidate.isAlive()) continue;
            double distance = mc.player.distanceTo(candidate);
            if (distance < best) {
                best = distance;
                target = candidate;
            }
        }
        if (target == null) return;

        String name = target.getName().getString();
        String sub = String.format("%.1fm", best);
        if (health.get()) sub += String.format("  %.0f HP", target.getHealth());

        GuiGraphicsExtractor g = event.graphics();
        float scale = UiFont.useGuiScale();
        int guiScale = mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getWidth();
        float width = Math.max(UiFont.SEMIBOLD.width(name), UiFont.SMALL.width(sub)) + 18;
        float left = (screenW - width) / 2;
        float top = mc.getWindow().getHeight() / 2.0F + 24;

        g.pose().pushMatrix();
        g.pose().scale(scale / guiScale, scale / guiScale);
        Render2D.roundedRect(g, left, top, width, 34, 6, 0xB0121218);
        Render2D.roundedOutline(g, left, top, width, 34, 6, 1, 0xFF262631);
        UiFont.SEMIBOLD.drawCentered(g, name, left + width / 2, top + 11, 0xFFEDEDF5);
        UiFont.SMALL.drawCentered(g, sub, left + width / 2, top + 24, 0xFF7C5CFF);
        g.pose().popMatrix();
    }
}
