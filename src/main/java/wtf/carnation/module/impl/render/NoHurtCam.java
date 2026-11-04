package wtf.carnation.module.impl.render;

import wtf.carnation.module.Category;
import wtf.carnation.module.Module;

/** Port of Naven's {@code NoHurtCam}; the camera gate lives in MixinGameRenderer. */
public class NoHurtCam extends Module {
    public NoHurtCam() {
        super("NoHurtCam", Category.RENDER, "Disables the hurt camera effect");
    }
}
