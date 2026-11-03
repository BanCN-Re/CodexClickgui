package wtf.carnation.module.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.EspRender;

/** Class name avoids the clash with {@code net.minecraft.world.entity.projectile.Projectile}. */
public class ProjectileESP extends Module {
    private final NumberValue range = new NumberValue("Range", 32, 8, 96, 4);

    public ProjectileESP() {
        super("Projectile", Category.RENDER, "标记飞行中的箭、珍珠与投掷物");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        GuiGraphicsExtractor g = event.graphics();
        EspRender.begin(g);
        for (net.minecraft.world.entity.projectile.Projectile projectile
                : mc.level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            double distance = mc.player.distanceTo(projectile);
            String text = projectile.getType().getDescription().getString()
                    + String.format(" %.0fm", distance);
            EspRender.label(g, projectile, text, 0.3F, 0xFF7C5CFF);
        }
        EspRender.end(g);
    }
}
