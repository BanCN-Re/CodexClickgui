package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Port of Naven's {@code AntiBlindness}; the effect gate lives in MixinLivingEntity. */
public class AntiBlindness extends Module {
    public AntiBlindness() {
        super("AntiBlindness", Category.RENDER, "Prevents blindness");
    }
}
