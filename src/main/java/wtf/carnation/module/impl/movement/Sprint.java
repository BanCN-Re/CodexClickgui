package wtf.carnation.module.impl.movement;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.utils.MoveUtil;

public class Sprint extends Module {
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);

    public Sprint() {
        super("Sprint", Category.MOVEMENT, "向前移动时自动保持疾跑");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        boolean moving = MoveUtil.isMoving();
        mc.options.keySprint.setDown(moving && keepSprint.get());
        if (!moving && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
    }
}
