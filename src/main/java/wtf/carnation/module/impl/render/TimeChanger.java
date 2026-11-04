package wtf.carnation.module.impl.render;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.MotionEvent;
import wtf.carnation.event.impl.network.PacketEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

/** Port of Naven's {@code TimeChanger}. */
public class TimeChanger extends Module {
    private final NumberValue time = new NumberValue("World Time", 8000.0, 0.0, 24000.0, 1.0);

    public TimeChanger() {
        super("TimeChanger", Category.RENDER, "Change the time of the world");
    }

    /** Read by MixinLevel.getOverworldClockTime. */
    public long time() {
        return time.get().longValue();
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (!e.isPre() || mc.level == null) return;
        mc.level.setTimeFromServer(time());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof ClientboundSetTimePacket) event.setCancelled(true);
    }
}
