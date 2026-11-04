package wtf.carnation.module.impl.movement;

import mixin.LivingEntityAccessor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Port of Naven's {@code tech.naven.modules.impl.move.NoJumpDelay}. */
public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("NoJumpDelay", Category.MOVEMENT, "Removes the delay when jumping");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;
        ((LivingEntityAccessor) mc.player).carnation$setNoJumpDelay(0);
    }
}
