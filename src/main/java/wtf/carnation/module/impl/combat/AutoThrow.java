package wtf.carnation.module.impl.combat;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.BoolValue;
import wtf.carnation.module.value.impl.NumberValue;

/** Charges the use item while the mouse is held and releases it after the set ticks. */
public class AutoThrow extends Module {
    private final NumberValue delay = new NumberValue("Delay", 20, 1, 40, 1);
    private final BoolValue autoSwitch = new BoolValue("Auto Switch", false);
    private final NumberValue switchSlot = new NumberValue("Switch Slot", 0, 0, 8, 1);

    private boolean charging;
    private int ticks;

    public AutoThrow() {
        super("AutoThrow", Category.COMBAT, "蓄力后自动松手投掷");
    }

    @Override
    protected void onDisable() {
        mc.options.keyUse.setDown(false);
        this.charging = false;
        this.ticks = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;
        boolean useDown = mc.options.keyUse.isDown();
        if (!useDown) {
            if (this.charging) {
                mc.options.keyUse.setDown(false);
                this.charging = false;
                this.ticks = 0;
            }
            return;
        }
        if (!this.charging) {
            this.charging = true;
            this.ticks = 0;
            if (this.autoSwitch.get()) {
                mc.player.getInventory().setSelectedSlot(this.switchSlot.getInt());
            }
            return;
        }
        if (++this.ticks >= this.delay.getInt()) {
            mc.options.keyUse.setDown(false);
            this.charging = false;
            this.ticks = 0;
        }
    }
}
