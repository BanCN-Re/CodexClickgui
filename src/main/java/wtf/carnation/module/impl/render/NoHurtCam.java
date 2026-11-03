package wtf.carnation.module.impl.render;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class NoHurtCam extends Module {
    private Double previous;
    private boolean applied;

    public NoHurtCam() {
        super("NoHurtCam", Category.RENDER, "关闭受伤时的视角抖动");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        if (applied && mc.options != null && previous != null) {
            mc.options.damageTiltStrength().set(previous);
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
            previous = mc.options.damageTiltStrength().get();
            applied = true;
        }
        mc.options.damageTiltStrength().set(0.0);
    }
}
