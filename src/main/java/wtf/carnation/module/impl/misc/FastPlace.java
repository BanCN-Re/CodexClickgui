package wtf.carnation.module.impl.misc;

import mixin.MinecraftAccessor;
import net.minecraft.world.item.BlockItem;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

/** Port of Naven's {@code FastPlace}. */
public class FastPlace extends Module {
    private final NumberValue cps = new NumberValue("CPS", 10.0, 5.0, 20.0, 1.0);
    private float counter = 0.0F;

    public FastPlace() {
        super("FastPlace", Category.MISC, "Place blocks faster");
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.player == null) return;
        if (mc.options.keyUse.isDown() && mc.player.getMainHandItem().getItem() instanceof BlockItem) {
            this.counter = this.counter + this.cps.getFloat() / 20.0F;
            if (this.counter >= 1.0F / this.cps.getFloat()) {
                ((MinecraftAccessor) mc).carnation$setRightClickDelay(0);
                this.counter--;
            }
        } else {
            this.counter = 0.0F;
        }
    }
}
