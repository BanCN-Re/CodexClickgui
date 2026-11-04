package wtf.carnation.module.impl.movement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.StuckInBlockEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Port of Naven's {@code tech.naven.modules.impl.move.FastWeb}. */
public class FastWeb extends Module {
    private int playerInWebTick = 0;
    private int ticksInWeb = 0;

    public FastWeb() {
        super("FastWeb", Category.MOVEMENT, "Allows you to walk faster on cobwebs");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (e.isPre() || mc.player == null) return;
        if (this.playerInWebTick < mc.player.tickCount) this.ticksInWeb = 0;
    }

    @EventTarget
    public void onJump(MoveInputEvent e) {
        if (this.ticksInWeb > 1) e.setJump(false);
    }

    @EventTarget
    public void onStuck(StuckInBlockEvent e) {
        if (mc.player == null) return;
        if (e.getState().getBlock() == Blocks.COBWEB) {
            this.playerInWebTick = mc.player.tickCount;
            this.ticksInWeb++;
            if (this.ticksInWeb > 5) {
                e.setMultiplier(new Vec3(0.88, 1.88, 0.88));
            }
        }
    }
}
