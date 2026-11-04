package wtf.carnation.module.impl.render;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.render.Render2DEvent;
import wtf.carnation.graphics.Render2D;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.InventoryUtils;

/**
 * Port of Naven's {@code Compass}.
 *
 * <p>The arrow is drawn as a triangle with Render2D instead of Naven's RoundedRectRenderState;
 * spawn position, bearing and rotation math are unchanged.</p>
 */
public class Compass extends Module {
    public final BoolValue compassOnly = new BoolValue("Compass Only", true);
    public final BoolValue noPlayerOnly = new BoolValue("No Player Only", true);
    private boolean hasCompass = false;
    private BlockPos spawnPosition;
    private float renderYaw;
    private double renderX;
    private double renderZ;

    public Compass() {
        super("Compass", Category.RENDER, "Shows a compass");
    }

    private BlockPos getSpawnPosition() {
        if (mc.level == null) return null;
        return mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                ? mc.level.getRespawnData().globalPos().pos() : null;
    }

    private boolean hasPlayer() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != mc.player && entity instanceof Player) return true;
        }
        return false;
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.level == null) return;
        this.hasCompass = InventoryUtils.hasItem(Items.COMPASS);
        this.spawnPosition = this.getSpawnPosition();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        float partial = event.partialTicks();
        this.renderX = Mth.lerp(partial, mc.player.xOld, mc.player.getX());
        this.renderZ = Mth.lerp(partial, mc.player.zOld, mc.player.getZ());
        this.renderYaw = Mth.lerp(partial, mc.player.yRotO, mc.player.getYRot());
        this.draw(event);
    }

    private void draw(Render2DEvent event) {
        if (this.compassOnly.get() && !this.hasCompass) return;
        if (this.noPlayerOnly.get() && this.hasPlayer()) return;
        if (this.spawnPosition == null) return;

        double dx = this.spawnPosition.getX() - this.renderX;
        double dz = this.spawnPosition.getZ() - this.renderZ;
        if (Math.abs(dx) + Math.abs(dz) < 0.01) return;

        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float angle = Mth.wrapDegrees(bearing - this.renderYaw);
        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getGuiScaledHeight() / 2.0F;

        var graphics = event.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(angle));
        graphics.pose().translate(-centerX, -centerY);
        drawArrow(graphics, centerX, centerY - 45.0F, 10.0F, 5.0F, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }

    private static void drawArrow(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                  float tipX, float tipY, float size, float halfWidth, int color) {
        Render2D.quad(graphics,
                tipX, tipY,
                tipX + halfWidth, tipY + size,
                tipX, tipY + size * 0.8F,
                tipX - halfWidth, tipY + size,
                color, color, color, color);
    }
}
