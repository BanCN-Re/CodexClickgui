package wtf.carnation.module.impl.render;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

public class LowFire extends Module {
    private Double previous;
    private boolean applied;

    public LowFire() {
        super("LowFire", Category.RENDER, "压低火焰与方块遮挡的屏幕特效");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        if (applied && mc.options != null && previous != null) {
            mc.options.screenEffectScale().set(previous);
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
            previous = mc.options.screenEffectScale().get();
            applied = true;
        }
        mc.options.screenEffectScale().set(0.0);
    }
}
