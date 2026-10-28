package wtf.carnation.module.impl.movement;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.UpdateEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", Category.MOVEMENT, "保持疾跑键按下，向前移动时自动疾跑");
    }

    @EventTarget
    public void onUpdate(UpdateEvent e) {
        mc.options.keySprint.setDown(true);
    }

    @Override
    protected void onDisable() {
        // Hand the key back to the physical keyboard state.
        mc.options.keySprint.setDown(false);
    }
}
