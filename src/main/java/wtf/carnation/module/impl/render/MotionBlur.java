package wtf.carnation.module.impl.render;

import net.minecraft.resources.Identifier;
import wtf.carnation.module.Category;
import wtf.carnation.module.Module;
import wtf.carnation.module.value.impl.NumberValue;
import wtf.carnation.utils.MoveUtil;

/** Real post effect: directional screen blur while moving fast. */
public class MotionBlur extends Module {
    private final NumberValue threshold = new NumberValue("Min Speed", 0.15, 0.05, 1.0, 0.05);

    public MotionBlur() {
        super("MotionBlur", Category.RENDER, "高速移动时的屏幕方向模糊（PostChain 后处理）");
    }

    public boolean active() {
        return mc.player != null && MoveUtil.getSpeed() >= threshold.get();
    }

    public Identifier effectId() {
        return Identifier.fromNamespaceAndPath("carnation", "motion_blur");
    }
}
