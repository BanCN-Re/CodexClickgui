package wtf.carnation.module.impl.movement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.StuckInBlockEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

public class FastCobweb extends Module {
    private final NumberValue groundMultiplier = new NumberValue("Ground Multiplier", 0.6, 0.0, 1.0, 0.05);

    public FastCobweb() {
        super("FastCobweb", Category.MOVEMENT, "降低蜘蛛网的减速，落地时按倍率恢复水平速度");
    }

    @EventTarget
    public void onStuck(StuckInBlockEvent event) {
        if (mc.player == null) return;
        if (!event.getState().is(Blocks.COBWEB)) return;
        Vec3 vanilla = event.getMultiplier();
        if (mc.player.onGround()) {
            double factor = groundMultiplier.get();
            double x = vanilla.x + (1.0 - vanilla.x) * factor;
            double z = vanilla.z + (1.0 - vanilla.z) * factor;
            event.setMultiplier(new Vec3(x, vanilla.y, z));
        } else {
            event.setMultiplier(new Vec3(1.0, 1.0, 1.0));
        }
    }
}
