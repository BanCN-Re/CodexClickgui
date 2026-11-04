package wtf.carnation.module.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import mixin.KeyMappingAccessor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Port of Naven's {@code tech.naven.modules.impl.move.SafeWalk}. */
public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", Category.MOVEMENT, "Prevents you from falling off blocks");
    }

    public static boolean isOnBlockEdge(float sensitivity) {
        return !mc.level.getCollisions(mc.player, mc.player.getBoundingBox()
                        .move(0.0, -0.5, 0.0)
                        .inflate(-sensitivity, 0.0, -sensitivity))
                .iterator().hasNext();
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;
        mc.options.keyShift.setDown(mc.player.onGround() && isOnBlockEdge(0.3F));
    }

    @Override
    protected void onDisable() {
        int key = ((KeyMappingAccessor) mc.options.keyShift).carnation$getKey().getValue();
        mc.options.keyShift.setDown(InputConstants.isKeyDown(key));
    }
}
