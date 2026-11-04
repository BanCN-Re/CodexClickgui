package wtf.carnation.module.impl.render;

import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.movement.RunTickEvent;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;

/**
 * Port of Naven's {@code FullBright}.
 *
 * <p>Naven feeds {@code brightness} into {@code GameRenderer.nightVisionScale}, which only reaches
 * the lightmap while the player has the night vision effect. That effect is not usable as the
 * primary gate, so the slider drives the vanilla gamma instead: the original value is captured on
 * the first tick after enabling and restored on disable. 26.3 clamps gamma to [0, 1], so a slider
 * of 0 leaves vanilla brightness untouched and anything above blends towards the ceiling.</p>
 */
public class FullBright extends Module {
    public final NumberValue brightness = new NumberValue("Brightness", 1.0, 0.0, 1.0, 0.1);

    private Double previousGamma;
    private double applied = Double.NaN;

    public FullBright() {
        super("FullBright", Category.RENDER, "Make your world brighter.");
    }

    @Override
    protected void onDisable() {
        if (mc.options != null && previousGamma != null) {
            mc.options.gamma().set(previousGamma);
        }
        previousGamma = null;
        applied = Double.NaN;
    }

    @EventTarget
    public void onTick(RunTickEvent event) {
        if (!event.isPre() || mc.options == null) return;
        if (previousGamma == null) previousGamma = mc.options.gamma().get();

        double target = previousGamma + (1.0 - previousGamma) * brightness.get();
        if (target == applied) return;
        applied = target;
        mc.options.gamma().set(target);
    }
}
