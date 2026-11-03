package wtf.carnation.module.impl.movement;

import mixin.LivingEntityAccessor;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("NoJumpDelay", Category.MOVEMENT, "移除跳跃冷却，落地后可以立刻再跳");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        ((LivingEntityAccessor) (Object) mc.player).carnation$setNoJumpDelay(0);
    }
}
