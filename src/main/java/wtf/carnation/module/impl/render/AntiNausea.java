package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Port of Naven's {@code AntiNausea}; the effect gate lives in MixinLivingEntity. */
public class AntiNausea extends Module {
    public AntiNausea() {
        super("AntiNausea", Category.RENDER, "Prevents nausea");
    }
}
