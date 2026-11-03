package wtf.carnation.module.impl.render;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class FullBright extends Module {
    private Double previous;
    private boolean applied;

    public FullBright() {
        super("FullBright", Category.RENDER, "把亮度拉到最高，洞穴与夜晚也清晰可见");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        if (applied && mc.options != null && previous != null) {
            mc.options.gamma().set(previous);
        }
        applied = false;
    }

    @EventTarget
    public void onRunTick(RunTickEvent event) {
        if (event.isPre()) apply();
    }

    private void apply() {
        if (mc.options == null) return;
        if (!applied) {
            previous = mc.options.gamma().get();
            applied = true;
        }
        mc.options.gamma().set(1.0);
    }
}
