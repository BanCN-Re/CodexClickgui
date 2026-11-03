package wtf.carnation.module.impl.render;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class NoFOV extends Module {
    private Double previous;
    private boolean applied;

    public NoFOV() {
        super("NoFOV", Category.RENDER, "关闭疾跑、喝药等造成的视野变化");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        if (applied && mc.options != null && previous != null) {
            mc.options.fovEffectScale().set(previous);
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
            previous = mc.options.fovEffectScale().get();
            applied = true;
        }
        mc.options.fovEffectScale().set(0.0);
    }
}
