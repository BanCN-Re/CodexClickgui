package wtf.carnation.module.impl.movement;

import net.minecraft.world.phys.AABB;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

public class SafeWalk extends Module {
    private final NumberValue sensitivity = new NumberValue("Sensitivity", 0.3, 0.05, 0.9, 0.05);

    public SafeWalk() {
        super("SafeWalk", Category.MOVEMENT, "走到方块边缘时自动潜行，防止掉落");
    }

    public static boolean isOnBlockEdge(float sensitivity) {
        if (mc.player == null || mc.level == null) return false;
        AABB box = mc.player.getBoundingBox().move(0.0, -0.5, 0.0)
                .inflate(-sensitivity, 0.0, -sensitivity);
        return !mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        mc.options.keyShift.setDown(mc.player.onGround() && isOnBlockEdge(sensitivity.getFloat()));
    }

    @Override
    protected void onDisable() {
        mc.options.keyShift.setDown(false);
    }
}
