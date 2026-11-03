package wtf.carnation.module.impl.movement;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

/** Circles the nearest player inside range by overriding the strafe input. */
public class TargetStrafe extends Module {
    private final NumberValue range = new NumberValue("Range", 3.0, 1.0, 6.0, 0.5);
    private final NumberValue forward = new NumberValue("Forward", 0.2, 0.0, 1.0, 0.05);

    public TargetStrafe() {
        super("TargetStrafe", Category.MOVEMENT, "围绕范围内最近的玩家横向移动");
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null || mc.level == null) return;
        Player target = nearestPlayer();
        if (target == null) return;
        Vec3 diff = target.position().subtract(mc.player.position());
        double angle = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0;
        double delta = Mth.wrapDegrees(angle - mc.player.getYRot());
        event.setForward(forward.getFloat());
        event.setStrafe(delta > 0 ? 1.0F : -1.0F);
    }

    private Player nearestPlayer() {
        Player nearest = null;
        double best = range.get() * range.get();
        for (Player candidate : mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(range.get()))) {
            if (candidate == mc.player) continue;
            double distance = mc.player.distanceToSqr(candidate);
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
