package wtf.carnation.module.impl.misc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

/** Places blocks at a higher rate than the vanilla use delay allows. */
public class FastPlace extends Module {
    private final NumberValue cps = new NumberValue("CPS", 10, 5, 20, 1);
    private float counter;

    public FastPlace() {
        super("FastPlace", Category.MISC, "加速放置方块");
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || mc.player == null || mc.gameMode == null) return;
        if (mc.options.keyUse.isDown() && mc.player.getMainHandItem().getItem() instanceof BlockItem) {
            this.counter += this.cps.get() / 20.0F;
            if (this.counter >= 1.0F / this.cps.get()) {
                this.counter--;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
        } else {
            this.counter = 0;
        }
    }
}
