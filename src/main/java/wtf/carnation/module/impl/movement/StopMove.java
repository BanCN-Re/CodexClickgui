package wtf.carnation.module.impl.movement;

import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.MoveEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class StopMove extends Module {
    private Vec3 locked = Vec3.ZERO;

    public StopMove() {
        super("StopMove", Category.MOVEMENT, "锁定当前速度，角色停在原地");
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) locked = mc.player.getDeltaMovement();
    }

    @Override
    protected void onDisable() {
        locked = Vec3.ZERO;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        mc.player.setDeltaMovement(locked);
    }

    @EventTarget
    public void onMove(MoveEvent event) {
        event.setVector(locked);
    }
}
