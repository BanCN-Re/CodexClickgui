package wtf.carnation.module.impl.movement;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.utils.MoveUtil;

public class LegitSpeed extends Module {
    private static final double MULTIPLIER = 1.002;

    public LegitSpeed() {
        super("LegitSpeed", Category.MOVEMENT, "极小幅提升水平速度，接近原版手感");
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (!mc.player.onGround() || !MoveUtil.isMoving()) return;
        double speed = MoveUtil.getSpeed();
        if (speed > 0.01) MoveUtil.setSpeed(speed * MULTIPLIER);
    }
}
