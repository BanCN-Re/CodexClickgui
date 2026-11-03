package wtf.carnation.module.impl.movement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.movement.MoveInputEvent;
import wtf.carnation.event.impl.movement.StuckInBlockEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Moves faster while stuck in a cobweb. */
public class FastWeb extends Module {
    private int playerInWebTick = -1;
    private int ticksInWeb;

    public FastWeb() {
        super("FastWeb", Category.MOVEMENT, "在蜘蛛网里也能快速移动");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null) return;
        if (this.playerInWebTick < mc.player.tickCount) {
            this.ticksInWeb = 0;
        }
    }

    @EventTarget
    public void onJump(MoveInputEvent event) {
        if (this.ticksInWeb > 1) {
            event.setJump(false);
        }
    }

    @EventTarget
    public void onStuck(StuckInBlockEvent event) {
        if (mc.player == null) return;
        if (!event.getState().is(Blocks.COBWEB)) return;
        this.playerInWebTick = mc.player.tickCount;
        this.ticksInWeb++;
        if (this.ticksInWeb > 5) {
            event.setMultiplier(new Vec3(0.88, 1.88, 0.88));
        }
    }
}
